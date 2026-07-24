#!/usr/bin/env python3
"""Generate / sign One Index (onetools.update.v1) with ECDSA P-256."""
from __future__ import annotations

import argparse
import base64
import json
import sys
from pathlib import Path

try:
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.hazmat.primitives.asymmetric.utils import decode_dss_signature
except ImportError:
    print("pip install cryptography", file=sys.stderr)
    raise


ROOT = Path(__file__).resolve().parents[1]
PRIV = Path(__file__).resolve().parent / "one-index-dev-private.pem"
KEYS_JSON = ROOT / "src" / "main" / "assets" / "one-index-keys.json"
# Bump when rotating; old public keys remain in one-index-keys.json for overlap.
KEY_ID = "one-cdn-2026r2"


def gen_keys(rotate: bool = False) -> None:
    key = ec.generate_private_key(ec.SECP256R1())
    priv = key.private_bytes(
        serialization.Encoding.PEM,
        serialization.PrivateFormat.PKCS8,
        serialization.NoEncryption(),
    )
    pub = key.public_key().public_bytes(
        serialization.Encoding.DER,
        serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    PRIV.write_bytes(priv)
    existing: dict = {}
    if rotate and KEYS_JSON.exists():
        existing = json.loads(KEYS_JSON.read_text(encoding="utf-8"))
    existing[KEY_ID] = base64.b64encode(pub).decode()
    KEYS_JSON.write_text(json.dumps(existing, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {PRIV} keyId={KEY_ID}")
    print(f"wrote {KEYS_JSON} keys={list(existing)}")


def canonical_bytes(doc: dict) -> bytes:
    payload = {k: v for k, v in doc.items() if k not in ("signature", "sigAlg", "keyId")}
    return json.dumps(payload, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode("utf-8")


def sign_file(path: Path) -> None:
    if not PRIV.exists():
        gen_keys(rotate=True)
    key = serialization.load_pem_private_key(PRIV.read_bytes(), password=None)
    doc = json.loads(path.read_text(encoding="utf-8"))
    # Drop previous signature fields before resigning.
    for k in ("signature", "sigAlg", "keyId"):
        doc.pop(k, None)
    sig = key.sign(canonical_bytes(doc), ec.ECDSA(hashes.SHA256()))
    doc["keyId"] = KEY_ID
    doc["sigAlg"] = "SHA256withECDSA"
    doc["signature"] = base64.b64encode(sig).decode()
    path.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"signed {path} with {KEY_ID}")


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("cmd", choices=["gen-keys", "rotate-keys", "sign"])
    p.add_argument("index", nargs="?", type=Path)
    args = p.parse_args()
    if args.cmd == "gen-keys":
        gen_keys(rotate=False)
    elif args.cmd == "rotate-keys":
        gen_keys(rotate=True)
    else:
        if not args.index:
            raise SystemExit("sign needs index path")
        sign_file(args.index)


if __name__ == "__main__":
    main()
