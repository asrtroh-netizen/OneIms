# PC TempRoot：禁 su 拉 Shizuku，成功后 shell 重绑

## 约束
与 App / oneso 一致：`su -c libshizuku.so` **禁止**（root/kernel server → binder 掉线）。

## 变更
- `PC-TempRoot-Lite`：成功后 `:rebind_shizuku`（su 仅可 killall，启动走 `adb shell lib… --apk=`）
- `PC-TempRoot-UI`：`Invoke-RebindShellShizuku` 同样策略
- 说明文件写明禁令

## 验证
- 静态：包内无 `su -c` + `libshizuku` 启动串
- Lite/UI `dry nopause` 仍 EXIT=0（dry 不触发 rebind）
