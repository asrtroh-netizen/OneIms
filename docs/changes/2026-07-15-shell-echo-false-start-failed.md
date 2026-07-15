# 2026-07-15 · 通知填码后 start_failed（shell 回显误判）（2.0.23）

## 现象（2.0.22）
通知：「配对失败：通道进程未启动成功」；失败可见已生效。

## 日志
```
pair succeeded / tcpip5555 persisted=true
shell out=<整段启动脚本回显，内含 echo OneBridge_missing … echo OneBridge_started>
```
无独立的 `OneBridge_started` 状态行。

## 根因
adb `shell:` PTY 会回显写入的命令。旧解析用 `contains("OneBridge_missing")`，
把脚本正文当成失败并提前 break，真实 `printf` 输出从未等到。

## 修复
- 状态标记改为 `__OB_BOOT_OK__` / `__OB_BOOT_MISS__`，只认「整行等于」
- 去掉 nohup（兼容性）；单测覆盖「命令回显误判」场景
- version 2.0.23 / code 32
