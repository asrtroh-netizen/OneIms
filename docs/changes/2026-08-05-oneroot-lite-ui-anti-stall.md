# OneRoot Lite/UI 防卡住（对照 Hub PIPE 死锁）

## 结论

| 包 | 是否有 Hub 同类 PIPE 死锁 | 处理 |
|---|---|---|
| Lite（CMD） | **否**（`>` 重定向到 `%TEMP%` 文件） | 补「开始前清理」+ 步骤编号 1–7 |
| UI（PS1） | **是**（`RedirectStandard*` 等到退出才 `ReadToEnd`） | 异步 `Begin*ReadLine` drain + 并行 su early-stop + 预清理 |

## 发布

- OneSo-assets `main`：`20933d0` — `oneroot/OneRoot-Lite.zip` / `OneRoot-UI.zip` + README 抗卡住说明
- 本地镜像：`release/OneRoot-*.zip`、`release/oneroot-public/oneroot/{Lite,UI}/`

## 验证

- ZIP 内脚本关键字核验：Lite steps 含 `pre-clean`；UI 含 `BeginOutputReadLine` / `early-stop`
- UI `TempRoot-UI.ps1` Parser：**OK**
- 真机完整 exploit 冒烟：按需人工（设备已连时可本地再跑一键）
