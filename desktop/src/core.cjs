const fs = require('node:fs/promises');
const path = require('node:path');
const { randomUUID } = require('node:crypto');

const defaults = () => ({ version: 1, tracks: [], liked: [], playlists: [], history: [], settings: { volume: 0.7, repeat: 'off', shuffle: false, theme: 'dark', eq: [0, 0, 0, 0, 0, 0], language: 'id' } });
function validateLibrary(data) {
    const strings = value => Array.isArray(value) && value.every(item => typeof item === 'string');
    if (!data || data.version !== 1 || !Array.isArray(data.tracks) ||
        !data.tracks.every(t => t && typeof t.id === 'string' && typeof t.title === 'string' &&
            (t.source === 'local' ? typeof t.path === 'string' && path.isAbsolute(t.path) : t.source === 'youtube' && /^[\w-]{11}$/.test(t.videoId))) ||
        !strings(data.liked) || !strings(data.history) || !Array.isArray(data.playlists) ||
        !data.playlists.every(p => p && typeof p.id === 'string' && typeof p.name === 'string' && strings(p.tracks))) {
        throw new Error('Unsupported library format; existing library has not been overwritten.');
    }
    const settings = { ...defaults().settings, ...data.settings };
    if (!Number.isFinite(settings.volume) || settings.volume < 0 || settings.volume > 1 ||
        !['off', 'all', 'one'].includes(settings.repeat) || typeof settings.shuffle !== 'boolean' ||
        !Array.isArray(settings.eq) || settings.eq.length !== 6 || !settings.eq.every(n => Number.isFinite(n) && Math.abs(n) <= 12)) {
        throw new Error('Invalid library settings; existing library has not been overwritten.');
    }
    return { ...data, settings: { ...settings, theme: 'dark' } };
}
class Library {
    constructor(file) { this.file = file; this.data = defaults(); this.pending = Promise.resolve(); }
    async load() {
        try {
            const data = JSON.parse(await fs.readFile(this.file, 'utf8'));
            this.data = validateLibrary(data);
        } catch (error) { if (error.code !== 'ENOENT') throw error; }
        return this.data;
    }
    async save() {
        const contents = JSON.stringify(this.data);
        this.pending = this.pending.catch(() => {}).then(async () => {
            await fs.mkdir(path.dirname(this.file), { recursive: true });
            const temp = `${this.file}.tmp`;
            await fs.writeFile(temp, contents, { mode: 0o600 });
            await fs.rename(temp, this.file);
        });
        await this.pending;
        return this.data;
    }
    add(track) {
        const previous = this.data.tracks.find(t => t.id === track.id);
        if (previous) Object.assign(previous, track);
        else this.data.tracks.push(track);
        return track;
    }
    get(id) {
        const track = this.data.tracks.find(t => t.id === id);
        if (!track) throw new Error('Lagu tidak ditemukan di koleksi.');
        return track;
    }
    playlist(name) {
        if (typeof name !== 'string' || !name.trim() || name.length > 100) throw new Error('Nama playlist tidak valid.');
        const item = { id: randomUUID(), name: name.trim(), tracks: [] };
        this.data.playlists.push(item);
        return item;
    }
}
function byteRange(header, size) {
    if (!Number.isSafeInteger(size) || size <= 0) return null;
    if (!header) return { start: 0, end: size - 1, partial: false };
    const match = /^bytes=(\d*)-(\d*)$/.exec(header);
    if (!match || (!match[1] && !match[2])) return null;
    const start = match[1] ? Number(match[1]) : Math.max(0, size - Number(match[2]));
    const end = match[1] ? (match[2] ? Math.min(size - 1, Number(match[2])) : size - 1) : size - 1;
    return Number.isSafeInteger(start) && Number.isSafeInteger(end) && start >= 0 && start <= end && start < size ? { start, end, partial: true } : null;
}
function youtubeId(value) {
    if (typeof value !== 'string' || !/^[\w-]{11}$/.test(value)) throw new Error('ID YouTube tidak valid.');
    return value;
}
function safeMediaUrl(value) {
    const u = new URL(value);
    if (u.protocol !== 'https:' || !u.hostname.endsWith('.googlevideo.com')) throw new Error('Host audio tidak diizinkan.');
    return u.href;
}
module.exports = { Library, defaults, validateLibrary, byteRange, youtubeId, safeMediaUrl };
