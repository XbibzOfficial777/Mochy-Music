const { safeMediaUrl } = require('./core.cjs');

// Let the runtime follow YouTube's redirect chain, then validate the FINAL URL.
// Manual redirect handling can return an opaque response and lose the body.
async function fetchMedia(url, options = {}) {
    const initial = safeMediaUrl(url);
    const response = await fetch(initial, { ...options, redirect: 'follow' });
    if (response.url) safeMediaUrl(response.url);
    return response;
}
module.exports = { fetchMedia };
