# OneRoot mode0：跳过 waiter cleanup 门闩

## 现象

`comet` / `CP2A.260705.006` 上：

- `slide-kaslr-ok` → `prepare_kernel_page mode=0` → CFI/pipe physrw 成功到  
  `phys step tasklist compare ok=1`
- 随后 `configfs * len=40` 刷屏（`user_pipe_buffer`），无 `root cred patched`
- 可致 `kernel_panic,oops:_fatal_exception`

App 内 so SHA 曾与 README「已验证」哈希 `e74cbc7d…` 一致——不是装错包。

## 根因

`install_child_root`（tokay 覆盖）：

```c
return install_pipe_physrw(fd) && cleanup_main_waiter_pi_state(fd) &&
       install_android_root(fd);
```

cleanup 失败或卡在 `find_task_by_tgid` 的 pipe_phys 时，永远进不了 `install_android_root`。

反汇编（原 so）：VA `0x28c5c` `bl cleanup`；`0x28c60` `cbz w0, return0`。

## 修复

| 层 | 内容 |
|---|---|
| 源码 | `OneSo-factory/.../tokay-CP2A.260605.012/fops.c`：cleanup best-effort + `SKIP_WAITER_CLEANUP` |
| 预编译 so | NOP 上述 `bl`+`cbz`；新 SHA256 `3c3c0868…`；原件备份 `*.orig-e74cbc7d` |
| App | 同步 assets so，版本 **1.1.5 / 16** |

## 验证

- 静态：补丁位点字节为 `1f2003d5`（NOP）×2；哈希见上。
- 动态：需真机再跑（仍有 panic 风险）；期望日志出现 `root cred patched` / `pipe physrw` 成功收尾，或至少越过 cleanup 刷屏。
