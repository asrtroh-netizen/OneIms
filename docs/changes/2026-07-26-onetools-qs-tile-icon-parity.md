# 变更说明 · OneTools 切卡磁贴图标对齐 OneIMS

## 现象

控制中心「OneTools 切卡」磁贴图标与 OneIMS 不一致。

## 根因

`onetools/.../drawable/ic_qs_data_sim.xml` 在 R1 迁入时写成了另一套「剪贴板」矢量，且 `fillColor` 为黑色；OneIMS 真源是「文档 + 切换箭头」白矢量（`@android:color/white`，适配 QS 染色）。

## 修复

- 将 OneTools `ic_qs_data_sim.xml` 替换为与 OneIMS 相同的 path / 白色填充
- 版本 → `0.3.8` / `20`

## 验证

```text
powershell -File onetools/scripts/build-local-apk.ps1
```

真机：安装后若系统缓存旧图标，请从控制中心移除再重新添加磁贴。
