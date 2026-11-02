#!/usr/bin/env python3
"""Reject missing, uncompressed, extra or incorrectly targeted native libraries."""

import argparse
import struct
import zipfile
from pathlib import Path

# ELF class and e_machine; Android APK libraries are little endian.
ABI_ELF = {
    "arm64-v8a": (2, 183),  # ELFCLASS64, EM_AARCH64
    "armeabi-v7a": (1, 40),  # ELFCLASS32, EM_ARM
    "x86": (1, 3),  # ELFCLASS32, EM_386
    "x86_64": (2, 62),  # ELFCLASS64, EM_X86_64
}
PACKAGES = {
    "arm64-v8a": frozenset({"arm64-v8a"}),
    "armeabi-v7a": frozenset({"armeabi-v7a"}),
    "universal": frozenset(ABI_ELF),
}


def inspect_apk(path: Path, package: str) -> dict[str, set[str]]:
    expected = PACKAGES[package]
    seen: dict[str, set[str]] = {}
    with zipfile.ZipFile(path) as apk:
        libraries = [
            item for item in apk.infolist()
            if item.filename.startswith("lib/") and item.filename.endswith(".so")
        ]
        if not libraries:
            raise ValueError(f"{path}: no native libraries")
        for item in libraries:
            parts = item.filename.split("/")
            if len(parts) != 3 or parts[1] not in expected:
                raise ValueError(f"{path}: unexpected native library {item.filename}")
            abi, name = parts[1:]
            names = seen.setdefault(abi, set())
            if not name or name in names:
                raise ValueError(f"{path}: duplicate or unnamed native library {item.filename}")
            names.add(name)
            if item.compress_type != zipfile.ZIP_DEFLATED:
                raise ValueError(f"{path}: native library is not compressed: {item.filename}")
            with apk.open(item) as source:
                header = source.read(20)
            elf_class, machine = ABI_ELF[abi]
            if len(header) < 20 or header[:4] != b"\x7fELF" or header[4] != elf_class or header[5] != 1:
                raise ValueError(f"{path}: incorrect ELF class or byte order: {item.filename}")
            if struct.unpack_from("<H", header, 18)[0] != machine:
                raise ValueError(f"{path}: incorrect ELF machine: {item.filename}")
    if set(seen) != expected:
        raise ValueError(f"{path}: expected ABI set {sorted(expected)}, found {sorted(seen)}")
    if len({frozenset(names) for names in seen.values()}) != 1:
        raise ValueError(f"{path}: native library sets differ across ABIs: {seen}")
    return seen


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("package", choices=PACKAGES)
    args = parser.parse_args()
    libraries = inspect_apk(args.apk, args.package)
    for abi, names in sorted(libraries.items()):
        print(f"Verified {len(names)} compressed {abi} ELF libraries in {args.apk.name}: {', '.join(sorted(names))}")


if __name__ == "__main__":
    main()
