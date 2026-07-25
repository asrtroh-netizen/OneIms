# 2026-07-25 · 灌全量号段库（geo.dat）

## 交付

- 资产：`onetools/src/main/assets/caller/geo.dat`（~4.1MB，版本 `2302`）
- 来源：[sndnvaps/Phonedata](https://github.com/sndnvaps/Phonedata) **MIT**（非 GPL）
- 查询：`PhoneDatIndex` 二分查找 7 位号段；`CnMobileGeo` 优先 dat，JSON 兜底
- Directory 继续用 `Hit.dialerLine()` 画系统拨号原生行

## 验证

```text
./gradlew :onetools:testDebugUnitTest :onetools:assembleDebug
```

`PhoneDatIndexTest` 用仓库内 `geo.dat` 实测 `13800138000` 可解析。

## 说明

- 携号转网后面运营商字段可能不准（行业通病）
- 后续可换更新的 MIT/自有库，保持同二进制布局即可热替换 `geo.dat`
