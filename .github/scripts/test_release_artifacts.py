#!/usr/bin/env python3
"""Test three-artifact agreement, checksums and fail-closed mismatches."""
import hashlib
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from test_release_native import make_apk, elf
from verify_release_native import PACKAGES

SCRIPTS = Path(__file__).resolve().parent
CERT = (SCRIPTS.parent / 'release-certificate.sha256').read_text().strip()

class ArtifactVerifierTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.folder = Path(self.tmp.name)
        self.version = '8.2.3-xbibz.6'
        for package, abis in PACKAGES.items():
            name = f'Mochy-Music-v{self.version}-{package}-gms.apk'
            file = self.folder / name
            make_apk(file, [(f'lib/{abi}/{lib}', elf(abi)) for abi in abis for lib in ('a.so', 'b.so')])
            digest = hashlib.sha256(file.read_bytes()).hexdigest()
            (self.folder / f'SHA256SUMS-{package}.txt').write_text(f'{digest}  {name}\n')
            data = dict(abi=package, applicationId='com.mochyy.mochymusic', versionName=self.version,
                        versionCode=79, minSdk=24, targetSdk=37, debuggable=False, signerSha256=CERT)
            (self.folder / f'metadata-{package}.json').write_text(json.dumps(data))

    def run_verifier(self, tag=None):
        command = ['python3', str(SCRIPTS / 'verify_dual_artifacts.py'), str(self.folder)]
        if tag: command += ['--tag', tag]
        return subprocess.run(command, text=True, capture_output=True)

    def test_valid_all_three(self):
        result = self.run_verifier(f'v{self.version}')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(len((self.folder / 'SHA256SUMS.txt').read_text().splitlines()), 3)

    def test_rejects_different_signer_and_wrong_tag(self):
        self.assertNotEqual(self.run_verifier('v8.2.3-xbibz.5').returncode, 0)
        file = self.folder / 'metadata-universal.json'
        data = json.loads(file.read_text()); data['signerSha256'] = '0' * 64
        file.write_text(json.dumps(data))
        self.assertNotEqual(self.run_verifier().returncode, 0)

    def test_rejects_modified_artifact_and_missing_package(self):
        (self.folder / f'Mochy-Music-v{self.version}-universal-gms.apk').write_bytes(b'modified')
        self.assertNotEqual(self.run_verifier().returncode, 0)
        (self.folder / f'Mochy-Music-v{self.version}-universal-gms.apk').unlink()
        self.assertNotEqual(self.run_verifier().returncode, 0)

if __name__ == '__main__':
    unittest.main()
