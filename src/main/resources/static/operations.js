const TOKEN_KEY = "helvoca_access_token";
const token = sessionStorage.getItem(TOKEN_KEY) || "";
const $ = s => document.querySelector(s);
let businessName = "Tu negocio";
let businessTimezone = "";
let loading = false;

if (!token) location.replace("/");

const CALL_STATUS_LABELS = {
  COMPLETED: "Finalizada",
  FAILED: "Falló",
  IN_PROGRESS: "En curso",
  RINGING: "Entrante",
  ANSWERED: "Atendida"
};

const EVENT_LABELS = {
  REQUEST_CREATED: "Solicitud creada",
  BOOKING_CREATED: "Reserva creada",
  BOOKING_RESCHEDULED: "Reserva reprogramada",
  BOOKING_CANCELLED: "Reserva cancelada",
  CUSTOMER_REGISTERED: "Cliente registrado",
  CALLER_LOOKUP: "Cliente identificado",
  FIND_CALLER: "Cliente identificado",
  KNOWLEDGE_SEARCH: "Consultó información",
  SEARCH_KNOWLEDGE: "Consultó información",
  SERVICES_LISTED: "Consultó servicios",
  LIST_SERVICES: "Consultó servicios",
  AVAILABILITY_LISTED: "Consultó horarios disponibles",
  LIST_AVAILABLE_SLOTS: "Consultó horarios disponibles",
  AVAILABILITY_CHECKED: "Verificó disponibilidad",
  CHECK_BOOKING_AVAILABILITY: "Verificó disponibilidad",
  CREATE_BOOKING: "Reserva creada",
  CREATE_REQUEST: "Solicitud creada",
  HUMAN_TRANSFER: "Transferencia a una persona",
  TRANSFER_TO_HUMAN: "Transferencia a una persona",
  UNANSWERED_QUESTION_RECORDED: "Pregunta guardada para revisar"
};

async function api(path, options = {}) {
  const headers = new Headers(options.headers || {});
  headers.set("Authorization", `Bearer ${token}`);
  if (options.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  const response = await fetch(path, {...options, headers});
  let payload = null;
  const type = response.headers.get("content-type") || "";
  if (type.includes("application/json")) { try { payload = await response.json(); } catch (_) {} }
  else if (response.status !== 204) { try { payload = await response.text(); } catch (_) {} }
  if (!response.ok) {
    if (response.status === 401) { sessionStorage.removeItem(TOKEN_KEY); location.replace("/"); }
    throw new Error(payload?.message || payload?.detail || payload?.error || (typeof payload === "string" && payload) || `HTTP ${response.status}`);
  }
  return payload;
}

function setText(selector, value) {
  const node = $(selector);
  if (node) node.textContent = value;
}

function esc(value) {
  return String(value ?? "").replace(/[&<>'"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
}

function humanize(value, labels = EVENT_LABELS) {
  if (!value) return "";
  if (labels[value]) return labels[value];
  const text = String(value);
  if (!/^[A-Z0-9_]+$/.test(text)) return text;
  return text.toLowerCase().split("_").filter(Boolean).map(word => word.charAt(0).toUpperCase() + word.slice(1)).join(" ");
}

function callStatus(value) {
  return CALL_STATUS_LABELS[value] || humanize(value, {});
}

function toast(text) {
  const el = $("#message");
  el.textContent = text;
  el.classList.remove("hidden");
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => el.classList.add("hidden"), 3500);
}

function fmtDate(value) {
  if (!value) return "";
  try {
    const options = {dateStyle:"short", timeStyle:"short"};
    if (businessTimezone) options.timeZone = businessTimezone;
    return new Intl.DateTimeFormat("es-CL", options).format(new Date(value));
  }
  catch (_) { return value; }
}

function fmtDuration(seconds) {
  const total = Math.max(0, Number(seconds || 0));
  const minutes = Math.floor(total / 60);
  const rest = total % 60;
  return `${minutes}:${String(rest).padStart(2, "0")}`;
}

function renderReadiness(data) {
  $("#readinessBadge").textContent = data.ready ? "LISTO" : `${data.requiredPassed}/${data.requiredTotal}`;
  $("#readinessBadge").className = `badge ${data.ready ? "" : "bad"}`.trim();
  const checks = $("#readinessChecks");
  checks.innerHTML = (data.checks || []).map(c => `
    <div class="item">
      <div class="item-head"><strong>${esc(c.label)}</strong><span class="pill ${c.ready ? "" : "bad"}">${c.ready ? "OK" : "FALTA"}</span></div>
      <div class="meta"><span>${esc(c.detail)}</span></div>
    </div>`).join("");

  const capabilities = Object.entries(data.capabilities || {});
  $("#capabilities").innerHTML = capabilities.map(([name, enabled]) =>
    `<span class="pill ${enabled ? "" : "bad"}">${esc(name)} ${enabled ? "✓" : "×"}</span>`).join(" ");

  $("#readinessWarnings").innerHTML = (data.warnings || []).map(w => `<div class="empty">${esc(w)}</div>`).join("");
}

function renderReadinessError(error) {
  $("#readinessBadge").textContent = "ERROR";
  $("#readinessBadge").className = "badge bad";
  $("#readinessChecks").innerHTML = '<div class="empty">No fue posible cargar el estado de voz.</div>';
  $("#capabilities").innerHTML = "";
  $("#readinessWarnings").innerHTML = `<div class="empty">${esc(error?.message || "Readiness no disponible")}</div>`;
}

function renderCertification(data) {
  const badge = $("#certificationBadge");
  const meta = $("#certificationMeta");
  const checks = $("#certificationChecks");

  if (!data?.available) {
    badge.textContent = "SIN EJECUTAR";
    badge.className = "badge";
    meta.innerHTML = "<span>No existe todavía una llamada de certificación registrada para este negocio.</span>";
    checks.innerHTML = '<div class="empty">La certificación real sigue siendo una operación controlada y no se inicia desde este dashboard.</div>';
    return;
  }

  const stateLabels = {PASSED: "APROBADA", FAILED: "FALLÓ", IN_PROGRESS: "EN CURSO", NOT_RUN: "SIN EJECUTAR"};
  badge.textContent = stateLabels[data.state] || humanize(data.state, {});
  badge.className = `badge ${data.state === "FAILED" ? "bad" : ""}`.trim();
  meta.innerHTML = [
    data.startedAt ? `<span>${fmtDate(data.startedAt)}</span>` : "",
    data.telephonyProvider ? `<span>Telefonía: ${esc(data.telephonyProvider)}</span>` : "",
    data.aiProvider ? `<span>IA: ${esc(data.aiProvider)}</span>` : "",
    data.callStatus ? `<span>Llamada: ${esc(callStatus(data.callStatus))}</span>` : "",
    `<span>${Number(data.passedChecks || 0)}/${Number(data.totalChecks || 0)} controles</span>`
  ].filter(Boolean).join("");

  checks.innerHTML = (data.checks || []).map(c => `
    <div class="item">
      <div class="item-head"><strong>${esc(c.label)}</strong><span class="pill ${c.passed ? "" : "bad"}">${c.passed ? "OK" : "FALTA"}</span></div>
      <div class="meta"><span>${esc(c.detail)}</span></div>
    </div>`).join("");
}

function renderCalls(items = []) {
  const root = $("#callsList");
  if (!items.length) { root.innerHTML = '<div class="empty">Todavía no hay llamadas reales.</div>'; return; }
  root.innerHTML = items.map(c => `
    <div class="item" data-call-id="${esc(c.id)}">
      <div class="item-head"><strong>${esc(c.callerNumber || "Número oculto")}</strong><span class="pill ${c.status === "FAILED" ? "bad" : ""}">${esc(callStatus(c.status))}</span></div>
      <div class="meta">
        <span>${fmtDate(c.startedAt)}</span>
        <span>${c.durationSeconds != null ? fmtDuration(c.durationSeconds) : "sin duración"}</span>
        ${c.resolution ? `<span>${esc(humanize(c.resolution))}</span>` : ""}
      </div>
      <div class="actions call-actions"><button data-call-detail class="ghost" type="button">Abrir detalle</button></div>
    </div>`).join("");
  root.querySelectorAll("[data-call-detail]").forEach(button => button.addEventListener("click", async e => {
    const item = e.target.closest("[data-call-id]");
    e.target.disabled = true;
    try { await loadCallDetail(item.dataset.callId); }
    catch (err) { toast(err.message); }
    finally { e.target.disabled = false; }
  }));
}

function renderCallDetail(data) {
  const call = data.call || {};
  $("#callDetailMeta").textContent = [
    call.callerNumber || "Número oculto",
    fmtDate(call.startedAt),
    callStatus(call.status),
    call.resolution ? humanize(call.resolution) : ""
  ].filter(Boolean).join(" · ");
  $("#callSummary").textContent = data.summary || "El resumen todavía no está disponible.";

  const actions = data.actions || [];
  $("#callActions").innerHTML = actions.length ? actions.map(a => `
    <div class="item">
      <div class="item-head"><strong>${esc(humanize(a.actionType))}</strong><span class="pill ${a.success ? "" : "bad"}">${a.success ? "Confirmada" : "Falló"}</span></div>
      <div class="meta">${a.detail ? `<span>${esc(a.detail)}</span>` : ""}<span>${fmtDate(a.createdAt)}</span></div>
    </div>`).join("") : '<div class="empty">No hay acciones registradas para esta llamada.</div>';

  const transcript = data.transcript || [];
  $("#callTranscript").innerHTML = transcript.length ? transcript.map(t => `
    <div class="transcript-line ${String(t.speaker || "").toLowerCase()}">
      <strong>${esc(t.speaker === "USER" ? "Cliente" : t.speaker === "ASSISTANT" ? businessName : humanize(t.speaker, {}))}</strong>
      <p>${esc(t.content)}</p>
      <span>${fmtDate(t.createdAt)}</span>
    </div>`).join("") : '<div class="empty">No hay transcripción disponible.</div>';

  $("#callDetailPanel").classList.remove("hidden");
  $("#callDetailPanel").scrollIntoView({behavior:"smooth", block:"start"});
}

async function loadCallDetail(callId) {
  const data = await api(`/api/v1/calls/${encodeURIComponent(callId)}`);
  renderCallDetail(data);
}

function updateDiagnosticsSummary(readiness, certification) {
  const target = $("#diagnosticsSummary");
  if (!target) return;
  const ready = Boolean(readiness?.ready);
  const certified = !certification?.available || certification?.state === "PASSED";
  if (ready && certified) {
    target.textContent = "Todo correcto";
    return;
  }
  const issues = [];
  if (!ready) issues.push("voz");
  if (!certified) issues.push("certificación");
  target.textContent = `Revisar ${issues.join(" y ")}`;
}

async function load() {
  if (loading) return;
  loading = true;
  const refresh = $("#refreshBtn");
  if (refresh) refresh.disabled = true;
  try {
    const diagnosticsPromise = Promise.allSettled([
      api("/api/v1/operations/readiness"),
      api("/api/v1/operations/certification")
    ]);
    const data = await api("/api/v1/operations/dashboard");
    businessName = data.businessName || "Tu negocio";
    businessTimezone = data.timezone || "";
    setText("#businessName", businessName);
    document.querySelector(".brand-block strong")?.replaceChildren(document.createTextNode(businessName.toUpperCase()));
    document.title = `${businessName} · Operaciones`;
    setText("#localNow", `${data.timezone || ""}${data.timezone ? " · " : ""}${fmtDate(data.localNow)}`);
    const healthBadge = $("#healthBadge");
    if (healthBadge) {
      healthBadge.textContent = data.callFailuresToday
        ? `${data.callFailuresToday} llamada${Number(data.callFailuresToday) === 1 ? "" : "s"} necesita${Number(data.callFailuresToday) === 1 ? "" : "n"} revisión`
        : "Todo funcionando";
      healthBadge.className = `badge ${data.callFailuresToday ? "bad" : ""}`.trim();
    }

    renderCalls(data.recentCalls);

    const [readinessResult, certificationResult] = await diagnosticsPromise;
    const readiness = readinessResult.status === "fulfilled" ? readinessResult.value : {ready: false};
    const certification = certificationResult.status === "fulfilled"
      ? certificationResult.value
      : {available: true, state: "FAILED"};
    if (readinessResult.status === "fulfilled") renderReadiness(readiness);
    else renderReadinessError(readinessResult.reason);
    if (certificationResult.status === "fulfilled") renderCertification(certification);
    else renderCertificationError(certificationResult.reason);
    updateDiagnosticsSummary(readiness, certification);
  } catch (err) {
    toast(err.message || "No pude cargar operaciones.");
  } finally {
    loading = false;
    if (refresh) refresh.disabled = false;
  }
}

function renderCertificationError(error) {
  $("#certificationBadge").textContent = "ERROR";
  $("#certificationBadge").className = "badge bad";
  $("#certificationMeta").innerHTML = `<span>${esc(error?.message || "Certificación no disponible")}</span>`;
  $("#certificationChecks").innerHTML = '<div class="empty">No fue posible cargar el estado de certificación.</div>';
}

$("#refreshBtn")?.addEventListener("click", load);
$("#closeCallDetailBtn")?.addEventListener("click", () => $("#callDetailPanel")?.classList.add("hidden"));
if (token) load();
