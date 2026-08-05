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

  function setRootSuccess(ok, detail) {
    const banner = $("successBanner");
    const title = $("successTitle");
    const det = $("successDetail");
    if (banner) banner.hidden = !ok;
    if (title) title.textContent = ok ? "临时 Root 已就绪" : "临时 Root 未就绪";
    if (det) {
      det.textContent = ok
        ? detail ||
          "设备上已验证 uid=0。手机 OneIMS 首页应显示「临时 ROOT」徽标（永久 Root 才是黑金 ROOT）。"
        : detail || "尚未检测到可用临时 Root。";
    }
    const stale =
      !ok &&
      detail &&
      (detail.includes("僵尸") ||
        detail.includes("残留") ||
        detail.includes("daemon"));
    setChip(
      $("rootChip"),
      ok
        ? "临时 Root · 已就绪"
        : stale
          ? "临时 Root · 僵尸su"
          : "临时 Root · 未检测到",
      ok ? "root" : stale ? "warn" : "muted",
    );
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
    const adbOffline = (items || []).some(
      (it) =>
        it &&
        typeof it.name === "string" &&
        it.name.indexOf("adb") !== -1 &&
        !it.ok,
    );
    summary.textContent =
      overall === "ok"
        ? "体检通过。可以预览或一键临时 Root（本窗不做运营商持久化）。"
        : overall === "warn"
          ? adbOffline
            ? "未完全就绪：请重插数据线并允许 USB 调试，再点「开始体检」/「一键临时 Root」。"
            : "未完全就绪：请连上 Pixel，并确认本机/缓存或 OneSo-assets 有匹配 so。掉线时先重插线再一键。"
          : "扫描中…";
  }

  function enableActions(ready) {
    const dry = $("btnTempDry");
    const run = $("btnTempRun");
    const clean = $("btnCleanup");
    if (dry) dry.disabled = !ready;
    if (run) run.disabled = !ready;
    if (clean) clean.disabled = !ready;
  }

  async function runCleanup() {
    const log = $("logPanel");
    const clean = $("btnCleanup");
    if (clean) clean.disabled = true;
    try {
      // 安全清理默认：只杀挂起 exploit，保留可用 uid=0。
      // 取消后再确认才强力拆除 sock+su（禁止只拆 sock 留二进制 → 僵尸）。
      const safe = window.confirm(
        "清理残留（安全）\n\n只杀挂起的 LD_PRELOAD/id，保留当前可用临时 Root。\n\n确定？",
      );
      let aggressive = false;
      if (!safe) {
        const force = window.confirm(
          "改为强力拆除？\n\n会删除 temp_su.sock + /data/local/tmp/su，临时 Root 失效。\n" +
            "必须一起删，避免僵尸 su。\n\n确定强力拆除？",
        );
        if (!force) {
          if (log) log.textContent = "已取消清理";
          return;
        }
        aggressive = true;
      }
      if (log) {
        log.textContent = aggressive
          ? "正在强力拆除（sock + su 二进制）…"
          : "正在安全清理挂起 exploit（保留临时 Root）…";
      }
      const r = await apiPost("/api/cleanup", { aggressive });
      const lines = [
        r.ok ? "清理完成" : "清理未完全成功",
        "mode=" + (r.mode || "?"),
        "aggressive=" + aggressive,
        r.detail || "",
        ...(r.steps || []),
      ].filter(Boolean);
      if (log) log.textContent = lines.join("\n");
      await boot();
    } catch (e) {
      if (log) log.textContent = "清理失败：" + (e && e.message ? e.message : e);
    } finally {
      if (clean) clean.disabled = false;
    }
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
      if (location.protocol === "file:") {
        throw new Error(
          "请用「一键启动.cmd / OneRoot.ps1」启动，不要双击打开 html（file:// 没有本地 API）",
        );
      }
      await apiGet("/api/ping");
      const st = await apiGet("/api/status");
      setChip($("adbChip"), st.adb_label, st.adb_ok ? "ok" : "warn");
      setChip($("soChip"), st.so_label, st.so_ok ? "ok" : "warn");
      setChip(
        $("versionChip"),
        st.version ? "v" + st.version : "OneRoot",
        "muted",
      );
      setRootSuccess(!!st.root_ok, st.root_label || "");
      const footerMeta = $("footerMeta");
      if (footerMeta) footerMeta.textContent = st.footer || "OneRoot";
      renderChecks(st.checks, st.overall);
      enableActions(true);
      appendLog(st.log || "[boot] ok");
      if (st.diag && st.diag.dir) {
        appendLog("[diag] " + st.diag.dir);
      }
    } catch (e) {
      enableActions(false);
      setChip($("adbChip"), "adb · ?", "warn");
      setChip($("soChip"), "so · ?", "warn");
      setRootSuccess(false, "本地服务未响应");
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
                ? "临时 Root 成功。请看上方黑金成功条与「临时 Root · 已就绪」芯片。"
                : "预览完成。确认后可点「一键临时 Root」。";
            if (name === "temp-root") {
              setRootSuccess(true, "刚刚一键流程成功，正在复核设备 uid=0…");
            }
          } else {
            summary.className = "check-summary is-warn";
            summary.textContent =
              "流程结束但未成功（exit=" +
              st.code +
              "）。若 adb 掉线：重插线并允许调试后再点一键；详见日志。";
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

  async function exportDiag() {
    const btn = $("btnDiagExport");
    if (btn) btn.disabled = true;
    try {
      if (location.protocol === "file:") {
        throw new Error("请用一键启动打开（file:// 无 API）");
      }
      appendLog("[diag] 正在打包详细日志…");
      const r = await apiPost("/api/diag/export", { open: true });
      if (!r.ok) {
        appendLog("[diag] 导出失败：" + (r.error || "unknown"));
        return;
      }
      appendLog("[diag] 已导出：" + (r.zip || ""));
      appendLog("[diag] 把该 zip 发给作者即可排查");
    } catch (e) {
      appendLog("[diag] ERROR " + (e && e.message ? e.message : e));
    } finally {
      if (btn) btn.disabled = false;
    }
  }

  wireOpenUrls();

  const btnBoot = $("btnBoot");
  if (btnBoot) {
    btnBoot.addEventListener("click", () => boot());
    $("btnCleanup").addEventListener("click", () => runCleanup());
    $("btnTempDry").addEventListener("click", () => runAction("preview", false));
    $("btnTempRun").addEventListener("click", async () => {
      const ok = window.confirm(
        "确认一键临时 Root？\n开始前会自动清理残留；优先本机/缓存 so；LD_PRELOAD 期间并行验 su 早停。\n本窗不做运营商持久化。",
      );
      if (!ok) return;
      await runAction("temp-root", true);
    });
    const btnDiag = $("btnDiagExport");
    if (btnDiag) btnDiag.addEventListener("click", () => exportDiag());
    window.__onerootBoot = boot;
    boot();
    setTimeout(() => boot(), 800);
  }
})();
