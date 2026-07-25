#!/usr/bin/env python3
"""Expand OneTools phone blocklist with CN spam prefixes + exact seeds, then rebuild onespam pack.

Preserves prior `source=community-report` exact blocks and optional ingest candidates
so community feedback is not wiped on each expand.
"""
from __future__ import annotations

import json
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SAMPLE = ROOT / "docs/product/samples/one-blocklist.json"
CANDIDATES = ROOT / "docs/product/samples/caller-report-candidates.json"

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

EXTRA_PREFIXES = [
    ("1010", "可能骚扰"),
    ("12520", "可能骚扰"),
    ("12590", "可能骚扰"),
    ("118114", "可能骚扰"),
]

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

TAG_ZH = {
    "spam": "骚扰电话",
    "fraud": "诈骗电话",
    "agent": "房产中介",
    "sales": "广告推销",
    "other": "骚扰电话",
}


def digits(s: str) -> str:
    d = "".join(c for c in str(s) if c.isdigit())
    if d.startswith("86") and len(d) > 11:
        d = d[2:]
    return d


def is_community_exact(item: dict) -> bool:
    if str(item.get("source", "")).lower() != "community-report":
        return False
    if item.get("mode") == "tag" or item.get("prefix"):
        return False
    if str(item.get("kind", "block")).lower() != "block":
        return False
    return len(digits(item.get("n") or "")) >= 7


def load_preserved_community() -> list[dict]:
    if not SAMPLE.exists():
        return []
    data = json.loads(SAMPLE.read_text(encoding="utf-8"))
    return [dict(x) for x in data.get("numbers") or [] if is_community_exact(x)]


def load_candidate_exact() -> list[dict]:
    if not CANDIDATES.exists():
        return []
    data = json.loads(CANDIDATES.read_text(encoding="utf-8"))
    demote = set(str(x) for x in data.get("demote") or [])
    out: list[dict] = []
    for item in data.get("promote") or []:
        phone = digits(item.get("phone") or "")
        if len(phone) < 7 or phone in demote:
            continue
        tag_wire = str(item.get("tag") or "spam").lower()
        out.append(
            {
                "n": phone,
                "kind": "block",
                "tag": item.get("labelZh") or TAG_ZH.get(tag_wire, "骚扰电话"),
                "source": "community-report",
            }
        )
    return out


def dedupe(numbers: list[dict]) -> list[dict]:
    seen: set[tuple] = set()
    uniq: list[dict] = []
    for item in numbers:
        key = (
            item.get("mode"),
            digits(item.get("n") or "") if item.get("mode") != "tag" else item.get("n"),
            item.get("kind"),
            bool(item.get("prefix")),
        )
        if key in seen:
            continue
        seen.add(key)
        uniq.append(item)
    return uniq


def main() -> int:
    today = datetime.now(tz=timezone.utc).strftime("%Y-%m-%d")
    preserved = load_preserved_community()
    from_candidates = load_candidate_exact()

    base = {
        "schema": "onetools.blocklist.v1",
        "version": 4,
        "updatedAt": today,
        "numbers": [],
        "note": (
            "PREFIX+EXACT dual-write into CallRuleStore and onespam. "
            "Virtual prefixes may false-positive delivery/taxi — whitelist as needed. "
            "Own OneBlock path only — do not bind Telo CDN."
        ),
    }

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

    # Community last so seed keys win on dedupe (same n keeps seed first).
    # Prefer community tag when only community has the number — append after seeds;
    # if same n already in seeds, community is skipped by dedupe (seed wins).
    community = preserved + from_candidates
    seed_ns = {n for n, _ in EXACT_SEEDS}
    for item in community:
        n = digits(item.get("n") or "")
        if n in seed_ns:
            continue
        base["numbers"].append(item)

    uniq = dedupe(base["numbers"])
    base["numbers"] = uniq

    SAMPLE.write_text(json.dumps(base, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    community_kept = sum(1 for x in uniq if is_community_exact(x))
    print(f"wrote {SAMPLE} entries={len(uniq)} community_exact={community_kept}")
    exact = sum(
        1
        for x in uniq
        if x.get("kind") == "block" and not x.get("prefix") and x.get("mode") != "tag"
    )
    prefix = sum(1 for x in uniq if x.get("prefix"))
    print(f"exact_blocks={exact} prefix_blocks={prefix}")

    build = ROOT / "onetools/scripts/build-onespam-pack.py"
    subprocess.check_call([sys.executable, str(build), str(SAMPLE)])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
