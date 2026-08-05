/* OneRoot — fetch HTTP API（不依赖 pywebview js_api） */
(function () {
  const $ = (id) => document.getElementById(id);
  const logEl = $("logPanel");
  const summary = $("checkSummary");
  const list = $("checkList");
  const progressPanel = $("progressPanel");
  const progressBar = $("progressBar");
  const progressStage = $("progressStage");
  const progressPct = $("progressPct");
  let booting = false;
  let lastJobLog = "";

  function appendLog(text) {
    if (!logEl) return;
    const line = String(text || "").trimEnd();
    logEl.textContent = (logEl.textContent + "\n" + line).slice(-8000);
    logEl.scrollTop = logEl.scrollHeight;
  }

  function setLog(text) {
    if (!logEl) return;
    logEl.textContent = String(text || "");
    logEl.scrollTop = logEl.scrollHeight;
  }

  function showProgress(show) {
    if (!progressPanel) return;
    progressPanel.hidden = !show;
  }

  function setProgress(percent, stage) {
    const pct = Math.max(0, Math.min(100, Number(percent) || 0));
    if (progressBar) progressBar.style.width = pct + "%";
    if (progressPct) progressPct.textContent = pct + "%";
    if (progressStage) progressStage.textContent = stage || "进行中…";
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

  function sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  async function pollJob(name) {
    lastJobLog = "";
    showProgress(true);
    setProgress(1, name === "temp-root" ? "开始一键临时 Root…" : "生成预览…");
    if (summary) {
      summary.className = "check-summary is-scan";
      summary.textContent =
        name === "temp-root"
          ? "正在执行一键临时 Root，请看进度条与日志（可能数分钟）。"
          : "正在生成预览计划…";
    }
    for (;;) {
      const st = await apiGet("/api/job");
      setProgress(st.percent || 0, st.stage || "进行中…");
      if (typeof st.log === "string" && st.log !== lastJobLog) {
        setLog(st.log);
        lastJobLog = st.log;
      }
      if (st.done) {
        appendLog("[" + name + "] exit=" + (st.code ?? "?"));
        if (summary) {
          if (st.code === 0) {
            summary.className = "check-summary is-ok";
            summary.textContent =
              name === "temp-root"
                ? "临时 Root 流程结束（成功）。本窗不做运营商持久化。"
                : "预览完成。确认后可点「一键临时 Root」。";
          } else {
            summary.className = "check-summary is-warn";
            summary.textContent = "流程结束但未成功（exit=" + st.code + "）。详见日志。";
          }
        }
        return st;
      }
      await sleep(500);
    }
  }

  async function runAction(name, run) {
    enableActions(false);
    const btnBoot = $("btnBoot");
    if (btnBoot) btnBoot.disabled = true;
    appendLog("── " + name + " ──");
    try {
      const started = await apiPost("/api/temp-root", { run: !!run });
      if (!started.ok) {
        appendLog("[job] " + (started.error || "无法启动"));
        enableActions(true);
        return;
      }
      await pollJob(name);
      await boot();
    } catch (e) {
      appendLog("[" + name + "] ERROR " + e);
      showProgress(true);
      setProgress(100, "出错：" + e);
      enableActions(true);
    } finally {
      if (btnBoot) btnBoot.disabled = false;
    }
  }

  wireOpenUrls();

  const btnBoot = $("btnBoot");
  if (btnBoot) {
    btnBoot.addEventListener("click", () => boot());
    $("btnTempDry").addEventListener("click", () => runAction("preview", false));
    $("btnTempRun").addEventListener("click", async () => {
      const ok = window.confirm(
        "确认一键临时 Root？\n会从 GitHub 取 so 并跑 LD_PRELOAD（可能数分钟）。\n过程中会显示进度条与心跳日志。\n本窗不做运营商持久化。",
      );
      if (!ok) return;
      await runAction("temp-root", true);
    });
    window.__onerootBoot = boot;
    boot();
    setTimeout(() => boot(), 800);
  }
})();
