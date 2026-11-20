export function normalizeSuggestions(values = [], limit = 8) {
    const seen = new Set();
    const result = [];
    for (const value of values) {
        if (typeof value !== 'string') continue;
        const text = value.replace(/\s+/g, ' ').trim();
        if (!text || seen.has(text.toLowerCase())) continue;
        seen.add(text.toLowerCase());
        result.push(text);
        if (result.length === limit) break;
    }
    return result;
}
