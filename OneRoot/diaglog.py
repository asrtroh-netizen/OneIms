#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""OneRoot 详细诊断日志：会话落盘 + 脱敏 meta + 一键打包导出。"""

from __future__ import annotations

import json
import os
import platform
import re
import shutil
import sys
import threading
import time
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

HERE = Path(__file__).resolve().parent
APP_NAME = "OneRoot"
APP_VERSION = "2026.08.05.1"
LOGS_ROOT = HERE / "logs"

_SECRET_KEY_RE = re.compile(
    r"(password|passwd|secret|token|api[_-]?key|access[_-]?key|private[_-]?key|"
    r"authorization|credential|bearer|session[_-]?key|\bjwt\b|(^|_)auth$)",
    re.I,
)


def _utc_stamp() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def _local_session_id() -> str:
    return datetime.now().strftime("%Y%m%d-%H%M%S")


def redact_value(key: str, value: Any) -> Any:
    if _SECRET_KEY_RE.search(str(key or "")):
        return "***REDACTED***"
    if isinstance(value, dict):
        return {str(k): redact_value(str(k), v) for k, v in value.items()}
    if isinstance(value, list):
        return [redact_value(key, v) for v in value]
    if isinstance(value, str) and len(value) > 4000:
        return value[:4000] + "…(truncated)"
    return value


def redact_mapping(data: dict[str, Any] | None) -> dict[str, Any]:
    if not data:
        return {}
    return {str(k): redact_value(str(k), v) for k, v in data.items()}


class SessionLogger:
    """一次 Hub 进程对应一个会话目录；线程安全追加。"""

    def __init__(
        self,
        root: Path | None = None,
        *,
        session_id: str | None = None,
        config: dict[str, Any] | None = None,
    ) -> None:
        self.root = Path(root or LOGS_ROOT)
        self.session_id = session_id or _local_session_id()
        self.dir = self.root / f"session-{self.session_id}"
        self.dir.mkdir(parents=True, exist_ok=True)
        self._lock = threading.Lock()
        self._line_no = 0
        self.session_log = self.dir / "session.log"
        self.meta_path = self.dir / "meta.json"
        self._write_meta(config)
        self.info("session.start", f"{APP_NAME} {APP_VERSION}")

    def _write_meta(self, config: dict[str, Any] | None) -> None:
        meta = {
            "app": APP_NAME,
            "version": APP_VERSION,
            "session_id": self.session_id,
            "started_at": _utc_stamp(),
            "pid": os.getpid(),
            "cwd": str(Path.cwd()),
            "here": str(HERE),
            "python": sys.version,
            "executable": sys.executable,
            "platform": platform.platform(),
            "machine": platform.machine(),
            "node": platform.node(),
            "env": {
                "USERNAME": os.environ.get("USERNAME") or os.environ.get("USER") or "",
                "COMPUTERNAME": os.environ.get("COMPUTERNAME") or "",
                "PATH_head": os.pathsep.join(
                    (os.environ.get("PATH") or "").split(os.pathsep)[:8],
                ),
            },
            "config_redacted": redact_mapping(config),
        }
        self.meta_path.write_text(
            json.dumps(meta, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )

    def update_meta(self, **fields: Any) -> None:
        with self._lock:
            try:
                data = json.loads(self.meta_path.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                data = {}
            for key, value in fields.items():
                data[key] = redact_value(key, value)
            data["updated_at"] = _utc_stamp()
            self.meta_path.write_text(
                json.dumps(data, ensure_ascii=False, indent=2) + "\n",
                encoding="utf-8",
            )

    def _append(self, level: str, event: str, message: str) -> None:
        level = (level or "INFO").upper()
        event = (event or "-").replace("\n", " ")
        message = (message or "").rstrip("\n")
        with self._lock:
            self._line_no += 1
            line = (
                f"{_utc_stamp()} | {level:<5} | {self._line_no:05d} | "
                f"{event} | {message}\n"
            )
            with self.session_log.open("a", encoding="utf-8", newline="\n") as fh:
                fh.write(line)

    def debug(self, event: str, message: str = "") -> None:
        self._append("DEBUG", event, message)

    def info(self, event: str, message: str = "") -> None:
        self._append("INFO", event, message)

    def warn(self, event: str, message: str = "") -> None:
        self._append("WARN", event, message)

    def error(self, event: str, message: str = "") -> None:
        self._append("ERROR", event, message)

    def line(self, text: str, *, event: str = "print") -> None:
        """镜像任务线程 print/日志行（可含多行）。"""
        raw = text if text.endswith("\n") else text + "\n"
        for part in raw.splitlines():
            if part.strip() == "":
                continue
            # progress 行单独标事件，便于检索
            if "[progress]" in part:
                self.info("progress", part)
            elif part.startswith("FAIL:") or part.startswith("ERROR:"):
                self.error(event, part)
            else:
                self.info(event, part)

    def begin_job(self, kind: str) -> None:
        self.info("job.begin", kind)
        self.update_meta(last_job=kind, last_job_started_at=_utc_stamp())

    def end_job(self, kind: str, code: int) -> None:
        level = "INFO" if int(code) == 0 else "ERROR"
        self._append(level, "job.end", f"{kind} exit={code}")
        self.update_meta(
            last_job=kind,
            last_job_code=int(code),
            last_job_ended_at=_utc_stamp(),
        )

    def snapshot(self) -> dict[str, Any]:
        return {
            "ok": True,
            "app": APP_NAME,
            "version": APP_VERSION,
            "session_id": self.session_id,
            "dir": str(self.dir),
            "session_log": str(self.session_log),
            "meta": str(self.meta_path),
            "logs_root": str(self.root),
        }

    def export_zip(self, dest_dir: Path | None = None) -> Path:
        """打包当前会话目录为 zip，返回 zip 路径。"""
        dest_root = Path(dest_dir or self.root)
        dest_root.mkdir(parents=True, exist_ok=True)
        stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        zip_path = dest_root / f"OneRoot-diag-{self.session_id}-{stamp}.zip"
        self.info("export.begin", str(zip_path))
        # 先写一份 README，方便小白打开 zip
        readme = self.dir / "README_发给作者.txt"
        readme.write_text(
            "\n".join(
                [
                    f"{APP_NAME} 诊断包 {APP_VERSION}",
                    f"session: {self.session_id}",
                    f"generated: {_utc_stamp()}",
                    "",
                    "请把本 zip 原样发给作者排查。",
                    "内含 session.log（详细过程）与 meta.json（环境/脱敏配置）。",
                    "已尝试脱敏 password/token/secret 类字段。",
                    "",
                ],
            ),
            encoding="utf-8",
        )
        with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
            for path in sorted(self.dir.rglob("*")):
                if path.is_file():
                    zf.write(path, arcname=str(path.relative_to(self.dir)))
        self.info("export.done", str(zip_path))
        self.update_meta(last_export=str(zip_path), last_export_at=_utc_stamp())
        return zip_path

    def open_in_explorer(self, target: Path | None = None) -> bool:
        path = Path(target or self.dir)
        if not path.exists():
            return False
        if sys.platform.startswith("win"):
            os.startfile(str(path))  # type: ignore[attr-defined]  # noqa: S606
            return True
        opener = shutil.which("xdg-open") or shutil.which("open")
        if not opener:
            return False
        import subprocess

        subprocess.Popen([opener, str(path)], shell=False)  # noqa: S603
        return True


def smoke_self_test(tmp: Path | None = None) -> dict[str, Any]:
    """无设备冒烟：写日志 → 导出 zip → 断言文件存在。"""
    root = Path(tmp or (HERE / ".cache" / f"diaglog-smoke-{int(time.time())}"))
    if root.exists():
        shutil.rmtree(root, ignore_errors=True)
    root.mkdir(parents=True, exist_ok=True)
    log = SessionLogger(
        root=root,
        session_id="smoke",
        config={"oneims_root": "E:/demo", "api_token": "should-hide"},
    )
    log.info("smoke.step", "hello")
    log.line("[progress] 10% · 测试进度\n")
    log.begin_job("preview")
    log.end_job("preview", 0)
    zpath = log.export_zip()
    meta = json.loads(log.meta_path.read_text(encoding="utf-8"))
    text = log.session_log.read_text(encoding="utf-8")
    ok = (
        zpath.is_file()
        and zpath.stat().st_size > 32
        and "should-hide" not in json.dumps(meta)
        and "***REDACTED***" in json.dumps(meta)
        and "smoke.step" in text
        and "progress" in text
    )
    return {
        "ok": ok,
        "zip": str(zpath),
        "dir": str(log.dir),
        "meta_keys": sorted(meta.keys()),
        "log_bytes": log.session_log.stat().st_size,
    }


def main(argv: list[str] | None = None) -> int:
    import argparse

    p = argparse.ArgumentParser(prog="diaglog")
    p.add_argument("cmd", choices=("smoke", "version"))
    args = p.parse_args(argv)
    if args.cmd == "version":
        print(f"{APP_NAME} {APP_VERSION}")
        return 0
    result = smoke_self_test()
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result.get("ok") else 1


if __name__ == "__main__":
    raise SystemExit(main())
