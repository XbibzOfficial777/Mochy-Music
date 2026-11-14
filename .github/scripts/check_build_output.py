#!/usr/bin/env python3
"""Fail CI on emitted compiler/resource warnings or on configured lint issues."""
import argparse
import collections
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('report', type=Path)
parser.add_argument('--lint', action='store_true')
args = parser.parse_args()
if args.lint:
    issues = ET.parse(args.report).getroot().findall('issue')
    findings = [issue for issue in issues if issue.get('severity') in {'Fatal', 'Error', 'Warning'}]
    counts = collections.Counter(issue.get('severity') for issue in findings)
    print(json.dumps(dict(counts), sort_keys=True))
    for issue in findings[:30]:
        location = issue.find('location')
        print(issue.get('severity'), issue.get('id'), issue.get('message'),
              location.attrib if location is not None else {})
    raise SystemExit(1 if findings else 0)
text = args.report.read_text(errors='replace')
ignore = (
    'We recommend using a newer Android Gradle plugin',
    'The SDK Manager CLI tool (sdkmanager) is deprecated',
    'WARNING: R8: An error occurred when parsing kotlin metadata',
)
findings = [line for line in text.splitlines() if re.match(
    r'^(?:w:|warn:|WARNING:|Warning:|warning:|.*:\d+:\s*warning:)', line)
    and not any(s in line for s in ignore)]
print(f'Compiler/resource warnings: {len(findings)}')
for line in findings:
    print(line)
raise SystemExit(1 if findings else 0)
