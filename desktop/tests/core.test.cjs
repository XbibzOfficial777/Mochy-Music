const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const { Library, byteRange, youtubeId, safeMediaUrl, validateLibrary, defaults } = require('../src/core.cjs');

test('byte ranges support seeking, suffix requests and clamped ends', () => {
    assert.deepEqual(byteRange(null, 100), { start: 0, end: 99, partial: false });
    assert.deepEqual(byteRange('bytes=20-', 100), { start: 20, end: 99, partial: true });
    assert.deepEqual(byteRange('bytes=-10', 100), { start: 90, end: 99, partial: true });
    assert.deepEqual(byteRange('bytes=10-200', 100), { start: 10, end: 99, partial: true });
    assert.deepEqual(byteRange('bytes=-200', 100), { start: 0, end: 99, partial: true });
});
test('invalid and unsatisfiable byte ranges cannot escape the file', () => {
    for (const range of ['bytes=100-', 'bytes=30-20', 'bytes=-0', 'bytes=-', 'bytes=1-2,4-5', 'bytes=9007199254740992-', 'x=0-1']) {
        assert.equal(byteRange(range, 100), null, range);
    }
    assert.equal(byteRange(null, 0), null);
    assert.equal(byteRange('bytes=0-1', -1), null);
});
test('online identifiers and media URLs reject arbitrary network destinations', () => {
    assert.equal(youtubeId('jNQXAC9IVRw'), 'jNQXAC9IVRw');
    for (const value of ['--help', '../anything', null, 'https://youtube.com/']) assert.throws(() => youtubeId(value));
    assert.equal(safeMediaUrl('https://r1.googlevideo.com/audio'), 'https://r1.googlevideo.com/audio');
    assert.equal(safeMediaUrl('https://googlevideo.com/audio'), 'https://googlevideo.com/audio');
    for (const url of ['http://r1.googlevideo.com/audio', 'file:///etc/passwd', 'https://googlevideo.com.evil.test/', 'https://localhost/', 'https://googlevideo.com@evil.test/']) assert.throws(() => safeMediaUrl(url));
});
test('library mutations survive a new process-equivalent load', async t => {
    const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'mochi-library-'));
    t.after(() => fs.rm(directory, { recursive: true, force: true }));
    const file = path.join(directory, 'library.json');
    const library = new Library(file);
    await library.load();
    library.add({ id: 'local:one', source: 'local', path: path.join(directory, 'one.wav'), title: 'One' });
    library.add({ id: 'local:one', source: 'local', path: path.join(directory, 'one.wav'), title: 'Updated' });
    const playlist = library.playlist('  Favorites  ');
    playlist.tracks.push('local:one');
    library.data.liked.push('local:one');
    const first = library.save();
    library.data.settings.volume = 0.3;
    const second = library.save();
    await Promise.all([first, second]);
    const restored = await new Library(file).load();
    assert.equal(restored.tracks.length, 1);
    assert.equal(restored.tracks[0].title, 'Updated');
    assert.equal(restored.playlists[0].name, 'Favorites');
    assert.deepEqual(restored.playlists[0].tracks, ['local:one']);
    assert.deepEqual(restored.liked, ['local:one']);
    assert.equal(restored.settings.volume, 0.3);
    assert.deepEqual((await fs.readdir(directory)).sort(), ['library.json']);
});
test('corrupt library is reported without being overwritten', async t => {
    const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'mochi-corrupt-'));
    t.after(() => fs.rm(directory, { recursive: true, force: true }));
    const file = path.join(directory, 'library.json');
    for (const contents of ['{broken', JSON.stringify({ version: 2, tracks: [] }), JSON.stringify({ ...defaults(), settings: { volume: 'loud' } })]) {
        await fs.writeFile(file, contents);
        await assert.rejects(new Library(file).load());
        assert.equal(await fs.readFile(file, 'utf8'), contents);
    }
});
test('invalid playlist names and persisted paths fail validation', () => {
    const library = new Library('unused');
    for (const value of ['', '   ', null, 'x'.repeat(101)]) assert.throws(() => library.playlist(value));
    assert.throws(() => validateLibrary({ ...defaults(), tracks: [{ id: 'one', source: 'local', title: 'One', path: '../one' }] }));
    assert.throws(() => validateLibrary({ ...defaults(), settings: { eq: [99, 0, 0, 0, 0, 0] } }));
});
