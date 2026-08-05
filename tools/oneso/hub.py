#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""OneRoot — 单窗；UI 用 HTTP API（不依赖 pywebview js_api 桥）。"""

from __future__ import annotations

import atexit
import io
import json
import os
import re
import sys
import tempfile
import threading
import traceback
from contextlib import redirect_stderr, redirect_stdout
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

import oneso

HERE = Path(__file__).resolve().parent
WEB = HERE / "web"
LOCK_NAME = "oneroot-single.lock"
API: "HubApi | None" = None
_PROGRESS_RE = re.compile(r"\[progress\]\s*(\d+)%\s*·\s*(.+)")


class _LiveLog(io.TextIOBase):
    """仅捕获「任务线程」的 print；其它线程（HTTP access log）走原 stderr。"""

    def __init__(
        self,
        job: "JobState",
        owner: threading.Thread,
        real: Any,
    ) -> None:
        super().__init__()
        self._job = job
        self._owner = owner
        self._real = real
        self._buf = ""

    def write(self, s: str) -> int:  # type: ignore[override]
        if not s:
            return 0
        if threading.current_thread() is not self._owner:
            return int(self._real.write(s) or 0)
        self._buf += s
        while "\n" in self._buf:
            line, self._buf = self._buf.split("\n", 1)
            self._job.append_line(line + "\n")
        return len(s)

    def flush(self) -> None:
        if threading.current_thread() is not self._owner:
            self._real.flush()
            return
        if self._buf:
            self._job.append_line(self._buf)
            self._buf = ""


class JobState:
    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.running = False
        self.done = False
        self.code: int | None = None
        self.percent = 0
        self.stage = "空闲"
        self.log = ""
        self.kind = ""

    def reset(self, kind: str) -> None:
        with self.lock:
            self.running = True
            self.done = False
            self.code = None
            self.percent = 1
            self.stage = "启动中…"
            self.log = ""
            self.kind = kind

    def append_line(self, line: str) -> None:
        m = _PROGRESS_RE.search(line)
        with self.lock:
            self.log = (self.log + line)[-12000:]
            if m:
                self.percent = int(m.group(1))
                self.stage = m.group(2).strip()

    def finish(self, code: int) -> None:
        with self.lock:
            self.code = int(code)
            self.running = False
            self.done = True
            if self.percent < 100:
                self.percent = 100
            if code == 0 and "失败" not in self.stage:
                if "预览" not in self.stage and "成功" not in self.stage:
                    self.stage = "完成"
            elif code != 0 and "失败" not in self.stage:
                self.stage = f"结束（exit={code}）"

    def snapshot(self) -> dict[str, Any]:
        with self.lock:
            return {
                "running": self.running,
                "done": self.done,
                "code": self.code,
                "percent": self.percent,
                "stage": self.stage,
                "log": self.log,
                "kind": self.kind,
            }


def _lock_path() -> Path:
    return Path(tempfile.gettempdir()) / LOCK_NAME


def acquire_single_instance() -> bool:
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
                handle = kernel32.OpenProcess(0x00100000, False, old)
                if handle:
                    kernel32.CloseHandle(handle)
                    print(
                        f"[OneRoot] already running pid={old} — single window only",
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


class HubApi:
    def __init__(self, config_path: Path | None) -> None:
        adb_dir = Path(r"E:\GQ\One\_toolchain\android-sdk\platform-tools")
        if adb_dir.is_dir():
            os.environ["PATH"] = str(adb_dir) + os.pathsep + os.environ.get("PATH", "")
        self.cfg = oneso.load_config(config_path)
        self.job = JobState()
        self._job_thread: threading.Thread | None = None

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

    def _probe_temp_root(self) -> tuple[bool, str]:
        """adb 侧验临时 Root（与 App / oneso 验活同路径）。"""
        for cmd in (
            "/data/local/tmp/su -c /system/bin/id",
            "/apex/com.android.virt/bin/su -c /system/bin/id",
        ):
            code, out = oneso.adb_shell(cmd, timeout=8.0)
            if oneso.looks_like_root_success(out):
                return True, "临时 Root · 已就绪"
            _ = code
        return False, "临时 Root · 未检测到"

    def _shizuku_ps_user(self) -> str:
        _code, out = oneso.adb_shell(
            "ps -A | grep shizuku_server || true",
            timeout=8.0,
        )
        line = (out or "").strip().replace("\r", "")
        if "shizuku_server" not in line:
            return ""
        return line.split()[0] if line.split() else ""

    def _shizuku_shell_ok(self) -> bool:
        return self._shizuku_ps_user() == "shell"

    def _shizuku_label(self) -> str:
        user = self._shizuku_ps_user()
        if user == "shell":
            return "shell · 正常（勿 su 拉）"
        if user == "root":
            return "root · 危险（App 会掉线）"
        if user:
            return f"{user} · 异常"
        return "未运行"

    def status(self) -> dict[str, Any]:
        device, build = oneso.adb_device_build()
        adb_ok = bool(device and build)
        so = oneso.resolve_temp_root_so(
            self.cfg,
            so_override=None,
            device=device,
            build=build,
            prefer_github=True,
        )
        so_ok = so is not None and so.is_file()
        root_ok, root_label = (
            self._probe_temp_root() if adb_ok else (False, "临时 Root · 无设备")
        )
        checks = [
            {
                "name": "adb / Pixel",
                "ok": adb_ok,
                "detail": f"{device}/{build}" if adb_ok else "offline",
            },
            {
                "name": "so ← GitHub",
                "ok": so_ok,
                "detail": so.name if so_ok else "OneSo-assets 无匹配条目",
            },
            {
                "name": "临时 Root",
                "ok": root_ok,
                "detail": "uid=0 已验证" if root_ok else "未检测到 su/uid=0",
            },
            {
                "name": "Shizuku",
                "ok": self._shizuku_shell_ok() if adb_ok else False,
                "detail": self._shizuku_label() if adb_ok else "无设备",
            },
            {
                "name": "本窗职责",
                "ok": True,
                "detail": "仅一键临时 Root；成功后 shell 重绑 Shizuku（禁 su 拉）",
            },
            {
                "name": "非本窗",
                "ok": True,
                "detail": "运营商持久化 → 手机 App",
            },
        ]
        overall = "ok" if adb_ok and so_ok else "warn"
        return {
            "adb_ok": adb_ok,
            "adb_label": f"adb · {device}" if adb_ok else "adb · offline",
            "so_ok": so_ok,
            "so_label": f"so · {so.name}" if so_ok else "so · github?",
            "root_ok": root_ok,
            "root_label": root_label,
            "overall": overall,
            "checks": checks,
            "footer": f"{device or '?'} · {build or '?'} · OneRoot",
            "log": (
                f"[status] device={device} build={build} so={so} "
                f"root_ok={root_ok} src=GitHub/OneSo-assets"
            ),
        }

    def temp_root(self, run: bool = False) -> dict[str, Any]:
        """兼容旧同步调用（完整捕获后返回）。"""
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

    def start_temp_root(self, run: bool = False) -> dict[str, Any]:
        """后台跑 temp-root，前端轮询 /api/job 看实时进度。"""
        if self.job.running:
            return {"ok": False, "error": "已有任务在跑", **self.job.snapshot()}
        kind = "temp-root" if run else "preview"
        self.job.reset(kind)

        def _worker() -> None:
            code = 1
            live = _LiveLog(self.job, threading.current_thread(), sys.__stderr__)
            try:
                with redirect_stdout(live), redirect_stderr(live):
                    code = int(
                        oneso.cmd_temp_root(
                            self.cfg,
                            run=bool(run),
                            so_override=None,
                            attempts=4,
                            timeout_sec=180,
                            retry_gap_sec=3.0,
                        ),
                    )
            except SystemExit as exc:
                live.write(f"FAIL: {exc}\n")
                code = int(exc.code) if isinstance(exc.code, int) else 1
            except Exception as exc:  # noqa: BLE001
                live.write(f"ERROR: {exc}\n{traceback.format_exc()}\n")
                code = 1
            finally:
                live.flush()
                self.job.finish(code)

        self._job_thread = threading.Thread(
            target=_worker,
            name="oneroot-job",
            daemon=True,
        )
        self._job_thread.start()
        return {"ok": True, "started": True, **self.job.snapshot()}

    def job_status(self) -> dict[str, Any]:
        return {"ok": True, **self.job.snapshot()}

    def open_url(self, url: str) -> dict[str, Any]:
        """在系统浏览器打开白名单外链（GitHub / 赞赏相关），避免壳内导航跑飞。"""
        import webbrowser

        raw = (url or "").strip()
        parsed = urlparse(raw)
        host = (parsed.hostname or "").lower()
        allowed = {
            "github.com",
            "www.github.com",
            "raw.githubusercontent.com",
        }
        if parsed.scheme not in ("http", "https") or host not in allowed:
            return {"ok": False, "error": f"url not allowed: {raw}"}
        webbrowser.open(raw)
        return {"ok": True, "url": raw}


class OneRootHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args: Any, **kwargs: Any) -> None:
        super().__init__(*args, directory=str(WEB), **kwargs)

    def log_message(self, fmt: str, *args: Any) -> None:
        sys.stderr.write("[http] " + (fmt % args) + "\n")

    def _json(self, code: int, payload: dict[str, Any]) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:  # noqa: N802
        path = urlparse(self.path).path
        if path == "/api/ping":
            self._json(200, {"ok": True, "app": "OneRoot"})
            return
        if path == "/api/status":
            assert API is not None
            try:
                self._json(200, API.status())
            except Exception as exc:  # noqa: BLE001
                self._json(
                    500,
                    {"ok": False, "error": str(exc), "trace": traceback.format_exc()},
                )
            return
        if path == "/api/job":
            assert API is not None
            self._json(200, API.job_status())
            return
        if path in ("/", ""):
            self.path = "/index.html"
        return SimpleHTTPRequestHandler.do_GET(self)

    def do_POST(self) -> None:  # noqa: N802
        path = urlparse(self.path).path
        length = int(self.headers.get("Content-Length") or "0")
        raw = self.rfile.read(length) if length else b"{}"
        try:
            data = json.loads(raw.decode("utf-8") or "{}")
        except json.JSONDecodeError:
            data = {}
        if path == "/api/temp-root":
            assert API is not None
            run = bool(data.get("run"))
            try:
                # 默认异步（实时进度）；sync=true 保留旧同步行为
                if bool(data.get("sync")):
                    self._json(200, API.temp_root(run=run))
                else:
                    self._json(200, API.start_temp_root(run=run))
            except Exception as exc:  # noqa: BLE001
                self._json(
                    500,
                    {"ok": False, "error": str(exc), "trace": traceback.format_exc()},
                )
            return
        if path == "/api/open-url":
            assert API is not None
            try:
                self._json(200, API.open_url(str(data.get("url") or "")))
            except Exception as exc:  # noqa: BLE001
                self._json(
                    500,
                    {"ok": False, "error": str(exc), "trace": traceback.format_exc()},
                )
            return
        self._json(404, {"ok": False, "error": "not found"})


def start_server() -> tuple[ThreadingHTTPServer, int]:
    httpd = ThreadingHTTPServer(("127.0.0.1", 0), OneRootHandler)
    port = int(httpd.server_address[1])
    threading.Thread(target=httpd.serve_forever, daemon=True).start()
    return httpd, port


def run_hub(config_path: Path | None = None) -> int:
    global API
    if not acquire_single_instance():
        return 0
    if not (WEB / "index.html").is_file():
        print(f"missing {WEB / 'index.html'}", file=sys.stderr)
        return 2

    API = HubApi(config_path)
    httpd, port = start_server()
    url = f"http://127.0.0.1:{port}/index.html"
    print(f"[OneRoot] {url}", file=sys.stderr)

    # 优先 pywebview 壳；失败则 Edge --app=
    opened = False
    try:
        import webview

        webview.create_window(
            "OneRoot",
            url=url,
            width=1040,
            height=720,
            min_size=(880, 600),
            background_color="#0a0b12",
        )
        opened = True
        try:
            webview.start(debug=False)
        finally:
            httpd.shutdown()
        return 0
    except Exception as exc:  # noqa: BLE001
        print(f"[OneRoot] webview unavailable: {exc}", file=sys.stderr)

    edge = Path(os.environ.get("ProgramFiles(x86)", r"C:\Program Files (x86)")) / (
        r"Microsoft\Edge\Application\msedge.exe"
    )
    if not edge.is_file():
        edge = Path(os.environ.get("ProgramFiles", r"C:\Program Files")) / (
            r"Microsoft\Edge\Application\msedge.exe"
        )
    if edge.is_file():
        import subprocess

        subprocess.Popen(  # noqa: S603
            [str(edge), f"--app={url}", "--new-window"],
        )
        opened = True
        print("[OneRoot] opened via Edge --app", file=sys.stderr)
        try:
            while True:
                threading.Event().wait(3600)
        except KeyboardInterrupt:
            pass
        finally:
            httpd.shutdown()
        return 0

    if not opened:
        print(f"[OneRoot] open this URL manually: {url}", file=sys.stderr)
    try:
        while True:
            threading.Event().wait(3600)
    except KeyboardInterrupt:
        pass
    finally:
        httpd.shutdown()
    return 0


def main(argv: list[str] | None = None) -> int:
    import argparse

    p = argparse.ArgumentParser(prog="OneRoot")
    p.add_argument("--config", type=Path, default=None)
    args = p.parse_args(argv)
    return run_hub(args.config)


if __name__ == "__main__":
    raise SystemExit(main())
