# 2026-07-31 · 飞牛 RK3568 NPU 用户态运行时安装

## 背景

HexHub 主机 **TTFN**（飞牛 / RK3568 / `Linux 6.18.18.c951-trim aarch64`）内核已加载 `rknpu`，但用户态缺少 `librknn*` / `rknn_server`，应用无法调用 NPU。  
群友方案：安装 `rknpu2` 运行时（拷贝 aarch64 库到 `/usr/lib`、服务到 `/usr/bin`）。用户本机在 `/home/TTFN` clone 失败。

## 根因（首轮）

1. `/home/TTFN` 属主为 `root:root`，普通用户不可写 → `git clone` Permission denied  
2. 飞牛出网访问 `github.com:443` 失败 → 远端无法直接拉仓库  
3. 仅有内核模块，无 RKNPU2 用户态 runtime

## 首轮做法（本机中转 · 1.4.0）

1. SSH：`TTFN@tfs.itt.fan:4848`（凭据来自本机 HexHub 已存主机）  
2. 本机下载 FastDeploy 包 `rknpu2_device_install_1.4.0.zip`（含 RK356X runtime）  
3. SFTP 上传 `librknnrt.so` / `librknn_api.so` / `rknn_server` / start·restart 脚本  
4. `sudo cp` 到 `/usr/lib`、`/usr/bin`，执行 `restart_rknn.sh`  
5. `chown -R TTFN:Users /home/TTFN` 修复家目录可写

### 首轮验证

| 检查 | 结果 |
|---|---|
| `/usr/lib/librknnrt.so`、`librknn_api.so` | 存在，aarch64 ELF |
| `rknn_server` 进程 | 运行中 |
| `lsmod \| grep rknpu` | 模块加载 |
| `ctypes.CDLL` 加载库 | LOAD_OK |
| `/home/TTFN` 可写 | HOME_WRITABLE_OK |
| 实际模型推理 | NOT RUN（无现成 `.rknn` 用例） |

---

## 续轮（用户机上 · 官方仓升级到 1.5.2）

**时间**：同日稍后（用户在 `root@TTFN:~/rknpu2` 操作）  
**变化**：飞牛已能直连 GitHub，`git clone https://github.com/rockchip-linux/rknpu2.git` 成功（约 387 MiB）。

### 路径坑（已踩过）

官方仓 **没有** 扁平路径 `runtime/Linux/...`，RK3568 必须用：

```text
runtime/RK356X/Linux/librknn_api/aarch64/librknnrt.so
runtime/RK356X/Linux/librknn_api/aarch64/librknn_api.so
runtime/RK356X/Linux/rknn_server/aarch64/usr/bin/rknn_server
```

错误的 `runtime/Linux/...` 会报 `No such file or directory`。

### 覆盖安装注意

若旧 `rknn_server` 仍在跑，直接 `cp` 到 `/usr/bin/rknn_server` 会报 **`Text file busy`**。正确顺序：

```bash
killall rknn_server
cp runtime/RK356X/Linux/rknn_server/aarch64/usr/bin/rknn_server /usr/bin/
chmod +x /usr/bin/rknn_server
rknn_server &
```

### 续轮结果（用户终端证据）

| 项 | 结果 |
|---|---|
| 源 | `rockchip-linux/rknpu2` clone 成功 |
| 库路径 | `runtime/RK356X/Linux/...` 拷贝成功 |
| 服务版本 | `start rknn server, version:1.5.2 (8babfea build@2023-08-25T10:29:51)` |
| Transfer | `NPU Transfer Server, Transfer version 2.1.0` |
| 进程 | `rknn_server &` → PID 示例 `636598` |
| 实际模型推理 | 仍 NOT RUN |

当前用户态以 **1.5.2** 为准（覆盖首轮 1.4.0）。

---

## 回滚

```bash
sudo killall start_rknn.sh rknn_server || true
sudo rm -f /usr/lib/librknnrt.so /usr/lib/librknn_api.so
sudo rm -f /usr/bin/rknn_server /usr/bin/start_rknn.sh /usr/bin/restart_rknn.sh
```

## 后续可选

- 用业务程序 / `rknn_toolkit_lite2` 实测推理  
- 配置开机自启（`rknn_server` 或 `restart_rknn.sh`）  
- 官方仓路径以 `runtime/RK356X/Linux/...` 为准；出网已通时可在机上直接 `git pull` 更新
