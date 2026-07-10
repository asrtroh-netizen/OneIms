# OneIms 📱✨

> **让 Pixel 和运营商重新学会沟通。**
> 一个面向 Google Pixel 的 IMS 配置、诊断与修复助手。
> 不靠玄学，不靠反复刷机，只想让隐藏的能力回到它应该存在的位置。

---

<div align="center">

**VoLTE · VoWiFi · VoNR · IMS Diagnostics · CarrierConfig · Android 17 Compatibility**

</div>

---

## 🤔 为什么会有 OneIms？

Google Pixel 是一台非常优秀的手机。

Tensor 芯片、纯净 Android、长期系统更新……

但是到了运营商网络环境里，经常会出现一些奇怪的问题：

* 📞 明明支持 VoLTE，却显示不可用；
* 📶 WiFi Calling 有硬件、有套餐，却无法注册；
* 🌎 海外运营商卡可以用，国内卡却需要额外调整；
* 🔧 Android 更新以后，旧方法突然失效；
* 🧐 设置里看不到原因，只能不停搜索论坛。

于是很多用户开始：

```
adb shell ...
改 CarrierConfig ...
刷模块 ...
重启测试 ...
再失败 ...
```

然后：

> “到底哪里出了问题？” 🤨

OneIms 就是为了解决这个问题。

它希望成为：

**Pixel 用户了解 IMS 状态、调整通信能力、排查运营商兼容问题的一站式工具。**

---

# 🌟 OneIms 可以做什么？

## 📡 IMS 能力管理

统一管理 Pixel 中隐藏的通信能力：

* ✅ VoLTE
* ✅ VoWiFi
* ✅ VoNR
* ✅ Enhanced 4G LTE
* ✅ Video Telephony
* ✅ WiFi Calling 模式

支持：

* 蜂窝优先
* WiFi 优先
* 仅 WiFi

并且所有修改都有状态检查。

---

## 🔍 IMS 深度诊断

OneIms 不只是告诉你：

> “支持 / 不支持”

而是尝试回答：

> “为什么不支持？”

诊断内容包括：

* 当前 SIM 信息
* MCC / MNC
* Carrier ID
* IMS 注册状态
* 当前注册技术：

```
LTE
IWLAN
NR
```

* ePDG 检测
* CarrierConfig 状态
* APN IMS 配置

让排障从：

“感觉应该是这里”

变成：

“问题发生在这里”。

---

# 🛡️ 通信安全优先

手机不是实验服务器。

电话、短信、数据永远优先。

所以 OneIms 有自己的“小脾气”：

> 可以不开 IMS，不能搞坏通信。

核心安全设计：

### 🚫 不碰危险区域

不会修改：

* ❌ 首选网络类型
* ❌ 蜂窝开关
* ❌ 基带参数
* ❌ 紧急通信相关配置

---

### 🔄 自动回滚

每次配置应用：

1. 写入配置；
2. 检查通信状态；
3. 发现异常；
4. 自动恢复。

因为：

> VoWiFi 可以晚点研究，电话不能失踪。😂

---

# 🧩 Android 16 / Android 17 新挑战

从 Android 新版本开始，Google 对 CarrierConfig 修改进行了更严格限制。

过去：

```
adb shell
↓
直接修改
↓
成功
```

现在：

```
App
↓
权限限制
↓
失败
```

很多旧方案因此失效。

OneIms 针对新的权限模型重新设计：

* Shizuku 权限桥接；
* Activity Instrumentation 代理；
* 最小权限委托；
* 写入后回读验证；
* 失败结果透明展示。

目标：

**不依赖 Root，也不假装拥有不存在的权限。**

---

# 📚 离线运营商数据库

OneIms 内置离线 APN 数据库。

包含：

* 🌍 全球运营商信息
* 📱 MCC/MNC 匹配
* 🛰️ IMS APN 候选
* 🧩 Carrier ID 辅助识别

特点：

✅ 不需要联网查询
✅ 本地搜索
✅ 不上传 SIM 信息
✅ 仅作为参考，不强制套用

因为：

运营商配置不是万能钥匙。

同一个 MCC/MNC：

可能有不同套餐。

所以 OneIms 更希望告诉你：

> “这里可能是答案”

而不是：

> “我觉得你应该这样改。” 😎

---

# 🎨 Pixel 原生体验

界面设计参考 Google Pixel Settings：

* Material 3
* 动态取色
* 深色 / 浅色模式
* 手机底部导航
* 大屏导航轨道
* 折叠屏适配

希望它看起来不像：

“一个黑乎乎的工程工具”

而像：

“系统里本来就应该存在的一项设置”。

---

# 📱 支持设备

目前重点：

## Google Tensor Pixel

支持方向：

* Pixel 6 / 6 Pro / 6a
* Pixel 7 / 7 Pro / 7a
* Pixel 8 / 8 Pro / 8a
* Pixel 9 系列
* Pixel 9 Pro Fold
* Pixel Fold
* Pixel Tablet
* 后续 Tensor 设备

系统：

```
Android 12
    ↓
Android 17
```

---

# 🧪 适合哪些人？

## 📡 通信爱好者

喜欢研究：

* IMS
* VoWiFi
* eSIM
* 全球运营商

---

## 🔧 Pixel 折腾玩家

喜欢：

* 解锁隐藏功能
* 研究系统行为
* 找出 Google 没告诉你的东西

---

## 🌎 国际用户

使用：

* 海外 SIM
* 国际漫游
* 多运营商环境

---

如果你只是：

“我要打电话”

那手机设置里开开就好。

如果你是：

> “为什么我的 Pixel 明明支持，但是它就是不给我用？”

欢迎来到 OneIms 😏

---

# 🚧 Roadmap

## 已完成

✅ IMS 能力控制
✅ CarrierConfig 分析
✅ IMS 注册检测
✅ ePDG 检查
✅ APN 离线数据库
✅ Android 17 兼容方案
✅ 安全回滚机制
✅ Pixel 风格 UI

---

## 计划中

🚀 更多运营商配置模板

🚀 自动诊断建议

🚀 更多 Android 设备支持

🚀 更完善的日志分析

🚀 社区运营商配置库

---

# ⚠️ Disclaimer

OneIms：

不是运营商官方软件。

不会：

❌ 破解运营商网络限制
❌ 修改基带能力
❌ 提供非法通信服务

它只是：

帮助用户理解自己的设备。

---

# 💬 最后的话

Android 很强大。

运营商网络也很复杂。

很多时候不是手机不支持，

只是：

**手机、系统、运营商之间少了一句正确的沟通。**

OneIms 希望做的事情很简单：

让这三者重新坐下来，好好聊聊。

---

Made with ☕
and countless times of:

> “为什么这个 IMS 又没注册？” 🤦‍♂️

**OneIms —— One device, one connection, one communication.**
