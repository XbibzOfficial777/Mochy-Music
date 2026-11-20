import { Playback, frequencies, parseLyrics } from './playback.mjs';

const $ = selector => document.querySelector(selector);
const api = window.mochi;
const paths = {
    home: '<path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1Z"/>',
    search: '<circle cx="10.5" cy="10.5" r="7.5"/><path d="m16 16 5 5"/>',
    library: '<rect x="3" y="3" width="4" height="18" rx="1"/><rect x="10" y="3" width="4" height="18" rx="1"/><path d="m17 3 4 17"/>',
    settings: '<path d="m9 3-1 3-3 1-2 3 2 2-1 3 3 2 1 3h4l1-3 3-1 2-3-2-2 1-3-3-2-1-3Z"/><circle cx="10.5" cy="11.5" r="3"/>',
    music: '<path d="M9 18V5l12-2v13M9 8l12-2"/><ellipse cx="6" cy="18" rx="3" ry="2"/><ellipse cx="18" cy="16" rx="3" ry="2"/>',
    play: '<path d="m8 4 12 8-12 8Z" fill="currentColor" stroke="none"/>',
    pause: '<path d="M7 4v16M17 4v16" stroke-width="5"/>',
    next: '<path d="m3 5 12 7-12 7Z" fill="currentColor"/><path d="M19 5v14" stroke-width="3"/>',
    previous: '<path d="m21 5-12 7 12 7Z" fill="currentColor"/><path d="M5 5v14" stroke-width="3"/>',
    down: '<path d="m5 9 7 7 7-7"/>',
    heart: '<path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    download: '<path d="M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5"/>',
    shuffle: '<path d="M3 6h3c5 0 7 12 12 12h3m-4-4 4 4-4 4M3 18h3c2 0 4-3 6-6s4-6 6-6h3m-4-4 4 4-4 4"/>',
    repeat: '<path d="m17 2 4 4-4 4M3 11V8a2 2 0 0 1 2-2h16M7 22l-4-4 4-4m14-3v5a2 2 0 0 1-2 2H3"/>',
    volume: '<path d="M4 9h4l5-5v16l-5-5H4Zm12-2a7 7 0 0 1 0 10m3-13a11 11 0 0 1 0 16"/>',
    close: '<path d="m6 6 12 12M6 18 18 6"/>',
};
function icon(node, name) {
    node.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name] || paths.music}</svg>`;
}
function node(tag, className, text) {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (text !== undefined) element.textContent = text;
    return element;
}
function button(label, action, className = 'pill', iconName) {
    const element = node('button', className, iconName ? undefined : label);
    element.type = 'button';
    element.setAttribute('aria-label', label);
    if (iconName) icon(element, iconName);
    element.addEventListener('click', () => run(action));
    return element;
}
function art(element, track) {
    element.replaceChildren();
    if (track?.artwork && /^(data:image\/(png|jpeg|webp|gif);base64,|https:\/\/i\.ytimg\.com\/)/.test(track.artwork)) {
        const img = node('img');
        img.src = track.artwork;
        img.alt = '';
        img.loading = 'lazy';
        img.addEventListener('error', () => { element.textContent = '♪'; }, { once: true });
        element.append(img);
    } else element.textContent = '♪';
}
function time(value) {
    if (!Number.isFinite(value)) return '0:00';
    return `${Math.floor(value / 60)}:${String(Math.floor(value % 60)).padStart(2, '0')}`;
}
let data, playback, page = 'home', filter = 'all', selectedPlaylist = null;
let searchResults = [], searchQuery = '', searching = false, searchError = '', searchGeneration = 0;
let toastTimer, addTrack, lyrics = [], lyricsTrack = null, shownTrack = null, activeLyric = -1;
function notify(message) {
    const toast = $('#toast');
    toast.textContent = message;
    toast.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { toast.hidden = true; }, 6000);
}
async function run(action) {
    try { await action(); } catch (error) { notify(error.message); }
}
function applyLibrary(next) {
    data = next;
    playback.updateSettings(data.settings);
}
async function saveSettings(changes) {
    applyLibrary(await api.settings(changes));
}
async function refresh() { applyLibrary(await api.library()); }
function heading(title, subtitle) {
    const container = node('div', 'page-heading');
    const titles = node('div');
    if (subtitle) titles.append(node('p', 'eyebrow', subtitle));
    titles.append(node('h1', '', title));
    container.append(titles);
    return container;
}
function empty(title, description, action) {
    const container = node('div', 'empty');
    container.append(node('h2', '', title), node('p', '', description));
    if (action) container.append(action);
    return container;
}
async function importFiles() {
    const result = await api.importFiles();
    applyLibrary(result.data);
    render();
    if (result.errors.length) notify(result.errors.join('\n'));
}
function trackRows(tracks, { queue = false, playlist = null } = {}) {
    const list = node('div', 'track-list');
    tracks.forEach((track, index) => {
        const row = node('div', `track${playback.track?.id === track.id ? ' active' : ''}`);
        row.dataset.trackId = track.id;
        const main = button(`Putar ${track.title}`, () => playback.play(tracks, index), 'track-main');
        const cover = node('span', 'artwork');
        art(cover, track);
        const label = node('span', 'track-label');
        label.append(node('strong', '', track.title), node('small', '', track.artist || 'Artis tidak diketahui'));
        main.replaceChildren(cover, label, node('span', 'track-duration', time(track.duration)));
        row.append(main);
        if (!queue) {
            const liked = button(`Sukai ${track.title}`, async () => {
                applyLibrary(await api.like(track.id)); render(); renderTransport();
            }, 'icon', 'heart');
            liked.setAttribute('aria-pressed', String(data.liked.includes(track.id)));
            row.append(liked, button(`Tambahkan ${track.title} ke playlist`, () => openAdd(track.id), 'icon', 'plus'));
            if (playlist) row.append(button(`Hapus ${track.title} dari playlist`, async () => {
                applyLibrary(await api.playlistRemove(playlist, track.id)); render();
            }, 'icon', 'close'));
        }
        list.append(row);
    });
    return list;
}
function section(title, child) {
    const element = node('section', 'section');
    element.append(node('h2', 'section-title', title), child);
    return element;
}
function cards(tracks) {
    const grid = node('div', 'cards');
    tracks.forEach((track, index) => {
        const card = button(`Putar ${track.title}`, () => playback.play(tracks, index), 'music-card');
        const cover = node('div', 'artwork');
        art(cover, track);
        card.replaceChildren(cover, node('strong', '', track.title), node('small', '', track.artist || 'Artis tidak diketahui'));
        grid.append(card);
    });
    return grid;
}
function home(container) {
    container.append(heading('Dengarkan sekarang', 'Mochi Bibz'));
    const history = data.history.map(id => data.tracks.find(t => t.id === id)).filter(Boolean);
    if (!data.tracks.length) {
        container.append(empty('Musikmu, ruangmu', 'Tambahkan file musik atau cari lagu. Koleksi ini berisi musik yang benar-benar kamu pilih.', button('Tambah musik', importFiles, 'primary')));
        container.append(button('Cari musik', () => navigate('search')));
        return;
    }
    if (history.length) container.append(section('Putar lagi', cards(history.slice(0, 6))));
    container.append(section('Baru ditambahkan', cards(data.tracks.slice(-6).reverse())));
    const liked = data.tracks.filter(t => data.liked.includes(t.id));
    if (liked.length) container.append(section('Lagu disukai', trackRows(liked.slice(0, 6))));
}
function search(container) {
    container.append(heading('Cari musik'));
    const form = node('form', 'search-form');
    const input = node('input');
    input.type = 'search'; input.maxLength = 200; input.placeholder = 'Lagu, artis, atau album'; input.required = true;
    input.value = searchQuery; input.setAttribute('aria-label', 'Cari musik');
    input.addEventListener('input', () => { searchQuery = input.value; });
    const submit = node('button', 'primary', searching ? 'Mencari…' : 'Cari');
    submit.type = 'submit'; submit.disabled = searching;
    form.append(input, submit);
    form.addEventListener('submit', event => {
        event.preventDefault();
        const query = input.value.trim();
        if (!query) return;
        run(async () => {
            const generation = ++searchGeneration;
            searching = true; searchError = ''; searchQuery = query; render();
            try {
                const results = await api.search(query);
                if (generation !== searchGeneration) return;
                searchResults = results;
                await refresh();
            } catch (error) { searchError = error.message; searchResults = []; }
            finally { if (generation === searchGeneration) { searching = false; if (page === 'search') render(); } }
        });
    });
    container.append(form);
    if (searching) { const status = node('p', 'muted', 'Mencari di YouTube…'); status.setAttribute('role', 'status'); container.append(status); }
    else if (searchError) { const error = node('p', 'error', searchError); error.setAttribute('role', 'alert'); container.append(error); }
    else if (searchResults.length) container.append(trackRows(searchResults));
    else container.append(empty(searchQuery ? 'Tidak ada hasil' : 'Temukan musik', 'Hasil berasal dari YouTube. Koneksi internet diperlukan; gangguan layanan ditampilkan, bukan diganti data contoh.'));
}
function library(container) {
    const title = heading('Koleksi');
    title.append(button('Playlist baru', () => { $('#playlist-form').reset(); $('#playlist-dialog').showModal(); }));
    container.append(title);
    const chips = node('div', 'chips');
    for (const [key, label] of [['all', 'Lagu'], ['liked', 'Disukai'], ['downloads', 'Unduhan'], ['playlists', 'Playlist']]) {
        const chip = button(label, () => { filter = key; selectedPlaylist = null; render(); }, '');
        chip.setAttribute('aria-pressed', String(filter === key)); chips.append(chip);
    }
    container.append(chips);
    if (filter === 'playlists') {
        const playlist = data.playlists.find(p => p.id === selectedPlaylist);
        if (playlist) {
            const subheading = node('div', 'section-title');
            subheading.append(node('h2', '', playlist.name), button('Hapus playlist', async () => {
                if (!confirm(`Hapus playlist “${playlist.name}”? File musik tidak dihapus.`)) return;
                applyLibrary(await api.playlistDelete(playlist.id)); selectedPlaylist = null; render();
            }));
            container.append(subheading);
            const tracks = playlist.tracks.map(id => data.tracks.find(t => t.id === id)).filter(Boolean);
            container.append(tracks.length ? trackRows(tracks, { playlist: playlist.id }) : empty('Playlist kosong', 'Gunakan tombol tambah pada lagu untuk mengisi playlist.'));
        } else if (data.playlists.length) {
            const grid = node('div', 'cards');
            for (const item of data.playlists) {
                const card = button(item.name, () => { selectedPlaylist = item.id; render(); }, 'music-card');
                const cover = node('div', 'artwork'); art(cover, data.tracks.find(t => t.id === item.tracks[0]));
                card.replaceChildren(cover, node('strong', '', item.name), node('small', '', `${item.tracks.length} lagu`)); grid.append(card);
            }
            container.append(grid);
        } else container.append(empty('Belum ada playlist', 'Buat playlist untuk menyimpan urutan lagu pilihanmu.'));
        return;
    }
    const tracks = data.tracks.filter(t => filter === 'liked' ? data.liked.includes(t.id) : filter === 'downloads' ? t.source === 'local' || t.downloadPath : true);
    container.append(tracks.length ? trackRows(tracks) : empty('Belum ada lagu', 'Tambahkan musik lokal atau simpan lagu dari pencarian.', button('Tambah musik', importFiles, 'primary')));
}
function settings(container) {
    container.append(heading('Pengaturan'));
    const eq = node('section', 'setting-card');
    eq.append(node('h2', '', 'Equalizer'), node('p', '', 'Enam pita audio. Perubahan langsung diterapkan pada suara yang sedang diputar.'));
    const bands = node('div', 'equalizer');
    frequencies.forEach((frequency, index) => {
        const label = node('label', '', frequency < 1000 ? `${frequency} Hz` : `${frequency / 1000} kHz`);
        const input = node('input'); input.type = 'range'; input.min = -12; input.max = 12; input.step = 1; input.value = data.settings.eq[index];
        input.setAttribute('aria-label', `Equalizer ${frequency} Hz`);
        const output = node('output', '', `${input.value} dB`);
        input.addEventListener('input', () => {
            const values = [...playback.settings.eq]; values[index] = Number(input.value);
            playback.updateSettings({ ...playback.settings, eq: values }); output.textContent = `${input.value} dB`;
        });
        input.addEventListener('change', () => run(() => saveSettings({ eq: playback.settings.eq })));
        label.append(input, output); bands.append(label);
    });
    eq.append(bands, button('Reset equalizer', async () => { await saveSettings({ eq: [0, 0, 0, 0, 0, 0] }); render(); }));
    const diagnostics = node('section', 'setting-card');
    diagnostics.append(node('h2', '', 'Diagnostik audio'), node('p', '', 'Level mengukur sampel PCM setelah decoding dan equalizer, sebelum dikirim ke perangkat audio.'));
    const readings = node('dl', 'diagnostics');
    readings.append(node('dt', '', 'Status'), Object.assign(node('dd', '', 'Berhenti'), { id: 'audio-state' }), node('dt', '', 'Level PCM (RMS)'), Object.assign(node('dd', '', '0.00000'), { id: 'audio-level' }));
    diagnostics.append(readings);
    const about = node('section', 'setting-card');
    about.append(node('h2', '', 'Mochi Bibz Desktop'), node('p', '', 'Audio lokal, YouTube, dan lirik LRCLIB. Koleksi tersimpan di profil aplikasi pada komputer ini. Tema gelap mengikuti Mochy mobile.'));
    container.append(eq, diagnostics, about);
    run(async () => { const version = await api.version(); about.append(node('p', '', `${version.version} · ${version.platform}`)); });
}
function render() {
    const content = $('#content');
    const container = node('div', 'page');
    ({ home, search, library, settings }[page] || home)(container);
    content.replaceChildren(container);
    document.querySelectorAll('.navigation [data-page]').forEach(tab => {
        const active = tab.dataset.page === page;
        tab.classList.toggle('active', active);
        if (active) tab.setAttribute('aria-current', 'page'); else tab.removeAttribute('aria-current');
    });
}
function navigate(next) {
    if (!['home', 'search', 'library', 'settings'].includes(next)) return;
    if (page === next && next === 'library' && selectedPlaylist) selectedPlaylist = null;
    page = next; render(); window.scrollTo({ top: 0, behavior: 'smooth' });
    $('#content').focus({ preventScroll: true });
}
function openAdd(id) {
    addTrack = id;
    const list = $('#add-playlists'); list.replaceChildren();
    if (!data.playlists.length) list.append(node('p', 'muted', 'Buat playlist melalui Koleksi terlebih dahulu.'));
    for (const playlist of data.playlists) list.append(button(playlist.name, async () => {
        applyLibrary(await api.playlistAdd(playlist.id, addTrack)); $('#add-dialog').close(); notify('Ditambahkan ke playlist.');
        if (page === 'library') render();
    }));
    $('#add-dialog').showModal();
}
function renderTransport() {
    const track = playback.track, audio = playback.audio;
    document.querySelectorAll('[data-action]').forEach(control => { control.disabled = !track || playback.loading; });
    $('#now-playing').disabled = !track;
    const playing = !audio.paused && !audio.ended;
    for (const control of document.querySelectorAll('[data-action=toggle]')) {
        icon(control, playing ? 'pause' : 'play'); control.setAttribute('aria-label', playing ? 'Jeda' : playback.error ? 'Coba lagi' : 'Putar');
    }
    if (track?.id !== shownTrack) {
        shownTrack = track?.id;
        $('#mini-title').textContent = track?.title || 'Belum ada musik';
        $('#mini-artist').textContent = track?.artist || 'Pilih lagu untuk mulai';
        $('#full-title').textContent = track?.title || 'Belum ada musik';
        $('#full-artist').textContent = track?.artist || '';
        art($('#mini-art'), track); art($('#full-art'), track);
        lyrics = []; lyricsTrack = null; activeLyric = -1;
        $('#lyrics-lines').replaceChildren(); $('#lyrics-panel > p').textContent = 'Lirik belum dimuat.';
        $('#load-lyrics').hidden = false; $('#load-lyrics').disabled = false;
        if (!track) $('#queue-items').replaceChildren();
        if (track && 'mediaSession' in navigator) navigator.mediaSession.metadata = new MediaMetadata({ title: track.title, artist: track.artist, album: track.album });
    }
    $('#like-current').setAttribute('aria-pressed', String(!!track && data.liked.includes(track.id)));
    $('#download-current').disabled = !track || track.source !== 'youtube' || !!data.tracks.find(t => t.id === track.id)?.downloadPath;
    $('#shuffle').setAttribute('aria-pressed', String(data.settings.shuffle));
    $('#repeat').setAttribute('aria-label', `Ulang: ${data.settings.repeat}`);
    $('#repeat').setAttribute('aria-pressed', String(data.settings.repeat !== 'off'));
    $('#repeat').title = `Ulang: ${data.settings.repeat}`;
    const duration = Number.isFinite(audio.duration) ? audio.duration : 0;
    $('#seek').max = String(duration || 100); $('#seek').value = String(audio.currentTime || 0); $('#seek').disabled = !duration;
    $('#elapsed').textContent = time(audio.currentTime); $('#duration').textContent = time(duration);
    $('#mini-progress').value = duration ? audio.currentTime / duration * 100 : 0;
    $('#volume').value = String(audio.volume);
    $('#playback-status').textContent = playback.error || (playback.loading ? 'Menyiapkan audio…' : playing ? 'Sedang diputar' : 'Dijeda');
    $('#playback-status').classList.toggle('error', !!playback.error);
    if (playback.error) { $('#mini-artist').textContent = playback.error; }
    if ('mediaSession' in navigator) navigator.mediaSession.playbackState = playing ? 'playing' : 'paused';
    const index = lyrics.findLastIndex(line => line.time <= audio.currentTime);
    if (index !== activeLyric) {
        activeLyric = index;
        $('#lyrics-lines').querySelectorAll('p').forEach((line, i) => line.classList.toggle('current', i === index));
        if ($('#player-dialog').open && !$('#lyrics-panel').hidden) $('#lyrics-lines p.current')?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    }
}
async function loadLyrics() {
    const track = playback.track;
    if (!track) return;
    lyricsTrack = track.id;
    $('#load-lyrics').disabled = true; $('#lyrics-panel > p').textContent = 'Mencari lirik…';
    try {
        const result = await api.lyrics(track.id);
        if (playback.track?.id !== track.id || lyricsTrack !== track.id) return;
        lyrics = parseLyrics(result?.syncedLyrics || '');
        const lines = $('#lyrics-lines'); lines.replaceChildren();
        if (lyrics.length) for (const line of lyrics) lines.append(node('p', '', line.text || '♪'));
        else if (result?.plainLyrics) lines.append(node('p', '', result.plainLyrics));
        $('#lyrics-panel > p').textContent = result ? result.instrumental ? 'Instrumental · LRCLIB' : 'Lirik dari LRCLIB' : 'Lirik tidak ditemukan di LRCLIB.';
        if (lyrics.length || result) $('#load-lyrics').hidden = true;
    } catch (error) {
        if (playback.track?.id === track.id) $('#lyrics-panel > p').textContent = error.message;
    } finally { if (playback.track?.id === track.id || !playback.track) $('#load-lyrics').disabled = false; }
}
function panel(name) {
    for (const key of ['lyrics', 'queue']) {
        $(`#${key}-tab`).setAttribute('aria-selected', String(key === name));
        $(`#${key}-panel`).hidden = key !== name;
    }
    if (name === 'queue') $('#queue-items').replaceChildren(trackRows(playback.queue, { queue: true }));
}

async function init() {
    document.querySelectorAll('[data-icon]').forEach(element => icon(element, element.dataset.icon));
    data = await api.library();
    playback = new Playback($('#audio'), api, data.settings);
    playback.addEventListener('change', renderTransport);
    playback.addEventListener('played', () => run(async () => {
        await refresh();
        if (page !== 'search' && page !== 'settings') render();
        if (!$('#queue-panel').hidden) panel('queue');
    }));
    document.querySelectorAll('[data-page]').forEach(tab => tab.addEventListener('click', () => navigate(tab.dataset.page)));
    $('.brand').addEventListener('click', event => { event.preventDefault(); navigate('home'); });
    $('#import').addEventListener('click', () => run(importFiles));
    document.querySelectorAll('[data-action]').forEach(control => control.addEventListener('click', () => run(() => control.dataset.action === 'toggle' ? playback.toggle() : playback.move(control.dataset.action === 'next' ? 1 : -1))));
    $('#now-playing').addEventListener('click', () => { $('#player-dialog').showModal(); renderTransport(); });
    $('#close-player').addEventListener('click', () => $('#player-dialog').close());
    document.querySelectorAll('[data-close]').forEach(control => control.addEventListener('click', () => $(`#${control.dataset.close}`).close()));
    $('#seek').addEventListener('input', event => playback.seek(Number(event.target.value)));
    $('#volume').addEventListener('input', event => { playback.audio.volume = Number(event.target.value); });
    $('#volume').addEventListener('change', () => run(() => saveSettings({ volume: playback.audio.volume })));
    $('#shuffle').addEventListener('click', () => run(async () => {
        await saveSettings({ shuffle: !data.settings.shuffle });
        renderTransport();
    }));
    $('#repeat').addEventListener('click', () => run(async () => {
        await saveSettings({ repeat: ['off', 'all', 'one'][(['off', 'all', 'one'].indexOf(data.settings.repeat) + 1) % 3] });
        renderTransport();
    }));
    $('#like-current').addEventListener('click', () => run(async () => {
        if (playback.track) { applyLibrary(await api.like(playback.track.id)); render(); renderTransport(); }
    }));
    $('#add-current').addEventListener('click', () => { if (playback.track) openAdd(playback.track.id); });
    $('#download-current').addEventListener('click', () => run(async () => {
        if (!playback.track) return;
        const control = $('#download-current'); control.disabled = true;
        try { applyLibrary(await api.download(playback.track.id)); renderTransport(); }
        finally { renderTransport(); }
    }));
    $('#playlist-form').addEventListener('submit', event => {
        event.preventDefault(); run(async () => {
            applyLibrary(await api.playlist($('#playlist-name').value));
            $('#playlist-dialog').close(); filter = 'playlists'; selectedPlaylist = data.playlists.at(-1).id; navigate('library');
        });
    });
    $('#lyrics-tab').addEventListener('click', () => panel('lyrics'));
    $('#queue-tab').addEventListener('click', () => panel('queue'));
    $('#load-lyrics').addEventListener('click', () => run(loadLyrics));
    document.addEventListener('keydown', event => {
        if (event.target.closest('input, textarea, select, button') || event.ctrlKey || event.metaKey || event.altKey) return;
        if (event.code === 'Space') { event.preventDefault(); run(() => playback.toggle()); }
        if (event.code === 'ArrowRight') playback.seek(playback.audio.currentTime + 5);
        if (event.code === 'ArrowLeft') playback.seek(playback.audio.currentTime - 5);
    });
    if ('mediaSession' in navigator) {
        for (const [name, action] of Object.entries({ play: () => playback.resume(), pause: () => playback.audio.pause(), nexttrack: () => playback.move(1), previoustrack: () => playback.move(-1), seekto: details => playback.seek(details.seekTime) })) {
            navigator.mediaSession.setActionHandler(name, details => run(() => action(details)));
        }
    }
    // The visible diagnostics read the same PCM graph that feeds the system audio output.
    setInterval(() => {
        const level = $('#audio-level');
        if (level) level.textContent = playback.signalLevel().toFixed(5);
        const state = $('#audio-state');
        if (state) state.textContent = playback.error || playback.context?.state || 'Belum dimulai';
    }, 100);
    render(); renderTransport();
}
init().catch(error => {
    const errorNode = node('p', 'error', `Aplikasi gagal dibuka: ${error.message}`);
    errorNode.setAttribute('role', 'alert'); $('#content').replaceChildren(errorNode);
});
