#!/usr/bin/env python3
"""Verify three signed release artifacts agree and generate the release SHA-256 list."""

import argparse
import hashlib
import json
import re
from pathlib import Path

from verify_release_native import PACKAGES, inspect_apk


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
    if len(apks) != len(PACKAGES):
        raise ValueError(f"Expected exactly {len(PACKAGES)} APKs, found {len(apks)}")
    metadata = {}
    all_library_names = {}
    for package in PACKAGES:
        matching = [apk for apk in apks if apk.name.endswith(f"-{package}-gms.apk")]
        if len(matching) != 1:
            raise ValueError(f"Expected one {package} APK, found {matching}")
        apk = matching[0]
        data = json.loads((directory / f"metadata-{package}.json").read_text())
        if data.get("abi") != package or data.get("applicationId") != "com.mochyy.mochymusic":
            raise ValueError(f"Invalid {package} metadata")
        if data.get("signerSha256", "").lower() != trusted_certificate:
            raise ValueError(f"{package} signing certificate is not the trusted Bibz release key")
        if data.get("minSdk") != 24 or data.get("targetSdk") != 36 or data.get("debuggable") is not False:
            raise ValueError(f"Invalid SDK or debuggable metadata for {package}")
        version = data.get("versionName", "")
        if not re.fullmatch(r"[0-9A-Za-z][0-9A-Za-z._+-]*", version):
            raise ValueError(f"Invalid version name for {package}")
        if apk.name != f"Mochy-Music-v{version}-{package}-gms.apk":
            raise ValueError(f"{package} APK filename does not match its embedded version")
        if args.tag and args.tag != f"v{version}":
            raise ValueError(f"Tag {args.tag} does not match version {version}")
        if not isinstance(data.get("versionCode"), int) or data["versionCode"] <= 0:
            raise ValueError(f"Invalid version code for {package}")
        lines = (directory / f"SHA256SUMS-{package}.txt").read_text().splitlines()
        if len(lines) != 1 or lines[0] != f"{sha256(apk)}  {apk.name}":
            raise ValueError(f"{package} artifact checksum does not match the APK")
        for abi, names in inspect_apk(apk, package).items():
            all_library_names[f"{package}/{abi}"] = frozenset(names)
        metadata[package] = {key: value for key, value in data.items() if key != "abi"}

    if len({json.dumps(data, sort_keys=True) for data in metadata.values()}) != 1:
        raise ValueError("APK package, version, SDK, debuggability or signer metadata differ between builds")
    if len(set(all_library_names.values())) != 1:
        raise ValueError(f"Native libraries differ between builds: {all_library_names}")
    (directory / "SHA256SUMS.txt").write_text("".join(f"{sha256(apk)}  {apk.name}\n" for apk in apks))
    print(f"All three APKs have matching metadata and native library sets: {next(iter(metadata.values()))}")
    print((directory / "SHA256SUMS.txt").read_text(), end="")


if __name__ == "__main__":
    main()
