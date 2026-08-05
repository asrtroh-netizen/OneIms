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
from pathlib import Path
from typing import Any

HERE = Path(__file__).resolve().parent
DEFAULT_CONFIG = HERE / "config.json"
EXAMPLE_CONFIG = HERE / "config.example.json"
PROJECT_RE = re.compile(r"^([A-Za-z0-9_]+)-(.+)$")


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

    sub.add_parser("gui", help="open simple Tk GUI")

    return p


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if args.cmd == "gui":
        if str(HERE) not in sys.path:
            sys.path.insert(0, str(HERE))
        from gui import run_gui

        run_gui(args.config)
        return 0
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
    raise SystemExit(f"unknown cmd {args.cmd}")


if __name__ == "__main__":
    raise SystemExit(main())
