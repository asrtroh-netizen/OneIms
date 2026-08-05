/* OneRoot — single window; so from GitHub OneSo-assets */
(function () {
  const $ = (id) => document.getElementById(id);
  const logEl = $("logPanel");
  const summary = $("checkSummary");
  const list = $("checkList");
  let booting = false;

  function appendLog(text) {
    const line = String(text || "").trimEnd();
    logEl.textContent = (logEl.textContent + "\n" + line).slice(-8000);
    logEl.scrollTop = logEl.scrollHeight;
  }

  function setChip(el, text, kind) {
    el.textContent = text;
    el.className = "chip" + (kind ? " chip-" + kind : " chip-muted");
  }

  function renderChecks(items, overall) {
    list.innerHTML = "";
    for (const it of items || []) {
      const li = document.createElement("li");
      const name = document.createElement("span");
      name.className = "soft";
      name.textContent = it.name;
      const val = document.createElement("span");
      val.className = it.ok ? "ok" : "bad";
      val.textContent = it.detail;
      li.appendChild(name);
      li.appendChild(val);
      list.appendChild(li);
    }
    summary.className =
      "check-summary " +
      (overall === "ok" ? "is-ok" : overall === "warn" ? "is-warn" : "is-scan");
    summary.textContent =
      overall === "ok"
        ? "体检通过。可以预览或一键持久化（未解锁 Pixel 也能写运营商配置）。"
        : overall === "warn"
          ? "未完全就绪：请连上 Pixel，并确认 GitHub OneSo-assets 有匹配 so。"
          : "扫描中…";
  }

  function enableActions(ready) {
    $("btnTempDry").disabled = !ready;
    $("btnTempRun").disabled = !ready;
  }

  function sleep(ms) {
    return new Promise((r) => setTimeout(r, ms));
  }

  async function waitBridge(maxMs) {
    const deadline = Date.now() + (maxMs || 10000);
    while (Date.now() < deadline) {
      if (window.pywebview && window.pywebview.api) return true;
      await sleep(150);
    }
    return false;
  }

  async function api(method, ...args) {
    if (!(await waitBridge(2000))) {
      throw new Error("pywebview bridge 未就绪");
    }
    return window.pywebview.api[method](...args);
  }

  async function boot() {
    if (booting) return;
    booting = true;
    summary.className = "check-summary is-scan";
    summary.textContent = "扫描中…";
    setChip($("adbChip"), "adb · 扫描中", "muted");
    setChip($("soChip"), "so · 扫描中", "muted");
    appendLog("[boot] start");
    try {
      if (!(await waitBridge(10000))) {
        throw new Error("等待 bridge 超时（点「开始体检」重试）");
      }
      const st = await api("status");
      setChip($("adbChip"), st.adb_label, st.adb_ok ? "ok" : "warn");
      setChip($("soChip"), st.so_label, st.so_ok ? "ok" : "warn");
      setChip($("versionChip"), "OneRoot", "muted");
      $("footerMeta").textContent = st.footer || "OneRoot";
      renderChecks(st.checks, st.overall);
      enableActions(true);
      appendLog(st.log || "[boot] ok");
    } catch (e) {
      enableActions(false);
      setChip($("adbChip"), "adb · 桥接失败", "warn");
      setChip($("soChip"), "so · —", "warn");
      renderChecks([{ name: "bridge", ok: false, detail: String(e) }], "warn");
      appendLog("[boot] FAIL " + e);
    } finally {
      booting = false;
    }
  }

  async function runAction(name, fn) {
    enableActions(false);
    $("btnBoot").disabled = true;
    appendLog("── " + name + " ──");
    try {
      const r = await fn();
      appendLog(r.log || JSON.stringify(r));
      appendLog("[" + name + "] exit=" + (r.code ?? "?"));
      await boot();
    } catch (e) {
      appendLog("[" + name + "] ERROR " + e);
      enableActions(true);
    } finally {
      $("btnBoot").disabled = false;
    }
  }

  $("btnBoot").addEventListener("click", () => boot());
  $("btnTempDry").addEventListener("click", () =>
    runAction("temp-root dry", () => api("temp_root", false)),
  );
  $("btnTempRun").addEventListener("click", async () => {
    const ok = window.confirm(
      "确认一键持久化？\n会从 GitHub 取 so → 临时提权 → 便于写入运营商配置（可能数分钟）。\n无需解锁 Bootloader。",
    );
    if (!ok) return;
    await runAction("oneroot run", () => api("temp_root", true));
  });

  window.__onerootBoot = boot;
  window.addEventListener("pywebviewready", () => boot());
  // 多拍重试：事件早到 / 晚到都能刷芯片，避免一直卡在 …
  [300, 800, 1600, 3000].forEach((ms) => setTimeout(() => boot(), ms));
})();
