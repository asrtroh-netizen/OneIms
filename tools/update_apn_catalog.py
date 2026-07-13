#!/usr/bin/env python3
"""Build OneIms' reproducible offline APN catalog from pinned Apache-2.0 sources."""

from __future__ import annotations

import base64
import hashlib
import io
import json
import re
import tarfile
import urllib.request
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path


CATALOG_VERSION = "2026.07.10"
LINEAGE_REVISION = "f61eef690eac185e6f10a227cd8ac28ab7861557"
AOSP_REVISION = "73f904fbeb872f5675cb5f18cd9dfb15856425a6"
LINEAGE_ARCHIVE = (
    "https://github.com/LineageOS/android_vendor_apn/archive/"
    f"{LINEAGE_REVISION}.tar.gz"
)
AOSP_APNS = (
    "https://android.googlesource.com/device/sample/+/"
    f"{AOSP_REVISION}/etc/apns-full-conf.xml?format=TEXT"
)
SOURCE_FILE_PATTERN = re.compile(r"^(?:[A-Z]{2}|INTL)\.xml$")

FIELDS = (
    "country",
    "source",
    "carrier",
    "mcc",
    "mnc",
    "apn",
    "type",
    "protocol",
    "roaming_protocol",
    "user",
    "password",
    "authtype",
    "mmsc",
    "mmsproxy",
    "mmsport",
    "proxy",
    "port",
    "carrier_id",
    "mvno_type",
    "mvno_match_data",
    "carrier_enabled",
    "user_visible",
    "user_editable",
    "network_type_bitmask",
    "bearer_bitmask",
)

PROJECT_ROOT = Path(__file__).resolve().parents[1]
ASSET_DIR = PROJECT_ROOT / "app" / "src" / "main" / "assets"
CATALOG_PATH = ASSET_DIR / "apn_catalog.tsv"
METADATA_PATH = ASSET_DIR / "apn_catalog.meta.json"
NOTICE_PATH = ASSET_DIR / "APN_CATALOG_NOTICE.txt"
LICENSE_PATH = ASSET_DIR / "APN_CATALOG_LICENSE.txt"


def fetch(url: str) -> bytes:
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "OneIms APN catalog builder"},
    )
    with urllib.request.urlopen(request, timeout=90) as response:
        return response.read()


def logical_key(row: dict[str, str]) -> tuple[str, ...]:
    normalized_types = ",".join(
        sorted(
            {
                value.strip().lower()
                for value in row.get("type", "").split(",")
                if value.strip()
            }
        )
    )
    return (
        row.get("mcc", ""),
        row.get("mnc", ""),
        row.get("carrier_id", ""),
        row.get("apn", "").lower(),
        normalized_types,
        row.get("mvno_type", "").lower(),
        row.get("mvno_match_data", "").lower(),
    )


def useful_value_count(row: dict[str, str]) -> int:
    return sum(bool(row.get(field, "").strip()) for field in FIELDS)


def load_lineage(
    archive_bytes: bytes,
) -> tuple[list[dict[str, str]], bytes]:
    rows: list[dict[str, str]] = []
    license_bytes: bytes | None = None
    with tarfile.open(fileobj=io.BytesIO(archive_bytes), mode="r:gz") as archive:
        for member in archive.getmembers():
            filename = member.name.rsplit("/", 1)[-1]
            if member.isfile() and filename == "Apache-2.0.txt":
                extracted = archive.extractfile(member)
                if extracted is not None:
                    license_bytes = extracted.read()
            if not member.isfile() or not SOURCE_FILE_PATTERN.fullmatch(filename):
                continue
            extracted = archive.extractfile(member)
            if extracted is None:
                continue
            country = filename.removesuffix(".xml")
            root = ET.fromstring(extracted.read())
            for element in root.findall("apn"):
                row = {key: value.strip() for key, value in element.attrib.items()}
                row["country"] = country
                row["source"] = "LineageOS"
                if row.get("apn"):
                    rows.append(row)
    if license_bytes is None:
        raise RuntimeError("LineageOS Apache-2.0 license was not found")
    return rows, license_bytes


def load_aosp(encoded_bytes: bytes) -> list[dict[str, str]]:
    root = ET.fromstring(base64.b64decode(encoded_bytes))
    rows: list[dict[str, str]] = []
    for element in root.findall("apn"):
        row = {key: value.strip() for key, value in element.attrib.items()}
        row["country"] = ""
        row["source"] = "AOSP"
        if row.get("apn"):
            rows.append(row)
    return rows


def build_country_lookup(
    rows: list[dict[str, str]],
) -> tuple[dict[tuple[str, str], str], dict[str, str]]:
    by_plmn: dict[tuple[str, str], set[str]] = defaultdict(set)
    by_mcc: dict[str, set[str]] = defaultdict(set)
    for row in rows:
        country = row.get("country", "")
        mcc = row.get("mcc", "")
        mnc = row.get("mnc", "")
        if country and mcc:
            by_plmn[(mcc, mnc)].add(country)
            by_mcc[mcc].add(country)
    unique_plmn = {
        key: next(iter(countries))
        for key, countries in by_plmn.items()
        if len(countries) == 1
    }
    unique_mcc = {
        key: next(iter(countries))
        for key, countries in by_mcc.items()
        if len(countries) == 1
    }
    return unique_plmn, unique_mcc


def merge_rows(
    lineage_rows: list[dict[str, str]],
    aosp_rows: list[dict[str, str]],
) -> list[dict[str, str]]:
    by_plmn, by_mcc = build_country_lookup(lineage_rows)
    merged: dict[tuple[str, ...], dict[str, str]] = {}

    for row in lineage_rows:
        key = logical_key(row)
        current = merged.get(key)
        if current is None or useful_value_count(row) > useful_value_count(current):
            merged[key] = row

    for source_row in aosp_rows:
        row = dict(source_row)
        row["country"] = (
            by_plmn.get((row.get("mcc", ""), row.get("mnc", "")))
            or by_mcc.get(row.get("mcc", ""))
            or "INTL"
        )
        key = logical_key(row)
        if key not in merged:
            merged[key] = row

    rows = list(merged.values())
    rows.sort(
        key=lambda row: (
            row.get("country", ""),
            row.get("mcc", ""),
            row.get("mnc", ""),
            row.get("carrier", "").casefold(),
            row.get("apn", "").casefold(),
            row.get("type", ""),
            row.get("source", ""),
        )
    )
    return rows


def escape_tsv(value: str) -> str:
    return (
        value.replace("\\", "\\\\")
        .replace("\t", "\\t")
        .replace("\r", "\\r")
        .replace("\n", "\\n")
    )


def render_catalog(rows: list[dict[str, str]]) -> bytes:
    lines = ["\t".join(FIELDS)]
    lines.extend(
        "\t".join(escape_tsv(row.get(field, "")) for field in FIELDS)
        for row in rows
    )
    return ("\n".join(lines) + "\n").encode("utf-8")


def validate(rows: list[dict[str, str]]) -> None:
    keys = [logical_key(row) for row in rows]
    if len(keys) != len(set(keys)):
        raise ValueError("Merged APN catalog still contains duplicate logical keys")
    if len(rows) < 5_000:
        raise ValueError(f"Unexpectedly small APN catalog: {len(rows)}")
    ims_count = sum(
        "ims"
        in {
            value.strip().lower()
            for value in row.get("type", "").split(",")
        }
        for row in rows
    )
    if ims_count < 500:
        raise ValueError(f"Unexpectedly small IMS catalog: {ims_count}")
    invalid_identity = [
        row
        for row in rows
        if not (
            (
                len(row.get("mcc", "")) == 3
                and row.get("mcc", "").isdigit()
                and (
                    row.get("mnc", "") == ""
                    or (
                        len(row.get("mnc", "")) in (2, 3)
                        and row.get("mnc", "").isdigit()
                    )
                )
            )
            or row.get("carrier_id", "").isdigit()
        )
    ]
    if invalid_identity:
        raise ValueError(
            "Rows without a valid MCC/MNC or carrier ID: "
            f"{len(invalid_identity)}"
        )


def main() -> None:
    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    lineage_archive = fetch(LINEAGE_ARCHIVE)
    lineage_rows, license_bytes = load_lineage(lineage_archive)
    aosp_rows = load_aosp(fetch(AOSP_APNS))
    rows = merge_rows(lineage_rows, aosp_rows)
    validate(rows)

    catalog_bytes = render_catalog(rows)
    CATALOG_PATH.write_bytes(catalog_bytes)
    LICENSE_PATH.write_bytes(license_bytes)

    ims_count = sum(
        "ims"
        in {
            value.strip().lower()
            for value in row.get("type", "").split(",")
        }
        for row in rows
    )
    metadata = {
        "catalogVersion": CATALOG_VERSION,
        "records": len(rows),
        "imsRecords": ims_count,
        "plmns": len(
            {
                (row.get("mcc", ""), row.get("mnc", ""))
                for row in rows
                if row.get("mcc", "")
            }
        ),
        "mccs": len(
            {row.get("mcc", "") for row in rows if row.get("mcc", "")}
        ),
        "countries": len({row.get("country", "") for row in rows}),
        "sha256": hashlib.sha256(catalog_bytes).hexdigest().upper(),
        "sources": {
            "lineageOs": {
                "revision": LINEAGE_REVISION,
                "url": "https://github.com/LineageOS/android_vendor_apn",
            },
            "aosp": {
                "revision": AOSP_REVISION,
                "url": (
                    "https://android.googlesource.com/device/sample/+/"
                    f"{AOSP_REVISION}/etc/apns-full-conf.xml"
                ),
            },
        },
        "license": "Apache-2.0",
    }
    METADATA_PATH.write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    NOTICE_PATH.write_text(
        "\n".join(
            (
                "OneIms offline APN catalog",
                "",
                "This derived catalog contains APN configuration candidates from:",
                f"- LineageOS android_vendor_apn @ {LINEAGE_REVISION}",
                f"- Android Open Source Project device/sample @ {AOSP_REVISION}",
                "",
                "Both sources and this derived data are distributed under Apache-2.0.",
                "The accompanying APN_CATALOG_LICENSE.txt contains the license text.",
                "",
                "APN values vary by plan, region, roaming state, and MVNO identity.",
                "OneIms treats these rows as offline candidates, never as a guarantee.",
                "",
            )
        ),
        encoding="utf-8",
    )
    print(json.dumps(metadata, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
