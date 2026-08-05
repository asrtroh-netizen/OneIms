/* OneRoot — fetch HTTP API（不依赖 pywebview js_api） */
(function () {
  const $ = (id) => document.getElementById(id);
  const logEl = $("logPanel");
  const summary = $("checkSummary");
  const list = $("checkList");
  let booting = false;

  function appendLog(text) {
    if (!logEl) return;
    const line = String(text || "").trimEnd();
    logEl.textContent = (logEl.textContent + "\n" + line).slice(-8000);
    logEl.scrollTop = logEl.scrollHeight;
  }

  function setChip(el, text, kind) {
    if (!el) return;
    el.textContent = text;
    el.className = "chip" + (kind ? " chip-" + kind : " chip-muted");
  }

  function renderChecks(items, overall) {
    if (!list || !summary) return;
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
        ? "体检通过。可以预览或一键临时 Root（本窗不做运营商持久化）。"
        : overall === "warn"
          ? "未完全就绪：请连上 Pixel，并确认 GitHub OneSo-assets 有匹配 so。"
          : "扫描中…";
  }

  function enableActions(ready) {
    const dry = $("btnTempDry");
    const run = $("btnTempRun");
    if (dry) dry.disabled = !ready;
    if (run) run.disabled = !ready;
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

  function wireOpenUrls() {
    document.querySelectorAll("[data-open-url]").forEach((el) => {
      el.addEventListener("click", async (ev) => {
        ev.preventDefault();
        const url = el.getAttribute("data-open-url") || "";
        try {
          const r = await apiPost("/api/open-url", { url });
          if (!r.ok) appendLog("[open-url] " + (r.error || "fail"));
        } catch (e) {
          // 无本地 API 时直接交给系统（浏览器打开静态页的场景）
          window.open(url, "_blank", "noopener");
        }
      });
    });
  }

  async function boot() {
    if (!summary || !list) return;
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
      const footerMeta = $("footerMeta");
      if (footerMeta) footerMeta.textContent = st.footer || "OneRoot";
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
    const btnBoot = $("btnBoot");
    if (btnBoot) btnBoot.disabled = true;
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
      if (btnBoot) btnBoot.disabled = false;
    }
  }

  wireOpenUrls();

  const btnBoot = $("btnBoot");
  if (btnBoot) {
    btnBoot.addEventListener("click", () => boot());
    $("btnTempDry").addEventListener("click", () =>
      runAction("preview", () => apiPost("/api/temp-root", { run: false })),
    );
    $("btnTempRun").addEventListener("click", async () => {
      const ok = window.confirm(
        "确认一键临时 Root？\n会从 GitHub 取 so 并跑 LD_PRELOAD（可能数分钟）。\n本窗不做运营商持久化。",
      );
      if (!ok) return;
      await runAction("temp-root", () => apiPost("/api/temp-root", { run: true }));
    });
    window.__onerootBoot = boot;
    boot();
    setTimeout(() => boot(), 800);
  }
})();
