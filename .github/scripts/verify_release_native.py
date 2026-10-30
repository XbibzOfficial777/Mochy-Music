#!/usr/bin/env python3
"""Reject an APK with missing, uncompressed or incorrectly targeted native libraries."""

import argparse
import struct
import zipfile
from pathlib import Path

EXPECTED = {
    "arm64-v8a": (2, 183),  # ELFCLASS64, EM_AARCH64
    "armeabi-v7a": (1, 40),  # ELFCLASS32, EM_ARM
}


def inspect_apk(path: Path, abi: str) -> set[str]:
    elf_class, machine = EXPECTED[abi]
    with zipfile.ZipFile(path) as apk:
        libraries = [
            item for item in apk.infolist()
            if item.filename.startswith("lib/") and item.filename.endswith(".so")
        ]
        if not libraries:
            raise ValueError(f"{path}: no native libraries")
        seen: set[str] = set()
        for item in libraries:
            parts = item.filename.split("/")
            if len(parts) != 3 or parts[1] != abi:
                raise ValueError(f"{path}: unexpected native library {item.filename}")
            name = parts[2]
            if not name or name in seen:
                raise ValueError(f"{path}: duplicate or unnamed native library {item.filename}")
            seen.add(name)
            if item.compress_type != zipfile.ZIP_DEFLATED:
                raise ValueError(f"{path}: native library is not compressed: {item.filename}")
            with apk.open(item) as source:
                header = source.read(20)
            if len(header) < 20 or header[:4] != b"\x7fELF" or header[4] != elf_class or header[5] != 1:
                raise ValueError(f"{path}: incorrect ELF class or byte order: {item.filename}")
            if struct.unpack_from("<H", header, 18)[0] != machine:
                raise ValueError(f"{path}: incorrect ELF machine: {item.filename}")
    return seen


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("abi", choices=EXPECTED)
    args = parser.parse_args()
    names = inspect_apk(args.apk, args.abi)
    print(f"Verified {len(names)} compressed {args.abi} ELF libraries in {args.apk.name}: {', '.join(sorted(names))}")


if __name__ == "__main__":
    main()
