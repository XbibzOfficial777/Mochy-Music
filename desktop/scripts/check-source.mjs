import { readdir, readFile } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import path from 'node:path';

for (const directory of ['src', 'renderer', 'scripts', 'tests']) {
    for (const name of await readdir(directory)) {
        if (!/\.(cjs|mjs|js)$/.test(name)) continue;
        const file = path.join(directory, name);
        const source = await readFile(file, 'utf8');
        const result = spawnSync(process.execPath, ['--check', '--input-type', name.endsWith('.cjs') ? 'commonjs' : 'module', '-'], { input: source, encoding: 'utf8' });
        if (result.status !== 0) throw new Error(`${file}: ${result.stderr}`);
    }
}
const html = await readFile('renderer/index.html', 'utf8');
for (const match of html.matchAll(/(?:src|href)="([^"#:]+\.(?:js|css))"/g)) {
    await readFile(path.join('renderer', match[1]));
}
console.log('All JavaScript parses; HTML script and style entrypoints exist.');
