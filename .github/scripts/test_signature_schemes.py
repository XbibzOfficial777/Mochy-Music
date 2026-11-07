#!/usr/bin/env python3
"""Exercise the real APK verifier against valid v2/v4 and forbidden v1/v3 signatures."""
import hashlib
import os
import secrets
import subprocess
import tempfile
from pathlib import Path

root = Path(__file__).resolve().parents[2]
sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
jdk = Path(os.environ['JAVA_HOME']) / 'bin'
tools = sdk / 'build-tools' / '36.0.0'
platform = sdk / 'platforms/android-36/android.jar'
env = os.environ.copy()
env['SCHEME_FIXTURE_PASSWORD'] = secrets.token_urlsafe(24)

def run(args, check=True):
    return subprocess.run([str(arg) for arg in args], env=env, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=check)

with tempfile.TemporaryDirectory(prefix='mochy-signature-policy-') as folder:
    tmp = Path(folder)
    manifest = tmp / 'AndroidManifest.xml'
    manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
                        'package="com.example.signaturefixture" android:versionCode="1" '
                        'android:versionName="1"><uses-sdk android:minSdkVersion="24" '
                        'android:targetSdkVersion="36"/><application android:hasCode="false"/></manifest>')
    unsigned = tmp / 'unsigned.apk'
    run([tools / 'aapt2', 'link', '-I', platform, '--manifest', manifest, '-o', unsigned])
    keystore = tmp / 'fixture.p12'
    run([jdk / 'keytool', '-genkeypair', '-alias', 'fixture', '-dname', 'CN=Signature Fixture',
         '-keyalg', 'RSA', '-keysize', '2048', '-validity', '2', '-storetype', 'PKCS12',
         '-keystore', keystore, '-storepass:env', 'SCHEME_FIXTURE_PASSWORD',
         '-keypass:env', 'SCHEME_FIXTURE_PASSWORD', '-noprompt'])
    certificate = tmp / 'certificate.der'
    run([jdk / 'keytool', '-exportcert', '-alias', 'fixture', '-keystore', keystore,
         '-storepass:env', 'SCHEME_FIXTURE_PASSWORD', '-file', certificate])
    digest = hashlib.sha256(certificate.read_bytes()).hexdigest()
    jar = tools / 'lib/apksigner.jar'
    run([jdk / 'javac', '-cp', jar, '-d', tmp, root / 'tools/VerifyApkSignatures.java'])
    for name, v1, v3, expected_success in [('v2-v4-only', False, False, True),
                                           ('forbidden-v1', True, False, False),
                                           ('forbidden-v3', False, True, False)]:
        apk = tmp / f'{name}.apk'
        run([tools / 'apksigner', 'sign', '--ks', keystore, '--ks-type', 'PKCS12',
             '--ks-key-alias', 'fixture', '--ks-pass', 'env:SCHEME_FIXTURE_PASSWORD',
             '--key-pass', 'env:SCHEME_FIXTURE_PASSWORD', '--v1-signing-enabled', str(v1).lower(),
             '--v2-signing-enabled', 'true', '--v3-signing-enabled', str(v3).lower(),
             '--v4-signing-enabled', 'true', '--out', apk, unsigned])
        result = run([jdk / 'java', '-cp', f'{jar}{os.pathsep}{tmp}', 'VerifyApkSignatures',
                      apk, Path(str(apk) + '.idsig'), digest], check=False)
        if (result.returncode == 0) != expected_success:
            raise AssertionError(f'{name}: unexpected verifier result\n{result.stdout}')
        print(f'PASS {name}: ' + ('accepted v2/v4 only' if expected_success else 'rejected'))
        if expected_success:
            print(result.stdout.strip())
            wrong_certificate = run([jdk / 'java', '-cp', f'{jar}{os.pathsep}{tmp}',
                'VerifyApkSignatures', apk, Path(str(apk) + '.idsig'), '0' * 64], check=False)
            assert wrong_certificate.returncode != 0, 'Verifier accepted the wrong signing certificate'
            print('PASS wrong-certificate: rejected')
            corrupted = tmp / 'corrupted.idsig'
            corrupted.write_bytes(b'not a valid v4 signature')
            invalid_v4 = run([jdk / 'java', '-cp', f'{jar}{os.pathsep}{tmp}',
                'VerifyApkSignatures', apk, corrupted, digest], check=False)
            assert invalid_v4.returncode != 0, 'Verifier accepted an invalid v4 sidecar'
            print('PASS invalid-v4-sidecar: rejected')
