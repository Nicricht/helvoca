const TOKEN_KEY = "helvoca_access_token";
const token = sessionStorage.getItem(TOKEN_KEY) || "";
const $ = selector => document.querySelector(selector);
const $$ = selector => [...document.querySelectorAll(selector)];

if (!token) location.replace("/");

let calls = [];
let whatsappConversations = [];
let customersById = new Map();
const params = new URLSearchParams(window.location.search);
const requestedChannel = params.get("channel");
const requestedConversation = params.get("conversation");
let activeChannel = ["all", "calls", "whatsapp"].includes(requestedChannel) ? requestedChannel : "all";
let selectedConversationKey = null;
let businessName = "Tu negocio";

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

function humanize(value) {
  if (!value) return "";
  if (EVENT_LABELS[value]) return EVENT_LABELS[value];
  const text = String(value);
  if (!/^[A-Z0-9_]+$/.test(text)) return text;
  return text.toLowerCase().split("_").filter(Boolean).map(word => word.charAt(0).toUpperCase() + word.slice(1)).join(" ");
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
  })[status] || humanize(status) || "Sin estado";
}

function customerLabel(customerId, fallback) {
  const customer = customerId ? customersById.get(String(customerId)) : null;
  return customer?.name || customer?.phone || fallback;
}

function toast(text) {
  const el = $("#message");
  el.textContent = text;
  el.classList.remove("hidden");
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => el.classList.add("hidden"), 3500);
}

function conversationItems() {
  const callItems = calls.map(call => ({
    key: `call:${call.id}`,
    kind: "call",
    id: call.id,
    customer: customerLabel(call.customerId, call.callerNumber || "Número oculto"),
    status: statusLabel(call.status),
    bad: call.status === "FAILED",
    timestamp: call.startedAt,
    durationSeconds: call.durationSeconds,
    summary: humanize(call.resolution)
  }));
  const whatsappItems = whatsappConversations.map(conversation => ({
    key: `whatsapp:${conversation.id}`,
    kind: "whatsapp",
    id: conversation.id,
    customer: customerLabel(conversation.customerId, conversation.sender || "Número desconocido"),
    status: "WhatsApp",
    bad: false,
    timestamp: conversation.lastMessageAt || conversation.openedAt,
    durationSeconds: null,
    summary: ""
  }));
  const items = activeChannel === "calls" ? callItems : activeChannel === "whatsapp" ? whatsappItems : [...callItems, ...whatsappItems];
  return items.sort((a, b) => new Date(b.timestamp || 0).getTime() - new Date(a.timestamp || 0).getTime());
}

function renderList() {
  const root = $("#conversationList");
  const count = $("#conversationCount");
  const items = conversationItems();
  count.textContent = String(items.length);

  if (!items.length) {
    const empty = activeChannel === "whatsapp"
      ? "WhatsApp todavía no tiene conversaciones reales."
      : activeChannel === "calls"
        ? "Todavía no hay llamadas reales."
        : "Todavía no hay conversaciones reales.";
    root.innerHTML = `<div class="empty">${empty}</div>`;
    return;
  }

  root.innerHTML = items.map(item => `
    <button type="button" class="conversation-row ${item.key === selectedConversationKey ? "active" : ""}" data-kind="${item.kind}" data-conversation-id="${esc(item.id)}">
      <div class="conversation-row-top">
        <strong>${esc(item.customer)}</strong>
        <span class="pill ${item.bad ? "bad" : ""}">${esc(item.status)}</span>
      </div>
      <span class="channel-badge">${item.kind === "call" ? "Llamada" : "WhatsApp"}</span>
      <div class="conversation-row-meta">
        <span>${fmtDate(item.timestamp)}</span>
        ${item.durationSeconds != null ? `<span>${fmtDuration(item.durationSeconds)}</span>` : ""}
      </div>
      ${item.summary ? `<div class="conversation-row-summary">${esc(item.summary)}</div>` : ""}
    </button>
  `).join("");

  root.querySelectorAll("[data-conversation-id]").forEach(button => button.addEventListener("click", async () => {
    const kind = button.dataset.kind;
    const id = button.dataset.conversationId;
    selectedConversationKey = `${kind}:${id}`;
    renderList();
    await loadDetail(kind, id);
  }));
}

function setActionsVisible(visible) {
  const section = $("#detailActions")?.closest(".detail-section");
  if (section) section.classList.toggle("hidden", !visible);
}

function renderCallDetail(data) {
  const call = data.call || {};
  $("#detailEmpty").classList.add("hidden");
  $("#detailContent").classList.remove("hidden");
  $("#detailChannel").textContent = "Llamada";
  $("#detailCustomer").textContent = customerLabel(call.customerId, call.callerNumber || "Número oculto");
  $("#detailMeta").textContent = [fmtDate(call.startedAt), call.durationSeconds != null ? fmtDuration(call.durationSeconds) : "", humanize(call.resolution)].filter(Boolean).join(" · ");
  $("#detailStatus").textContent = statusLabel(call.status);
  $("#detailStatus").className = `pill ${call.status === "FAILED" ? "bad" : ""}`;
  $("#detailSummary").textContent = data.summary || "El resumen todavía no está disponible.";

  const transcript = data.transcript || [];
  $("#detailTranscript").innerHTML = transcript.length ? transcript.map(item => `
    <div class="transcript-line ${String(item.speaker || "").toLowerCase()}">
      <strong>${esc(item.speaker === "USER" ? "Cliente" : item.speaker === "ASSISTANT" ? businessName : humanize(item.speaker))}</strong>
      <p>${esc(item.content)}</p>
      <span>${fmtDate(item.createdAt)}</span>
    </div>
  `).join("") : '<div class="empty">No hay transcripción disponible.</div>';

  const actions = data.actions || [];
  setActionsVisible(true);
  $("#detailActions").innerHTML = actions.length ? actions.map(action => `
    <div class="action-row">
      <div class="item-head">
        <strong>${esc(humanize(action.actionType))}</strong>
        <span class="pill ${action.success ? "" : "bad"}">${action.success ? "Confirmada" : "Falló"}</span>
      </div>
      <div class="meta">
        ${action.detail ? `<span>${esc(action.detail)}</span>` : ""}
        <span>${fmtDate(action.createdAt)}</span>
      </div>
    </div>
  `).join("") : '<div class="empty">No hay acciones registradas para esta llamada.</div>';
}

function renderWhatsAppDetail(data) {
  const conversation = data.conversation || {};
  const messages = data.messages || [];
  $("#detailEmpty").classList.add("hidden");
  $("#detailContent").classList.remove("hidden");
  $("#detailChannel").textContent = "WhatsApp";
  $("#detailCustomer").textContent = customerLabel(conversation.customerId, conversation.sender || "Número desconocido");
  $("#detailMeta").textContent = [fmtDate(conversation.lastMessageAt || conversation.openedAt), `${messages.length} mensaje${messages.length === 1 ? "" : "s"}`].filter(Boolean).join(" · ");
  $("#detailStatus").textContent = "WhatsApp";
  $("#detailStatus").className = "pill";
  $("#detailSummary").textContent = `Conversación real por WhatsApp con ${messages.length} mensaje${messages.length === 1 ? "" : "s"}.`;
  $("#detailTranscript").innerHTML = messages.length ? messages.map(item => `
    <div class="transcript-line ${String(item.role || "").toLowerCase()}">
      <strong>${esc(item.role === "USER" ? "Cliente" : item.role === "ASSISTANT" ? businessName : humanize(item.role || item.direction))}</strong>
      <p>${esc(item.content)}</p>
      <span>${fmtDate(item.createdAt)}</span>
    </div>
  `).join("") : '<div class="empty">No hay mensajes guardados en esta conversación.</div>';
  setActionsVisible(false);
  $("#detailActions").innerHTML = "";
}

async function loadDetail(kind, id) {
  try {
    const path = kind === "whatsapp"
      ? `/api/v1/messaging/conversations/${encodeURIComponent(id)}`
      : `/api/v1/calls/${encodeURIComponent(id)}`;
    const data = await api(path);
    if (kind === "whatsapp") renderWhatsAppDetail(data);
    else renderCallDetail(data);
  } catch (error) {
    toast(error.message || "No pude cargar el detalle de la conversación.");
  }
}

async function openMostRecentIfNeeded() {
  if (selectedConversationKey) return;
  const items = conversationItems();
  const target = requestedConversation
    ? items.find(item => String(item.id) === String(requestedConversation))
    : items[0];
  if (!target) {
    const first = items[0];
    if (!first) return;
    selectedConversationKey = first.key;
    renderList();
    await loadDetail(first.kind, first.id);
    return;
  }
  selectedConversationKey = target.key;
  renderList();
  await loadDetail(target.kind, target.id);
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
  const [dashboardResult, whatsappResult, customersResult] = await Promise.allSettled([
    api("/api/v1/operations/dashboard"),
    api("/api/v1/messaging/conversations"),
    api("/api/v1/customers")
  ]);
  const dashboardAvailable = dashboardResult.status === "fulfilled";
  const whatsappAvailable = whatsappResult.status === "fulfilled";

  if (dashboardAvailable || whatsappAvailable) {
    const data = dashboardAvailable ? dashboardResult.value : {};
    businessName = data.businessName || "Tu negocio";
    customersById = customersResult.status === "fulfilled" && Array.isArray(customersResult.value)
      ? new Map(customersResult.value.map(customer => [String(customer.id), customer]))
      : new Map();
    document.querySelector(".brand-block strong")?.replaceChildren(document.createTextNode(businessName.toUpperCase()));
    document.title = `${businessName} · Conversaciones`;
    calls = data.recentCalls || [];
    whatsappConversations = whatsappAvailable ? whatsappResult.value || [] : [];
    const total = calls.length + whatsappConversations.length;
    $("#conversationContext").textContent = data.businessName ? `${data.businessName} · ${total} conversaciones recientes` : `${total} conversaciones recientes`;
    renderList();
    await openMostRecentIfNeeded();
    const failure = dashboardAvailable ? whatsappResult.reason : dashboardResult.reason;
    if (failure) toast(failure.message || "Una fuente de conversaciones no está disponible.");
    return;
  }

  calls = [];
  whatsappConversations = [];
  $("#conversationList").innerHTML = '<div class="empty">No pude cargar las conversaciones.</div>';
  const error = dashboardResult.reason || whatsappResult.reason;
  toast(error?.message || "No pude cargar las conversaciones.");
}

$$(".channel-tab").forEach(button => button.addEventListener("click", () => setChannel(button.dataset.channel)));
$("#refreshBtn").addEventListener("click", async event => {
  const button = event.currentTarget;
  button.disabled = true;
  try { await load(); } finally { button.disabled = false; }
});

setChannel(activeChannel);
load();
