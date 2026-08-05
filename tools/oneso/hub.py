#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""OneSo Hub — OneAE-style HTML splash + pywebview desk."""

from __future__ import annotations

import io
import subprocess
import sys
import threading
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from typing import Any

import oneso

HERE = Path(__file__).resolve().parent
WEB = HERE / "web"


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
        app = oneso.oneims_root(self.cfg)
        assets = app / "app" / "src" / "main" / "assets" / "temproot"
        device, build = oneso.adb_device_build()
        adb_ok = bool(device and build)
        catalog_ok = oneso.catalog_0705_complete(self.cfg)
        checks: list[dict[str, Any]] = []
        checks.append(
            {
                "name": "adb device",
                "ok": adb_ok,
                "detail": f"{device}/{build}" if adb_ok else "offline",
            },
        )
        checks.append(
            {
                "name": "catalog 0705",
                "ok": catalog_ok,
                "detail": "4/4 ready" if catalog_ok else "incomplete",
            },
        )
        for d in oneso.DEVICES_0705:
            name = f"preload-{d}-{oneso.BUILD_0705}.so"
            path = assets / name
            checks.append(
                {
                    "name": f"P9 {d}",
                    "ok": path.is_file(),
                    "detail": "ok" if path.is_file() else "missing",
                },
            )
        p10_ok = oneso.catalog_p10_complete(self.cfg)
        for d in oneso.DEVICES_P10:
            name = f"preload-{d}-{oneso.BUILD_P10}.so"
            path = assets / name
            checks.append(
                {
                    "name": f"P10 {d}",
                    "ok": path.is_file(),
                    "detail": "ok" if path.is_file() else "missing",
                },
            )
        overall = "ok" if adb_ok and catalog_ok and p10_ok else "warn"
        return {
            "adb_ok": adb_ok,
            "adb_label": f"adb · {device}" if adb_ok else "adb · offline",
            "catalog_ok": catalog_ok and p10_ok,
            "catalog_label": (
                "catalog · P9+P10 ok"
                if catalog_ok and p10_ok
                else ("catalog · need P10" if catalog_ok else "catalog · need P9")
            ),
            "overall": overall,
            "checks": checks,
            "footer": f"{device or '?'} · {build or '?'} · P9+P10 desk",
            "log": (
                f"[status] device={device} build={build} "
                f"catalog0705={catalog_ok} catalogP10={p10_ok}"
            ),
        }

    def pack_0705(self) -> dict[str, Any]:
        code, log = self._capture(lambda: oneso.cmd_pack_0705(self.cfg, None))
        return {"code": code, "log": log}

    def pack_p10(self) -> dict[str, Any]:
        code, log = self._capture(lambda: oneso.cmd_pack_p10(self.cfg))
        return {"code": code, "log": log}

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

    def open_tk_gui(self) -> dict[str, Any]:
        # 独立进程打开旧 Tk GUI，不阻塞 Hub
        cmd = [sys.executable, str(HERE / "oneso.py"), "gui"]
        if self.cfg_path:
            cmd.extend(["--config", str(self.cfg_path)])
        subprocess.Popen(cmd, cwd=str(HERE))  # noqa: S603
        return {"code": 0, "log": "[hub] spawned oneso gui"}


def run_hub(config_path: Path | None = None) -> int:
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
    window = webview.create_window(
        "OneSo Hub",
        url=index.as_uri(),
        js_api=api,
        width=1100,
        height=760,
        min_size=(900, 640),
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
