/* OneSo Hub — TempRoot only (so factory lives on GitHub) */
(function () {
  const $ = (id) => document.getElementById(id);
  const logEl = $("logPanel");
  const summary = $("checkSummary");
  const list = $("checkList");

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
        ? "体检通过。可以预览或一键临时 Root。"
        : overall === "warn"
          ? "未完全就绪：可先预览；执行前请连上设备并确保有匹配 so。"
          : "扫描中…";
  }

  function enableActions(ready) {
    $("btnTempDry").disabled = !ready;
    $("btnTempRun").disabled = !ready;
  }

  async function api(method, ...args) {
    if (!window.pywebview || !window.pywebview.api) {
      throw new Error("pywebview bridge 未就绪");
    }
    return window.pywebview.api[method](...args);
  }

  async function boot() {
    summary.className = "check-summary is-scan";
    summary.textContent = "扫描中…";
    appendLog("[boot] start");
    try {
      const st = await api("status");
      setChip($("adbChip"), st.adb_label, st.adb_ok ? "ok" : "warn");
      setChip($("soChip"), st.so_label, st.so_ok ? "ok" : "warn");
      $("footerMeta").textContent = st.footer || "temp-root only";
      renderChecks(st.checks, st.overall);
      enableActions(true);
      appendLog(st.log || "[boot] ok");
    } catch (e) {
      enableActions(false);
      renderChecks([{ name: "bridge", ok: false, detail: String(e) }], "warn");
      appendLog("[boot] FAIL " + e);
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
      "确认在已连接设备上执行一键临时 Root？\n会 push so 并跑多轮 LD_PRELOAD（可能数分钟）。",
    );
    if (!ok) return;
    await runAction("temp-root run", () => api("temp_root", true));
  });

  window.addEventListener("pywebviewready", () => boot());
  setTimeout(() => {
    if (window.pywebview && window.pywebview.api) boot();
  }, 400);
})();
