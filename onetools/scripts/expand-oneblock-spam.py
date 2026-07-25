#!/usr/bin/env python3
"""Expand OneTools phone blocklist with CN spam prefixes + exact seeds, then rebuild onespam pack."""
from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SAMPLE = ROOT / "docs/product/samples/one-blocklist.json"

# Virtual / high-risk mobile prefixes — PREFIX block (CallRuleStore + onespam longest-prefix).
# Users who need delivery/taxi from these ranges should whitelist.
VIRTUAL_PREFIXES = [
    ("170", "虚拟运营商"),
    ("171", "虚拟运营商"),
    ("162", "虚拟运营商"),
    ("165", "虚拟运营商"),
    ("167", "虚拟运营商"),
    ("168", "虚拟运营商"),
    ("146", "物联网号段"),
    ("148", "物联网号段"),
    ("1440", "物联网号段"),
    ("1410", "物联网号段"),
]

# Extra short PREFIX blocks commonly used by spam channels (already have 400/106).
EXTRA_PREFIXES = [
    ("1010", "可能骚扰"),
    ("12520", "可能骚扰"),
    ("12590", "可能骚扰"),
    ("118114", "可能骚扰"),
]

# Exact 11-digit seeds for onespam demos / known-pattern placeholders (not scraped private numbers).
# Pattern: virtual-looking numbers reserved for list testing + a few public-style landlines.
EXACT_SEEDS = [
    ("17000000000", "骚扰电话"),
    ("17000000001", "广告推销"),
    ("17000000002", "骚扰电话"),
    ("17000000003", "房产中介"),
    ("17000000004", "贷款推销"),
    ("17000000005", "保险推销"),
    ("17100000000", "骚扰电话"),
    ("17100000001", "广告推销"),
    ("17100000002", "诈骗电话"),
    ("16200000000", "诈骗电话"),
    ("16200000001", "骚扰电话"),
    ("16500000000", "骚扰电话"),
    ("16500000001", "广告推销"),
    ("16700000000", "骚扰电话"),
    ("16700000001", "诈骗电话"),
    ("16800000000", "骚扰电话"),
    ("14600000000", "骚扰电话"),
    ("14800000000", "骚扰电话"),
]


def main() -> int:
    base = {
        "schema": "onetools.blocklist.v1",
        "version": 3,
        "updatedAt": "2026-07-25",
        "numbers": [],
        "note": "PREFIX+EXACT dual-write into CallRuleStore and onespam. Virtual prefixes may false-positive delivery/taxi — whitelist as needed.",
    }

    # Preserve useful LABEL / TAG / bank entries
    base["numbers"].extend(
        [
            {"n": "400", "prefix": True, "tag": "可能骚扰", "kind": "block"},
            {"n": "106", "prefix": True, "tag": "短信通道", "kind": "block"},
            {"n": "95", "prefix": True, "tag": "企业热线", "kind": "label"},
            {"n": "95588", "kind": "label", "tag": "工商银行客服"},
            {"n": "10086", "kind": "label", "tag": "中国移动"},
            {"n": "10010", "kind": "label", "tag": "中国联通"},
            {"n": "10000", "kind": "label", "tag": "中国电信"},
            {"n": "95566", "kind": "label", "tag": "中国银行客服"},
            {"n": "95533", "kind": "label", "tag": "建设银行客服"},
            {"n": "95599", "kind": "label", "tag": "农业银行客服"},
            {"n": "95559", "kind": "label", "tag": "交通银行客服"},
            {"n": "95528", "kind": "label", "tag": "浦发银行客服"},
            {"n": "可能骚扰", "mode": "tag", "kind": "block"},
            {"n": "虚拟运营商", "mode": "tag", "kind": "block"},
            {"n": "诈骗电话", "mode": "tag", "kind": "block"},
        ]
    )

    for n, tag in VIRTUAL_PREFIXES + EXTRA_PREFIXES:
        base["numbers"].append({"n": n, "prefix": True, "tag": tag, "kind": "block"})

    for n, tag in EXACT_SEEDS:
        base["numbers"].append({"n": n, "kind": "block", "tag": tag})

    # Deduplicate by (mode,n,kind)
    seen: set[tuple] = set()
    uniq = []
    for item in base["numbers"]:
        key = (
            item.get("mode"),
            item.get("n"),
            item.get("kind"),
            item.get("prefix"),
        )
        if key in seen:
            continue
        seen.add(key)
        uniq.append(item)
    base["numbers"] = uniq

    SAMPLE.write_text(json.dumps(base, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {SAMPLE} entries={len(uniq)}")
    exact = sum(1 for x in uniq if x.get("kind") == "block" and not x.get("prefix") and x.get("mode") != "tag")
    prefix = sum(1 for x in uniq if x.get("prefix"))
    print(f"exact_blocks={exact} prefix_blocks={prefix}")

    # resign + rebuild pack + publish helpers left to caller
    build = ROOT / "onetools/scripts/build-onespam-pack.py"
    subprocess.check_call([sys.executable, str(build), str(SAMPLE)])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
