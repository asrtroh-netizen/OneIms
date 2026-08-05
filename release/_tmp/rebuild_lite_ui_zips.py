"""Rebuild Lite/UI zips from oneroot-public; sync zipstage + OneSo-assets."""
from __future__ import annotations

import hashlib
import os
import shutil
import zipfile
from pathlib import Path

RELEASE = Path(r"E:\GQ\One\OneIMS\release")
PUBLIC = RELEASE / "oneroot-public" / "oneroot"
STAGE = RELEASE / "_zipstage"
ASSETS = Path(r"E:\GQ\One\OneSo-assets\oneroot")


def sync_dir(src: Path, dst: Path) -> None:
    if dst.exists():
        shutil.rmtree(dst)
    shutil.copytree(src, dst)
    # 云端-only：发包不带 preload-*.so（避免内置漂移）
    so_dir = dst / "so"
    if so_dir.is_dir():
        for p in so_dir.glob("*.so"):
            p.unlink(missing_ok=True)


def build_zip(src_dir: Path, zip_path: Path, root_name: str) -> str:
    if zip_path.exists():
        zip_path.unlink()
    files = [
        p
        for p in src_dir.rglob("*")
        if p.is_file() and p.suffix.lower() != ".so"
    ]
    if not files:
        raise SystemExit(f"empty source: {src_dir}")
    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for p in sorted(files, key=lambda x: x.as_posix().lower()):
            arc = f"{root_name}/{p.relative_to(src_dir).as_posix()}"
            # keep shell scripts bytes as-is (LF)
            zf.write(p, arcname=arc)
    digest = hashlib.sha256(zip_path.read_bytes()).hexdigest()
    with zipfile.ZipFile(zip_path) as zf:
        count = sum(1 for i in zf.infolist() if not i.is_dir())
    print(f"ZIP {zip_path.name}: files={count} size={zip_path.stat().st_size} sha256={digest}")
    if count < 10:
        raise SystemExit(f"zip too small entry count: {count}")
    return digest


def main() -> None:
    sync_dir(PUBLIC / "Lite", STAGE / "OneRoot-Lite")
    sync_dir(PUBLIC / "UI", STAGE / "OneRoot-UI")

    lite_hash = build_zip(STAGE / "OneRoot-Lite", RELEASE / "OneRoot-Lite.zip", "OneRoot-Lite")
    ui_hash = build_zip(STAGE / "OneRoot-UI", RELEASE / "OneRoot-UI.zip", "OneRoot-UI")

    # OneSo-assets 默认只读：同步 zip 到云端仓需显式授权（与 oneso 写保护一致）
    allow = os.environ.get("ONESO_ALLOW_CLOUD_WRITE", "").strip().lower() in (
        "1",
        "true",
        "yes",
        "on",
    )
    if allow:
        ASSETS.mkdir(parents=True, exist_ok=True)
        shutil.copy2(RELEASE / "OneRoot-Lite.zip", ASSETS / "OneRoot-Lite.zip")
        shutil.copy2(RELEASE / "OneRoot-UI.zip", ASSETS / "OneRoot-UI.zip")
        print("ASSETS synced:", ASSETS)
    else:
        print(
            "SKIP sync to OneSo-assets (set ONESO_ALLOW_CLOUD_WRITE=1 to allow)",
        )

    nested = RELEASE / "OneRoot-Lite"
    if nested.exists():
        shutil.rmtree(nested)
    nested.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(RELEASE / "OneRoot-Lite.zip") as zf:
        zf.extractall(nested)

    # verify keywords + cloud-only so (no bundled preload-*.so)
    with zipfile.ZipFile(RELEASE / "OneRoot-Lite.zip") as zf:
        names = zf.namelist()
        assert not any(n.endswith(".so") for n in names), "Lite zip must not bundle .so"
        assert any(n.endswith("fetch-cloud-so.ps1") for n in names), "Lite missing fetch-cloud-so.ps1"
        cmd = zf.read("OneRoot-Lite/一键临时Root.cmd").decode("utf-8", "replace")
        assert "fetch-cloud-so.ps1" in cmd, "Lite cmd must call cloud fetch"
        assert "su-keep" in cmd, "Lite missing su-keep"
        assert "su-teardown" in cmd or "TEARDOWN_OK" in cmd, "Lite missing teardown"
    with zipfile.ZipFile(RELEASE / "OneRoot-UI.zip") as zf:
        names = zf.namelist()
        assert not any(n.endswith(".so") for n in names), "UI zip must not bundle .so"
        assert any(n.endswith("fetch-cloud-so.ps1") for n in names), "UI missing fetch-cloud-so.ps1"
        ps1 = zf.read("OneRoot-UI/ui/TempRoot-UI.ps1").decode("utf-8", "replace")
        assert "fetch-cloud-so.ps1" in ps1, "UI must call cloud fetch"
        assert "su-keep" in ps1, "UI missing su-keep"
        assert "su-teardown" in ps1 or "TEARDOWN_OK" in ps1, "UI missing teardown"

    print("OK", lite_hash[:16], ui_hash[:16])


if __name__ == "__main__":
    main()
