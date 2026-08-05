#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""OneSo GUI — 视觉对齐 OneAE Hub（深底 + 青绿强调）。"""

from __future__ import annotations

import io
import threading
import tkinter as tk
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from tkinter import filedialog, messagebox
from typing import Any, Callable

import oneso

# OneAE app.css tokens
BG0 = "#0a0b12"
BG1 = "#12131c"
BG2 = "#181a26"
BG3 = "#1f2233"
TEXT = "#f2f3ff"
TEXT_SOFT = "#c8d0dc"
MUTED = "#8b90a8"
ACCENT = "#2bc4b4"
ACCENT_BRIGHT = "#4af0dc"
ACCENT_DIM = "#163a36"
DANGER = "#ff6f7a"
OK = "#62e0a8"
WARN = "#ffb048"
LINE = "#2a2d3d"
FONT_UI = ("Microsoft YaHei UI", 10)
FONT_DISPLAY = ("Bahnschrift", 16, "bold")
FONT_MONO = ("Cascadia Mono", 9)


class OneSoApp(tk.Tk):
    def __init__(self, config_path: Path | None) -> None:
        super().__init__()
        self.title("OneSo")
        self.geometry("960x640")
        self.minsize(860, 560)
        self.configure(bg=BG0)
        self.cfg_path = config_path
        self.cfg: dict[str, Any] = oneso.load_config(config_path)
        self.project_var = tk.StringVar()
        self.status_var = tk.StringVar(value="ready")

        self._build_chrome()
        self.refresh_projects()
        self.append(
            f"config  {self.cfg.get('_config_path')}\n"
            f"exploit {self.cfg.get('exploit_root')}\n"
            f"oneims  {self.cfg.get('oneims_root')}\n"
            f"0705    {', '.join(oneso.DEVICES_0705)} @ {oneso.BUILD_0705}\n",
        )
        # 启动尽量自动化：补齐 0705 + adb 认机
        if bool(self.cfg.get("auto_pack_0705_on_gui_start", True)):
            self.after(200, self.on_auto)

    def _card(self, parent: tk.Misc, **kw: Any) -> tk.Frame:
        return tk.Frame(parent, bg=BG2, highlightbackground=LINE, highlightthickness=1, **kw)

    def _btn(
        self,
        parent: tk.Misc,
        text: str,
        command: Callable[[], None],
        *,
        primary: bool = False,
        danger: bool = False,
    ) -> tk.Label:
        if primary:
            bg, fg, active = ACCENT, "#04221e", ACCENT_BRIGHT
        elif danger:
            bg, fg, active = "#3a1820", DANGER, "#5a2230"
        else:
            bg, fg, active = BG3, TEXT, "#2a2e42"
        lab = tk.Label(
            parent,
            text=text,
            bg=bg,
            fg=fg,
            font=FONT_UI,
            padx=14,
            pady=8,
            cursor="hand2",
        )
        lab.bind("<Button-1>", lambda _e: command())
        lab.bind("<Enter>", lambda _e: lab.configure(bg=active))
        lab.bind("<Leave>", lambda _e: lab.configure(bg=bg))
        return lab

    def _build_chrome(self) -> None:
        # top brand bar
        top = tk.Frame(self, bg=BG1, height=64)
        top.pack(fill=tk.X)
        top.pack_propagate(False)
        brand = tk.Frame(top, bg=BG1)
        brand.pack(side=tk.LEFT, padx=20, pady=12)
        tk.Label(
            brand,
            text="OneSo",
            bg=BG1,
            fg=ACCENT_BRIGHT,
            font=FONT_DISPLAY,
        ).pack(anchor="w")
        tk.Label(
            brand,
            text="preload factory · OneAE feel",
            bg=BG1,
            fg=MUTED,
            font=("Microsoft YaHei UI", 9),
        ).pack(anchor="w")
        tk.Label(
            top,
            textvariable=self.status_var,
            bg=BG1,
            fg=MUTED,
            font=FONT_MONO,
        ).pack(side=tk.RIGHT, padx=20)

        body = tk.Frame(self, bg=BG0)
        body.pack(fill=tk.BOTH, expand=True, padx=16, pady=12)

        # left panel
        left = self._card(body)
        left.pack(side=tk.LEFT, fill=tk.Y, padx=(0, 12))
        inner = tk.Frame(left, bg=BG2)
        inner.pack(fill=tk.BOTH, expand=True, padx=14, pady=14)

        tk.Label(inner, text="PROJECT", bg=BG2, fg=MUTED, font=FONT_UI).pack(anchor="w")
        self.combo = tk.Listbox(
            inner,
            bg=BG3,
            fg=TEXT,
            selectbackground=ACCENT_DIM,
            selectforeground=ACCENT_BRIGHT,
            activestyle="none",
            highlightthickness=0,
            borderwidth=0,
            font=FONT_MONO,
            height=18,
            width=36,
        )
        self.combo.pack(fill=tk.BOTH, expand=True, pady=(6, 10))
        self.combo.bind("<<ListboxSelect>>", self._on_select)

        row = tk.Frame(inner, bg=BG2)
        row.pack(fill=tk.X, pady=4)
        self._btn(row, "刷新列表", self.refresh_projects).pack(side=tk.LEFT)
        self._btn(row, "Info", self.on_info).pack(side=tk.LEFT, padx=(8, 0))

        # right panel
        right = tk.Frame(body, bg=BG0)
        right.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)

        hero = self._card(right)
        hero.pack(fill=tk.X, pady=(0, 12))
        hero_i = tk.Frame(hero, bg=BG2)
        hero_i.pack(fill=tk.X, padx=16, pady=14)
        tk.Label(
            hero_i,
            text="CP2A.260705.006 · P9 全家桶",
            bg=BG2,
            fg=TEXT,
            font=("Bahnschrift", 13, "bold"),
        ).pack(anchor="w")
        tk.Label(
            hero_i,
            text="从已验证 so 改 label → tokay / caiman / komodo / comet，写入 OneIMS catalog",
            bg=BG2,
            fg=TEXT_SOFT,
            font=FONT_UI,
            wraplength=520,
            justify="left",
        ).pack(anchor="w", pady=(4, 10))
        hero_btns = tk.Frame(hero_i, bg=BG2)
        hero_btns.pack(anchor="w")
        self._btn(hero_btns, "一键自动化", self.on_auto, primary=True).pack(side=tk.LEFT)
        self._btn(hero_btns, "强制重打包 0705", self.on_pack_0705).pack(
            side=tk.LEFT,
            padx=(8, 0),
        )
        self._btn(hero_btns, "adb 认机", self.on_adb_select).pack(
            side=tk.LEFT,
            padx=(8, 0),
        )
        self._btn(hero_btns, "TEMP Dry-Run", self.on_import_batch_dry).pack(
            side=tk.LEFT,
            padx=(8, 0),
        )

        actions = self._card(right)
        actions.pack(fill=tk.X, pady=(0, 12))
        act_i = tk.Frame(actions, bg=BG2)
        act_i.pack(fill=tk.X, padx=12, pady=12)
        for text, cmd, primary in (
            ("Build", self.on_build, False),
            ("Install+Build", self.on_install, False),
            ("Import 单个…", self.on_import_one, False),
            ("批量 Import…", self.on_import_batch, True),
        ):
            self._btn(act_i, text, cmd, primary=primary).pack(side=tk.LEFT, padx=(0, 8))

        log_card = self._card(right)
        log_card.pack(fill=tk.BOTH, expand=True)
        log_i = tk.Frame(log_card, bg=BG2)
        log_i.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)
        tk.Label(log_i, text="LOG", bg=BG2, fg=MUTED, font=FONT_UI).pack(anchor="w")
        wrap = tk.Frame(log_i, bg=BG3)
        wrap.pack(fill=tk.BOTH, expand=True, pady=(6, 0))
        self.log = tk.Text(
            wrap,
            bg=BG3,
            fg=TEXT_SOFT,
            insertbackground=ACCENT,
            highlightthickness=0,
            borderwidth=0,
            font=FONT_MONO,
            wrap=tk.WORD,
        )
        scroll = tk.Scrollbar(wrap, command=self.log.yview, bg=BG2, troughcolor=BG1)
        self.log.configure(yscrollcommand=scroll.set)
        self.log.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        scroll.pack(side=tk.RIGHT, fill=tk.Y)

    def append(self, text: str) -> None:
        self.log.insert(tk.END, text if text.endswith("\n") else text + "\n")
        self.log.see(tk.END)

    def set_status(self, text: str) -> None:
        self.status_var.set(text)

    def refresh_projects(self) -> None:
        try:
            projects = oneso.list_projects(oneso.exploit_root(self.cfg))
        except Exception as exc:  # noqa: BLE001
            messagebox.showerror("OneSo", str(exc))
            return
        # 0705 全家桶虚拟项置顶（可无 target.h，靠 pack-0705 / import）
        synthetic = [f"{d}-{oneso.BUILD_0705}" for d in oneso.DEVICES_0705]
        prefer = []
        for p in synthetic:
            if p not in prefer:
                prefer.append(p)
        for p in projects:
            if oneso.BUILD_0705 in p and p not in prefer:
                prefer.append(p)
        rest = [p for p in projects if p not in prefer]
        ordered = prefer + rest
        self.combo.delete(0, tk.END)
        for p in ordered:
            self.combo.insert(tk.END, p)
        pick = f"comet-{oneso.BUILD_0705}"
        if pick in ordered:
            idx = ordered.index(pick)
        elif ordered:
            idx = 0
            pick = ordered[0]
        else:
            return
        self.combo.selection_clear(0, tk.END)
        self.combo.selection_set(idx)
        self.combo.see(idx)
        self.project_var.set(pick)
        self.append(f"[gui] {len(projects)} projects · 0705 hits={len(prefer)}")
        self.set_status(f"{len(projects)} projects")

    def _on_select(self, _evt: object = None) -> None:
        sel = self.combo.curselection()
        if not sel:
            return
        self.project_var.set(self.combo.get(sel[0]))

    def selected_project(self) -> str:
        p = self.project_var.get().strip()
        if not p:
            raise RuntimeError("请先选择 PROJECT")
        return p

    def run_bg(self, title: str, fn: Callable[[], int]) -> None:
        self.set_status(f"running · {title}")

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
                self.append(f"── {title} ──")
                if text:
                    self.append(text.rstrip("\n"))
                self.append(f"[{title}] exit={code}")
                self.set_status("ready" if code == 0 else f"exit {code}")

            self.after(0, ui)

        threading.Thread(target=worker, daemon=True).start()

    def on_info(self) -> None:
        project = self.selected_project()
        self.run_bg("info", lambda: oneso.cmd_info(self.cfg, project))

    def on_build(self) -> None:
        project = self.selected_project()
        if not messagebox.askyesno("OneSo", f"Build {project}？需要 WSL + NDK"):
            return
        self.run_bg("build", lambda: oneso.cmd_build(self.cfg, project))

    def on_install(self) -> None:
        project = self.selected_project()
        self.run_bg(
            "install",
            lambda: oneso.cmd_install(self.cfg, project, build_first=True),
        )

    def on_import_one(self) -> None:
        project = self.selected_project()
        path = filedialog.askopenfilename(
            title="选择 preload.so",
            filetypes=[("shared object", "*.so"), ("all", "*.*")],
        )
        if not path:
            return
        self.run_bg("import-so", lambda: oneso.cmd_import_so(self.cfg, project, Path(path)))

    def _batch(self, dry_run: bool) -> None:
        directory = filedialog.askdirectory(title="选择含 *.so 的目录")
        if not directory:
            return
        self.run_bg(
            "import-batch-dry" if dry_run else "import-batch",
            lambda: oneso.cmd_import_batch(
                self.cfg,
                Path(directory),
                recursive=True,
                dry_run=dry_run,
                mapping_path=None,
            ),
        )

    def on_import_batch(self) -> None:
        self._batch(False)

    def on_import_batch_dry(self) -> None:
        # 快捷：直接 dry-run TEMP
        temp = Path(r"E:\Down\TEMP")
        if temp.is_dir():
            self.run_bg(
                "import-batch-dry",
                lambda: oneso.cmd_import_batch(
                    self.cfg,
                    temp,
                    recursive=False,
                    dry_run=True,
                    mapping_path=None,
                ),
            )
        else:
            self._batch(True)

    def on_pack_0705(self) -> None:
        # 强制重打包：仍要确认，避免误覆盖
        if not messagebox.askyesno(
            "OneSo",
            "强制重跑 pack-0705？\n" + ", ".join(oneso.DEVICES_0705),
        ):
            return
        self.run_bg("pack-0705", lambda: oneso.cmd_pack_0705(self.cfg, None))

    def on_auto(self) -> None:
        """无确认：缺啥补啥 + adb 认机 + TEMP dry-run。"""

        def job() -> int:
            code = oneso.cmd_auto(self.cfg, force_pack=False)
            device, build = oneso.adb_device_build()
            if device and build:
                self.after(0, lambda: self.select_project(f"{device}-{build}"))
            return code

        self.run_bg("auto", job)

    def on_adb_select(self) -> None:
        device, build = oneso.adb_device_build()
        if not device or not build:
            messagebox.showwarning("OneSo", "adb 读不到设备（未连接或无 adb）")
            return
        project = f"{device}-{build}"
        self.append(f"[adb] device={device} build={build} -> {project}")
        self.select_project(project)
        self.set_status(f"adb {project}")

    def select_project(self, project: str) -> None:
        values = list(self.combo.get(0, tk.END))
        if project in values:
            idx = values.index(project)
            self.combo.selection_clear(0, tk.END)
            self.combo.selection_set(idx)
            self.combo.see(idx)
            self.project_var.set(project)
            return
        # 不在 list：插入顶部虚拟项
        self.combo.insert(0, project)
        self.combo.selection_clear(0, tk.END)
        self.combo.selection_set(0)
        self.project_var.set(project)


def run_gui(config_path: Path | None = None) -> None:
    app = OneSoApp(config_path)
    app.mainloop()


if __name__ == "__main__":
    run_gui(None)
