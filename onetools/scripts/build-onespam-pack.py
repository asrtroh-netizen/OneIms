#!/usr/bin/env python3
"""Build onespam.db + zip + spam-sync.json from onetools.blocklist.v1 JSON."""
from __future__ import annotations

import hashlib
import json
import sqlite3
import sys
import zipfile
from pathlib import Path


def digits(s: str) -> str:
    return "".join(c for c in s if c.isdigit())


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    src = Path(sys.argv[1]) if len(sys.argv) > 1 else root / "docs/product/samples/one-blocklist.json"
    out_dir = Path(sys.argv[2]) if len(sys.argv) > 2 else root / "onetools/cdn/caller"
    out_dir.mkdir(parents=True, exist_ok=True)

    data = json.loads(src.read_text(encoding="utf-8"))
    version = str(data.get("updatedAt") or data.get("version") or "local")
    version_safe = "".join(c if c.isalnum() or c in "-_" else "-" for c in version)

    rows: list[tuple[str, str, str]] = []
    for item in data.get("numbers", []):
        mode = str(item.get("mode", "")).lower()
        if mode == "tag" or item.get("tagRule"):
            continue
        kind = str(item.get("kind", "block")).lower()
        if kind not in ("block",):
            continue
        is_prefix = bool(item.get("prefix")) or mode == "prefix"
        n = digits(str(item.get("n") or item.get("number") or ""))
        n = n[2:] if n.startswith("86") and len(n) > 11 else n
        # Exact need >=7; prefixes can be shorter (400/170/…).
        if is_prefix:
            if len(n) < 2:
                continue
        elif len(n) < 7:
            continue
        tag = str(item.get("tag") or "骚扰电话")
        rows.append((n, tag, "oneblock"))

    # dedupe
    seen: set[str] = set()
    uniq: list[tuple[str, str, str]] = []
    for r in rows:
        if r[0] in seen:
            continue
        seen.add(r[0])
        uniq.append(r)

    db_path = out_dir / f"onespam_{version_safe}.db"
    if db_path.exists():
        db_path.unlink()
    conn = sqlite3.connect(db_path)
    try:
        c = conn.cursor()
        c.execute(
            "CREATE TABLE spam_numbers (phone_number TEXT PRIMARY KEY NOT NULL, tag TEXT NOT NULL, source TEXT NOT NULL)"
        )
        c.execute(
            "CREATE TABLE metadata (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)"
        )
        c.executemany(
            "INSERT INTO spam_numbers(phone_number, tag, source) VALUES (?,?,?)",
            uniq,
        )
        c.execute(
            "INSERT INTO metadata(key, value) VALUES ('version', ?)",
            (version_safe,),
        )
        conn.commit()
    finally:
        conn.close()

    zip_path = out_dir / f"onespam_{version_safe}.zip"
    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.write(db_path, arcname=f"onespam_{version_safe}.db")

    sha = hashlib.sha256(zip_path.read_bytes()).hexdigest()
    # Default to OneBlock release mirror (same tag as phone blocklist assets).
    download_url = (
        "https://github.com/asrtroh-netizen/OneBlock/releases/download/"
        f"onetools-cdn-assets/{zip_path.name}"
    )
    manifest = {
        "has_update": True,
        "latest_version": version_safe,
        "download_url": download_url,
        "size_bytes": zip_path.stat().st_size,
        "checksum": sha,
        "row_count": len(uniq),
    }
    manifest_path = out_dir / "spam-sync.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    # also copy blocklist next to pack
    (out_dir / "one-blocklist.json").write_text(
        json.dumps(data, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    print(f"db={db_path} rows={len(uniq)}")
    print(f"zip={zip_path} sha256={sha} size={manifest['size_bytes']}")
    print(f"manifest={manifest_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
