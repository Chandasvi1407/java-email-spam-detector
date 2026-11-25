(() => {
  const SAMPLE_SPAM = [
    {
      sender: "security-alert@paypa1-verify.xyz",
      subject: "URGENT: Your Account Has Been Suspended!!",
      body: "Dear Customer,\n\nUnusual activity was detected on your account. Please verify your account immediately at http://bit.ly/verify-now-99 or your funds will be frozen within 24 hours!!!\n\nClick here to confirm your bank details and password now."
    },
    {
      sender: "claims@global-lottery-prize.win",
      subject: "CONGRATULATIONS YOU WON $2,500,000.00 JACKPOT!!!",
      body: "You are our lucky winner! Claim your prize cash payout now with 100% free processing. Act now limited time offer — visit https://claim-prize-portal.top/win to enter your wire transfer details!!"
    },
    {
      sender: "crypto-wealth@fastprofits.top",
      subject: "Double Your Bitcoin in 48 Hours - RISK FREE",
      body: "Join our automated crypto trading bot and earn guaranteed daily passive income! Limited time bonus: deposit $250 and receive $5,000 cash profit. Click here: www.fast-crypto-roi.top"
    }
  ];

  const SAMPLE_HAM = [
    {
      sender: "sarah.chen@acmecorp.com",
      subject: "Q4 Engineering Roadmap Review - Thursday 2:00 PM",
      body: "Hi team,\n\nPlease review the updated Q4 engineering milestones before Thursday's planning meeting. I added the sprint capacity breakdown and database migration timeline to our shared workspace.\n\nBest,\nSarah"
    },
    {
      sender: "notifications@github.com",
      subject: "[acmecorp/backend-api] Pull Request #412 merged by alex-dev",
      body: "alex-dev merged 3 commits into main from feature/rate-limiter.\n\nAll 48 unit tests and integration checks passed on CI. Release notes have been drafted for Friday's deployment window."
    },
    {
      sender: "appointments@brightsmile-dental.com",
      subject: "Appointment Reminder: Dental Cleaning Friday at 10:30 AM",
      body: "Hi Jordan,\n\nThis is a friendly reminder of your upcoming routine checkup with Dr. Patel this Friday at 10:30 AM. Please reply to this email or call our front desk if you need to reschedule."
    }
  ];

  let spamPresetIdx = 0;
  let hamPresetIdx = 0;
  let toastTimer = null;

  const els = {
    form: document.getElementById("analyze-form"),
    sender: document.getElementById("input-sender"),
    subject: document.getElementById("input-subject"),
    body: document.getElementById("input-body"),
    model: document.getElementById("select-model"),
    btnAnalyze: document.getElementById("btn-analyze"),
    btnClear: document.getElementById("btn-clear"),
    btnSampleSpam: document.getElementById("btn-sample-spam"),
    btnSampleHam: document.getElementById("btn-sample-ham"),
    btnTeachSpam: document.getElementById("btn-teach-spam"),
    btnTeachHam: document.getElementById("btn-teach-ham"),
    metricVocab: document.getElementById("metric-vocab"),
    metricSpam: document.getElementById("metric-spam"),
    metricHam: document.getElementById("metric-ham"),
    metricAnalyzed: document.getElementById("metric-analyzed"),
    resultEmpty: document.getElementById("result-empty"),
    resultContent: document.getElementById("result-content"),
    resultModelBadge: document.getElementById("result-model-badge"),
    verdictBadge: document.getElementById("verdict-badge"),
    probPercent: document.getElementById("prob-percent"),
    meterFill: document.getElementById("meter-fill"),
    signalsList: document.getElementById("signals-list"),
    highlightedPreview: document.getElementById("highlighted-preview"),
    historyCount: document.getElementById("history-count"),
    historyList: document.getElementById("history-list"),
    toast: document.getElementById("toast")
  };

  function showToast(message, type = "info") {
    els.toast.textContent = message;
    els.toast.className = `toast ${type}`;
    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
      els.toast.classList.add("hidden");
    }, 3400);
  }

  async function fetchStats() {
    try {
      const res = await fetch("/api/stats");
      if (!res.ok) throw new Error("Failed to load stats");
      const data = await res.json();
      renderStats(data);
    } catch (err) {
      showToast("Unable to reach Java API server.", "error");
    }
  }

  function renderStats(stats) {
    els.metricVocab.textContent = stats.vocabularySize ?? 0;
    els.metricSpam.textContent = stats.spamCount ?? 0;
    els.metricHam.textContent = stats.hamCount ?? 0;
    els.metricAnalyzed.textContent = stats.totalClassifications ?? 0;

    const history = Array.isArray(stats.recentHistory) ? stats.recentHistory : [];
    els.historyCount.textContent = `${history.length} ${history.length === 1 ? "entry" : "entries"}`;

    if (history.length === 0) {
      els.historyList.innerHTML = `<div class="history-empty">No emails analyzed in this session yet.</div>`;
      return;
    }

    els.historyList.innerHTML = "";
    history.forEach((item) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "history-item";

      const isSpam = item.predictedLabel === "SPAM";
      const pct = ((item.spamProbability || 0) * 100).toFixed(1);
      const timeText = formatTime(item.timestamp);

      btn.innerHTML = `
        <div class="history-item-top">
          <span class="history-subject">${escapeHtml(item.subject || "(No Subject)")}</span>
          <span class="mini-badge ${isSpam ? "spam" : "ham"}">${item.predictedLabel} ${pct}%</span>
        </div>
        <div class="history-meta">
          <span>${escapeHtml(item.sender || "unknown sender")} &bull; ${escapeHtml(item.modelName || "")}</span>
          <span>${escapeHtml(timeText)}</span>
        </div>
      `;

      btn.addEventListener("click", () => {
        els.sender.value = item.sender || "";
        els.subject.value = item.subject || "";
        els.body.value = item.bodyPreview || "";
        showToast("Loaded email summary from history.");
      });

      els.historyList.appendChild(btn);
    });
  }

  async function analyzeCurrentEmail() {
    const sender = els.sender.value.trim();
    const subject = els.subject.value.trim();
    const body = els.body.value.trim();
    const model = els.model.value;

    if (!subject && !body) {
      showToast("Please enter a subject or body to analyze.", "error");
      return;
    }

    setBusy(true);
    try {
      const res = await fetch("/api/classify", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ sender, subject, body, model })
      });
      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.error || "Classification failed");
      }
      renderResult(data, subject, body);
      await fetchStats();
    } catch (err) {
      showToast(err.message || "Error classifying email.", "error");
    } finally {
      setBusy(false);
    }
  }

  async function teachModel(label) {
    const sender = els.sender.value.trim();
    const subject = els.subject.value.trim();
    const body = els.body.value.trim();

    if (!subject && !body) {
      showToast("Enter or load an email before teaching the AI.", "error");
      return;
    }

    setBusy(true);
    try {
      const res = await fetch("/api/train", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ sender, subject, body, label })
      });
      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.error || "Training failed");
      }
      renderStats(data);
      const labelText = label === "SPAM" ? "SPAM" : "Not Spam (HAM)";
      showToast(`Trained live model as ${labelText}! Vocabulary: ${data.vocabularySize} tokens.`, "success");

      // Re-classify automatically if a result was already visible so user sees live probability shift
      if (!els.resultContent.classList.contains("hidden")) {
        await analyzeCurrentEmail();
      }
    } catch (err) {
      showToast(err.message || "Error updating model.", "error");
    } finally {
      setBusy(false);
    }
  }

  function renderResult(result, subject, body) {
    els.resultEmpty.classList.add("hidden");
    els.resultContent.classList.remove("hidden");

    const isSpam = result.predictedLabel === "SPAM";
    const pctValue = Math.max(0, Math.min(100, (result.spamProbability || 0) * 100));

    els.resultModelBadge.textContent = result.modelName || "AI Detector";
    els.verdictBadge.textContent = result.predictedLabel;
    els.verdictBadge.className = `verdict-badge ${isSpam ? "spam" : "ham"}`;

    els.probPercent.textContent = `${pctValue.toFixed(1)}%`;
    els.meterFill.style.width = `${Math.max(3, pctValue)}%`;
    els.meterFill.className = `meter-fill ${isSpam ? "spam" : "ham"}`;

    const signals = Array.isArray(result.matchedSignals) ? result.matchedSignals : [];
    els.signalsList.innerHTML = "";
    if (signals.length === 0) {
      const li = document.createElement("li");
      li.className = "signal-tag";
      li.textContent = "No prominent trigger signals detected.";
      els.signalsList.appendChild(li);
    } else {
      signals.forEach((sig) => {
        const li = document.createElement("li");
        const isHamSignal = sig.includes("ham token") || sig.includes("No heuristic");
        li.className = `signal-tag ${isHamSignal ? "ham-signal" : "spam-signal"}`;
        li.textContent = sig;
        els.signalsList.appendChild(li);
      });
    }

    els.highlightedPreview.innerHTML = buildHighlightedHtml(subject, body, signals, isSpam);
  }

  function buildHighlightedHtml(subject, body, signals, isSpam) {
    const terms = extractHighlightTerms(signals);
    const fullText = (subject ? `Subject: ${subject}\n\n` : "") + body;
    let escaped = escapeHtml(fullText);

    if (terms.length === 0) {
      return escaped;
    }

    const sorted = [...terms].sort((a, b) => b.length - a.length);
    const pattern = sorted.map(escapeRegex).join("|");
    if (!pattern) return escaped;

    const regex = new RegExp(`(${pattern})`, "gi");
    const cls = isSpam ? "hl-spam" : "hl-ham";
    return escaped.replace(regex, `<mark class="${cls}">$1</mark>`);
  }

  function extractHighlightTerms(signals) {
    const terms = new Set();
    const quotedRe = /'([^']{2,})'/g;
    signals.forEach((sig) => {
      let match;
      while ((match = quotedRe.exec(sig)) !== null) {
        terms.add(match[1]);
      }
      if (sig.toLowerCase().includes("url")) {
        terms.add("http://");
        terms.add("https://");
        terms.add("www.");
      }
      if (sig.includes("Urgency punctuation")) {
        terms.add("!!!");
        terms.add("!!");
      }
    });
    return Array.from(terms);
  }

  function setBusy(busy) {
    els.btnAnalyze.disabled = busy;
    els.btnTeachSpam.disabled = busy;
    els.btnTeachHam.disabled = busy;
    els.btnAnalyze.querySelector(".btn-label").textContent = busy ? "Analyzing..." : "Analyze Email";
  }

  function formatTime(iso) {
    if (!iso) return "";
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return "";
    return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
  }

  function escapeHtml(str) {
    return String(str)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function escapeRegex(str) {
    return str.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  }

  // Event Listeners
  els.form.addEventListener("submit", (e) => {
    e.preventDefault();
    analyzeCurrentEmail();
  });

  els.body.addEventListener("keydown", (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
      e.preventDefault();
      analyzeCurrentEmail();
    }
  });

  els.btnSampleSpam.addEventListener("click", () => {
    const sample = SAMPLE_SPAM[spamPresetIdx % SAMPLE_SPAM.length];
    spamPresetIdx++;
    els.sender.value = sample.sender;
    els.subject.value = sample.subject;
    els.body.value = sample.body;
    showToast("Loaded sample SPAM email. Click Analyze Email to inspect.");
  });

  els.btnSampleHam.addEventListener("click", () => {
    const sample = SAMPLE_HAM[hamPresetIdx % SAMPLE_HAM.length];
    hamPresetIdx++;
    els.sender.value = sample.sender;
    els.subject.value = sample.subject;
    els.body.value = sample.body;
    showToast("Loaded sample HAM email. Click Analyze Email to inspect.");
  });

  els.btnClear.addEventListener("click", () => {
    els.sender.value = "";
    els.subject.value = "";
    els.body.value = "";
    els.resultContent.classList.add("hidden");
    els.resultEmpty.classList.remove("hidden");
    els.resultModelBadge.textContent = "Ready";
  });

  els.btnTeachSpam.addEventListener("click", () => teachModel("SPAM"));
  els.btnTeachHam.addEventListener("click", () => teachModel("HAM"));

  // Initial load
  fetchStats();
})();
