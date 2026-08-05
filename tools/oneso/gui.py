#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""OneSo 简易 Tk GUI。"""

from __future__ import annotations

import io
import threading
import tkinter as tk
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from tkinter import filedialog, messagebox, ttk
from typing import Any, Callable

import oneso


class OneSoApp(tk.Tk):
    def __init__(self, config_path: Path | None) -> None:
        super().__init__()
        self.title("OneSo — preload.so 工厂")
        self.geometry("820x560")
        self.cfg_path = config_path
        self.cfg: dict[str, Any] = oneso.load_config(config_path)

        top = ttk.Frame(self, padding=8)
        top.pack(fill=tk.X)
        ttk.Label(top, text="PROJECT").pack(side=tk.LEFT)
        self.project_var = tk.StringVar()
        self.combo = ttk.Combobox(top, textvariable=self.project_var, width=42)
        self.combo.pack(side=tk.LEFT, padx=6)
        ttk.Button(top, text="刷新列表", command=self.refresh_projects).pack(side=tk.LEFT)

        btns = ttk.Frame(self, padding=8)
        btns.pack(fill=tk.X)
        ttk.Button(btns, text="Info", command=self.on_info).pack(side=tk.LEFT, padx=2)
        ttk.Button(btns, text="Build", command=self.on_build).pack(side=tk.LEFT, padx=2)
        ttk.Button(btns, text="Install(--build)", command=self.on_install).pack(
            side=tk.LEFT,
            padx=2,
        )
        ttk.Button(btns, text="Import 单个 so…", command=self.on_import_one).pack(
            side=tk.LEFT,
            padx=2,
        )
        ttk.Button(btns, text="批量 Import 目录…", command=self.on_import_batch).pack(
            side=tk.LEFT,
            padx=2,
        )
        ttk.Button(btns, text="批量 Dry-Run…", command=self.on_import_batch_dry).pack(
            side=tk.LEFT,
            padx=2,
        )

        log_frame = ttk.Frame(self, padding=8)
        log_frame.pack(fill=tk.BOTH, expand=True)
        self.log = tk.Text(log_frame, height=24, wrap=tk.WORD)
        self.log.pack(fill=tk.BOTH, expand=True, side=tk.LEFT)
        scroll = ttk.Scrollbar(log_frame, command=self.log.yview)
        scroll.pack(side=tk.RIGHT, fill=tk.Y)
        self.log.configure(yscrollcommand=scroll.set)

        self.refresh_projects()
        self.append(
            f"config={self.cfg.get('_config_path')}\n"
            f"exploit={self.cfg.get('exploit_root')}\n"
            f"oneims={self.cfg.get('oneims_root')}\n",
        )

    def append(self, text: str) -> None:
        self.log.insert(tk.END, text if text.endswith("\n") else text + "\n")
        self.log.see(tk.END)

    def refresh_projects(self) -> None:
        try:
            projects = oneso.list_projects(oneso.exploit_root(self.cfg))
        except Exception as exc:  # noqa: BLE001
            messagebox.showerror("OneSo", str(exc))
            return
        self.combo["values"] = projects
        if projects and not self.project_var.get():
            # 优先 comet 0705
            prefer = "comet-CP2A.260705.006"
            self.project_var.set(prefer if prefer in projects else projects[0])
        self.append(f"[gui] {len(projects)} projects loaded")

    def selected_project(self) -> str:
        p = self.project_var.get().strip()
        if not p:
            raise RuntimeError("请先选择 PROJECT")
        return p

    def run_bg(self, title: str, fn: Callable[[], int]) -> None:
        def worker() -> None:
            buf = io.StringIO()
            code = 1
            try:
                with redirect_stdout(buf), redirect_stderr(buf):
                    code = fn()
            except SystemExit as exc:
                buf.write(f"FAIL: {exc}\n")
            except Exception as exc:  # noqa: BLE001
                buf.write(f"ERROR: {exc}\n")
            text = buf.getvalue()

            def ui() -> None:
                self.append(f"--- {title} ---")
                if text:
                    self.append(text.rstrip("\n"))
                self.append(f"[{title}] exit={code}")

            self.after(0, ui)

        threading.Thread(target=worker, daemon=True).start()

    def on_info(self) -> None:
        project = self.selected_project()

        def job() -> int:
            return oneso.cmd_info(self.cfg, project)

        self.run_bg("info", job)

    def on_build(self) -> None:
        project = self.selected_project()
        if not messagebox.askyesno("OneSo", f"Build {project}？需要 WSL+NDK"):
            return

        def job() -> int:
            return oneso.cmd_build(self.cfg, project)

        self.run_bg("build", job)

    def on_install(self) -> None:
        project = self.selected_project()

        def job() -> int:
            return oneso.cmd_install(self.cfg, project, build_first=True)

        self.run_bg("install", job)

    def on_import_one(self) -> None:
        project = self.selected_project()
        path = filedialog.askopenfilename(
            title="选择 preload.so",
            filetypes=[("shared object", "*.so"), ("all", "*.*")],
        )
        if not path:
            return

        def job() -> int:
            return oneso.cmd_import_so(self.cfg, project, Path(path))

        self.run_bg("import-so", job)

    def _batch(self, dry_run: bool) -> None:
        directory = filedialog.askdirectory(title="选择含 *.so 的目录")
        if not directory:
            return

        def job() -> int:
            return oneso.cmd_import_batch(
                self.cfg,
                Path(directory),
                recursive=True,
                dry_run=dry_run,
                mapping_path=None,
            )

        self.run_bg("import-batch-dry" if dry_run else "import-batch", job)

    def on_import_batch(self) -> None:
        self._batch(False)

    def on_import_batch_dry(self) -> None:
        self._batch(True)


def run_gui(config_path: Path | None = None) -> None:
    app = OneSoApp(config_path)
    app.mainloop()


if __name__ == "__main__":
    run_gui(None)
