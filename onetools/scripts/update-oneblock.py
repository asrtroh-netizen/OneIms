#!/usr/bin/env python3
"""Own OneBlock update pipeline — no Telo CDN, no commercial lookup API.

Steps:
  1) optional ingest community reports → candidates (+ optional --apply into sample)
  2) expand seeds + preserve community-report rows → rebuild onespam zip + spam-sync.json
  3) mirror sample into app assets + onetools/cdn/caller/

Does NOT push GitHub Release / OneBlock repo (run publish-blocklist.ps1 yourself).
"""
from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SAMPLE = ROOT / "docs/product/samples/one-blocklist.json"
REPORTS = ROOT / "docs/product/samples/caller-reports"
ASSET = ROOT / "onetools/src/main/assets/sample-one-blocklist.json"
CDN = ROOT / "onetools/cdn/caller"


def run(cmd: list[str]) -> None:
    print("+", " ".join(cmd))
    subprocess.check_call(cmd)


def main() -> int:
    ap = argparse.ArgumentParser(description="Update own OneBlock → onespam assets")
    ap.add_argument(
        "--ingest",
        action="store_true",
        help="run ingest-reports.py on caller-reports (dry candidates by default)",
    )
    ap.add_argument(
        "--apply-ingest",
        action="store_true",
        help="with --ingest, also merge candidates into sample before expand",
    )
    ap.add_argument(
        "--skip-expand",
        action="store_true",
        help="only rebuild pack from current sample (no rewrite seeds)",
    )
    args = ap.parse_args()

    py = sys.executable
    if args.ingest:
        ingest = [py, str(ROOT / "onetools/scripts/ingest-reports.py"), str(REPORTS)]
        if args.apply_ingest:
            ingest += ["--apply", "--bump-date"]
        run(ingest)

    if args.skip_expand:
        run([py, str(ROOT / "onetools/scripts/build-onespam-pack.py"), str(SAMPLE)])
    else:
        run([py, str(ROOT / "onetools/scripts/expand-oneblock-spam.py")])

    ASSET.parent.mkdir(parents=True, exist_ok=True)
    CDN.mkdir(parents=True, exist_ok=True)
    shutil.copy2(SAMPLE, ASSET)
    shutil.copy2(SAMPLE, CDN / "one-blocklist.json")
    print(f"mirrored → {ASSET}")
    print(f"mirrored → {CDN / 'one-blocklist.json'}")
    print()
    print("Next (manual publish, not run by this script):")
    print("  pwsh onetools/scripts/publish-blocklist.ps1")
    print("  gh release upload onetools-cdn-assets onetools/cdn/caller/onespam_*.zip \\")
    print("    onetools/cdn/caller/spam-sync.json --repo asrtroh-netizen/OneBlock --clobber")
    print("App: Caller → 检查云端更新 (manifest = OneBlock spam-sync.json)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
