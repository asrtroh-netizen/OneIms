# OneSo 编译工厂 CLI

- 路径：`tools/oneso/oneso.py` + `gui.py`
- 命令：`list` / `info` / `build` / `install` / `import-so` / `import-batch` / `gui`
- 包 IonStack `Makefile`；`import-so` / `import-batch` 用于已验证成品
- 冒烟：`oneso list` → 31；`import-batch E:\Down\TEMP` → comet 入库，`preload.so` SKIP（无名）
