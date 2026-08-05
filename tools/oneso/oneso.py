"""Compat shim: real oneso tree lives in repo-root OneRoot/."""
from __future__ import annotations

import os
import runpy
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / "OneRoot"
TARGET = ROOT / "oneso.py"
if not TARGET.is_file():
    raise SystemExit(f"OneRoot pack missing: {TARGET}")

os.chdir(ROOT)
sys.path.insert(0, str(ROOT))
sys.argv[0] = str(TARGET)
runpy.run_path(str(TARGET), run_name="__main__")
