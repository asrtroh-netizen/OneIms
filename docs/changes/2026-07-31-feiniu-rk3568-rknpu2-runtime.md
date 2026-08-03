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

## 开机自启（已部署 · 2026-07-31）

主机：`bdy,g18-pro` / RK3568。已写入并启用 systemd 单元：

- 单元：`/etc/systemd/system/rknn_server.service`
- 状态：`enabled` + `active`（`rknn_server` 1.5.2）
- 内核：`RKNPU driver: v0.9.8`（满足 Immich RKNN ≥0.9.8 前提）
- 设备节点：`/dev/dri/renderD128`、`/dev/dma_heap`、`/dev/rga` 存在

启动前会 `killall` 旧的 `rknn_server` / `start_rknn.sh` 循环，避免双实例。

### 与飞牛 AI 相册的关系

- 系统已有 `ai_manager.service`（运行中）与模型目录  
  `/vol1/@sysappmeta/ai-manager/models/{face,img2vec,txt2vec}-*`
- 当日日志显示模型包已下载；`ai_manager` 侧配置偏 GPU 列表，**未见直接绑定 RKNN**  
  → 原生相册 AI 可能仍走 CPU；NPU 用户态是给 RKNN 应用（如 Immich `-rknn`）用的地基
- 相册数据目录线索：`/vol1/1000/Photos`（含 `MobileBackup`）

## 回滚（含自启）

```bash
sudo systemctl disable --now rknn_server.service
sudo rm -f /etc/systemd/system/rknn_server.service
sudo systemctl daemon-reload
sudo killall start_rknn.sh rknn_server || true
sudo rm -f /usr/lib/librknnrt.so /usr/lib/librknn_api.so
sudo rm -f /usr/bin/rknn_server /usr/bin/start_rknn.sh /usr/bin/restart_rknn.sh
```

## 续轮 · 原生相册「RKNPU 调用失败」（重启后仍失败）

**现象**：相册 AI 设置里硬件加速选 RKNPU → 红字「调用失败，请检查是否已安装显卡驱动」（文案通用，实为硬件校验失败）。别人成功机型同 UI 为绿勾「调用成功」。

**本机已具备（重启后复验）**

| 项 | 结果 |
|---|---|
| `rknn_server.service` | enabled + active，版本 1.5.2 |
| `ensure-renderd129.service` | 克隆 `/dev/dri/renderD129`（与 D128 同 major/minor） |
| `ai_manager.service` | active |
| `/sys/kernel/debug/rknpu/version` | `RKNPU driver: v0.9.8` |
| `NPU load` | `0%`（节点可读） |
| solib | `/vol1/@sysappmeta/ai-manager/solib/{librknnrt,librknn_api}.so` |
| DB `ai_manager.kv` | 无 `setting-hardware-id`（校验未通过故不落库） |

**根因（证据链，非“驱动没装”）**

1. **内核 NPU 半残**：`dmesg` 与论坛 RK3566/3568 同型：  
   `error -EBUSY: can't request region for resource [mem 0xfde40000-0xfde4ffff]`，  
   同时 `soc version=0, speed=0`、`Failed to get specification_serial_number`。  
   驱动能报版本，但资源申请失败，官方相册校验过不去。
2. **飞牛校验 ≠ 有 librknnrt**：`ai_manager` 二进制含 `IsRkNPU` / `IsRknnRuntimeReady` / `GetRknnRuntimeRootDir` / `ValidateHardware`，并期望应用态路径  
   `/var/apps/trim.ai-runtime-%s/target`（硬编码可见完整套件仅 `trim.ai-runtime-amd-migraphx`）。  
   本机 `/var/apps` **无** Rockchip/RKNPU 对应 `trim.ai-runtime-*` 套件。
3. **模型全是 OpenVINO/ONNX**：`face` / `img2vec` / `txt2vec` 仅有 `.onnx/.xml/.bin`，无 `.rknn`；虽有 `--rknn_model` 参数字符串，当前未下发 NPU 模型包。
4. **推理后端无 NPU EP**：内置 Python `onnxruntime.get_available_providers()` → 仅  
   `AzureExecutionProvider` / `CPUExecutionProvider`。
5. **机型定位**：`BDY-G18 AX3000 Router` / `bdy,g18-pro` + `rockchip,rk3568`，非官方优先的 NPU 相册 SKU；社区结论多为「要指定 AI 套件 / Immich-rknn 旁路」。

**结论**

- 用户态 runtime + 自启 + D129 克隆：**已做好「给 RKNN 应用用的地基」**。  
- 飞牛原生相册 UI「RKNPU 调用成功」：**本机当前无法靠继续拷库/造节点骗过**；需官方对 3568/G18 打开完整 NPU + 下发 AI runtime/模型套件，或改走旁路。

**可行路径**

| 路径 | 说明 |
|---|---|
| A. 关硬件加速，用 CPU 跑相册 AI | 功能可用，慢但稳 |
| B. Docker Immich `*-rknn` | 挂 `/dev/dri`，数据建议放 `/vol2`；社区 ARM 成功路径 |
| C. 等官方 AI 套件 / 内核修复 EBUSY | 观察更新与论坛「指定 AI 套件」包 |

## 对照视频（BV1KD3Y6qEXj · 2026-07-31）

来源：[程亮折腾TIME · 云塔G16机箱 / 农商云G16＆彼度云G18 改四盘位](https://www.bilibili.com/video/BV1KD3Y6qEXj/)  
水印「距离一台真正的多盘NAS」与该 UP 系列一致；硬件与本机同系：**彼度云 G18 Pro / RK3568**（`fdtfile=rockchip/rk3568-bdy-g18-pro.dtb`，fnOS `1.2.0302`）。

视频主体是机箱改造，不是 NPU 教程；评论区仍有人问「vpu npu 驱动都正常吗」。若成功绿勾截图出自此片，说明**同款机型上原生校验可以过**，本机失败更宜归因于运行时半残，而非「芯片根本不支持」。

### 本机 DRM 映射（纠正社区「D128=VPU / D129=NPU」套话）

| 节点 | 本机事实 |
|---|---|
| `/dev/dri/by-path/platform-fde40000.npu-render` | → `renderD128` |
| `renderD128` 的 sysfs `uevent` | `DRIVER=RKNPU`，设备即 `fde40000.npu` |
| `renderD129` | 仅有我们 `mknod` 出的重复节点（同 226,128），**不在** `/sys/class/drm/`，也无 by-path |
| DTB `npu@fde40000` | `status = "okay"`（已开启，不是 disabled） |
| `/proc/iomem` | **没有** `fde40000-fde4ffff`（与 `EBUSY: can't request region` 一致） |

结论：克隆 D129 只能骗「有没有这个文件名」，骗不过 MMIO 未拿到 + `soc version=0` 的运行时校验。

## EBUSY 深挖（谁占了 NPU 寄存器）

**结论（证据链）**：不是神秘第三方驱动独占了 `0xfde40000`，而是 **`rk_iommu`（`fde4b000.iommu`）先占了落在 NPU 64KB 窗口内部的子区间**，导致 RKNPU 再 `request_mem_region(0xfde40000-0xfde4ffff)` 时返回 `-EBUSY`。

| 证据 | 内容 |
|---|---|
| NPU DT `reg` | `0xfde40000` size `0x10000` → 覆盖到 `0xfde4ffff` |
| IOMMU DT `reg` | `0xfde4b000` size `0x40` → **落在上述窗口内**（偏移 +0xb000） |
| `/proc/iomem` | 有 `fde4b000-fde4b03f : fde4b000.iommu`；**没有** `fde40000-fde4ffff` |
| NPU sysfs | `fde40000.npu` **无** `resource*`（独占 MMIO 从未登记成功） |
| 驱动绑定 | 仅 `RKNPU` 绑定该设备；无第二驱动抢同一 platform 设备 |
| 后果 | `soc version=0, speed=0`、读不到 leakage/serial；DRM 仍半初始化（`renderD128`） |
| 社区对照 | 同款 EBUSY 见于飞牛/Armbian 356x；mainline 草案把 NPU 拆成多段小 `reg`，避免整窗 64KB 盖住 IOMMU |

**5-Why**：相册红字 ← 校验读硬件失败 ← `soc version=0` ← MMIO 申请失败 ← **IOMMU 子区间与 NPU 大窗口重叠**。

### 已落地修复（2026-07-31 · 用户授权试修）

对 `/boot/dtb/rockchip/rk3568-bdy-g18-pro.dtb` 做了两处修改（均有备份）：

| 改动 | 内容 | 结果 |
|---|---|---|
| NPU `reg` | `0x10000` → `0xb000`（止于 IOMMU 前） | **EBUSY 消失**；`/proc/iomem` 出现 `fde40000-fde4afff : npu` |
| OTP `clock-names` | `usr/sbpi/apb/phy` → `otp/sbpi/apb_pclk/phy` | **rockchip-otp 绑定成功**；出现 `rockchip-otp0`；RKNPU 读到 `leakage=3`、`pvtm`、`speed=1` |

备份目录示例：
- `/vol1/1000/npu-dtb-backup-20260731-153132/`（NPU reg）
- `/vol1/1000/npu-dtb-backup-otp-20260731-153624/`（OTP clocks）

回滚：
```bash
sudo cp -a /vol1/1000/npu-dtb-backup-20260731-153132/rk3568-bdy-g18-pro.dtb.orig \
  /boot/dtb/rockchip/rk3568-bdy-g18-pro.dtb && sync && sudo reboot
```

**注意**：系统 OTA/内核更新可能覆盖 dtb，需复查。`soc version=0` 在本机 GPU 上同样出现，可能为硅信息字段常态，不单独当作失败。原生相册 UI 是否绿勾需在 Web 刷新验证。

## 续轮 · UI 仍「调用失败」深挖（2026-07-31 下午）

**现象复验**：内核侧 EBUSY/OTP 已修好后，相册选 RKNPU 仍红字「调用失败，请检查是否已安装显卡驱动」。

### 新证据

| 项 | 结果 |
|---|---|
| 文案对应码 | 前端 `code===20802` → `gpuCallFailedCheckDriver`（通用失败，不是 AMD 专用 20807） |
| 校验入参 | `POST /p/api/v1/ai-base/validate-gpu`，body 字段为 **`gpuId`**（不是 hardwareId） |
| 下游 | photos → `ai_manager` Unix：`/v1/setting/gpus`、`/v1/setting/validate-gpu`（需 trimrpc 签名；裸调返回 `invalid sign`） |
| SoC 解析 | `/proc/device-tree/compatible` = `rockchip,rk3568` → 期望根目录 `/var/apps/trim.ai-runtime-rk3568/target` |
| 库探测串 | `…/target` + `/ld_lib/librknnrt.so`（`ai_manager` 内明文） |
| NPU DRM | 仍为 `renderD128`=`RKNPU`；`renderD129` 仅有克隆节点、无 sysfs（对当前 `gpu_verify`/`ai_manager` 字符串检索 **无** `renderD129` 字面量） |
| 已造 runtime 桩 | `/var/apps/trim.ai-runtime-rk3568` 按 app 样式：`target`→`/usr/local/apps/@appcenter/.../ld_lib/librknnrt.so`（`LOAD_OK`） |
| `kv` | 仍无 `setting-hardware-id`（校验未成功落库） |
| CDN | `static.fnnas.com/ai_v2/solib-1.0.0.tgz` → 403（需签名 URL）；本机无官方 Rockchip `trim.ai-runtime-*` 应用中心包 |

### 结论更新

- **内核 NPU**：已可用（iomem 有 npu 窗口、leakage/pvtm 正常、`rknn_server` active）。  
- **用户态地基**：`librknnrt` + 期望路径桩已就位，仍 **不足以** 让原生相册绿勾。  
- **卡点**：官方 AI runtime/模型套件未下发 + `ValidateHardware` 业务校验（需登录态/`gpuId` 真调用才能看到详细 `msg`）；应用中心未见 3568 对应 `trim.ai-runtime-*` 正式包。

### 建议下一步（需你选）

1. **你在 Web 点一次「校验/保存 RKNPU」**，我同步 `strace`/`journal` 抓 `ai_manager` 真实打开的路径与返回码。  
2. **旁路 Immich `*-rknn`**：挂 `/dev/dri`，数据放 `/vol2`（实效优先）。  
3. **先关硬件加速走 CPU AI**：功能可用、偏慢。

## 对照 · GitHub ophub#3496（2026-07-31）

来源：[ophub/amlogic-s9xxx-armbian#3496](https://github.com/ophub/amlogic-s9xxx-armbian/issues/3496)  
场景：Armbian + RK3566 + **Immich RKNN**；内核 6.1.141 丢 `renderD129` / `CONFIG_ROCKCHIP_RKNPU_DRM_GEM` 未开；容器硬编码要 `-v renderD129` 与 `compatible`。

| 对照项 | #3496 | 本机飞牛 G18 |
|---|---|---|
| 系统 | Armbian rk35xx 6.1.x | fnOS `6.18.18.c951-trim` |
| 应用 | Immich + `.rknn` | 原生相册 / `ai_manager`（ONNX） |
| 失败形态 | 容器缺 `renderD129` / 打不开 rknpu | UI `20802` 校验失败 |
| DRM | 坏内核时常只剩 panfrost；好内核 NPU 占 card/render | **已有** `card0`+`renderD128`=`RKNPU`，`card1`=显示 |
| D129 | Immich 硬编码节点名 | 仅有我们克隆的 `/dev` 节点，**无** sysfs |
| 可借鉴 | Immich 部署时挂 `renderD128→129` + `compatible`；社区确认 356x+Immich-rknn 可行 | **不能**用 Armbian `armbian-update` 修飞牛闭源内核；也**解释不了**原生相册绿勾 |

**结论**：对「原生飞牛相册绿勾」参考价值有限；对「下一步上 Immich-rknn」参考价值高。

## 里程碑 · 原生 RKNPU 校验已通过（2026-07-31 16:19）

用户反馈「映射成功了」。主机侧证据：

```text
setting-hardware-id | npu-1-RKNPU | 2026-07-31 16:19:15
```

此前该键不存在（校验失败不落库）。同秒 `ai_manager` 重启了 face_det / gpu_verify / img2vec / txt2vec / cluster。  
配套仍在：`rknn_server` active、`ensure-renderd129` active、`trim.ai-runtime-rk3568/target/ld_lib/librknnrt.so` 存在、NPU DRM=`renderD128`。

**下一步**：在相册跑人脸/智能搜索做业务冒烟；观察 NPU load 是否非 0。

### 用户确认「调用成了」（同日）

Web UI 已显示调用成功。复验（仍成立）：

| 检查 | 结果 |
|---|---|
| `setting-hardware-id` | `npu-1-RKNPU` @ 16:19:15 |
| `rknn_server` / `ensure-renderd129` / `ai_manager` | active |
| NPU 真源 | `by-path/...npu-render` → `renderD128`，driver v0.9.8 |
| runtime 库 | `trim.ai-runtime-rk3568/target/ld_lib/librknnrt.so` OK |
| NPU load | `0%`（未跑任务时正常） |

成功链路回顾：DTB 修 EBUSY+OTP → 用户态 rknpu2 1.5.2 + 自启 → runtime 路径桩 → UI 校验通过落库。

## 任务面板「0/518 · 准备中」诊断（2026-07-31 ~16:25–16:30）

UI：视频增强识别/智能识别「准备中」；人脸识别「识别中 0/518」。

### 主机证据

| 项 | 结果 |
|---|---|
| `user_photo` 总数 | **518**（与 UI 分母一致） |
| `face_task_log` | **28 → 29 / 10s**（在前进；约 ~6 张/分，粗算全库数小时级） |
| `face` 检出 | 2 条（多数照片无人脸属正常） |
| 进程 | `trim.face_det` ~75% CPU；`trim.txt2vec` ~120% CPU；**无** `img2vec` |
| NPU load | **持续 0%** |
| face_det 启动命令 | `--model_format ONNX --rec_model …/rec.onnx --det_model …/det.onnx --num_threads_onnx 1`（无 `--det_rknn_model`） |
| txt2vec 启动命令 | `--device CPU --model_format ONNX` |
| 模型目录 | 仅有 `.onnx` + OpenVINO `.xml/.bin`；**全机无 `*.rknn`** |
| 二进制能力 | `face_det` 含 `insight_rknn.so` 与 `--det_rknn_model`/`--rec_rknn_model` 字面量，但当前未启用 |
| txt2vec 日志 | 持续 `call txt2vec` 灌中文标签（智能识别前置向量化） |
| UI「0/518」 | 与 DB `face_task_log=29` **不同步**（UI 滞后或只按检出/另一计数刷新） |

### 结论

1. **不是卡死**：人脸任务在 CPU/ONNX 路径缓慢推进；绿勾 ≠ 推理走 NPU。  
2. **智能识别「准备中」**：`txt2vec` 在建标签向量；`img2vec` 尚未拉起（大模型 ~346MB ONNX，预计标签阶段后再启动）。  
3. **原生相册当前无法靠选 RKNPU 切到真 NPU 推理**：缺官方 `.rknn` 模型包；要 NPU 实效仍看 Immich `*-rknn` 或官方后续 AI 包。

### 仪表盘复验（同日 ~16:32）

用户截图：CPU 56%→79%、NPU 0%。主机对照：

| 项 | 16:32 采样 |
|---|---|
| `face_det` / `img2vec` | ~74% / ~123% CPU；**皆 ONNX + `--device CPU`** |
| `txt2vec` | 已退出（标签向量阶段结束） |
| `face_task_log` | **43**/518（较 16:29 的 29 继续前进） |
| `*.rknn` 数量 | **0** |
| NPU load / 仪表盘 NPU | **0%**（与截图一致） |
| `img2vec` boot | `16:31:18` 拉起，仍 `--device CPU --model_format ONNX` |

## 回滚用户态驱动（2026-07-31 ~16:38 · 为他人驱动包让路）

用户要求恢复到「没装我们这套驱动」的状态。已卸用户态；**保留 DTB 修复**（NPU `reg=0xb000` + OTP clocks），备份仍在 `/vol1/1000/npu-dtb-backup-*`。

### 已删除 / 已停

| 项 | 动作 |
|---|---|
| `rknn_server.service` / `ensure-renderd129.service` | disable --now + 删单元 |
| `/usr/bin/rknn_server`、`start/restart_rknn.sh` | 删除 |
| `/usr/lib/librknnrt.so`、`librknn_api.so` | 删除 |
| `ai-manager/solib/librknn*.so` | 删除 |
| `/dev/dri/renderD129` 克隆节点 | 删除 |
| `/var/apps` + `@appcenter` + `@appmeta` 的 `trim.ai-runtime-{rk3568,rk3588,rknn,rockchip}` 桩 | 删除 |
| `ai_manager.kv` `setting-hardware-id=npu-1-RKNPU` | 删除 |

### 刻意保留

- 内核 `rknpu` 模块 / DRM `renderD128`（飞牛自带，非我们安装包）
- DTB 修复与备份目录
- 官方 AI 模型包（ONNX）与相册业务数据
- ~~源码树 ` /root/rknpu2`~~ → **已删**（同日续清，约 1.2G）

### 验证（回滚后）

- 单元 not found；无 `rknn_server` 进程；无 `librknn*` / runtime 桩 / D129
- `RKNPU driver: v0.9.8` 仍在（内核）；iomem npu 窗口仍为修复后范围

### 续清 · git clone（同日）

用户补充：前面还有 `git clone`。已删除：

| 路径 | 大小 | 结果 |
|---|---|---|
| `/root/rknpu2` | 1.2G | `rm -rf` → `CLONE_GONE_OK` |

未删（非 clone / 属回滚备份）：`/vol1/1000/npu-dtb-backup-*`、`/home/TTFN/npu-dtb-backup/`。

### 续清 · 卸载残留（同日 ~16:44）

用户要求把乱七八糟残留也删掉。已删：

| 残留 | 结果 |
|---|---|
| `/vol1/1000/npu-dtb-backup-20260731-153132` + `-otp-…`（一对备份） | 删除 |
| `/home/TTFN/npu-dtb-backup` | 删除 |
| `@appconf/@appdata/@apptemp/@apphome/@appmeta/trim.ai-runtime-rk3568` 空目录 | 删除 |
| `/tmp/fn_*.sh`、`/tmp/fn_task_*.py` 等临时脚本 | 删除 |
| 空 `ai-manager/solib` | rmdir |

仍保留：内核 `rknpu.ko`、当前启动用 `/boot/dtb/rockchip/rk3568-bdy-g18-pro.dtb`（仍为修复版）、`Photos`、官方 AI 模型。  
**注意**：DTB 原厂备份已不在盘上；若要回原厂 DTB，需另找备份或重装/官方包。

### 续清 · 启动 DTB 改回原厂（同日）

用户要求把启动 DTB 改回原厂。盘上 `.orig` 已删，按当时改动 **逆向还原** 后写入 boot：

| 项 | 还原 |
|---|---|
| NPU `reg` | `0xb000` → **`0x10000`** |
| OTP `clock-names` | `otp/sbpi/apb_pclk/phy` → **`usr/sbpi/apb/phy`** |
| 安装路径 | `/boot/dtb/rockchip/rk3568-bdy-g18-pro.dtb`（`fdtfile` 指向此文件） |
| 文件大小 | **78434**（与当时 `.orig` 记录一致；修复版曾为 78438） |
| 当前 SHA256 | `8ca5a11123b81368d7470d195a612b114630de63dcc9637940324d5a8ea63c82` |
| 修复版备份（可再装回） | `/vol1/1000/rk3568-bdy-g18-pro.dtb.fixed-before-factory-20260731` |

**未重启**：运行中内核仍用内存里旧 DTB；重启后才会吃到原厂树（可能再现 NPU EBUSY / OTP 绑不上）。

### 官方包 `trim.ai-runtime-rk3568.fpk`（同日 ~16:57–17:22）

用户经迅雷下载官方 FPK（约 1.92GB）。应用中心解包后 `install_callback` 因联网拉 numpy 卡住；杀进程后中心回滚。随后从残留 `…-tpk/app.tgz` **手工重装**并离线装 wheels。

| 项 | 结果 |
|---|---|
| 路径 | `/var/apps/trim.ai-runtime-rk3568` → `target`=`/vol1/@appcenter/trim.ai-runtime-rk3568` |
| `librknnrt.so` | `/usr/lib` + `target/ld_lib`，`ctypes` **LOAD_OK** |
| `.rknn` 模型 | `face_det/rec/genderage`、`img2vec_{medium,large}`、`txt2vec_{medium,large}`（均 `*.rk3568.rknn`） |
| penv | `rknn-toolkit-lite2 2.3.2` + `psutil`；`from rknnlite.api import RKNNLite` → **RKNNLite_ok** |
| `cmd/main status` | **0** |
| appcenter DB | **尚未登记**该应用行（UI 可能不显示已安装；路径侧相册探测仍可用） |

**注意**：启动 DTB 当前是原厂向文件；真要跑 NPU 可能仍需把修复版 DTB 装回并重启。

### 卸载残余扫描 + UI 卡 55% + 重启后 EBUSY（同日 ~17:26–17:31）

主机曾重启（uptime≈0）。扫描结论：

| 项 | 状态 |
|---|---|
| 自建 `rknn_server` / `ensure-renderd129` / `renderD129` / `/root/rknpu2` / `/tmp/fn_*` | **已无** |
| 误下的 `trim.ai-runtime-rk3588-*.pkg`（45M） | **已删** |
| 官方引擎落盘 + models + penv | **保留**（`cmd/main status`=0） |
| `…-tpk/` 安装缓存（含 2.0G `app.tgz`） | **保留**（便于重装；可装完后清） |
| appcenter DB | **仍无** `trim.ai-runtime-rk3568` 行 |
| UI「安装中」 | 日志：`17:01` install → `17:13` rollback `error=10238`；当前无 pip/callback 进程（进度条僵尸） |
| 官方 `install_callback` | 原版 `pip install $file` **会联网**拉依赖 → 易卡；已改为 offline `--no-index --find-links` |
| 重启后 NPU | 原厂 DTB → 再现 `EBUSY` + OTP `clk 'otp'` 失败；**已把修复版 DTB 写回 boot**（SHA `8943f420…`），**需再重启**才生效 |

### 「NPU 没反应」根因闭环（同日 ~17:34–17:41）

监控页：CPU 高、NPU 0%。证据链：

1. 上次开机仍吃原厂 DTB → EBUSY；写回修复 DTB 后**再重启**，dmesg **无 EBUSY**，NPU 正常取 leakage/pvtm。
2. 相册曾尝试 RKNN，但 `img2vec` 报 `ModuleNotFoundError: No module named 'psutil'`（egg 安装，frozen 二进制只认 `PYTHONPATH=site-packages` 平铺包）→ 回退 ONNX/CPU → NPU 0%。
3. 将 `psutil` **平铺复制**到 `…/site-packages/psutil/` 后，手工拉起：
   `RKNN model loaded and runtime initialized` + `HTTP RPC socket ready`。
4. appcenter DB 已插入 `trim.ai-runtime-rk3568`（id=7），相册已 `appcenter-cli start`。

## 后续可选

- 飞牛 Web：相册可先关硬件加速验证 CPU AI  
- 若要 NPU 实效：部署 Immich `*-rknn` 并挂载 `/dev/dri`  
- 用业务程序 / `rknn_toolkit_lite2` 实测推理（真 `.rknn` 冒烟仍 NOT RUN）  
- 官方仓路径以 `runtime/RK356X/Linux/...` 为准  
- **不要**伪造 `setting-hardware-id` 骗 UI：校验失败时服务仍可能崩
- 装别人驱动包前先确认是否自带 `librknnrt`/`rknn_server`；若其包要求原厂 DTB，再用备份回滚 DTB 并重启


### UI 确认 NPU 有负载（同日 ~17:44）

监控截图 NPU **22%**；对照 `face_det`/`img2vec` 曾为 `model_format:RKNN`，`face_det` socket ready；dmesg 无 EBUSY。

---

## 续查 · `rknn_server` 缺失（2026-08-03）

用户在清理代理残留时发现 `/usr/bin/rknn_server` 不存在，要求继续排查。

### 5-Why（证据）

1. 现象：`/usr/bin/rknn_server` 不存在；`rknn_server.service` unit not found；无进程。
2. 非本次 Mihomo/clash 清理误删：clash 清理只触及用户态代理路径与 `/root/clash-*`，未执行任何 `rm /usr/bin/rknn*`。
3. 文档与历史：`2026-07-31 ~16:38` **用户明确要求**「回滚用户态驱动、为他人驱动包让路」，已主动 `disable --now` + 删除 `rknn_server` 二进制与单元（见上文「回滚用户态驱动」表）。
4. 后续官方路径：已安装 `trim.ai-runtime-rk3568`，走 **RKNNLite + `librknnrt.so` + `*.rk3568.rknn`**，不依赖独立 `rknn_server` 守护进程。
5. 故「缺失」是**预期终态**，不是意外损坏。

### 2026-08-03 复验

| 检查 | 结果 |
|---|---|
| `rknn_server` 二进制 / systemd 单元 / `librknn_api.so` | **ABSENT**（符合回滚） |
| `/usr/lib/librknnrt.so` vs `trim.ai-runtime-rk3568/ld_lib` | SHA256 一致 `d31fc19c…` |
| `*.rknn` 模型数量 | **7** |
| `cmd/main status` | exit **0** |
| `from rknnlite.api import RKNNLite` + `ctypes.CDLL(librknnrt)` | **OK** |
| 内核 `RKNPU driver` | **v0.9.8**；`/dev/dri/renderD128` 在；NPU load 采样 **0%**（空闲） |
| `ai_manager.service` | **active** |

### 结论与建议

- **不必为了「有个 rknn_server 文件」而强行恢复**；当前官方相册 AI runtime 不靠它。
- 若后续要 Immich `*-rknn`、USB 调试传输或其它强依赖 `rknn_server` 的栈，再从 `rknpu2` 的 `runtime/RK356X/Linux/rknn_server/...` 最小恢复并加 systemd（需与官方 `trim.ai-runtime-rk3568` 版本协调，避免双栈互踩）。
