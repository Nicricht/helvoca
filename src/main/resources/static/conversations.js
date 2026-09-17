const TOKEN_KEY = "helvoca_access_token";
const token = sessionStorage.getItem(TOKEN_KEY) || "";
const $ = selector => document.querySelector(selector);
const $$ = selector => [...document.querySelectorAll(selector)];

if (!token) location.replace("/");

let calls = [];
let activeChannel = "all";
let selectedCallId = null;

async function api(path) {
  const response = await fetch(path, {headers: {Authorization: `Bearer ${token}`}});
  let payload = null;
  const type = response.headers.get("content-type") || "";
  if (type.includes("application/json")) {
    try { payload = await response.json(); } catch (_) {}
  } else if (response.status !== 204) {
    try { payload = await response.text(); } catch (_) {}
  }
  if (!response.ok) {
    if (response.status === 401) {
      sessionStorage.removeItem(TOKEN_KEY);
      location.replace("/");
    }
    throw new Error(payload?.message || payload?.detail || payload?.error || (typeof payload === "string" && payload) || `HTTP ${response.status}`);
  }
  return payload;
}

function esc(value) {
  return String(value ?? "").replace(/[&<>'"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
}

function fmtDate(value) {
  if (!value) return "";
  try { return new Intl.DateTimeFormat("es-CL", {dateStyle:"short", timeStyle:"short"}).format(new Date(value)); }
  catch (_) { return String(value); }
}

function fmtDuration(seconds) {
  const total = Math.max(0, Number(seconds || 0));
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, "0")}`;
}

function statusLabel(status) {
  return ({
    COMPLETED: "Finalizada",
    FAILED: "Falló",
    IN_PROGRESS: "En curso",
    RINGING: "Entrante",
    ANSWERED: "Atendida"
  })[status] || status || "Sin estado";
}

function toast(text) {
  const el = $("#message");
  el.textContent = text;
  el.classList.remove("hidden");
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => el.classList.add("hidden"), 3500);
}

function renderList() {
  const root = $("#conversationList");
  const count = $("#conversationCount");

  if (activeChannel === "whatsapp") {
    count.textContent = "0";
    root.innerHTML = `<div class="whatsapp-empty"><strong>WhatsApp todavía no tiene conversaciones reales</strong><span>Cuando Helvoca empiece a recibir mensajes, aparecerán aquí junto a las llamadas. No mostramos datos simulados.</span></div>`;
    return;
  }

  count.textContent = String(calls.length);
  if (!calls.length) {
    root.innerHTML = '<div class="empty">Todavía no hay llamadas reales.</div>';
    return;
  }

  root.innerHTML = calls.map(call => `
    <button type="button" class="conversation-row ${call.id === selectedCallId ? "active" : ""}" data-call-id="${esc(call.id)}">
      <div class="conversation-row-top">
        <strong>${esc(call.callerNumber || "Número oculto")}</strong>
        <span class="pill ${call.status === "FAILED" ? "bad" : ""}">${esc(statusLabel(call.status))}</span>
      </div>
      <span class="channel-badge">Llamada</span>
      <div class="conversation-row-meta">
        <span>${fmtDate(call.startedAt)}</span>
        ${call.durationSeconds != null ? `<span>${fmtDuration(call.durationSeconds)}</span>` : ""}
      </div>
      ${call.resolution ? `<div class="conversation-row-summary">${esc(call.resolution)}</div>` : ""}
    </button>
  `).join("");

  root.querySelectorAll("[data-call-id]").forEach(button => button.addEventListener("click", async () => {
    selectedCallId = button.dataset.callId;
    renderList();
    await loadDetail(selectedCallId);
  }));
}

function renderDetail(data) {
  const call = data.call || {};
  $("#detailEmpty").classList.add("hidden");
  $("#detailContent").classList.remove("hidden");
  $("#detailChannel").textContent = "Llamada";
  $("#detailCustomer").textContent = call.callerNumber || "Número oculto";
  $("#detailMeta").textContent = [fmtDate(call.startedAt), call.durationSeconds != null ? fmtDuration(call.durationSeconds) : "", call.resolution || ""].filter(Boolean).join(" · ");
  $("#detailStatus").textContent = statusLabel(call.status);
  $("#detailStatus").className = `pill ${call.status === "FAILED" ? "bad" : ""}`;
  $("#detailSummary").textContent = data.summary || "El resumen todavía no está disponible.";

  const transcript = data.transcript || [];
  $("#detailTranscript").innerHTML = transcript.length ? transcript.map(item => `
    <div class="transcript-line ${String(item.speaker || "").toLowerCase()}">
      <strong>${esc(item.speaker === "USER" ? "Cliente" : item.speaker === "ASSISTANT" ? "Helvoca" : item.speaker)}</strong>
      <p>${esc(item.content)}</p>
      <span>${fmtDate(item.createdAt)}</span>
    </div>
  `).join("") : '<div class="empty">No hay transcripción disponible.</div>';

  const actions = data.actions || [];
  $("#detailActions").innerHTML = actions.length ? actions.map(action => `
    <div class="action-row">
      <div class="item-head">
        <strong>${esc(action.actionType)}</strong>
        <span class="pill ${action.success ? "" : "bad"}">${action.success ? "Confirmada" : "Falló"}</span>
      </div>
      <div class="meta">
        ${action.detail ? `<span>${esc(action.detail)}</span>` : ""}
        ${action.entityType ? `<span>${esc(action.entityType)}</span>` : ""}
        <span>${fmtDate(action.createdAt)}</span>
      </div>
    </div>
  `).join("") : '<div class="empty">No hay acciones registradas para esta llamada.</div>';
}

async function loadDetail(callId) {
  try {
    const data = await api(`/api/v1/calls/${encodeURIComponent(callId)}`);
    renderDetail(data);
  } catch (error) {
    toast(error.message || "No pude cargar el detalle de la conversación.");
  }
}

function setChannel(channel) {
  activeChannel = channel;
  $$(".channel-tab").forEach(button => {
    const active = button.dataset.channel === channel;
    button.classList.toggle("active", active);
    button.setAttribute("aria-selected", active ? "true" : "false");
  });
  $("#inboxTitle").textContent = channel === "whatsapp" ? "WhatsApp" : channel === "calls" ? "Llamadas" : "Recientes";
  renderList();
}

async function load() {
  $("#conversationList").innerHTML = '<div class="loading-line">Cargando conversaciones…</div>';
  try {
    const data = await api("/api/v1/operations/dashboard");
    calls = data.recentCalls || [];
    $("#conversationContext").textContent = data.businessName ? `${data.businessName} · conversaciones reales` : "Conversaciones reales";
    renderList();
  } catch (error) {
    $("#conversationList").innerHTML = '<div class="empty">No pude cargar las conversaciones.</div>';
    toast(error.message || "No pude cargar las conversaciones.");
  }
}

$$(".channel-tab").forEach(button => button.addEventListener("click", () => setChannel(button.dataset.channel)));
$("#refreshBtn").addEventListener("click", async event => {
  event.currentTarget.disabled = true;
  try { await load(); } finally { event.currentTarget.disabled = false; }
});

load();
