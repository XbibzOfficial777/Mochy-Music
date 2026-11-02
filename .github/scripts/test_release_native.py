#!/usr/bin/env python3
"""Unit-test the release verifier against synthetic good and malformed APKs."""

import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

from verify_release_native import ABI_ELF, PACKAGES, inspect_apk


def elf(abi: str) -> bytes:
    elf_class, machine = ABI_ELF[abi]
    header = bytearray(20)
    header[:4] = b"\x7fELF"
    header[4] = elf_class
    header[5] = 1
    struct.pack_into("<H", header, 18, machine)
    return bytes(header)


def make_apk(path: Path, members: list[tuple[str, bytes]], compression=zipfile.ZIP_DEFLATED) -> None:
    with zipfile.ZipFile(path, "w", compression=compression) as apk:
        for name, data in members:
            apk.writestr(name, data)


class NativeVerifierTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.apk = Path(self.tmp.name) / "test.apk"

    def members(self, abis: set[str]) -> list[tuple[str, bytes]]:
        return [(f"lib/{abi}/{name}", elf(abi)) for abi in abis for name in ("one.so", "two.so")]

    def test_valid_split_and_universal(self) -> None:
        for package, abis in PACKAGES.items():
            make_apk(self.apk, self.members(set(abis)))
            self.assertEqual(set(inspect_apk(self.apk, package)), set(abis))

    def test_rejects_universal_missing_abi(self) -> None:
        make_apk(self.apk, self.members(set(ABI_ELF) - {"x86"}))
        with self.assertRaisesRegex(ValueError, 'expected ABI set'):
            inspect_apk(self.apk, "universal")

    def test_rejects_cross_abi_and_wrong_machine(self) -> None:
        make_apk(self.apk, self.members({"arm64-v8a", "x86_64"}))
        with self.assertRaisesRegex(ValueError, 'unexpected native library'):
            inspect_apk(self.apk, "arm64-v8a")
        make_apk(self.apk, [("lib/arm64-v8a/one.so", elf("x86_64"))])
        with self.assertRaisesRegex(ValueError, 'incorrect ELF machine'):
            inspect_apk(self.apk, "arm64-v8a")

    def test_rejects_mismatched_sets_and_uncompressed(self) -> None:
        members = self.members(set(ABI_ELF))
        members = [(name, data) for name, data in members if name != "lib/x86/two.so"]
        make_apk(self.apk, members)
        with self.assertRaisesRegex(ValueError, 'native library sets differ'):
            inspect_apk(self.apk, "universal")
        make_apk(self.apk, self.members({"arm64-v8a"}), compression=zipfile.ZIP_STORED)
        with self.assertRaisesRegex(ValueError, 'not compressed'):
            inspect_apk(self.apk, "arm64-v8a")


if __name__ == "__main__":
    unittest.main()
