#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""OneSo Hub — 单窗 · 仅 PC 一键临时 Root（so 工厂在 GitHub）。"""

from __future__ import annotations

import atexit
import io
import sys
import tempfile
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from typing import Any

import oneso

HERE = Path(__file__).resolve().parent
WEB = HERE / "web"
LOCK_NAME = "oneso-hub-single.lock"


def _lock_path() -> Path:
    return Path(tempfile.gettempdir()) / LOCK_NAME


def acquire_single_instance() -> bool:
    """Windows 简易单实例：锁文件写 pid；已有存活进程则拒绝。"""
    import os

    path = _lock_path()
    if path.is_file():
        try:
            old = int(path.read_text(encoding="utf-8").strip() or "0")
        except ValueError:
            old = 0
        if old and old != os.getpid():
            try:
                import ctypes

                kernel32 = ctypes.windll.kernel32  # type: ignore[attr-defined]
                SYNCHRONIZE = 0x00100000
                handle = kernel32.OpenProcess(SYNCHRONIZE, False, old)
                if handle:
                    kernel32.CloseHandle(handle)
                    print(
                        f"[hub] already running pid={old} — keep one window only",
                        file=sys.stderr,
                    )
                    return False
            except Exception:  # noqa: BLE001
                pass
    path.write_text(str(os.getpid()), encoding="utf-8")

    def _clear() -> None:
        try:
            if path.is_file() and path.read_text(encoding="utf-8").strip() == str(
                os.getpid(),
            ):
                path.unlink(missing_ok=True)
        except Exception:  # noqa: BLE001
            pass

    atexit.register(_clear)
    return True


class Api:
    def __init__(self, config_path: Path | None) -> None:
        self.cfg_path = config_path
        self.cfg = oneso.load_config(config_path)

    def _capture(self, fn) -> tuple[int, str]:
        buf = io.StringIO()
        code = 1
        try:
            with redirect_stdout(buf), redirect_stderr(buf):
                code = int(fn())
        except SystemExit as exc:
            buf.write(f"FAIL: {exc}\n")
            code = int(exc.code) if isinstance(exc.code, int) else 1
        except Exception as exc:  # noqa: BLE001
            buf.write(f"ERROR: {exc}\n")
            code = 1
        return code, buf.getvalue()

    def status(self) -> dict[str, Any]:
        device, build = oneso.adb_device_build()
        adb_ok = bool(device and build)
        so = oneso.resolve_temp_root_so(
            self.cfg,
            so_override=None,
            device=device,
            build=build,
        )
        so_ok = so is not None and so.is_file()
        remote = (
            "https://github.com/asrtroh-netizen/OneSo-assets"
        )
        checks = [
            {
                "name": "adb",
                "ok": adb_ok,
                "detail": f"{device}/{build}" if adb_ok else "offline",
            },
            {
                "name": "匹配 so",
                "ok": so_ok,
                "detail": so.name if so_ok else "missing (拉 GitHub assets 或本地 catalog)",
            },
            {
                "name": "so 工厂",
                "ok": True,
                "detail": "GitHub OneSo-assets（本窗不打包）",
            },
            {
                "name": "本窗职责",
                "ok": True,
                "detail": "仅一键临时 Root",
            },
        ]
        overall = "ok" if adb_ok and so_ok else "warn"
        return {
            "adb_ok": adb_ok,
            "adb_label": f"adb · {device}" if adb_ok else "adb · offline",
            "so_ok": so_ok,
            "so_label": f"so · {so.name}" if so_ok else "so · none",
            "overall": overall,
            "checks": checks,
            "footer": f"{device or '?'} · {build or '?'} · factory→GitHub",
            "log": (
                f"[status] device={device} build={build} so={so} remote={remote}"
            ),
        }

    def temp_root(self, run: bool = False) -> dict[str, Any]:
        code, log = self._capture(
            lambda: oneso.cmd_temp_root(
                self.cfg,
                run=bool(run),
                so_override=None,
                attempts=4,
                timeout_sec=180,
                retry_gap_sec=3.0,
            ),
        )
        return {"code": code, "log": log}


def run_hub(config_path: Path | None = None) -> int:
    if not acquire_single_instance():
        return 0
    try:
        import webview
    except ImportError:
        print("pywebview required: pip install pywebview", file=sys.stderr)
        return 2
    index = WEB / "index.html"
    if not index.is_file():
        print(f"missing {index}", file=sys.stderr)
        return 2
    api = Api(config_path)
    webview.create_window(
        "OneSo · Temp Root",
        url=index.as_uri(),
        js_api=api,
        width=1040,
        height=720,
        min_size=(880, 600),
        background_color="#0a0b12",
    )
    webview.start(debug=False)
    return 0


def main(argv: list[str] | None = None) -> int:
    import argparse

    p = argparse.ArgumentParser(prog="oneso-hub")
    p.add_argument("--config", type=Path, default=None)
    args = p.parse_args(argv)
    return run_hub(args.config)


if __name__ == "__main__":
    raise SystemExit(main())
