const TOKEN_KEY = "helvoca_access_token";
const token = sessionStorage.getItem(TOKEN_KEY) || "";
const $ = s => document.querySelector(s);

if (!token) location.replace("/");

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

function esc(value) {
  return String(value ?? "").replace(/[&<>'"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
}

function toast(text) {
  const el = $("#message");
  el.textContent = text; el.classList.remove("hidden");
  clearTimeout(toast.timer); toast.timer = setTimeout(() => el.classList.add("hidden"), 3500);
}

function fmtDate(value) {
  if (!value) return "";
  try { return new Intl.DateTimeFormat("es", {dateStyle:"short", timeStyle:"short"}).format(new Date(value)); }
  catch (_) { return value; }
}

function renderReadiness(data) {
  $("#readinessBadge").textContent = data.ready ? "LISTO" : `${data.requiredPassed}/${data.requiredTotal}`;
  const checks = $("#readinessChecks");
  checks.innerHTML = (data.checks || []).map(c => `
    <div class="item">
      <div class="item-head"><strong>${esc(c.label)}</strong><span class="pill ${c.ready ? "" : "bad"}">${c.ready ? "OK" : "FALTA"}</span></div>
      <div class="meta"><span>${esc(c.detail)}</span></div>
    </div>`).join("");

  const capabilities = Object.entries(data.capabilities || {});
  $("#capabilities").innerHTML = capabilities.map(([name, enabled]) =>
    `<span class="pill ${enabled ? "" : "bad"}">${esc(name)} ${enabled ? "✓" : "×"}</span>`).join(" ");

  const warnings = $("#readinessWarnings");
  warnings.innerHTML = (data.warnings || []).map(w => `<div class="empty">${esc(w)}</div>`).join("");
}

function renderCalls(items = []) {
  const root = $("#callsList");
  if (!items.length) { root.innerHTML = '<div class="empty">Todavía no hay llamadas.</div>'; return; }
  root.innerHTML = items.map(c => `
    <div class="item" data-call-id="${esc(c.id)}">
      <div class="item-head"><strong>${esc(c.callerNumber || "Número oculto")}</strong><span class="pill ${c.status === "FAILED" ? "bad" : ""}">${esc(c.status)}</span></div>
      <div class="meta"><span>${fmtDate(c.startedAt)}</span><span>${c.durationSeconds != null ? `${c.durationSeconds}s` : "sin duración"}</span>${c.resolution ? `<span>${esc(c.resolution)}</span>` : ""}</div>
      <div class="actions call-actions"><button data-call-detail class="ghost">Ver detalle</button></div>
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
  $("#callDetailMeta").textContent = `${call.callerNumber || "Número oculto"} · ${fmtDate(call.startedAt)} · ${call.status || ""}${call.resolution ? ` · ${call.resolution}` : ""}`;
  $("#callSummary").textContent = data.summary || "El resumen todavía no está disponible.";

  const actions = data.actions || [];
  $("#callActions").innerHTML = actions.length ? actions.map(a => `
    <div class="item">
      <div class="item-head"><strong>${esc(a.actionType)}</strong><span class="pill ${a.success ? "" : "bad"}">${a.success ? "CONFIRMADO" : "FALLÓ"}</span></div>
      <div class="meta">${a.detail ? `<span>${esc(a.detail)}</span>` : ""}${a.entityType ? `<span>${esc(a.entityType)}</span>` : ""}${a.entityId ? `<span>${esc(a.entityId)}</span>` : ""}${a.errorCode ? `<span>${esc(a.errorCode)}</span>` : ""}<span>${fmtDate(a.createdAt)}</span></div>
    </div>`).join("") : '<div class="empty">No hay acciones registradas para esta llamada.</div>';

  const transcript = data.transcript || [];
  $("#callTranscript").innerHTML = transcript.length ? transcript.map(t => `
    <div class="transcript-line ${String(t.speaker || "").toLowerCase()}">
      <strong>${esc(t.speaker === "USER" ? "Cliente" : t.speaker === "ASSISTANT" ? "Helvoca" : t.speaker)}</strong>
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

function renderRequests(items = []) {
  const root = $("#requestsList");
  if (!items.length) { root.innerHTML = '<div class="empty">No hay solicitudes abiertas todavía.</div>'; return; }
  root.innerHTML = items.map(r => `
    <div class="item" data-request-id="${esc(r.id)}">
      <div class="item-head"><strong>${esc(r.title)}</strong><span class="pill ${r.priority === "URGENT" || r.priority === "HIGH" ? "high" : ""}">${esc(r.priority)}</span></div>
      <div class="meta"><span>${esc(r.type)}</span><span>${esc(r.status)}</span><span>${fmtDate(r.createdAt)}</span></div>
      ${r.status !== "RESOLVED" && r.status !== "CANCELLED" ? '<div class="actions" style="margin-top:10px"><button data-status="IN_PROGRESS">En curso</button><button data-status="RESOLVED" class="ghost">Resolver</button></div>' : ""}
    </div>`).join("");
  root.querySelectorAll("button[data-status]").forEach(button => button.addEventListener("click", async e => {
    const item = e.target.closest("[data-request-id]");
    e.target.disabled = true;
    try {
      await api(`/api/v1/requests/${item.dataset.requestId}/status`, {method:"PATCH", body:JSON.stringify({status:e.target.dataset.status})});
      await load(); toast("Solicitud actualizada.");
    } catch (err) { toast(err.message); e.target.disabled = false; }
  }));
}

function renderQuestions(items = []) {
  const root = $("#questionsList");
  if (!items.length) { root.innerHTML = '<div class="empty">Helvoca no tiene preguntas pendientes. ✨</div>'; return; }
  root.innerHTML = items.map(q => `
    <div class="item" data-question-id="${esc(q.id)}">
      <div class="item-head"><strong>${esc(q.question)}</strong><span class="pill">${q.occurrences}×</span></div>
      <div class="meta"><span>Última vez ${fmtDate(q.lastSeenAt)}</span></div>
      <div class="question-actions"><input data-answer placeholder="Escribe la respuesta oficial"><button data-answer-btn>Enseñar</button><button data-dismiss-btn class="ghost">Descartar</button></div>
    </div>`).join("");
  root.querySelectorAll("[data-answer-btn]").forEach(button => button.addEventListener("click", async e => {
    const item = e.target.closest("[data-question-id]");
    const answer = item.querySelector("[data-answer]").value.trim();
    if (!answer) { toast("Escribe una respuesta antes de enseñar a Helvoca."); return; }
    e.target.disabled = true;
    try {
      await api(`/api/v1/learning/questions/${item.dataset.questionId}/answer`, {method:"POST", body:JSON.stringify({answer})});
      await load(); toast("Respuesta aprendida y guardada en conocimiento.");
    } catch (err) { toast(err.message); e.target.disabled = false; }
  }));
  root.querySelectorAll("[data-dismiss-btn]").forEach(button => button.addEventListener("click", async e => {
    const item = e.target.closest("[data-question-id]");
    e.target.disabled = true;
    try {
      await api(`/api/v1/learning/questions/${item.dataset.questionId}/dismiss`, {method:"POST"});
      await load(); toast("Pregunta descartada.");
    } catch (err) { toast(err.message); e.target.disabled = false; }
  }));
}

async function load() {
  try {
    const [data, readiness] = await Promise.all([
      api("/api/v1/operations/dashboard"),
      api("/api/v1/operations/readiness")
    ]);
    $("#businessName").textContent = data.businessName;
    $("#localNow").textContent = `${data.timezone} · ${fmtDate(data.localNow)}`;
    $("#callsToday").textContent = data.callsToday;
    $("#bookingsToday").textContent = data.bookingsToday;
    $("#customersToday").textContent = data.newCustomersToday;
    $("#openRequests").textContent = data.openRequests;
    $("#unknownQuestions").textContent = data.unansweredQuestions;
    $("#failuresToday").textContent = data.callFailuresToday;
    $("#healthBadge").textContent = data.callFailuresToday ? `${data.callFailuresToday} llamada(s) con fallo` : "Operación saludable";
    renderReadiness(readiness);
    renderCalls(data.recentCalls);
    renderRequests(data.recentRequests);
    renderQuestions(data.unanswered);
  } catch (err) { toast(err.message || "No pude cargar operaciones."); }
}

$("#refreshBtn").addEventListener("click", load);
$("#closeCallDetailBtn").addEventListener("click", () => $("#callDetailPanel").classList.add("hidden"));
$("#newRequestBtn").addEventListener("click", () => $("#requestForm").classList.remove("hidden"));
$("#cancelRequestBtn").addEventListener("click", () => $("#requestForm").classList.add("hidden"));
$("#requestForm").addEventListener("submit", async e => {
  e.preventDefault();
  const form = e.currentTarget;
  const data = Object.fromEntries(new FormData(form).entries());
  form.querySelectorAll("button").forEach(b => b.disabled = true);
  try {
    await api("/api/v1/requests", {method:"POST", body:JSON.stringify(data)});
    form.reset(); form.classList.add("hidden"); await load(); toast("Solicitud creada.");
  } catch (err) { toast(err.message); }
  finally { form.querySelectorAll("button").forEach(b => b.disabled = false); }
});

load();
