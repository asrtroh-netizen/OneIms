#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""本机冒烟：打开 OneSo GUI，自动点 Info + 批量 Dry-Run，写日志后退出。"""

from __future__ import annotations

import io
import sys
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path

HERE = Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))

import oneso
from gui import OneSoApp

LOG = HERE / "smoke_gui_click.log"
TEMP = Path(r"E:\Down\TEMP")


def main() -> int:
    lines: list[str] = []
    app = OneSoApp(None)
    app.update()
    projects = list(app.combo["values"])
    lines.append(f"projects={len(projects)}")
    lines.append(f"selected={app.project_var.get()}")

    # 点 Info（同步跑，便于采证）
    buf = io.StringIO()
    with redirect_stdout(buf), redirect_stderr(buf):
        code_info = oneso.cmd_info(app.cfg, app.project_var.get())
    lines.append(f"info_exit={code_info}")
    lines.append(buf.getvalue().rstrip())

    # 批量 Dry-Run（不弹文件夹对话框）
    buf2 = io.StringIO()
    with redirect_stdout(buf2), redirect_stderr(buf2):
        code_batch = oneso.cmd_import_batch(
            app.cfg,
            TEMP,
            recursive=False,
            dry_run=True,
            mapping_path=None,
        )
    lines.append(f"batch_dry_exit={code_batch}")
    lines.append(buf2.getvalue().rstrip())

    app.update_idletasks()
    app.destroy()

    text = "\n".join(lines) + "\n"
    LOG.write_text(text, encoding="utf-8")
    print(text)
    return 0 if code_info == 0 and code_batch == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
