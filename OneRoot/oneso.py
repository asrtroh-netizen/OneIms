#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""OneSo — IonStack preload.so 编译工厂（包 Makefile，不是魔法生成器）。"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

HERE = Path(__file__).resolve().parent
DEFAULT_CONFIG = HERE / "config.json"
EXAMPLE_CONFIG = HERE / "config.example.json"
PROJECT_RE = re.compile(r"^([A-Za-z0-9_]+)-(.+)$")

# Pixel 9 家族 · CP2A.260705.006：同构建共享偏移，仅改 label（见 comet README-ADAPT）。
BUILD_0705 = "CP2A.260705.006"
LABEL_0705_SUFFIX = "_cp2a_260705_006"
# 标签槽：原串 comet_cp2a_260705_006 + \\0 + pad，最长 label 22 字节。
LABEL_0705_MAX = 22
DEVICES_0705 = ("tokay", "caiman", "komodo", "comet")

# Pixel 9 · 0805：尚无官方 OTA/偏移时，从已验证 0705 so 改 label 克隆（同槽长）。
# 注意：若 0805 内核偏移变了，需换真源 so；此包仅作 catalog/目录占位与邻近 build 试验。
BUILD_0805 = "CP2A.260805.005"
LABEL_0805_SUFFIX = "_cp2a_260805_005"

# Pixel 10 家族：blazer / frankel / mustang / rango（与 P9 二进制不同，不可交叉改 label）。
# OneSo-assets 当前最新齐套档：CP2A.260605.012（尚无 0705 P10 成品）。
BUILD_P10 = "CP2A.260605.012"
DEVICES_P10 = ("blazer", "frankel", "mustang", "rango")
DEFAULT_ONESO_ASSETS = Path(r"E:\GQ\One\OneSo-assets")


def load_config(path: Path | None) -> dict[str, Any]:
    cfg_path = path or DEFAULT_CONFIG
    if not cfg_path.is_file():
        if EXAMPLE_CONFIG.is_file() and path is None:
            shutil.copyfile(EXAMPLE_CONFIG, DEFAULT_CONFIG)
            cfg_path = DEFAULT_CONFIG
            print(f"[oneso] created {DEFAULT_CONFIG} from example", file=sys.stderr)
        else:
            raise SystemExit(f"config not found: {cfg_path}")
    data = json.loads(cfg_path.read_text(encoding="utf-8"))
    for key in ("exploit_root", "oneims_root"):
        if key not in data or not str(data[key]).strip():
            raise SystemExit(f"config missing {key}")
    data["_config_path"] = str(cfg_path)
    return data


def exploit_root(cfg: dict[str, Any]) -> Path:
    p = Path(cfg["exploit_root"]).expanduser().resolve()
    if not (p / "Makefile").is_file():
        raise SystemExit(f"exploit Makefile missing under {p}")
    return p


def oneims_root(cfg: dict[str, Any]) -> Path:
    p = Path(cfg["oneims_root"]).expanduser().resolve()
    if not (p / "app").is_dir():
        raise SystemExit(f"oneims_root looks wrong: {p}")
    return p


def list_projects(root: Path) -> list[str]:
    targets = root / "src" / "targets"
    if not targets.is_dir():
        return []
    out: list[str] = []
    for child in sorted(targets.iterdir()):
        if child.is_dir() and (child / "target.h").is_file():
            out.append(child.name)
    return out


def parse_project(project: str) -> tuple[str, str]:
    m = PROJECT_RE.match(project.strip())
    if not m:
        raise SystemExit(
            f"PROJECT must look like device-BuildId, got: {project!r}",
        )
    return m.group(1), m.group(2)


def win_to_wsl(path: Path) -> str:
    s = str(path.resolve())
    # E:\foo -> /mnt/e/foo
    if len(s) >= 2 and s[1] == ":":
        drive = s[0].lower()
        rest = s[2:].replace("\\", "/")
        return f"/mnt/{drive}{rest}"
    return s.replace("\\", "/")


def run_wsl(
    cfg: dict[str, Any],
    bash_cmd: str,
    *,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    distro = str(cfg.get("wsl_distro") or "").strip()
    args = ["wsl"]
    if distro:
        args += ["-d", distro]
    args += ["-e", "bash", "-lc", bash_cmd]
    print(f"[oneso] $ {' '.join(args[:4])} …", file=sys.stderr)
    return subprocess.run(
        args,
        check=check,
        text=True,
        capture_output=False,
    )


def cmd_list(cfg: dict[str, Any]) -> int:
    root = exploit_root(cfg)
    projects = list_projects(root)
    if not projects:
        print("(no targets with target.h)")
        return 1
    for name in projects:
        device, build = parse_project(name)
        print(f"{name}\tdevice={device}\tbuild={build}")
    print(f"[oneso] {len(projects)} project(s) under {root}", file=sys.stderr)
    return 0


def build_output_so(root: Path, project: str) -> Path:
    return root / "build" / project / "bin" / "preload.so"


def cmd_build(cfg: dict[str, Any], project: str) -> int:
    root = exploit_root(cfg)
    if project not in list_projects(root):
        raise SystemExit(f"unknown PROJECT={project} (no target.h). Run: oneso list")
    api = int(cfg.get("api") or 35)
    use_wsl = bool(cfg.get("use_wsl", True))
    out = build_output_so(root, project)
    if use_wsl:
        wsl_root = win_to_wsl(root)
        ndk = str(cfg.get("ndk_root_wsl") or "").strip()
        env_prefix = ""
        if ndk:
            env_prefix = f'export ANDROID_NDK_HOME="{ndk}"; export NDK_ROOT="{ndk}"; '
        bash = (
            f"{env_prefix}cd '{wsl_root}' && "
            f"make preload PROJECT='{project}' API={api}"
        )
        run_wsl(cfg, bash, check=True)
    else:
        env = os.environ.copy()
        ndk_win = str(cfg.get("ndk_root") or "").strip()
        if ndk_win:
            env["ANDROID_NDK_HOME"] = ndk_win
            env["NDK_ROOT"] = ndk_win
        subprocess.run(
            ["make", "preload", f"PROJECT={project}", f"API={api}"],
            cwd=str(root),
            check=True,
            env=env,
        )
    if not out.is_file():
        raise SystemExit(f"build finished but missing {out}")
    digest = hashlib.sha256(out.read_bytes()).hexdigest().upper()
    print(f"[oneso] OK {out}")
    print(f"[oneso] SHA256 {digest}")
    return 0


def asset_file_name(device: str, build_id: str) -> str:
    safe_build = build_id.replace("/", "_")
    return f"preload-{device}-{safe_build}.so"


def cmd_install(cfg: dict[str, Any], project: str, *, build_first: bool) -> int:
    if build_first:
        cmd_build(cfg, project)
    root = exploit_root(cfg)
    app = oneims_root(cfg)
    src = build_output_so(root, project)
    if not src.is_file():
        raise SystemExit(f"missing built so: {src} (run oneso build {project})")
    device, build_id = parse_project(project)
    name = asset_file_name(device, build_id)
    assets = app / "app" / "src" / "main" / "assets" / "temproot"
    assets.mkdir(parents=True, exist_ok=True)
    dst = assets / name
    shutil.copy2(src, dst)
    digest = hashlib.sha256(dst.read_bytes()).hexdigest().upper()

    catalog_path = assets / "catalog.json"
    if catalog_path.is_file():
        catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    else:
        catalog = {"version": 1, "devices": {}}
    devices = catalog.setdefault("devices", {})
    if not isinstance(devices, dict):
        raise SystemExit("catalog.json devices must be object")
    bucket = devices.setdefault(device, {})
    if not isinstance(bucket, dict):
        raise SystemExit(f"catalog devices.{device} must be object")
    bucket[build_id] = name
    catalog["version"] = int(catalog.get("version") or 1)
    catalog_path.write_text(
        json.dumps(catalog, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"[oneso] installed {dst}")
    print(f"[oneso] catalog <- {device}/{build_id} = {name}")
    print(f"[oneso] SHA256 {digest}")
    return 0


def _write_catalog_entry(app: Path, device: str, build_id: str, name: str) -> Path:
    assets = app / "app" / "src" / "main" / "assets" / "temproot"
    assets.mkdir(parents=True, exist_ok=True)
    catalog_path = assets / "catalog.json"
    if catalog_path.is_file():
        catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    else:
        catalog = {"version": 1, "devices": {}}
    devices = catalog.setdefault("devices", {})
    if not isinstance(devices, dict):
        raise RuntimeError("catalog.json devices must be object")
    bucket = devices.setdefault(device, {})
    if not isinstance(bucket, dict):
        raise RuntimeError(f"catalog devices.{device} must be object")
    bucket[build_id] = name
    catalog["version"] = int(catalog.get("version") or 1)
    catalog_path.write_text(
        json.dumps(catalog, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return catalog_path


def import_so_core(cfg: dict[str, Any], project: str, so_path: Path) -> dict[str, str]:
    """入库成品 so；成功返回路径与哈希。"""
    src = so_path.expanduser().resolve()
    if not src.is_file():
        raise FileNotFoundError(f"so not found: {src}")
    app = oneims_root(cfg)
    device, build_id = parse_project(project)
    name = asset_file_name(device, build_id)
    assets = app / "app" / "src" / "main" / "assets" / "temproot"
    assets.mkdir(parents=True, exist_ok=True)
    dst = assets / name
    shutil.copy2(src, dst)
    digest = hashlib.sha256(dst.read_bytes()).hexdigest().upper()
    _write_catalog_entry(app, device, build_id, name)
    return {
        "project": project,
        "src": str(src),
        "dst": str(dst),
        "sha256": digest,
        "device": device,
        "build": build_id,
        "asset": name,
    }


def cmd_import_so(cfg: dict[str, Any], project: str, so_path: Path) -> int:
    """把已有成品 so（例如字符串改标签产物）入库 OneIMS，不经过 make。"""
    try:
        result = import_so_core(cfg, project, so_path)
    except Exception as exc:  # noqa: BLE001 — CLI 边界
        raise SystemExit(str(exc)) from exc
    print(f"[oneso] imported {result['src']} -> {result['dst']}")
    print(f"[oneso] catalog <- {result['device']}/{result['build']} = {result['asset']}")
    print(f"[oneso] SHA256 {result['sha256']}")
    return 0


def _norm_key(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", "", text.lower())


def guess_project(path: Path, projects: list[str]) -> str | None:
    """根据路径/文件名猜测 PROJECT（仅匹配已知 targets）。"""
    if path.parent.name in projects:
        return path.parent.name
    stem = path.stem
    stem_key = _norm_key(stem)
    # 最长匹配，避免 comet 吃掉 comet-xxx
    ranked = sorted(projects, key=len, reverse=True)
    for proj in ranked:
        device, build = parse_project(proj)
        candidates = [
            proj,
            f"preload-{proj}",
            f"preload-{device}-{build}",
            f"preload-{device}-{build.replace('.', '_')}",
            f"preload-{device}-{build.replace('.', '-')}",
            f"{device}_{build.replace('.', '_')}",
            f"{device}-{build.replace('.', '-')}",
        ]
        for c in candidates:
            if stem_key == _norm_key(c) or stem_key.endswith(_norm_key(c)):
                return proj
        # 宽松：stem 含 device + 构建数字串
        build_digits = _norm_key(build)
        if _norm_key(device) in stem_key and build_digits in stem_key:
            return proj
    return None


def iter_so_files(directory: Path, *, recursive: bool) -> list[Path]:
    directory = directory.expanduser().resolve()
    if not directory.is_dir():
        raise FileNotFoundError(f"not a directory: {directory}")
    if recursive:
        return sorted(p for p in directory.rglob("*.so") if p.is_file())
    return sorted(p for p in directory.glob("*.so") if p.is_file())


def cmd_import_batch(
    cfg: dict[str, Any],
    directory: Path,
    *,
    recursive: bool,
    dry_run: bool,
    mapping_path: Path | None,
) -> int:
    root = exploit_root(cfg)
    projects = list_projects(root)
    mapping: dict[str, str] = {}
    if mapping_path is not None:
        raw = json.loads(mapping_path.read_text(encoding="utf-8"))
        if not isinstance(raw, dict):
            raise SystemExit("mapping json must be object: filename -> PROJECT")
        mapping = {str(k): str(v) for k, v in raw.items()}

    files = iter_so_files(directory, recursive=recursive)
    if not files:
        print("[oneso] no .so files found", file=sys.stderr)
        return 1

    ok = 0
    skip = 0
    for so in files:
        project = mapping.get(so.name) or mapping.get(str(so)) or guess_project(so, projects)
        if not project:
            print(f"[oneso] SKIP (no project match): {so}")
            skip += 1
            continue
        if project not in projects:
            print(f"[oneso] SKIP (unknown PROJECT={project}): {so}")
            skip += 1
            continue
        if dry_run:
            print(f"[oneso] DRY {so} -> {project}")
            ok += 1
            continue
        try:
            result = import_so_core(cfg, project, so)
        except Exception as exc:  # noqa: BLE001
            print(f"[oneso] FAIL {so}: {exc}")
            skip += 1
            continue
        print(
            f"[oneso] OK {so.name} -> {result['project']} "
            f"({result['sha256'][:12]}…)",
        )
        ok += 1
    print(f"[oneso] batch done ok={ok} skip={skip} total={len(files)}", file=sys.stderr)
    return 0 if ok else 1


def make_0705_label(device: str) -> str:
    label = f"{device}{LABEL_0705_SUFFIX}"
    if len(label) > LABEL_0705_MAX:
        raise ValueError(f"label too long for in-place patch: {label} ({len(label)})")
    return label


def patch_0705_label(so_bytes: bytes, device: str) -> bytes:
    """把 so 内 `*_cp2a_260705_006` 标签改成目标机型（利用尾随 NUL 填充槽）。"""
    return _patch_cp2a_label(
        so_bytes,
        device,
        from_suffixes=(LABEL_0705_SUFFIX,),
        to_suffix=LABEL_0705_SUFFIX,
    )


def make_0805_label(device: str) -> str:
    label = f"{device}{LABEL_0805_SUFFIX}"
    if len(label) > LABEL_0705_MAX:
        raise ValueError(f"label too long for in-place patch: {label} ({len(label)})")
    return label


def _patch_cp2a_label(
    so_bytes: bytes,
    device: str,
    *,
    from_suffixes: tuple[str, ...],
    to_suffix: str,
) -> bytes:
    """通用：替换 so 内 `*<suffix>` 标签槽为目标 `{device}{to_suffix}`。"""
    m = None
    for suf in from_suffixes:
        pat = re.escape(suf.encode("ascii"))
        m = re.search(rb"([a-z0-9]+" + pat + rb")(\x00+)", so_bytes)
        if m:
            break
    if not m:
        raise ValueError(
            "source so missing label suffixes "
            + "/".join(from_suffixes),
        )
    slot_start = m.start(1)
    slot_end = m.end(2)
    slot = slot_end - slot_start
    new_label = f"{device}{to_suffix}".encode("ascii")
    if len(new_label) > LABEL_0705_MAX:
        raise ValueError(f"label too long: {new_label.decode()}")
    if len(new_label) + 1 > slot:
        raise ValueError(
            f"label {new_label.decode()} does not fit slot={slot}",
        )
    patched = new_label + b"\x00" * (slot - len(new_label))
    return so_bytes[:slot_start] + patched + so_bytes[slot_end:]


def patch_0805_label(so_bytes: bytes, device: str) -> bytes:
    """从 0705（或已是 0805）标签改成目标机型的 0805 标签。"""
    return _patch_cp2a_label(
        so_bytes,
        device,
        from_suffixes=(LABEL_0705_SUFFIX, LABEL_0805_SUFFIX),
        to_suffix=LABEL_0805_SUFFIX,
    )


def catalog_0705_complete(cfg: dict[str, Any]) -> bool:
    app = oneims_root(cfg)
    catalog_path = app / "app" / "src" / "main" / "assets" / "temproot" / "catalog.json"
    if not catalog_path.is_file():
        return False
    data = json.loads(catalog_path.read_text(encoding="utf-8"))
    devices = data.get("devices") or {}
    for device in DEVICES_0705:
        entry = devices.get(device) or {}
        name = entry.get(BUILD_0705)
        if not name:
            return False
        so = app / "app" / "src" / "main" / "assets" / "temproot" / name
        if not so.is_file():
            return False
        label = make_0705_label(device).encode("ascii")
        if label not in so.read_bytes():
            return False
    return True


def adb_device_build() -> tuple[str | None, str | None]:
    """读 adb getprop；失败返回 (None, None)。"""
    try:
        device = subprocess.check_output(
            ["adb", "shell", "getprop", "ro.product.device"],
            text=True,
            stderr=subprocess.DEVNULL,
            timeout=8,
        ).strip()
        build = subprocess.check_output(
            ["adb", "shell", "getprop", "ro.build.id"],
            text=True,
            stderr=subprocess.DEVNULL,
            timeout=8,
        ).strip()
        return (device or None, build or None)
    except Exception:  # noqa: BLE001
        return (None, None)


def cmd_auto(cfg: dict[str, Any], *, force_pack: bool = False) -> int:
    """
    尽量自动化：
    1) 0705 catalog 不齐则 pack-0705
    2) adb 识别机型并提示对应 PROJECT
    3) 可选扫描 temp_so_dir 批量 dry 报告
    """
    print("[oneso] auto start")
    complete = catalog_0705_complete(cfg)
    print(f"[oneso] catalog_0705_complete={complete}")
    code = 0
    if force_pack or not complete:
        code = cmd_pack_0705(cfg, None)
        if code != 0:
            return code
        complete = catalog_0705_complete(cfg)
        print(f"[oneso] catalog_0705_complete_after={complete}")
    else:
        print("[oneso] skip pack-0705 (already complete)")

    device, build = adb_device_build()
    if device and build:
        project = f"{device}-{build}"
        print(f"[oneso] adb device={device} build={build}")
        print(f"[oneso] suggest PROJECT={project}")
        if build == BUILD_0705 and device in DEVICES_0705:
            print("[oneso] adb matches 0705 P9 family — App catalog ready")
        elif build == BUILD_0705:
            print("[oneso] WARN build is 0705 but device not in P9 pack list")
    else:
        print("[oneso] adb unavailable — skip device detect")

    temp = Path(str(cfg.get("temp_so_dir") or r"E:\Down\TEMP"))
    if temp.is_dir():
        print(f"[oneso] scan temp dir {temp}")
        cmd_import_batch(
            cfg,
            temp,
            recursive=False,
            dry_run=True,
            mapping_path=None,
        )
    print("[oneso] auto done")
    return 0 if complete else 1


def oneso_assets_root(cfg: dict[str, Any]) -> Path:
    raw = cfg.get("oneso_assets_root") or str(DEFAULT_ONESO_ASSETS)
    return Path(str(raw)).expanduser().resolve()


def catalog_p10_complete(cfg: dict[str, Any], build: str = BUILD_P10) -> bool:
    app = oneims_root(cfg)
    catalog_path = app / "app" / "src" / "main" / "assets" / "temproot" / "catalog.json"
    if not catalog_path.is_file():
        return False
    data = json.loads(catalog_path.read_text(encoding="utf-8"))
    devices = data.get("devices") or {}
    for device in DEVICES_P10:
        name = (devices.get(device) or {}).get(build)
        if not name:
            return False
        so = app / "app" / "src" / "main" / "assets" / "temproot" / name
        if not so.is_file() or so.stat().st_size <= 0:
            return False
    return True


def cmd_pack_p10(
    cfg: dict[str, Any],
    *,
    build: str = BUILD_P10,
    assets_root: Path | None = None,
    devices: tuple[str, ...] = DEVICES_P10,
) -> int:
    """
    把 P10 家族成品 so 从 OneSo-assets 导入 OneIMS catalog。
    不改 label（P10 so 无 P9 那套 *_cp2a_260705_006 槽；且与 P9 不同二进制）。
    """
    root = (assets_root or oneso_assets_root(cfg)).expanduser().resolve()
    so_dir = root / "so" / build
    if not so_dir.is_dir():
        raise SystemExit(f"P10 so dir missing: {so_dir}")
    print(f"[oneso] pack-p10 build={build} from={so_dir}")
    ok = 0
    for device in devices:
        src = so_dir / f"preload-{device}-{build}.so"
        if not src.is_file():
            print(f"[oneso] FAIL missing {src}", file=sys.stderr)
            continue
        project = f"{device}-{build}"
        try:
            result = import_so_core(cfg, project, src)
        except Exception as exc:  # noqa: BLE001
            print(f"[oneso] FAIL {project}: {exc}", file=sys.stderr)
            continue
        print(
            f"[oneso] OK {project} -> {result['asset']} "
            f"({result['sha256'][:12]}…)",
        )
        ok += 1
    print(f"[oneso] pack-p10 done ok={ok}/{len(devices)}", file=sys.stderr)
    return 0 if ok == len(devices) else 1


def _assets_so_name(device: str, build: str) -> str:
    return f"preload-{device}-{build}.so"


def _assets_rel_path(device: str, build: str) -> str:
    return f"so/{build}/{_assets_so_name(device, build)}"


def _rewrite_assets_catalog_and_sums(root: Path) -> tuple[int, int]:
    """Scan so/ and rewrite catalog.json + SHA256SUMS. Returns (files, entries)."""
    so_root = root / "so"
    if not so_root.is_dir():
        raise SystemExit(f"assets so/ missing: {so_root}")

    devices_map: dict[str, dict[str, str]] = {}
    sum_lines: list[str] = []
    file_count = 0
    for so in sorted(so_root.rglob("preload-*.so")):
        if not so.is_file():
            continue
        build = so.parent.name
        # preload-<device>-<build>.so
        prefix = "preload-"
        suffix = f"-{build}.so"
        if not (so.name.startswith(prefix) and so.name.endswith(suffix)):
            print(f"[oneso] WARN skip unexpected name: {so}", file=sys.stderr)
            continue
        device = so.name[len(prefix) : -len(suffix)]
        rel = _assets_rel_path(device, build).replace("\\", "/")
        digest = hashlib.sha256(so.read_bytes()).hexdigest()
        sum_lines.append(f"{digest}  {rel}")
        devices_map.setdefault(device, {})[build] = rel
        file_count += 1

    catalog = {
        "version": 1,
        "note": (
            "P9: tokay/caiman/komodo/comet · P10: blazer/frankel/mustang/rango. "
            "0705 P9 uses per-device labels; 0605 P9 family shares identical blobs."
        ),
        "base_url": "https://raw.githubusercontent.com/asrtroh-netizen/OneSo-assets/main/",
        "devices": devices_map,
    }
    catalog_path = root / "catalog.json"
    catalog_path.write_text(
        json.dumps(catalog, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    (root / "SHA256SUMS").write_text("\n".join(sum_lines) + "\n", encoding="utf-8")
    print(f"[oneso] wrote {catalog_path} devices={len(devices_map)} files={file_count}")
    print(f"[oneso] wrote {root / 'SHA256SUMS'} lines={len(sum_lines)}")
    return file_count, sum(len(v) for v in devices_map.values())


def cmd_complete_assets(
    cfg: dict[str, Any],
    *,
    assets_root: Path | None = None,
    dry_run: bool = False,
) -> int:
    """
    完善 OneSo-assets：补齐同构建下可安全复制的 P9 缺口，刷新 catalog/SHA256SUMS。

    - P9 @ 0705：若有源 so，用 label retarget 补齐缺失机型
    - P9 @ 其它 build：仅当已有机型 blob 两两相同（或只剩一份）时复制补齐
    - P10：只报告缺口，不伪造二进制
    """
    root = (assets_root or oneso_assets_root(cfg)).expanduser().resolve()
    so_root = root / "so"
    if not so_root.is_dir():
        raise SystemExit(f"assets so/ missing: {so_root}")

    print(f"[oneso] complete-assets root={root} dry_run={dry_run}")
    created = 0
    skipped_p10_gap = 0

    builds = sorted(p.name for p in so_root.iterdir() if p.is_dir())
    for build in builds:
        so_dir = so_root / build
        present_p9 = {
            d: so_dir / _assets_so_name(d, build)
            for d in DEVICES_0705
            if (so_dir / _assets_so_name(d, build)).is_file()
        }
        missing_p9 = [d for d in DEVICES_0705 if d not in present_p9]
        present_p10 = {
            d: so_dir / _assets_so_name(d, build)
            for d in DEVICES_P10
            if (so_dir / _assets_so_name(d, build)).is_file()
        }
        missing_p10 = [d for d in DEVICES_P10 if d not in present_p10]

        if missing_p10 and present_p10:
            print(
                f"[oneso] P10 {build}: have={list(present_p10)} "
                f"missing={missing_p10} (no invent)",
                file=sys.stderr,
            )
            skipped_p10_gap += len(missing_p10)
        elif missing_p10 and not present_p10:
            # 纯 P9 build，忽略
            pass
        elif present_p10 and not missing_p10:
            print(f"[oneso] P10 {build}: complete {list(DEVICES_P10)}")

        if not missing_p9:
            if present_p9:
                print(f"[oneso] P9 {build}: complete {list(present_p9)}")
            continue
        if not present_p9:
            # 纯 P10 build 没有 P9 源，不报错；只有混装/未知目录才提示。
            if not present_p10:
                print(
                    f"[oneso] P9 {build}: empty, cannot fill {missing_p9}",
                    file=sys.stderr,
                )
            continue

        src_path = next(iter(present_p9.values()))
        raw = src_path.read_bytes()
        identical = all(p.read_bytes() == raw for p in present_p9.values())
        is_0705 = build == BUILD_0705 or (LABEL_0705_SUFFIX.encode() in raw)

        print(
            f"[oneso] P9 {build}: have={list(present_p9)} missing={missing_p9} "
            f"identical={identical} label0705={is_0705}",
        )

        for device in missing_p9:
            dst = so_dir / _assets_so_name(device, build)
            if is_0705:
                try:
                    out = patch_0705_label(raw, device)
                except Exception as exc:  # noqa: BLE001
                    print(f"[oneso] FAIL patch {device}-{build}: {exc}", file=sys.stderr)
                    continue
                mode = "label-0705"
            elif identical:
                out = raw
                mode = "copy-identical"
            else:
                print(
                    f"[oneso] SKIP {device}-{build}: blobs differ, not safe to copy",
                    file=sys.stderr,
                )
                continue
            print(f"[oneso] {'DRY ' if dry_run else ''}FILL {dst.name} via {mode}")
            if not dry_run:
                dst.write_bytes(out)
            created += 1

    if not dry_run:
        _rewrite_assets_catalog_and_sums(root)
    else:
        print("[oneso] dry-run: catalog/SHA256SUMS not rewritten")

    print(
        f"[oneso] complete-assets done created={created} "
        f"p10_gaps_reported={skipped_p10_gap}",
        file=sys.stderr,
    )
    return 0 if skipped_p10_gap == 0 or created >= 0 else 1


def cmd_pack_0705(
    cfg: dict[str, Any],
    source_so: Path | None,
    *,
    devices: tuple[str, ...] = DEVICES_0705,
) -> int:
    """从已验证 0705 so 改标签，批量入库 OneIMS catalog（tokay/caiman/komodo/comet）。"""
    app = oneims_root(cfg)
    assets = app / "app" / "src" / "main" / "assets" / "temproot"
    default_src = assets / f"preload-comet-{BUILD_0705}.so"
    if source_so is None:
        source_so = default_src
    src = source_so.expanduser().resolve()
    if not src.is_file():
        alt = Path(r"E:\Down\TEMP\preload-comet-cp2a-260705-006.so")
        if alt.is_file():
            src = alt
        else:
            raise SystemExit(f"source so not found: {source_so}")
    raw = src.read_bytes()
    print(f"[oneso] pack-0705 source={src} bytes={len(raw)}")
    ok = 0
    for device in devices:
        project = f"{device}-{BUILD_0705}"
        try:
            out_bytes = patch_0705_label(raw, device)
        except Exception as exc:  # noqa: BLE001
            print(f"[oneso] FAIL {device}: {exc}")
            continue
        tmp = HERE / f"_tmp-{device}-0705.so"
        tmp.write_bytes(out_bytes)
        try:
            result = import_so_core(cfg, project, tmp)
        finally:
            tmp.unlink(missing_ok=True)
        # 确认标签
        label = make_0705_label(device)
        if label.encode() not in Path(result["dst"]).read_bytes():
            print(f"[oneso] WARN label not found after write: {label}")
        print(
            f"[oneso] OK {project} -> {result['asset']} "
            f"({result['sha256'][:12]}…)",
        )
        ok += 1
    print(f"[oneso] pack-0705 done ok={ok}/{len(devices)}", file=sys.stderr)
    return 0 if ok == len(devices) else 1


def cmd_pack_0805(
    cfg: dict[str, Any],
    source_so: Path | None,
    *,
    devices: tuple[str, ...] = DEVICES_0705,
    assets_root: Path | None = None,
    also_oneims: bool = True,
) -> int:
    """
    从已验证 0705 so 改 label → CP2A.260805.006 P9 四机，写入 OneSo-assets
    （可选同步 OneIMS temproot catalog）。
    """
    root = (assets_root or oneso_assets_root(cfg)).expanduser().resolve()
    app = oneims_root(cfg)
    default_src = (
        app
        / "app"
        / "src"
        / "main"
        / "assets"
        / "temproot"
        / f"preload-comet-{BUILD_0705}.so"
    )
    assets_src = root / "so" / BUILD_0705 / f"preload-comet-{BUILD_0705}.so"
    if source_so is None:
        source_so = default_src if default_src.is_file() else assets_src
    src = source_so.expanduser().resolve()
    if not src.is_file():
        raise SystemExit(f"source so not found: {source_so}")
    raw = src.read_bytes()
    print(f"[oneso] pack-0805 source={src} bytes={len(raw)} -> {BUILD_0805}")
    out_dir = root / "so" / BUILD_0805
    out_dir.mkdir(parents=True, exist_ok=True)
    ok = 0
    for device in devices:
        try:
            out_bytes = patch_0805_label(raw, device)
        except Exception as exc:  # noqa: BLE001
            print(f"[oneso] FAIL {device}: {exc}", file=sys.stderr)
            continue
        name = f"preload-{device}-{BUILD_0805}.so"
        dst = out_dir / name
        dst.write_bytes(out_bytes)
        label = make_0805_label(device)
        if label.encode() not in dst.read_bytes():
            print(f"[oneso] WARN label not found after write: {label}")
        print(f"[oneso] OK assets {device}-{BUILD_0805} -> {dst}")
        if also_oneims:
            project = f"{device}-{BUILD_0805}"
            try:
                result = import_so_core(cfg, project, dst)
                print(
                    f"[oneso] OK oneims {project} -> {result['asset']} "
                    f"({result['sha256'][:12]}…)",
                )
            except Exception as exc:  # noqa: BLE001
                print(f"[oneso] WARN oneims import {project}: {exc}", file=sys.stderr)
        ok += 1
    _rewrite_assets_catalog_and_sums(root)
    print(f"[oneso] pack-0805 done ok={ok}/{len(devices)}", file=sys.stderr)
    return 0 if ok == len(devices) else 1


def cmd_info(cfg: dict[str, Any], project: str) -> int:
    root = exploit_root(cfg)
    device, build_id = parse_project(project)
    target = root / "src" / "targets" / project / "target.h"
    so = build_output_so(root, project)
    print(f"project\t{project}")
    print(f"device\t{device}")
    print(f"build\t{build_id}")
    print(f"target.h\t{target} ({'OK' if target.is_file() else 'MISSING'})")
    print(f"preload.so\t{so} ({'OK' if so.is_file() else 'not built'})")
    if so.is_file():
        print(f"sha256\t{hashlib.sha256(so.read_bytes()).hexdigest().upper()}")
    return 0 if target.is_file() else 1


# 与 App TempRootShellCommands 对齐（PC adb 真源；手机端一键入口已撤）。
REMOTE_SO = "/data/local/tmp/preload-comet.so"
# 只杀挂起的 exploit 进程，绝不 rm su/sock —— 成功后的 post-clean 若误删 sock 会弄死刚起来的 daemon。
KILL_STUCK_PRELOAD = (
    "pkill -9 -f 'timeout .*LD_PRELOAD' 2>/dev/null; "
    "pkill -9 -f preload-comet.so 2>/dev/null; "
    "pkill -9 -f 'LD_PRELOAD=/data/local/tmp/preload' 2>/dev/null; "
    "killall -9 id 2>/dev/null; "
    "for p in $(pidof id 2>/dev/null); do "
    "grep -q preload-comet /proc/$p/maps 2>/dev/null && kill -9 $p; "
    "done; "
    "echo KILL_OK"
)
# Host-side defaults tuned vs old 4×180s (felt much slower than on-device Root My Pixel).
DEFAULT_TEMP_ROOT_ATTEMPTS = 2
DEFAULT_TEMP_ROOT_TIMEOUT_SEC = 90
DEFAULT_TEMP_ROOT_RETRY_GAP_SEC = 1.0
VERIFY_SU_TMP = "/data/local/tmp/su -c /system/bin/id"
VERIFY_SU_APEX = "/apex/com.android.virt/bin/su -c /system/bin/id"


def ld_preload_cmd(timeout_sec: int) -> str:
    """Device-side timeout so a hung exploit dies even if adb is sticky."""
    sec = max(15, int(timeout_sec))
    # toybox `timeout` on Pixel; host adb_shell_heartbeat still hard-kills as backup
    return f"timeout {sec}s sh -c 'LD_PRELOAD={REMOTE_SO} /system/bin/id'"


def looks_like_root_success(output: str) -> bool:
    t = output or ""
    return (
        "uid=0(root)" in t
        or "root=1" in t
        or ("uid=0" in t and "gid=0" in t)
    )


def looks_like_stale_su_daemon(output: str) -> bool:
    """su 客户端在、但 daemon/socket 半死（Permission denied / No such file 等）。"""
    t = (output or "").lower()
    if "connect daemon" not in t:
        return False
    return (
        "permission denied" in t
        or "no such file" in t
        or "connection refused" in t
        or "not found" in t
    )


def _root_owned_su_undeletable() -> bool:
    """shell 删不掉的 root 属主 /data/local/tmp/su（僵尸二进制）。"""
    _c, lsout = adb_shell("ls -l /data/local/tmp/su 2>&1", timeout=5.0)
    low = (lsout or "").lower()
    if "no such file" in low or "no such" in low:
        return False
    if "permission denied" in low:
        return True
    # 典型：-rwsr-xr-x 1 root root … /data/local/tmp/su
    if "root" not in low:
        return False
    _c2, rmout = adb_shell(
        "rm -f /data/local/tmp/su 2>/dev/null; "
        "test -e /data/local/tmp/su && echo STILL_THERE || echo GONE",
        timeout=8.0,
    )
    _ = _c, _c2
    return "STILL_THERE" in (rmout or "")


def detect_stale_temp_root() -> str | None:
    """半死不活的临时 Root 残留：shell 连不上 daemon，也删不掉 sock/su。"""
    _code, out = adb_shell(VERIFY_SU_TMP, timeout=5.0)
    if looks_like_root_success(out):
        return None
    if looks_like_stale_su_daemon(out):
        return (
            "检测到残留 temp_su（su: connect daemon 失败："
            f"{(out or '').strip()[:80]}）。"
            "shell 无法删除 /data/local/tmp/su 与 temp_su.sock。"
            "请先在仍有 uid=0 时点「清理残留」，或再跑一键让 exploit 覆盖；"
            "仅重启手机通常清不掉 root 属主的 su。"
        )
    if _root_owned_su_undeletable():
        return (
            "检测到僵尸 /data/local/tmp/su（root 属主、daemon 已死，shell 删不掉）。"
            "清理按钮在无 uid=0 时无法移除它；请再跑一键临时 Root 让 exploit "
            "以 root 覆盖，或在成功窗口内立刻点清理。"
        )
    _c, lsout = adb_shell("ls -l /data/local/tmp/temp_su.sock 2>&1", timeout=5.0)
    low = (lsout or "").lower()
    if "permission denied" in low:
        return (
            "检测到残留 temp_su.sock（shell Permission denied）。"
            "请先点「清理残留」（需已有 uid=0）或再跑一键覆盖。"
        )
    _ = _c
    return None


# 强力拆除：sock + su 二进制必须一起删，禁止「只拆 sock 留二进制」人造僵尸。
TEARDOWN_VIA_SU = (
    "/data/local/tmp/su -c '"
    "pkill -9 -f \"timeout .*LD_PRELOAD\" 2>/dev/null; "
    "pkill -9 -f preload-comet.so 2>/dev/null; "
    "killall -9 id 2>/dev/null; "
    "rm -f /data/local/tmp/temp_su.sock /dev/socket/temp_su.sock; "
    "rm -f /data/local/tmp/su; "
    "echo TEARDOWN_OK"
    "'"
)


def cleanup_temp_root_residuals(*, aggressive: bool = False) -> dict[str, Any]:
    """清理临时 Root 残留。

    - 默认（aggressive=False）：只杀挂起 LD_PRELOAD/id。
      若当前已有可用 uid=0，**绝不拆 sock/su**（旧逻辑只删 sock 会把活 root 变成僵尸）。
    - 强力（aggressive=True）：在仍有 uid=0 时一次性拆除 sock + su 二进制，不留僵尸。
    """
    steps: list[str] = []
    kcode, kout = adb_shell(KILL_STUCK_PRELOAD, timeout=20.0)
    steps.append(f"kill_stuck rc={kcode} {(kout or '')[:80]}")

    root_ok, root_out = probe_su_uid0(timeout=5.0)
    if root_ok and not aggressive:
        return {
            "ok": True,
            "mode": "su-keep",
            "root_before": True,
            "stale_after": None,
            "steps": steps,
            "detail": (
                "已有可用临时 Root：仅清理挂起 exploit，保留 su daemon。"
                "若要完全拆除，请用「强力清理」（aggressive）。"
            ),
        }

    if root_ok and aggressive:
        tcode, tout = adb_shell(TEARDOWN_VIA_SU, timeout=15.0)
        steps.append(f"su_teardown rc={tcode} {(tout or '')[:100]}")
        still_ok, still_out = probe_su_uid0(timeout=4.0)
        stale_after = detect_stale_temp_root()
        return {
            "ok": (not still_ok) and stale_after is None,
            "mode": "su-teardown",
            "root_before": True,
            "stale_after": stale_after,
            "steps": steps + [f"probe_after={(still_out or '')[:80]}"],
            "detail": (
                "已用 uid=0 完整拆除 temp_su（sock + su 二进制）"
                if not still_ok and stale_after is None
                else f"拆除后仍有残留：stale={stale_after} probe={still_out[:80]}"
            ),
        }

    # 无可用 root：shell 尽力（通常删不掉 root 属主 sock/su）
    scode, sout = adb_shell(
        "rm -f /data/local/tmp/temp_su.sock 2>/dev/null; echo SHELL_RM_DONE",
        timeout=8.0,
    )
    steps.append(f"shell_rm rc={scode} {(sout or '')[:60]}")

    stale = detect_stale_temp_root()
    if looks_like_stale_su_daemon(root_out) or stale or _root_owned_su_undeletable():
        return {
            "ok": False,
            "mode": "blocked",
            "root_before": False,
            "stale_after": stale or "stale_su_or_daemon",
            "steps": steps + [f"su_probe={(root_out or '')[:100]}"],
            "detail": (
                "当前无可用 uid=0，shell 删不掉 root 属主残留"
                f"（{(stale or root_out or 'zombie su')[:120]}）。"
                "可：① 再点「一键临时 Root」覆盖；"
                "② 成功后若要拆除请用「强力清理」（会同时删 sock+su）；"
                "③ 仅重启通常清不掉 /data/local/tmp/su。"
            ),
        }

    return {
        "ok": True,
        "mode": "shell",
        "root_before": False,
        "stale_after": None,
        "steps": steps,
        "detail": "无半死 daemon / 僵尸 su；已清理挂起的 LD_PRELOAD/id 进程",
    }


def rebind_shell_shizuku() -> bool:
    """临时 Root 成功后：杀掉可能出现的 root 态 shizuku_server，再用 adb shell 拉起。

    **严禁** ``su -c libshizuku.so``：会生成 root/kernel 态 server，
    App binder 立刻掉线（黑标亮、OneLink 死）。
    """
    print("[oneso] rebind Shizuku as shell (FORBIDDEN: su -c libshizuku)…")
    adb_shell(
        "/data/local/tmp/su -c '/system/bin/killall -9 shizuku_server' 2>/dev/null; "
        "/system/bin/killall -9 shizuku_server 2>/dev/null; true",
        timeout=12.0,
    )
    time.sleep(0.2)
    _code, path_out = adb_shell("pm path moe.shizuku.privileged.api", timeout=8.0)
    apk = ""
    for line in (path_out or "").splitlines():
        line = line.strip()
        if line.startswith("package:"):
            apk = line.split("package:", 1)[1].strip()
            break
    if not apk:
        print("[oneso] WARN: Shizuku not installed; skip rebind")
        return False
    # …/base.apk → …/lib/arm64/libshizuku.so
    lib = (
        apk[: -len("base.apk")] + "lib/arm64/libshizuku.so"
        if apk.endswith("base.apk")
        else apk.rstrip("/") + "/lib/arm64/libshizuku.so"
    )
    # adb shell → uid shell；绝不用 su 包一层
    scode, sout = adb_shell(f"{lib} --apk={apk}", timeout=25.0)
    print(f"[oneso] shell-start shizuku rc={scode} out={sout[:220]}")
    _pcode, pout = adb_shell("ps -A | grep shizuku_server || true", timeout=8.0)
    line = (pout or "").strip().replace("\r", "")
    print(f"[oneso] ps shizuku_server: {line[:180]}")
    user = line.split()[0] if line.split() else ""
    if "shizuku_server" in line and user == "root":
        print(
            "[oneso] WARN: shizuku_server still root — "
            "App will try wireless rebind; do NOT su-start again",
        )
        return False
    if "shizuku_server" in line and user == "shell":
        return True
    return "shizuku_starter" in sout or "starter exit with 0" in sout or scode == 0


def adb_shell(command: str, *, timeout: float) -> tuple[int, str]:
    try:
        proc = subprocess.run(
            ["adb", "shell", command],
            capture_output=True,
            text=True,
            timeout=timeout,
            encoding="utf-8",
            errors="replace",
        )
        out = ((proc.stdout or "") + (proc.stderr or "")).strip()
        return proc.returncode, out
    except subprocess.TimeoutExpired as exc:
        out = ((exc.stdout or b"") + (exc.stderr or b""))
        if isinstance(out, bytes):
            out = out.decode("utf-8", errors="replace")
        return 124, (out or "").strip() + "\n[timeout]"


def probe_su_uid0(*, timeout: float = 4.0) -> tuple[bool, str]:
    """快速验临时 Root（不经 LD_PRELOAD 输出）。

    失败时返回最后一次 su 输出（便于识别 connect daemon / 僵尸 su）。
    """
    last = ""
    for su_cmd in (VERIFY_SU_TMP, VERIFY_SU_APEX):
        _code, out = adb_shell(su_cmd, timeout=timeout)
        last = out or last
        if looks_like_root_success(out):
            return True, out
    return False, last


def adb_shell_heartbeat(
    command: str,
    *,
    timeout: float,
    label: str = "adb",
    beat_sec: float = 5.0,
    probe_su: bool = False,
    probe_su_every_sec: float = 2.0,
) -> tuple[int, str]:
    """长 adb shell：等待期间排空管道+心跳；可选并行验 su 以便早停。

    必须边跑边读 stdout/stderr：exploit 日志一多，PIPE 塞满会把子进程
    卡在 write 上，表现为「只打出 preload starting 然后干等到超时」。
    Windows 上用线程排空（select 不能用于 pipe）。
    """
    import threading
    from queue import Empty, Queue

    proc = subprocess.Popen(
        ["adb", "shell", command],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
        errors="replace",
        bufsize=1,
    )
    started = time.time()
    last_beat = started
    last_su = started
    chunks: list[str] = []
    q: Queue[str | None] = Queue()

    def _reader(stream) -> None:  # type: ignore[no-untyped-def]
        try:
            for line in iter(stream.readline, ""):
                q.put(line)
        finally:
            try:
                stream.close()
            except Exception:  # noqa: BLE001
                pass
            q.put(None)

    assert proc.stdout is not None and proc.stderr is not None
    threading.Thread(target=_reader, args=(proc.stdout,), daemon=True).start()
    threading.Thread(target=_reader, args=(proc.stderr,), daemon=True).start()
    closed = 0

    def _drain(wait: float = 0.2) -> None:
        nonlocal closed
        end = time.time() + wait
        while time.time() < end:
            try:
                item = q.get(timeout=max(0.01, end - time.time()))
            except Empty:
                break
            if item is None:
                closed += 1
                continue
            chunks.append(item)
            print(item.rstrip("\n"), flush=True)

    try:
        while proc.poll() is None:
            _drain(0.25)
            now = time.time()
            elapsed = now - started
            if elapsed >= timeout:
                proc.kill()
                _drain(1.0)
                try:
                    proc.wait(timeout=3)
                except Exception:  # noqa: BLE001
                    pass
                text = "".join(chunks).strip()
                print(
                    f"[oneso] {label} TIMEOUT after {int(elapsed)}s",
                    file=sys.stderr,
                )
                return 124, (text + "\n[timeout]").strip()
            if probe_su and (now - last_su) >= probe_su_every_sec:
                last_su = now
                ok, sout = probe_su_uid0(timeout=3.5)
                if ok:
                    # daemon 刚起来时常挂在 exploit 进程树下：先等它脱离，再杀 LD_PRELOAD。
                    print(
                        f"[oneso] {label} early-stop: su uid=0 at {int(elapsed)}s "
                        f"— wait 4s for daemon detach",
                        flush=True,
                    )
                    time.sleep(4.0)
                    ok2, sout2 = probe_su_uid0(timeout=3.5)
                    if not ok2:
                        print(
                            f"[oneso] {label} early-stop aborted: su died during "
                            f"detach wait ({(sout2 or '')[:80]})",
                            flush=True,
                        )
                        continue
                    print(
                        f"[oneso] {label} early-stop: daemon stable, "
                        f"stop hung LD_PRELOAD",
                        flush=True,
                    )
                    try:
                        proc.kill()
                    except Exception:  # noqa: BLE001
                        pass
                    _drain(1.0)
                    try:
                        proc.wait(timeout=3)
                    except Exception:  # noqa: BLE001
                        pass
                    # 杀完再验一次：避免把「杀穿 daemon」误判成成功
                    ok3, sout3 = probe_su_uid0(timeout=3.5)
                    if ok3:
                        return 0, sout3
                    print(
                        f"[oneso] {label} WARN: su lost after killing LD_PRELOAD "
                        f"({(sout3 or '')[:80]}) — treat as not-yet",
                        flush=True,
                    )
                    # 不要把旧的 uid=0 文案带回，避免上层误判 SUCCESS
                    return 1, f"[early-stop-su-lost] {(sout3 or '')[:120]}"
            if now - last_beat >= beat_sec:
                print(
                    f"[oneso] …仍在执行 {label} "
                    f"已等待 {int(elapsed)}s / {int(timeout)}s",
                    flush=True,
                )
                last_beat = now
        _drain(1.0)
        try:
            proc.wait(timeout=3)
        except Exception:  # noqa: BLE001
            pass
        text = "".join(chunks).strip()
        return int(proc.returncode or 0), text
    finally:
        if proc.poll() is None:
            try:
                proc.kill()
            except Exception:  # noqa: BLE001
                pass


ONESO_ASSETS_RAW = (
    "https://raw.githubusercontent.com/asrtroh-netizen/OneSo-assets/main/"
)
ONESO_ASSETS_CATALOG = ONESO_ASSETS_RAW + "catalog.json"
ONESO_ASSETS_HOST = "raw.githubusercontent.com"
ONESO_ASSETS_PATH_PREFIX = "/asrtroh-netizen/OneSo-assets/"
_CATALOG_CACHE: dict[str, Any] = {"ts": 0.0, "data": None}
_CATALOG_TTL_SEC = 300.0


def _http_get_bytes(url: str, *, timeout: float = 60) -> bytes:
    """仅允许 OneSo-assets raw.githubusercontent.com。"""
    from urllib.parse import urlparse

    parsed = urlparse(url)
    if parsed.scheme != "https" or parsed.netloc != ONESO_ASSETS_HOST:
        raise ValueError(f"blocked host: {parsed.netloc}")
    if not parsed.path.startswith(ONESO_ASSETS_PATH_PREFIX):
        raise ValueError(f"blocked path: {parsed.path}")
    req = urllib.request.Request(
        url,
        headers={"User-Agent": "OneRoot/1.0 (OneIMS temp-root)"},
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:  # noqa: S310
        return resp.read()


def _load_github_catalog() -> dict[str, Any] | None:
    now = time.time()
    cached = _CATALOG_CACHE.get("data")
    if cached is not None and (now - float(_CATALOG_CACHE.get("ts") or 0)) < _CATALOG_TTL_SEC:
        return cached  # type: ignore[return-value]
    try:
        catalog = json.loads(
            _http_get_bytes(ONESO_ASSETS_CATALOG, timeout=30).decode("utf-8"),
        )
    except (urllib.error.URLError, TimeoutError, ValueError, json.JSONDecodeError) as exc:
        print(f"[oneso] GitHub catalog FAIL: {exc}", file=sys.stderr)
        return None
    _CATALOG_CACHE["ts"] = now
    _CATALOG_CACHE["data"] = catalog
    return catalog


def fetch_so_from_github(
    device: str,
    build: str,
    *,
    cache_dir: Path,
) -> Path | None:
    """从 GitHub OneSo-assets 拉 catalog + 匹配 so，缓存到 cache_dir。"""
    catalog = _load_github_catalog()
    if catalog is None:
        return None
    rel = (catalog.get("devices") or {}).get(device, {}).get(build)
    if not rel:
        print(
            f"[oneso] GitHub catalog: no entry for {device}/{build}",
            file=sys.stderr,
        )
        return None
    base = str(catalog.get("base_url") or ONESO_ASSETS_RAW).rstrip("/") + "/"
    if not rel.startswith("http"):
        url = base + rel.lstrip("/")
    else:
        url = rel
    cache_dir.mkdir(parents=True, exist_ok=True)
    name = Path(rel).name
    if not name.endswith(".so"):
        name = f"preload-{device}-{build}.so"
    dst = cache_dir / name
    if dst.is_file() and dst.stat().st_size >= 64 and dst.read_bytes()[:4] == b"\x7fELF":
        print(f"[oneso] GitHub so cache hit -> {dst}")
        return dst
    try:
        data = _http_get_bytes(url, timeout=120)
    except (urllib.error.URLError, TimeoutError, ValueError) as exc:
        print(f"[oneso] GitHub so FAIL: {exc}", file=sys.stderr)
        return None
    if len(data) < 64 or data[:4] != b"\x7fELF":
        print("[oneso] GitHub so FAIL: not ELF", file=sys.stderr)
        return None
    dst.write_bytes(data)
    print(f"[oneso] GitHub so -> {dst} ({len(data)} bytes)")
    return dst


def _so_from_oneso_assets_root(
    cfg: dict[str, Any],
    *,
    device: str,
    build: str,
) -> Path | None:
    """本机 clone 的 OneSo-assets（config.oneso_assets_root）——比 GitHub 快一个数量级。"""
    raw = str(cfg.get("oneso_assets_root") or "").strip()
    if not raw:
        return None
    root = Path(raw).expanduser()
    if not root.is_dir():
        return None
    catalog_path = root / "catalog.json"
    if catalog_path.is_file():
        try:
            catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
            rel = (catalog.get("devices") or {}).get(device, {}).get(build)
            if rel:
                cand = root / str(rel).lstrip("/").replace("/", os.sep)
                if cand.is_file():
                    print(f"[oneso] local OneSo-assets so -> {cand}")
                    return cand
        except Exception:  # noqa: BLE001
            pass
    direct = root / "so" / build / f"preload-{device}-{build}.so"
    if direct.is_file():
        print(f"[oneso] local OneSo-assets so -> {direct}")
        return direct
    return None


def classify_so_source(cfg: dict[str, Any], so: Path | None) -> str:
    """给 Hub 状态条用的 so 来源短标签。"""
    if so is None or not so.is_file():
        return "missing"
    try:
        resolved = so.resolve()
    except Exception:  # noqa: BLE001
        resolved = so
    raw = str(cfg.get("oneso_assets_root") or "").strip()
    if raw:
        try:
            root = Path(raw).expanduser().resolve()
            if root in resolved.parents or resolved.parent == root:
                return "local-assets"
        except Exception:  # noqa: BLE001
            pass
    parts = {p.lower() for p in resolved.parts}
    if ".cache" in parts:
        return "cache"
    if "temproot" in parts:
        return "app-assets"
    return "file"


def resolve_temp_root_so(
    cfg: dict[str, Any],
    *,
    so_override: Path | None,
    device: str | None,
    build: str | None,
    prefer_github: bool = True,
) -> Path | None:
    """
    so 解析（快路径优先）：
    --so > 本机 OneSo-assets > .cache > GitHub > App assets。
    """
    if so_override is not None:
        p = so_override.expanduser().resolve()
        return p if p.is_file() else None

    cache = HERE / ".cache" / "so"
    if device and build:
        local_assets = _so_from_oneso_assets_root(cfg, device=device, build=build)
        if local_assets is not None:
            return local_assets
        cached = cache / f"preload-{device}-{build}.so"
        if cached.is_file() and cached.stat().st_size >= 64:
            print(f"[oneso] using cached so -> {cached}")
            return cached
        for hit in sorted(cache.glob(f"preload-{device}-{build}*.so")):
            if hit.is_file() and hit.stat().st_size >= 64:
                print(f"[oneso] using cached so -> {hit}")
                return hit

    if prefer_github and device and build:
        gh = fetch_so_from_github(device, build, cache_dir=cache)
        if gh is not None:
            return gh

    app = oneims_root(cfg)
    assets = app / "app" / "src" / "main" / "assets" / "temproot"
    catalog_path = assets / "catalog.json"
    if device and build and catalog_path.is_file():
        try:
            catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
            name = (catalog.get("devices") or {}).get(device, {}).get(build)
            if name:
                cand = assets / name
                if cand.is_file():
                    return cand
        except Exception:  # noqa: BLE001
            pass
    if device and build:
        named = assets / f"preload-{device}-{build}.so"
        if named.is_file():
            return named
    legacy = assets / "preload-comet.so"
    if legacy.is_file():
        return legacy
    return None


def cmd_temp_root(
    cfg: dict[str, Any],
    *,
    run: bool,
    so_override: Path | None,
    attempts: int,
    timeout_sec: int,
    retry_gap_sec: float,
) -> int:
    """
    PC 侧按需临时 Root（替代手机首页一键入口）。
    默认只探测/打印计划；加 --run 才 push + LD_PRELOAD + 验 su。
    """

    def _progress(pct: int, stage: str) -> None:
        pct = max(0, min(100, int(pct)))
        print(f"[progress] {pct}% · {stage}", flush=True)

    print("[oneso] OneRoot · one-tap temp root only (so ← GitHub)", flush=True)
    _progress(3, "探测 adb / Pixel")
    device, build = adb_device_build()
    print(f"[oneso] adb device={device or '?'} build={build or '?'}", flush=True)
    _progress(8, "解析 preload.so（本地/缓存优先，必要时才拉 GitHub）")
    so = resolve_temp_root_so(
        cfg,
        so_override=so_override,
        device=device,
        build=build,
    )
    _progress(12, "已拿到 so，准备计划" if so else "未匹配到 so")
    if so is None:
        print(
            "[oneso] FAIL: no matching so from local OneSo-assets / cache / "
            "GitHub / --so",
            file=sys.stderr,
        )
        _progress(100, "失败：无匹配 so")
        return 2
    sha = hashlib.sha256(so.read_bytes()).hexdigest()
    print(f"[oneso] so={so} sha256={sha[:16]}…")
    print(f"[oneso] remote={REMOTE_SO}")
    print(
        f"[oneso] plan: push → kill stuck → "
        f"LD_PRELOAD×{attempts} (timeout={timeout_sec}s, fast defaults) → su verify",
    )
    if not run:
        stale = detect_stale_temp_root()
        if stale:
            print(f"[oneso] WARN: {stale}", file=sys.stderr)
        _progress(100, "预览完成（未执行）")
        print("[oneso] dry-run only. Re-run with --run / OneRoot 一键临时 Root。")
        print("[oneso] tip: .\\OneRoot\\OneRoot.ps1  (or OneRoot\\一键启动.cmd)")
        return 0

    _progress(13, "开始前清理残留")
    cleaned = cleanup_temp_root_residuals(aggressive=False)
    print(
        f"[oneso] pre-clean mode={cleaned.get('mode')} ok={cleaned.get('ok')} "
        f"detail={cleaned.get('detail')}",
        flush=True,
    )
    for line in cleaned.get("steps") or []:
        print(f"[oneso]   · {line}", flush=True)
    if not cleaned.get("ok"):
        print(f"[oneso] WARN: {cleaned.get('detail')}", file=sys.stderr)
        print(
            "[oneso] 仍继续 exploit（成功后会再做一次清理）",
            flush=True,
        )

    _progress(15, "推送 preload.so 到设备")
    print(f"[oneso] adb push {so} {REMOTE_SO}")
    push = subprocess.run(
        ["adb", "push", str(so), REMOTE_SO],
        capture_output=True,
        text=True,
        timeout=120,
        encoding="utf-8",
        errors="replace",
    )
    if push.returncode != 0:
        print(
            f"[oneso] FAIL push: {(push.stdout or '') + (push.stderr or '')}",
            file=sys.stderr,
        )
        _progress(100, "失败：adb push")
        return 3
    chmod_code, chmod_out = adb_shell(f"chmod 644 {REMOTE_SO}", timeout=15)
    print(f"[oneso] chmod rc={chmod_code} {chmod_out[:120]}")

    last_out = ""
    verified = False
    total = max(1, attempts)
    for attempt in range(1, total + 1):
        base = 25 + int(60 * (attempt - 1) / total)
        _progress(base, f"清理卡住进程 · 第 {attempt}/{total} 轮")
        kcode, kout = adb_shell(KILL_STUCK_PRELOAD, timeout=20)
        print(f"[oneso] kill rc={kcode} {kout[:80]}")
        _progress(
            base + 5,
            f"LD_PRELOAD exploit · 第 {attempt}/{total} 轮（最长 {timeout_sec}s）",
        )
        print(
            f"[oneso] exploit attempt={attempt}/{attempts} "
            f"timeout={timeout_sec}s …",
        )
        code, out = adb_shell_heartbeat(
            ld_preload_cmd(timeout_sec),
            timeout=float(timeout_sec) + 5.0,
            label=f"LD_PRELOAD#{attempt}",
            beat_sec=3.0,
            probe_su=True,
            probe_su_every_sec=2.0,
        )
        last_out = out
        print(f"[oneso] ld_preload rc={code} out={out[:240]}")
        if "[early-stop-su-lost]" in (out or ""):
            print("[oneso] early-stop 曾见 uid=0 但 daemon 未稳住，继续校验/重试")
        elif looks_like_root_success(out):
            # 心跳早停成功时再现场复验，防止「日志里有 uid=0、daemon 已死」
            ok_live, live_out = probe_su_uid0(timeout=5.0)
            if ok_live:
                verified = True
                last_out = live_out
                break
            print(
                f"[oneso] WARN: 输出像 root 但现场 su 失败："
                f"{(live_out or '')[:100]}",
                flush=True,
            )
        _progress(base + 12, f"校验 su · 第 {attempt}/{total} 轮")
        ok, sout = probe_su_uid0(timeout=8.0)
        if ok:
            print(f"[oneso] verify su ok: {sout[:120]}")
            verified = True
            last_out = sout
            break
        print("[oneso] verify su: not uid=0 yet")
        if attempt < attempts:
            print(f"[oneso] 本轮未拿到 uid=0，{retry_gap_sec}s 后重试…")
            time.sleep(max(0.0, retry_gap_sec))

    _progress(92, "读取 SELinux / 汇总")
    gcode, gout = adb_shell("getenforce", timeout=8)
    print(f"[oneso] getenforce rc={gcode} {gout}")
    if verified:
        # 成功后只杀挂起 exploit，绝不走会拆 sock/su 的 cleanup（会弄死刚起来的 daemon）
        _progress(94, "成功后清理挂起 exploit（保留 su daemon）")
        kcode, kout = adb_shell(KILL_STUCK_PRELOAD, timeout=20)
        print(f"[oneso] post-kill-stuck rc={kcode} {kout[:80]}", flush=True)
        still_ok, still_out = probe_su_uid0(timeout=5.0)
        if not still_ok:
            print(
                f"[oneso] WARN: uid=0 在收尾后丢失：{(still_out or '')[:120]}",
                file=sys.stderr,
            )
            _progress(100, "失败：拿到后又丢了 uid=0（daemon 可能被误杀）")
            return 1
        last_out = still_out or last_out
        _progress(96, "重绑 Shizuku（shell，禁用 su 拉起）")
        rebind_ok = rebind_shell_shizuku()
        print(f"[oneso] shizuku shell rebind ok={rebind_ok}")
        _progress(100, "成功：已拿到临时 Root")
        print(f"[oneso] SUCCESS root ok: {last_out[:160]}")
        return 0
    _progress(100, f"失败：{attempts} 轮后仍无 uid=0")
    print(
        f"[oneso] FAIL: no uid=0 after {attempts} attempt(s). "
        f"last={last_out[:200]}",
        file=sys.stderr,
    )
    return 1


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        prog="oneso",
        description="OneSo: build IonStack preload.so and install into OneIMS catalog",
    )
    p.add_argument(
        "--config",
        type=Path,
        default=None,
        help="config.json (default: tools/oneso/config.json)",
    )
    sub = p.add_subparsers(dest="cmd", required=True)

    sub.add_parser("list", help="list PROJECT folders with target.h")

    b = sub.add_parser("build", help="make preload for PROJECT")
    b.add_argument("project", help="e.g. comet-CP2A.260705.006")

    i = sub.add_parser("install", help="copy so into OneIMS assets + update catalog")
    i.add_argument("project")
    i.add_argument(
        "--build",
        action="store_true",
        help="build before install",
    )

    n = sub.add_parser("info", help="show paths for one PROJECT")
    n.add_argument("project")

    imp = sub.add_parser(
        "import-so",
        help="import a prebuilt so into OneIMS catalog (skip make)",
    )
    imp.add_argument("project", help="e.g. comet-CP2A.260705.006")
    imp.add_argument("so_path", type=Path, help="path to preload.so")

    batch = sub.add_parser(
        "import-batch",
        help="batch-import *.so from a folder into OneIMS catalog",
    )
    batch.add_argument("directory", type=Path, help="folder containing preload*.so")
    batch.add_argument(
        "--recursive",
        action="store_true",
        help="scan subfolders",
    )
    batch.add_argument(
        "--dry-run",
        action="store_true",
        help="only print guessed PROJECT mapping",
    )
    batch.add_argument(
        "--map",
        type=Path,
        default=None,
        dest="mapping",
        help="optional JSON: {\"preload.so\": \"komodo-...\"}",
    )

    pack = sub.add_parser(
        "pack-0705",
        help="retarget verified 0705 so labels for tokay/caiman/komodo/comet and import",
    )
    pack.add_argument(
        "--source",
        type=Path,
        default=None,
        help="source preload.so (default: OneIMS comet 0705 asset)",
    )

    pack08 = sub.add_parser(
        "pack-0805",
        help="clone 0705 so → CP2A.260805.005 labels into OneSo-assets (P9 family)",
    )
    pack08.add_argument(
        "--source",
        type=Path,
        default=None,
        help="source 0705 preload.so (default: OneIMS/assets comet 0705)",
    )
    pack08.add_argument(
        "--assets-root",
        type=Path,
        default=None,
        help="OneSo-assets root",
    )
    pack08.add_argument(
        "--no-oneims",
        action="store_true",
        help="only write OneSo-assets, skip OneIMS temproot import",
    )

    pack10 = sub.add_parser(
        "pack-p10",
        help="import P10 family (blazer/frankel/mustang/rango) from OneSo-assets",
    )
    pack10.add_argument(
        "--build",
        default=BUILD_P10,
        help=f"build id (default {BUILD_P10})",
    )
    pack10.add_argument(
        "--assets-root",
        type=Path,
        default=None,
        help="OneSo-assets root (default config oneso_assets_root or E:/GQ/One/OneSo-assets)",
    )

    complete = sub.add_parser(
        "complete-assets",
        help="fill safe P9 gaps in OneSo-assets and rewrite catalog/SHA256SUMS",
    )
    complete.add_argument(
        "--assets-root",
        type=Path,
        default=None,
        help="OneSo-assets root (default config oneso_assets_root)",
    )
    complete.add_argument(
        "--dry-run",
        action="store_true",
        help="report fills only, do not write files",
    )

    auto = sub.add_parser(
        "auto",
        help="automate: ensure 0705 pack + adb detect + temp dry-run",
    )
    auto.add_argument(
        "--force-pack",
        action="store_true",
        help="always re-run pack-0705 even if catalog complete",
    )

    tr = sub.add_parser(
        "temp-root",
        help="PC on-demand temp root via adb (replaces phone one-tap UI)",
    )
    tr.add_argument(
        "--run",
        action="store_true",
        help="actually push/exploit/verify (default: dry-run plan only)",
    )
    tr.add_argument(
        "--so",
        type=Path,
        default=None,
        help="override local preload.so path",
    )
    tr.add_argument(
        "--attempts",
        type=int,
        default=DEFAULT_TEMP_ROOT_ATTEMPTS,
        help=f"LD_PRELOAD retries after kill stuck (default {DEFAULT_TEMP_ROOT_ATTEMPTS})",
    )
    tr.add_argument(
        "--timeout-sec",
        type=int,
        default=DEFAULT_TEMP_ROOT_TIMEOUT_SEC,
        help=(
            "per-attempt LD_PRELOAD timeout seconds "
            f"(default {DEFAULT_TEMP_ROOT_TIMEOUT_SEC})"
        ),
    )
    tr.add_argument(
        "--retry-gap-sec",
        type=float,
        default=DEFAULT_TEMP_ROOT_RETRY_GAP_SEC,
        help=(
            "sleep between failed attempts "
            f"(default {DEFAULT_TEMP_ROOT_RETRY_GAP_SEC})"
        ),
    )

    cl = sub.add_parser(
        "cleanup",
        help="清理临时 Root 残留（挂起进程 / temp_su.sock；有 uid=0 时用 su）",
    )
    cl.add_argument(
        "--aggressive",
        action="store_true",
        help="有 root 时连旧 /data/local/tmp/su 二进制一并删除",
    )

    sub.add_parser("gui", help="open OneAE-styled Tk GUI (legacy)")
    sub.add_parser(
        "hub",
        help="open OneAE-replica HTML splash Hub (pywebview)",
    )

    return p


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if args.cmd == "gui":
        if str(HERE) not in sys.path:
            sys.path.insert(0, str(HERE))
        from gui import run_gui

        run_gui(args.config)
        return 0
    if args.cmd == "hub":
        if str(HERE) not in sys.path:
            sys.path.insert(0, str(HERE))
        from hub import run_hub

        return run_hub(args.config)
    cfg = load_config(args.config)
    if args.cmd == "list":
        return cmd_list(cfg)
    if args.cmd == "build":
        return cmd_build(cfg, args.project)
    if args.cmd == "install":
        return cmd_install(cfg, args.project, build_first=bool(args.build))
    if args.cmd == "info":
        return cmd_info(cfg, args.project)
    if args.cmd == "import-so":
        return cmd_import_so(cfg, args.project, args.so_path)
    if args.cmd == "import-batch":
        return cmd_import_batch(
            cfg,
            args.directory,
            recursive=bool(args.recursive),
            dry_run=bool(args.dry_run),
            mapping_path=args.mapping,
        )
    if args.cmd == "pack-0705":
        return cmd_pack_0705(cfg, args.source)
    if args.cmd == "pack-0805":
        return cmd_pack_0805(
            cfg,
            args.source,
            assets_root=args.assets_root,
            also_oneims=not bool(args.no_oneims),
        )
    if args.cmd == "pack-p10":
        return cmd_pack_p10(
            cfg,
            build=str(args.build),
            assets_root=args.assets_root,
        )
    if args.cmd == "complete-assets":
        return cmd_complete_assets(
            cfg,
            assets_root=args.assets_root,
            dry_run=bool(args.dry_run),
        )
    if args.cmd == "auto":
        return cmd_auto(cfg, force_pack=bool(args.force_pack))
    if args.cmd == "cleanup":
        result = cleanup_temp_root_residuals(aggressive=bool(args.aggressive))
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0 if result.get("ok") else 1
    if args.cmd == "temp-root":
        return cmd_temp_root(
            cfg,
            run=bool(args.run),
            so_override=args.so,
            attempts=int(args.attempts),
            timeout_sec=int(args.timeout_sec),
            retry_gap_sec=float(args.retry_gap_sec),
        )
    raise SystemExit(f"unknown cmd {args.cmd}")


if __name__ == "__main__":
    raise SystemExit(main())
