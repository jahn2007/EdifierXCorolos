#!/usr/bin/env python3
"""Offline APK inventory for research; never executes vendor code or emits app keys.

Run in an isolated analysis venv with androguard==4.1.3. Input and report must
remain outside the public checkout. This extracts certificates, not an apksigner
cryptographic verification result. Android builds still run only in Actions.
"""
import argparse
import hashlib
import json
import pathlib
import re
import struct
import zipfile
import zlib


def dex_inventory(data):
    if data[:4] != b"dex\x0a" or len(data) < 112:
        raise ValueError("Unsupported DEX header")
    if hashlib.sha1(data[32:]).digest() != data[12:32]:
        raise ValueError("DEX SHA-1 mismatch")
    if zlib.adler32(data[12:]) & 0xffffffff != struct.unpack_from("<I", data, 8)[0]:
        raise ValueError("DEX Adler-32 mismatch")
    strings_count, strings_offset = struct.unpack_from("<II", data, 56)
    types_count, types_offset = struct.unpack_from("<II", data, 64)
    classes_count, classes_offset = struct.unpack_from("<II", data, 96)
    strings = []
    for i in range(strings_count):
        offset = struct.unpack_from("<I", data, strings_offset + i * 4)[0]
        for _ in range(5):
            value = data[offset]
            offset += 1
            if value < 128:
                break
        else:
            raise ValueError("Invalid DEX string length")
        strings.append(data[offset:data.index(0, offset)].decode("utf-8", "replace"))
    types = [strings[struct.unpack_from("<I", data, types_offset + i * 4)[0]] for i in range(types_count)]
    classes = [types[struct.unpack_from("<I", data, classes_offset + i * 32)[0]] for i in range(classes_count)]
    # Publish only selected class names, never arbitrary DEX strings or SDK keys.
    evidence = [name for name in classes if name in {
        "Lcom/edifier/edifierconnect/EdifierApplication;",
        "Lcom/edifier/edifierconnect/util/CryptUtil;",
        "Lcom/edifier/lib_base/handler/CommandParserHelper;",
        "Lcom/edifier/product_connect/command/CommandBean;",
        "Lcom/edifier/product_connect/command/CommandManager;",
        "Lcom/edifier/product_connect/command/bean/base/D8Bean;",
        "Lcom/edifier/product_connect/connect/ble/BLEManager;",
        "Lcom/edifier/product_connect/connect/spp/SPPManager;",
    }]
    return {"size": len(data), "sha256": hashlib.sha256(data).hexdigest(),
            "header_checksums_valid": True, "class_count": classes_count,
            "evidence_classes": evidence}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=pathlib.Path)
    parser.add_argument("report", type=pathlib.Path)
    args = parser.parse_args()
    root = pathlib.Path(__file__).resolve().parents[1]
    if any(p.resolve().is_relative_to(root) for p in (args.apk, args.report)):
        parser.error("Keep private APK and analysis report outside the public checkout")
    from loguru import logger
    logger.remove()
    from androguard.core.apk import APK

    apk = APK(str(args.apk))
    with args.apk.open("rb") as input_file:
        digest = hashlib.file_digest(input_file, "sha256").hexdigest()
    report = {"size": args.apk.stat().st_size,
              "sha256": digest,
              "package": apk.get_package(), "version_name": apk.get_androidversion_name(),
              "version_code": apk.get_androidversion_code(),
              "min_sdk": apk.get_min_sdk_version(), "target_sdk": apk.get_target_sdk_version(),
              "application": apk.get_attribute_value("application", "name"),
              "main_activity": apk.get_main_activity(), "permissions": sorted(apk.get_permissions()),
              "certificate_sha256": sorted({hashlib.sha256(c.dump()).hexdigest() for c in apk.get_certificates()}),
              "signature_blocks_present": {"v1": apk.is_signed_v1(), "v2": apk.is_signed_v2(), "v3": apk.is_signed_v3()},
              "apk_signature_cryptographically_verified": False}
    with zipfile.ZipFile(args.apk) as archive:
        if archive.testzip() is not None:
            raise ValueError("APK ZIP CRC failure")
        report["zip_entry_count"] = len(archive.infolist())
        report["dex"] = {n: dex_inventory(archive.read(n)) for n in archive.namelist()
                         if re.fullmatch(r"classes\d*\.dex", n)}
        name = "assets/products_release.json"
        if name in archive.namelist():
            data = archive.read(name)
            report["product_asset"] = {"path": name, "size": len(data), "sha256": hashlib.sha256(data).hexdigest()}
    with args.report.open("x", encoding="utf-8") as output:
        json.dump(report, output, ensure_ascii=False, indent=2)
        output.write("\n")
    print("Offline inventory complete; APK and report remain outside the checkout.")


if __name__ == "__main__":
    main()
