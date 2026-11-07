#!/usr/bin/env python3
"""Keep the APK version, bundled changelog and downloadable release notes consistent."""
import json
import re
from pathlib import Path

root = Path(__file__).resolve().parents[2]
gradle = (root / 'app/build.gradle.kts').read_text()
version = re.search(r'versionName = "([^"]+)"', gradle).group(1)
code = int(re.search(r'versionCode = (\d+)', gradle).group(1))
data = json.loads((root / 'app/src/main/assets/changelog.json').read_text())
assert (data['versionName'], data['versionCode']) == (version, code), 'Changelog version mismatch'
assert data['description'].strip(), 'Missing release description'
assert data['changelog'], 'Empty changelog'
for section in data['changelog']:
    assert section['title'].strip() and section['items'], 'Empty changelog section'
    assert all(isinstance(item, str) and item.strip() for item in section['items'])
notes = root / f'release-notes/{version}.md'
assert notes.is_file() and version in notes.read_text(), 'Missing matching Markdown release notes'
print(f'Version, changelog and notes agree: {version} ({code})')
