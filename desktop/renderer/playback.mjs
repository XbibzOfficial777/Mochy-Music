export const frequencies = [60, 230, 910, 3600, 8000, 14000];

export function parseLyrics(text = '') {
    const lines = [];
    for (const line of text.split('\n')) {
        const stamps = [...line.matchAll(/\[(\d+):(\d+(?:\.\d+)?)\]/g)];
        const words = line.replace(/\[[^\]]*\]/g, '').trim();
        for (const stamp of stamps) lines.push({ time: Number(stamp[1]) * 60 + Number(stamp[2]), text: words });
    }
    return lines.sort((a, b) => a.time - b.time);
}

export function nextIndex(index, length, direction, repeat, shuffle, random = Math.random) {
    if (!length) return -1;
    if (shuffle && direction > 0 && length > 1) return (index + 1 + Math.floor(random() * (length - 1))) % length;
    const next = index + direction;
    if (next >= 0 && next < length) return next;
    return repeat === 'all' ? (next + length) % length : -1;
}

// One audio element owns transport. Controls follow media events, never a simulated clock.
export class Playback extends EventTarget {
    constructor(audio, api, settings) {
        super();
        this.audio = audio;
        this.api = api;
        this.settings = settings;
        this.queue = [];
        this.index = -1;
        this.generation = 0;
        this.loading = false;
        this.error = '';
        audio.volume = settings.volume;
        this.context = null;
        this.filters = null;
        for (const event of ['play', 'pause', 'timeupdate', 'durationchange', 'waiting', 'playing', 'seeked', 'volumechange', 'emptied']) {
            audio.addEventListener(event, () => this.changed());
        }
        audio.addEventListener('error', () => {
            if (audio.error) this.fail(new Error(`Audio gagal diputar (${audio.error.code}). Periksa file atau coba lagi.`));
        });
        audio.addEventListener('ended', () => {
            if (this.settings.repeat === 'one') {
                audio.currentTime = 0;
                this.resume().catch(error => this.fail(error));
            } else this.move(1).catch(error => this.fail(error));
        });
    }
    get track() { return this.queue[this.index] || null; }
    changed() { this.dispatchEvent(new Event('change')); }
    fail(error) { this.error = error.message; this.loading = false; this.changed(); }
    ensureGraph() {
        if (this.context) return;
        this.context = new AudioContext();
        this.source = this.context.createMediaElementSource(this.audio);
        this.filters = frequencies.map((frequency, index) => {
            const filter = this.context.createBiquadFilter();
            filter.type = index === 0 ? 'lowshelf' : index === frequencies.length - 1 ? 'highshelf' : 'peaking';
            filter.frequency.value = frequency;
            filter.Q.value = 1;
            filter.gain.value = this.settings.eq[index];
            return filter;
        });
        this.analyser = this.context.createAnalyser();
        this.analyser.fftSize = 2048;
        let output = this.source;
        for (const filter of this.filters) { output.connect(filter); output = filter; }
        output.connect(this.analyser);
        this.analyser.connect(this.context.destination);
    }
    signalLevel() {
        if (!this.analyser) return 0;
        const samples = new Float32Array(this.analyser.fftSize);
        this.analyser.getFloatTimeDomainData(samples);
        return Math.sqrt(samples.reduce((sum, sample) => sum + sample * sample, 0) / samples.length);
    }
    async play(tracks, index = 0) {
        if (!tracks[index]) return;
        const generation = ++this.generation;
        this.audio.pause();
        this.audio.removeAttribute('src');
        this.audio.load();
        this.error = '';
        this.queue = [...tracks];
        this.index = index;
        this.loading = true;
        this.error = '';
        this.changed();
        try {
            this.ensureGraph();
            await this.context.resume();
            const url = await this.api.resolve(this.track.id);
            if (generation !== this.generation) return;
            this.audio.src = url;
            await this.audio.play();
            if (generation !== this.generation) return;
            this.loading = false;
            this.changed();
            await this.api.played(this.track.id);
            this.dispatchEvent(new Event('played'));
        } catch (error) {
            if (generation === this.generation) this.fail(error);
        }
    }
    async resume() {
        this.ensureGraph();
        await this.context.resume();
        await this.audio.play();
    }
    async toggle() {
        if (this.loading || !this.track) return;
        if (this.error || !this.audio.src) return this.play(this.queue, this.index);
        if (this.audio.paused) await this.resume();
        else this.audio.pause();
    }
    async move(direction) {
        if (direction < 0 && this.audio.currentTime > 3) { this.seek(0); return; }
        const index = nextIndex(this.index, this.queue.length, direction, this.settings.repeat, this.settings.shuffle);
        if (index !== -1) await this.play(this.queue, index);
        else this.changed();
    }
    seek(seconds) {
        if (Number.isFinite(this.audio.duration)) this.audio.currentTime = Math.max(0, Math.min(this.audio.duration, seconds));
    }
    updateSettings(settings) {
        this.settings = settings;
        this.audio.volume = settings.volume;
        this.filters?.forEach((filter, index) => { filter.gain.value = settings.eq[index]; });
        this.changed();
    }
}
