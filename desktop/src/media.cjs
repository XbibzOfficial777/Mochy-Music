const { safeMediaUrl } = require('./core.cjs');

// Validate every redirect, not only the URL returned by the extractor.
async function fetchMedia(url, options = {}) {
    for (let redirects = 0; redirects < 5; redirects++) {
        const response = await fetch(safeMediaUrl(url), { ...options, redirect: 'manual' });
        if (![301, 302, 303, 307, 308].includes(response.status)) return response;
        const location = response.headers.get('location');
        await response.body?.cancel();
        if (!location) throw new Error('Audio redirect has no destination.');
        url = new URL(location, url).href;
    }
    throw new Error('Too many audio redirects.');
}
module.exports = { fetchMedia };
