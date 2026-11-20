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
test('synced lyrics retain repeated timestamps and sort chronologically', async () => {
    const { parseLyrics } = await import('../renderer/playback.mjs');
    assert.deepEqual(parseLyrics('[ar:Artist]\n[00:12.50][01:12.5]Chorus\n[00:02]Intro\n[00:05.125]'), [
        { time: 2, text: 'Intro' }, { time: 5.125, text: '' }, { time: 12.5, text: 'Chorus' }, { time: 72.5, text: 'Chorus' },
    ]);
    assert.deepEqual(parseLyrics('plain lyrics'), []);
});
