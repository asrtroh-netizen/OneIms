#!/usr/bin/env python3
"""Aggregate onetools.report.v1 JSON → candidate / merge into one-blocklist.json.

Phase-2 community feedback (¥0): no commercial API, GitHub/local script only.

Rules (defaults match product design):
  - same phone + tag with ≥ threshold distinct clientIds → candidate block
  - single clientId > max_per_client_day reports in a UTC day → ignore surplus
  - wrong_tag with ≥ threshold distinct clients → demote / remove candidate & block entry
  - LABEL / protected service numbers never become block candidates
"""
from __future__ import annotations

import argparse
import json
import sys
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_BLOCKLIST = ROOT / "docs/product/samples/one-blocklist.json"
DEFAULT_REPORTS = ROOT / "docs/product/samples/caller-reports"
DEFAULT_OUT_CANDIDATES = ROOT / "docs/product/samples/caller-report-candidates.json"

SCHEMA = "onetools.report.v1"

TAG_ZH = {
    "spam": "骚扰电话",
    "fraud": "诈骗电话",
    "agent": "房产中介",
    "sales": "广告推销",
    "other": "骚扰电话",
}

# Never auto-block these exact numbers / short codes (LABEL / public service).
PROTECTED_EXACT = {
    "10086",
    "10010",
    "10000",
    "10001",
    "95588",
    "95566",
    "95533",
    "95599",
    "95559",
    "95528",
    "110",
    "119",
    "120",
    "122",
}


def digits(s: str) -> str:
    d = "".join(c for c in str(s) if c.isdigit())
    if d.startswith("86") and len(d) > 11:
        d = d[2:]
    return d


def load_report_files(paths: list[Path]) -> list[dict]:
    out: list[dict] = []
    for p in paths:
        if p.is_dir():
            files = sorted(p.glob("*.json"))
        else:
            files = [p]
        for f in files:
            data = json.loads(f.read_text(encoding="utf-8"))
            if data.get("schema") and data.get("schema") != SCHEMA:
                print(f"warn: skip {f} schema={data.get('schema')}", file=sys.stderr)
                continue
            reports = data.get("reports") or []
            if not isinstance(reports, list):
                print(f"warn: skip {f} bad reports", file=sys.stderr)
                continue
            for r in reports:
                if not isinstance(r, dict):
                    continue
                phone = digits(r.get("phone", ""))
                tag = str(r.get("tag") or "spam").strip().lower()
                client = str(r.get("clientId") or "").strip() or "anonymous"
                created = int(r.get("createdAt") or 0)
                if len(phone) < 7:
                    continue
                out.append(
                    {
                        "phone": phone,
                        "tag": tag,
                        "clientId": client,
                        "createdAt": created,
                        "sourceFile": str(f),
                    }
                )
    return out


def utc_day(ms: int) -> str:
    if ms <= 0:
        return "unknown"
    return datetime.fromtimestamp(ms / 1000.0, tz=timezone.utc).strftime("%Y-%m-%d")


def apply_rate_limit(
    rows: list[dict], max_per_client_day: int
) -> tuple[list[dict], int]:
    counts: dict[tuple[str, str], int] = defaultdict(int)
    kept: list[dict] = []
    dropped = 0
    # Stable order: older first so early reports count
    for r in sorted(rows, key=lambda x: x["createdAt"]):
        key = (r["clientId"], utc_day(r["createdAt"]))
        counts[key] += 1
        if counts[key] > max_per_client_day:
            dropped += 1
            continue
        kept.append(r)
    return kept, dropped


def aggregate(
    rows: list[dict], threshold: int
) -> tuple[dict[tuple[str, str], set[str]], dict[str, set[str]]]:
    """Return (phone,tag)->clients and wrong_tag phone->clients."""
    votes: dict[tuple[str, str], set[str]] = defaultdict(set)
    wrong: dict[str, set[str]] = defaultdict(set)
    for r in rows:
        phone, tag, cid = r["phone"], r["tag"], r["clientId"]
        if tag == "wrong_tag":
            wrong[phone].add(cid)
            continue
        if tag not in TAG_ZH:
            tag = "spam"
        votes[(phone, tag)].add(cid)
    return votes, wrong


def protected(phone: str) -> bool:
    if phone in PROTECTED_EXACT:
        return True
    # Any LABEL-style short code ≤6 digits that looks like carrier/bank
    if len(phone) <= 6 and phone.startswith(("100", "95", "12")):
        return True
    return False


def load_blocklist(path: Path) -> dict:
    if path.exists():
        return json.loads(path.read_text(encoding="utf-8"))
    return {
        "schema": "onetools.blocklist.v1",
        "version": 1,
        "updatedAt": datetime.now(tz=timezone.utc).strftime("%Y-%m-%d"),
        "numbers": [],
    }


def is_exact_block(item: dict) -> bool:
    if item.get("mode") == "tag" or item.get("tagRule"):
        return False
    if item.get("prefix"):
        return False
    if str(item.get("kind", "block")).lower() != "block":
        return False
    n = digits(item.get("n") or item.get("number") or "")
    return len(n) >= 7


def merge_candidates(
    blocklist: dict,
    promote: list[tuple[str, str, int]],
    demote_phones: set[str],
) -> tuple[int, int]:
    numbers: list[dict] = list(blocklist.get("numbers") or [])
    removed = 0
    if demote_phones:
        kept = []
        for item in numbers:
            if is_exact_block(item):
                n = digits(item.get("n") or "")
                if n in demote_phones:
                    removed += 1
                    continue
            kept.append(item)
        numbers = kept

    existing_exact = {
        digits(item.get("n") or "")
        for item in numbers
        if is_exact_block(item)
    }
    added = 0
    for phone, tag, _count in promote:
        if phone in demote_phones or protected(phone):
            continue
        if phone in existing_exact:
            # refresh tag if already present
            for item in numbers:
                if is_exact_block(item) and digits(item.get("n") or "") == phone:
                    item["tag"] = TAG_ZH.get(tag, "骚扰电话")
                    item["kind"] = "block"
                    item["source"] = "community-report"
            continue
        numbers.append(
            {
                "n": phone,
                "kind": "block",
                "tag": TAG_ZH.get(tag, "骚扰电话"),
                "source": "community-report",
            }
        )
        existing_exact.add(phone)
        added += 1

    blocklist["numbers"] = numbers
    return added, removed


def main() -> int:
    ap = argparse.ArgumentParser(description="Ingest onetools.report.v1 → OneBlock candidates")
    ap.add_argument(
        "inputs",
        nargs="*",
        type=Path,
        help="report JSON files or directories (default: docs/product/samples/caller-reports)",
    )
    ap.add_argument("--blocklist", type=Path, default=DEFAULT_BLOCKLIST)
    ap.add_argument("--threshold", type=int, default=3)
    ap.add_argument("--max-per-client-day", type=int, default=50)
    ap.add_argument("--candidates-out", type=Path, default=DEFAULT_OUT_CANDIDATES)
    ap.add_argument(
        "--apply",
        action="store_true",
        help="merge promoted candidates into blocklist (default: dry candidates only)",
    )
    ap.add_argument(
        "--bump-date",
        action="store_true",
        help="set blocklist updatedAt to today UTC when --apply",
    )
    args = ap.parse_args()

    inputs = args.inputs or [DEFAULT_REPORTS]
    rows = load_report_files(inputs)
    rows, dropped = apply_rate_limit(rows, args.max_per_client_day)
    votes, wrong = aggregate(rows, args.threshold)

    promote: list[tuple[str, str, int]] = []
    for (phone, tag), clients in sorted(votes.items()):
        if protected(phone):
            continue
        if len(clients) >= args.threshold:
            promote.append((phone, tag, len(clients)))

    demote = {p for p, clients in wrong.items() if len(clients) >= args.threshold}

    # wrong_tag also blocks promotion of that phone this run
    promote = [x for x in promote if x[0] not in demote]

    candidates = {
        "schema": "onetools.report.candidates.v1",
        "threshold": args.threshold,
        "maxPerClientDay": args.max_per_client_day,
        "inputReports": len(rows),
        "rateLimitedDropped": dropped,
        "promote": [
            {"phone": p, "tag": t, "distinctClients": c, "labelZh": TAG_ZH.get(t, "骚扰电话")}
            for p, t, c in promote
        ],
        "demote": sorted(demote),
        "generatedAt": datetime.now(tz=timezone.utc).isoformat(),
    }
    args.candidates_out.parent.mkdir(parents=True, exist_ok=True)
    args.candidates_out.write_text(
        json.dumps(candidates, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(
        f"reports={len(rows)} dropped_rate={dropped} "
        f"promote={len(promote)} demote={len(demote)} → {args.candidates_out}"
    )

    if not args.apply:
        print("dry-run: pass --apply to merge into blocklist")
        return 0

    bl = load_blocklist(args.blocklist)
    added, removed = merge_candidates(bl, promote, demote)
    if args.bump_date:
        bl["updatedAt"] = datetime.now(tz=timezone.utc).strftime("%Y-%m-%d")
        try:
            bl["version"] = int(bl.get("version") or 0) + 1
        except (TypeError, ValueError):
            bl["version"] = bl.get("version")
    args.blocklist.parent.mkdir(parents=True, exist_ok=True)
    args.blocklist.write_text(
        json.dumps(bl, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"applied blocklist={args.blocklist} added={added} removed={removed}")
    print("next: python onetools/scripts/build-onespam-pack.py <blocklist>")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
