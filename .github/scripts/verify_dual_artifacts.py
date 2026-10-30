#!/usr/bin/env python3
"""Verify two signed build artifacts agree and generate the release SHA-256 list."""

import argparse
import hashlib
import json
import re
from pathlib import Path

from verify_release_native import EXPECTED, inspect_apk


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--tag", default="", help="optional tag to compare with the embedded version")
    args = parser.parse_args()
    directory = args.directory
    trusted_certificate = (Path(__file__).resolve().parent.parent / "release-certificate.sha256").read_text().strip().lower()
    if not re.fullmatch(r"[0-9a-f]{64}", trusted_certificate):
        raise ValueError("Invalid trusted certificate fingerprint")

    apks = sorted(directory.glob("*.apk"))
    if len(apks) != len(EXPECTED):
        raise ValueError(f"Expected exactly two APKs, found {len(apks)}")
    metadata = {}
    library_names = {}
    for abi in EXPECTED:
        matching = [apk for apk in apks if apk.name.endswith(f"-{abi}-gms.apk")]
        if len(matching) != 1:
            raise ValueError(f"Expected one {abi} APK, found {matching}")
        apk = matching[0]
        data = json.loads((directory / f"metadata-{abi}.json").read_text())
        if data.get("abi") != abi or data.get("applicationId") != "com.mochyy.mochymusic":
            raise ValueError(f"Invalid {abi} metadata")
        if data.get("signerSha256", "").lower() != trusted_certificate:
            raise ValueError(f"{abi} signing certificate is not the trusted Bibz release key")
        if data.get("minSdk") != 24:
            raise ValueError(f"{abi} minimum Android API is not 24")
        version = data.get("versionName", "")
        if not re.fullmatch(r"[0-9A-Za-z][0-9A-Za-z._+-]*", version):
            raise ValueError(f"Invalid version name for {abi}")
        if apk.name != f"Mochy-Music-v{version}-{abi}-gms.apk":
            raise ValueError(f"{abi} APK filename does not match its embedded version")
        if args.tag and args.tag != f"v{version}":
            raise ValueError(f"Tag {args.tag} does not match version {version}")
        if not isinstance(data.get("versionCode"), int) or data["versionCode"] <= 0:
            raise ValueError(f"Invalid version code for {abi}")
        lines = (directory / f"SHA256SUMS-{abi}.txt").read_text().splitlines()
        if len(lines) != 1 or lines[0] != f"{sha256(apk)}  {apk.name}":
            raise ValueError(f"{abi} artifact checksum does not match the APK")
        library_names[abi] = inspect_apk(apk, abi)
        metadata[abi] = {key: value for key, value in data.items() if key != "abi"}

    a, b = EXPECTED
    if metadata[a] != metadata[b]:
        raise ValueError("APK package, version, minSdk or signer metadata differ between ABIs")
    if library_names[a] != library_names[b]:
        raise ValueError(f"Native libraries differ between ABIs: {library_names}")
    (directory / "SHA256SUMS.txt").write_text("".join(f"{sha256(apk)}  {apk.name}\n" for apk in apks))
    print(f"Both APKs have matching metadata and native library sets: {metadata[a]}")
    print((directory / "SHA256SUMS.txt").read_text(), end="")


if __name__ == "__main__":
    main()
