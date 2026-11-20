const test = require('node:test');
const assert = require('node:assert/strict');

test('queue boundaries respect repeat and avoid repeating the current shuffled item', async () => {
    const { nextIndex } = await import('../renderer/playback.mjs');
    assert.equal(nextIndex(0, 0, 1, 'off', false), -1);
    assert.equal(nextIndex(2, 3, 1, 'off', false), -1);
    assert.equal(nextIndex(2, 3, 1, 'all', false), 0);
    assert.equal(nextIndex(0, 3, -1, 'all', false), 2);
    assert.equal(nextIndex(0, 3, -1, 'off', false), -1);
    for (let index = 0; index < 5; index++) {
        for (const random of [0, .2, .5, .9999]) {
            const next = nextIndex(index, 5, 1, 'off', true, () => random);
            assert.notEqual(next, index);
            assert.ok(next >= 0 && next < 5);
        }
    }
});
test('search suggestions are trimmed, deduplicated and bounded', async () => {
    const { normalizeSuggestions } = await import('../renderer/suggest.mjs');
    assert.deepEqual(normalizeSuggestions(['  Lagu  pertama ', 'lagu pertama', '', 5, 'Lagu kedua'], 2), ['Lagu pertama', 'Lagu kedua']);
    assert.deepEqual(normalizeSuggestions([null, '   ']), []);
});
test('switching tracks does not emit a bogus missing-source error', async () => {
    const { Playback } = await import('../renderer/playback.mjs');
    const previousAudioContext = global.AudioContext;
    delete global.AudioContext;
    class FakeAudio extends EventTarget {
        constructor() { super(); this.attributes = new Map(); this.paused = true; this.currentTime = 0; this.volume = 1; }
        get src() { return this.attributes.get('src') || ''; }
        set src(value) { this.attributes.set('src', value); }
        getAttribute(name) { return this.attributes.get(name) ?? null; }
        removeAttribute(name) { this.attributes.delete(name); }
        load() { this.loadCalls = (this.loadCalls || 0) + 1; this.dispatchEvent(new Event('error')); }
        async play() { this.paused = false; }
        pause() { this.paused = true; }
    }
    try {
        const audio = new FakeAudio();
        const played = [];
        const playback = new Playback(audio, {
            resolve: async () => 'mochi://audio/token',
            played: async id => played.push(id),
        }, { volume: .7, repeat: 'off', shuffle: false, eq: [0, 0, 0, 0, 0, 0] });
        const tracks = [{ id: 'yt:1' }, { id: 'yt:2' }];
        await playback.play(tracks, 0);
        assert.equal(playback.error, '');
        assert.equal(audio.src, 'mochi://audio/token');
        await playback.play(tracks, 1);
        assert.equal(playback.error, '');
        assert.equal(audio.loadCalls, undefined);
        assert.deepEqual(played, ['yt:1', 'yt:2']);
    } finally { global.AudioContext = previousAudioContext; }
});
test('synced lyrics retain repeated timestamps and sort chronologically', async () => {
    const { parseLyrics } = await import('../renderer/playback.mjs');
    assert.deepEqual(parseLyrics('[ar:Artist]\n[00:12.50][01:12.5]Chorus\n[00:02]Intro\n[00:05.125]'), [
        { time: 2, text: 'Intro' }, { time: 5.125, text: '' }, { time: 12.5, text: 'Chorus' }, { time: 72.5, text: 'Chorus' },
    ]);
    assert.deepEqual(parseLyrics('plain lyrics'), []);
});
