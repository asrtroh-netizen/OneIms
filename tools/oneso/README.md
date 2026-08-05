# OneSo

IonStack `preload.so` **编译工厂**（包现成 `Makefile` + `src/targets/*`），不是「填型号就魔法生成」。

## 依赖

- 本机已有 IonStack exploit 树（含 `Makefile` / `target.h`）
- Windows：WSL + `make` + Android NDK（把 NDK 路径填进 `config.json` 的 `ndk_root_wsl`）
- 目标：`aarch64-linux-android`

## 配置

```bash
copy config.example.json config.json
# 改 exploit_root / oneims_root / ndk_root_wsl
```

## 命令

```bash
python oneso.py list
python oneso.py info comet-CP2A.260705.006
python oneso.py build tokay-CP2A.260605.012
python oneso.py install tokay-CP2A.260605.012 --build
# 已有成品 so（例如改标签产物）直接入库：
python oneso.py import-so comet-CP2A.260705.006 E:/Down/TEMP/preload-comet-cp2a-260705-006.so
# 批量入库（先 dry-run 看映射）
python oneso.py import-batch E:/Down/TEMP --dry-run
python oneso.py import-batch E:/Down/TEMP
# 简易 GUI（OneAE 深色青绿风）
python oneso.py gui
# 或
python gui.py
# 0705 P9 全家桶：改 label → tokay/caiman/komodo/comet 并写入 catalog
python oneso.py pack-0705
# 尽量自动化：catalog 不齐则 pack + adb 认机 + TEMP dry-run
python oneso.py auto
```

GUI 启动默认跑一遍 `auto`（`config.auto_pack_0705_on_gui_start`，可关）。主按钮「一键自动化」无确认。

说明：`comet-CP2A.260705.006` 的 `target.h` 若仍是 `#error` 脚手架，`build` 会失败——用 `pack-0705` / `import-so` 入库已验证成品。  
`preload.so` 这种无型号文件名会被 SKIP，可用 `--map mapping.json` 指定。

`install` 会：

1. 复制 `build/<PROJECT>/bin/preload.so` → `app/src/main/assets/temproot/preload-<device>-<build>.so`
2. 更新 `catalog.json` 的 `devices.<device>.<buildId>`

## 新机型

1. 在 IonStack `src/targets/<device>-<BuildId>/` 准备好 `target.h`（必要时从同家族模板克隆并改 fingerprint）
2. `oneso build …` → 真机验证
3. `oneso install …` 写入 OneIMS
