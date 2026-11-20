import { mkdir, writeFile, chmod } from 'node:fs/promises';
import { createHash } from 'node:crypto';
const version = '2026.10.26';
const binaries = { win32: { x64: 'yt-dlp.exe', arm64: 'yt-dlp_arm64.exe' }, darwin: { x64: 'yt-dlp_macos', arm64: 'yt-dlp_macos' }, linux: { x64: 'yt-dlp_linux', arm64: 'yt-dlp_linux_aarch64' } };
const name = binaries[process.platform]?.[process.arch];
if (!name) throw new Error(`Unsupported engine platform: ${process.platform}/${process.arch}`);
const base = `https://github.com/yt-dlp/yt-dlp/releases/download/${version}`;
async function fetchOk(url) {
    const response = await fetch(url, { signal: AbortSignal.timeout(120000) });
    if (!response.ok) throw new Error(`${response.status}: ${url}`);
    return response;
}
const sums = await (await fetchOk(`${base}/SHA2-256SUMS`)).text();
const expected = sums.split('\n').find(line => line.trim().endsWith(` ${name}`))?.split(/\s+/)[0];
if (!expected) throw new Error(`Checksum missing for ${name}`);
const bytes = Buffer.from(await (await fetchOk(`${base}/${name}`)).arrayBuffer());
if (createHash('sha256').update(bytes).digest('hex') !== expected) throw new Error('Engine checksum mismatch');
await mkdir('resources', { recursive: true });
const output = `resources/yt-dlp${process.platform === 'win32' ? '.exe' : ''}`;
await writeFile(output, bytes);
await chmod(output, 0o755);
console.log(`Verified yt-dlp ${version} (${name})`);
