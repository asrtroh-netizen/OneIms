/* OneRoot — fetch HTTP API（不依赖 pywebview js_api） */
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

  async function apiGet(path) {
    const r = await fetch(path, { cache: "no-store" });
    if (!r.ok) throw new Error("HTTP " + r.status + " " + path);
    return r.json();
  }

  async function apiPost(path, body) {
    const r = await fetch(path, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body || {}),
      cache: "no-store",
    });
    if (!r.ok) throw new Error("HTTP " + r.status + " " + path);
    return r.json();
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
      await apiGet("/api/ping");
      const st = await apiGet("/api/status");
      setChip($("adbChip"), st.adb_label, st.adb_ok ? "ok" : "warn");
      setChip($("soChip"), st.so_label, st.so_ok ? "ok" : "warn");
      setChip($("versionChip"), "OneRoot", "muted");
      $("footerMeta").textContent = st.footer || "OneRoot";
      renderChecks(st.checks, st.overall);
      enableActions(true);
      appendLog(st.log || "[boot] ok");
    } catch (e) {
      enableActions(false);
      setChip($("adbChip"), "adb · ?", "warn");
      setChip($("soChip"), "so · ?", "warn");
      renderChecks(
        [{ name: "本地 API", ok: false, detail: String(e) }],
        "warn",
      );
      summary.className = "check-summary is-warn";
      summary.textContent = "本地服务未响应，请重开 OneRoot 或点「开始体检」。";
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
    runAction("preview", () => apiPost("/api/temp-root", { run: false })),
  );
  $("btnTempRun").addEventListener("click", async () => {
    const ok = window.confirm(
      "确认一键持久化？\n会从 GitHub 取 so → 临时提权 → 便于写入运营商配置（可能数分钟）。\n无需解锁 Bootloader。",
    );
    if (!ok) return;
    await runAction("persist", () => apiPost("/api/temp-root", { run: true }));
  });

  window.__onerootBoot = boot;
  boot();
  setTimeout(() => boot(), 800);
})();
