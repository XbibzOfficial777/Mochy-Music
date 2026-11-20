const { test, expect, _electron: electron } = require('@playwright/test');
const fs = require('node:fs/promises');
const path = require('node:path');
const MOCHI_ONLINE = process.env.MOCHI_ONLINE === '1';

// This is a real PCM WAV, decoded by Chromium through the production media protocol.
function wave(seconds, frequency) {
    const rate = 44100, samples = rate * seconds;
    const bytes = Buffer.alloc(44 + samples * 2);
    bytes.write('RIFF', 0); bytes.writeUInt32LE(bytes.length - 8, 4); bytes.write('WAVEfmt ', 8);
    bytes.writeUInt32LE(16, 16); bytes.writeUInt16LE(1, 20); bytes.writeUInt16LE(1, 22);
    bytes.writeUInt32LE(rate, 24); bytes.writeUInt32LE(rate * 2, 28); bytes.writeUInt16LE(2, 32); bytes.writeUInt16LE(16, 34);
    bytes.write('data', 36); bytes.writeUInt32LE(samples * 2, 40);
    for (let i = 0; i < samples; i++) bytes.writeInt16LE(Math.round(Math.sin(i * 2 * Math.PI * frequency / rate) * 9000), 44 + i * 2);
    return bytes;
}
async function launch(profile, files = []) {
    const env = { ...process.env, MOCHI_USER_DATA: profile, ELECTRON_EXTRA_LAUNCH_ARGS: '--autoplay-policy=no-user-gesture-required' };
    delete env.ELECTRON_RUN_AS_NODE;
    const app = await electron.launch({
        ...(process.env.MOCHI_EXECUTABLE ? { executablePath: process.env.MOCHI_EXECUTABLE } : {}),
        args: [...(process.env.MOCHI_EXECUTABLE ? [] : [path.resolve('.')]), ...files],
        env,
        timeout: 60000,
    });
    const page = await app.firstWindow();
    if (!MOCHI_ONLINE) await page.route('**/*', route => {
        const url = route.request().url();
        if (/^(file|mochi|data|blob):/.test(url)) return route.continue();
        return route.abort();
    });
    await expect(page.getByRole('heading', { name: 'Dengarkan sekarang' })).toBeVisible();
    return { app, page };
}
async function screenshot(page, info, name) {
    const file = info.outputPath(`${name}.png`);
    await page.screenshot({ path: file, fullPage: true });
    await info.attach(name, { path: file, contentType: 'image/png' });
}
async function selectTab(page, name) {
    await page.getByRole('navigation').getByRole('button', { name, exact: true }).click();
}
async function proveAudio(page) {
    await expect.poll(() => page.locator('#audio').evaluate(audio => audio.currentTime)).toBeGreaterThan(.4);
    await selectTab(page, 'Pengaturan');
    await expect.poll(() => page.locator('#audio-state').textContent()).toBe('running');
    await expect.poll(async () => Number(await page.locator('#audio-level').textContent())).toBeGreaterThan(.001);
}

test('real local audio, seek, transport, equalizer and persistent library', async ({}, info) => {
    const profile = info.outputPath('profile');
    await fs.mkdir(profile, { recursive: true });
    const files = [info.outputPath('Lagu pertama.wav'), info.outputPath('Lagu kedua.wav')];
    await fs.writeFile(files[0], wave(45, 440)); await fs.writeFile(files[1], wave(45, 660));
    let instance = await launch(profile, files);
    const errors = [];
    instance.page.on('pageerror', error => errors.push(error.message));
    try {
        const { page, app } = instance;
        await screenshot(page, info, 'home-desktop');
        await selectTab(page, 'Koleksi');
        await expect(page.getByRole('button', { name: 'Putar Lagu pertama', exact: true })).toBeVisible();
        await page.getByRole('button', { name: 'Playlist baru', exact: true }).click();
        await page.getByLabel('Nama playlist', { exact: true }).fill('Favorit <b>asli</b>');
        await page.getByRole('button', { name: 'Buat playlist', exact: true }).click();
        await expect(page.getByRole('heading', { name: 'Favorit <b>asli</b>', exact: true })).toBeVisible();
        await expect(page.locator('#content h2 b')).toHaveCount(0);
        await page.getByRole('button', { name: 'Lagu', exact: true }).click();
        await page.getByRole('button', { name: 'Sukai Lagu pertama', exact: true }).click();
        await page.getByRole('button', { name: 'Tambahkan Lagu pertama ke playlist', exact: true }).click();
        await page.locator('#add-dialog').getByRole('button', { name: 'Favorit <b>asli</b>', exact: true }).click();
        await page.getByRole('button', { name: 'Putar Lagu pertama', exact: true }).click();
        await proveAudio(page);
        await screenshot(page, info, 'decoded-pcm');
        const pcm = await page.locator('#audio-level').textContent();
        await info.attach('decoded-pcm-rms', { body: pcm, contentType: 'text/plain' });
        expect(Number(pcm)).toBeGreaterThan(.001);
        await page.getByLabel('Equalizer 60 Hz', { exact: true }).fill('4');
        await page.getByLabel('Equalizer 60 Hz', { exact: true }).dispatchEvent('change');
        await page.getByRole('button', { name: 'Buka pemutar', exact: true }).click();
        await expect(page.locator('#full-title')).toHaveText('Lagu pertama');
        await screenshot(page, info, 'flagship-desktop');
        await page.locator('#play').click();
        await expect.poll(() => page.locator('#audio').evaluate(audio => audio.paused)).toBe(true);
        const paused = await page.locator('#audio').evaluate(audio => audio.currentTime);
        await page.waitForTimeout(500);
        expect(await page.locator('#audio').evaluate(audio => audio.currentTime)).toBe(paused);
        await page.getByLabel('Posisi pemutaran', { exact: true }).fill('10');
        await page.getByLabel('Posisi pemutaran', { exact: true }).dispatchEvent('input');
        await expect.poll(() => page.locator('#audio').evaluate(audio => audio.currentTime)).toBeGreaterThan(9);
        await page.getByLabel('Volume', { exact: true }).evaluate(input => {
            input.value = '0.35';
            input.dispatchEvent(new Event('input', { bubbles: true }));
            input.dispatchEvent(new Event('change', { bubbles: true }));
        });
        await page.locator('#play').click();
        await expect.poll(() => page.locator('#audio').evaluate(audio => audio.paused)).toBe(false);
        await page.getByRole('dialog').getByRole('button', { name: 'Lagu berikutnya', exact: true }).click();
        await expect(page.locator('#full-title')).toHaveText('Lagu kedua');
        await expect.poll(() => page.locator('#audio').evaluate(audio => audio.currentTime)).toBeGreaterThan(.2);
        await page.getByRole('dialog').getByRole('button', { name: 'Lagu sebelumnya', exact: true }).click();
        await expect(page.locator('#full-title')).toHaveText('Lagu pertama');
        await page.getByRole('tab', { name: 'Antrean', exact: true }).click();
        await expect(page.locator('#queue-items .track')).toHaveCount(2);
        await page.getByRole('button', { name: 'Tutup pemutar', exact: true }).click();
        await app.evaluate(({ BrowserWindow }) => {
            const window = BrowserWindow.getAllWindows()[0];
            const [width, height] = window.getSize();
            window.setBounds({ x: 0, y: 0, width, height });
            window.setContentSize(412, 892);
        });
        await selectTab(page, 'Beranda');
        await expect(page.getByRole('heading', { name: 'Dengarkan sekarang' })).toBeVisible();
        await screenshot(page, info, 'home-mobile-width');
        await expect(page.locator('.navigation')).toHaveCSS('height', '64px');
        await expect(page.locator('html')).toHaveCSS('background-color', 'rgb(0, 0, 0)');
        await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        const security = await page.evaluate(() => ({ require: typeof window.require, process: typeof window.process }));
        expect(security).toEqual({ require: 'undefined', process: 'undefined' });
        expect(errors).toEqual([]);
        await app.close();
        instance = await launch(profile);
        await selectTab(instance.page, 'Koleksi');
        await instance.page.getByRole('button', { name: 'Disukai', exact: true }).click();
        await expect(instance.page.getByRole('button', { name: 'Putar Lagu pertama', exact: true })).toBeVisible();
        await instance.page.getByRole('button', { name: 'Playlist', exact: true }).click();
        await instance.page.getByRole('button', { name: 'Favorit <b>asli</b>', exact: true }).click();
        await expect(instance.page.getByRole('button', { name: 'Putar Lagu pertama', exact: true })).toBeVisible();
        const saved = JSON.parse(await fs.readFile(path.join(profile, 'library.json'), 'utf8'));
        expect(saved.settings.volume).toBe(.35);
        expect(saved.settings.eq[0]).toBe(4);
        expect(saved.history.length).toBeGreaterThan(0);
        await instance.page.getByRole('button', { name: 'Putar Lagu pertama', exact: true }).click();
        await proveAudio(instance.page);
        await screenshot(instance.page, info, 'persisted-library-audio');
    } finally { await instance.app.close().catch(() => {}); }
});

test('missing audio reports a real error instead of simulated progress', async ({}, info) => {
    const profile = info.outputPath('profile'); await fs.mkdir(profile, { recursive: true });
    const file = info.outputPath('Akan hilang.wav'); await fs.writeFile(file, wave(1, 440));
    const { app, page } = await launch(profile, [file]);
    try {
        await fs.unlink(file);
        await page.getByRole('button', { name: 'Putar Akan hilang', exact: true }).click();
        await expect(page.locator('#playback-status')).toHaveClass(/error/, { timeout: 30000 });
        await page.getByRole('button', { name: 'Buka pemutar', exact: true }).click();
        await expect(page.locator('#playback-status')).toHaveClass(/error/);
        expect(await page.locator('#audio').evaluate(audio => audio.currentTime)).toBe(0);
        await screenshot(page, info, 'missing-file-error');
    } finally { await app.close(); }
});

test('online search resolves and decodes real YouTube audio', async ({}, info) => {
    test.skip(!MOCHI_ONLINE, 'Network gate runs separately with MOCHI_ONLINE=1.');
    test.setTimeout(240000);
    const profile = info.outputPath('profile'); await fs.mkdir(profile, { recursive: true });
    const { app, page } = await launch(profile);
    try {
        await selectTab(page, 'Cari');
        await page.getByRole('searchbox', { name: 'Cari musik', exact: true }).fill('Rick Astley Never Gonna Give You Up');
        await page.locator('.search-form').getByRole('button', { name: 'Cari', exact: true }).click();
        await expect(page.locator('.track-main').first()).toBeVisible({ timeout: 120000 });
        await page.locator('.track-main').first().click();
        await expect.poll(() => page.locator('#audio').evaluate(audio => audio.currentTime), { timeout: 120000 }).toBeGreaterThan(.4);
        await proveAudio(page);
        await screenshot(page, info, 'youtube-real-pcm');
        await page.getByRole('button', { name: 'Buka pemutar', exact: true }).click();
        await screenshot(page, info, 'youtube-player');
        await info.attach('online-track', { body: await page.locator('#full-title').textContent(), contentType: 'text/plain' });
    } finally { await app.close(); }
});
