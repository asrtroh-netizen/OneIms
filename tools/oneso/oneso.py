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
    m = re.search(rb"([a-z0-9]+_cp2a_260705_006)(\x00+)", so_bytes)
    if not m:
        raise ValueError("source so missing *_cp2a_260705_006 label")
    slot_start = m.start(1)
    slot_end = m.end(2)  # 含全部尾随 \\0
    slot = slot_end - slot_start
    new_label = make_0705_label(device).encode("ascii")
    if len(new_label) + 1 > slot:
        raise ValueError(
            f"label {new_label.decode()} does not fit slot={slot}",
        )
    patched = new_label + b"\x00" * (slot - len(new_label))
    return so_bytes[:slot_start] + patched + so_bytes[slot_end:]


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
KILL_STUCK_PRELOAD = (
    "pkill -9 -f preload-comet.so 2>/dev/null; "
    "pkill -9 -f 'LD_PRELOAD=/data/local/tmp/preload' 2>/dev/null; "
    "for p in $(pidof id 2>/dev/null); do "
    "grep -q preload-comet /proc/$p/maps 2>/dev/null && kill -9 $p; "
    "done; "
    "echo KILL_OK"
)
LD_PRELOAD_CMD = f"LD_PRELOAD={REMOTE_SO} /system/bin/id"
VERIFY_SU_TMP = "/data/local/tmp/su -c /system/bin/id"
VERIFY_SU_APEX = "/apex/com.android.virt/bin/su -c /system/bin/id"


def looks_like_root_success(output: str) -> bool:
    t = output or ""
    return (
        "uid=0(root)" in t
        or "root=1" in t
        or ("uid=0" in t and "gid=0" in t)
    )


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


def resolve_temp_root_so(
    cfg: dict[str, Any],
    *,
    so_override: Path | None,
    device: str | None,
    build: str | None,
) -> Path | None:
    if so_override is not None:
        p = so_override.expanduser().resolve()
        return p if p.is_file() else None
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
    device, build = adb_device_build()
    so = resolve_temp_root_so(
        cfg,
        so_override=so_override,
        device=device,
        build=build,
    )
    print("[oneso] temp-root (PC; phone one-tap UI retired)")
    print(f"[oneso] adb device={device or '?'} build={build or '?'}")
    if so is None:
        print(
            "[oneso] FAIL: no matching so "
            "(pass --so PATH, or ensure assets/temproot + catalog)",
            file=sys.stderr,
        )
        return 2
    sha = hashlib.sha256(so.read_bytes()).hexdigest()
    print(f"[oneso] so={so} sha256={sha[:16]}…")
    print(f"[oneso] remote={REMOTE_SO}")
    print(
        f"[oneso] plan: push → kill stuck → "
        f"LD_PRELOAD×{attempts} (timeout={timeout_sec}s) → su verify",
    )
    if not run:
        print("[oneso] dry-run only. Re-run with --run to execute on device.")
        print("[oneso] tip: .\\scripts\\temp-root-pc.ps1 -Run")
        return 0

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
        return 3
    chmod_code, chmod_out = adb_shell(f"chmod 644 {REMOTE_SO}", timeout=15)
    print(f"[oneso] chmod rc={chmod_code} {chmod_out[:120]}")

    last_out = ""
    verified = False
    for attempt in range(1, max(1, attempts) + 1):
        kcode, kout = adb_shell(KILL_STUCK_PRELOAD, timeout=20)
        print(f"[oneso] kill rc={kcode} {kout[:80]}")
        print(
            f"[oneso] exploit attempt={attempt}/{attempts} "
            f"timeout={timeout_sec}s …",
        )
        code, out = adb_shell(LD_PRELOAD_CMD, timeout=float(timeout_sec))
        last_out = out
        print(f"[oneso] ld_preload rc={code} out={out[:240]}")
        if looks_like_root_success(out):
            verified = True
            break
        # 立即补验绝对路径 su（与 App verifyRootHonest 对齐意图）
        for su_cmd in (VERIFY_SU_TMP, VERIFY_SU_APEX):
            scode, sout = adb_shell(su_cmd, timeout=12)
            print(f"[oneso] verify {su_cmd.split()[0]} rc={scode} {sout[:120]}")
            if looks_like_root_success(sout):
                verified = True
                last_out = sout
                break
        if verified:
            break
        if attempt < attempts:
            time.sleep(max(0.0, retry_gap_sec))

    gcode, gout = adb_shell("getenforce", timeout=8)
    print(f"[oneso] getenforce rc={gcode} {gout}")
    if verified:
        print(f"[oneso] SUCCESS root ok: {last_out[:160]}")
        return 0
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
        default=4,
        help="LD_PRELOAD retries after kill stuck (default 4)",
    )
    tr.add_argument(
        "--timeout-sec",
        type=int,
        default=180,
        help="per-attempt LD_PRELOAD timeout seconds (default 180)",
    )
    tr.add_argument(
        "--retry-gap-sec",
        type=float,
        default=3.0,
        help="sleep between failed attempts (default 3)",
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
    if args.cmd == "auto":
        return cmd_auto(cfg, force_pack=bool(args.force_pack))
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
