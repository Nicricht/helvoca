const TOKEN_KEY = "helvoca_access_token";
const token = sessionStorage.getItem(TOKEN_KEY) || "";
const $ = s => document.querySelector(s);
let sessionId = null;
let active = false;
let recognition = null;
let listening = false;
let recognitionSessionId = null;
let businessName = "Tu negocio";
let operation = null;

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
  el.textContent = text;
  el.classList.remove("hidden");
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => el.classList.add("hidden"), 3500);
}

function updateControls() {
  const operationBusy = Boolean(operation);
  const sessionBusy = operationBusy || listening;
  const start = $("#startBtn");
  const input = $("#messageInput");
  const send = $("#sendBtn");
  const finish = $("#finishBtn");
  const mic = $("#micBtn");
  start.disabled = sessionBusy;
  input.disabled = !active || sessionBusy;
  send.disabled = !active || sessionBusy;
  finish.disabled = !active || sessionBusy;
  mic.disabled = !active || operationBusy || !recognition;

  start.title = sessionBusy ? "Espera a que termine la operación actual." : "Iniciar una prueba segura nueva.";
  input.title = !active
    ? "Inicia una prueba para poder escribir."
    : (sessionBusy ? "Espera a que termine la operación actual." : "Escribe tu mensaje de prueba.");
  send.title = !active
    ? "Inicia una prueba para poder enviar mensajes."
    : (sessionBusy ? "Espera a que termine la operación actual." : "Enviar mensaje de prueba.");
  finish.title = !active
    ? "Inicia una prueba para poder finalizarla."
    : (sessionBusy ? "Espera a que termine la operación actual." : "Finalizar esta prueba segura.");
  mic.title = !recognition
    ? "El reconocimiento de voz no está disponible en este navegador."
    : (!active
      ? "Inicia una prueba para poder usar el micrófono."
      : (operationBusy ? "Espera a que termine la operación actual." : (listening ? "Detener escucha." : "Hablar.")));
}

function setActive(value) {
  active = value;
  updateControls();
  $("#sessionBadge").textContent = value ? "Prueba activa" : (sessionId ? "Prueba finalizada" : "Sin prueba activa");
  if (value && !operation) $("#messageInput").focus();
}

function resetTrace() {
  $("#resolution").textContent = "SIN ACCIÓN";
  $("#actionCount").textContent = "0";
  $("#traceList").innerHTML = '<div class="empty">Las herramientas usadas aparecerán aquí.</div>';
}

function resetChat() {
  $("#chat").innerHTML = "";
  resetTrace();
}

function bubble(role, text) {
  const root = $("#chat");
  root.querySelector(".empty")?.remove();
  const div = document.createElement("div");
  div.className = `bubble ${role}`;
  div.innerHTML = `${esc(text)}<small>${role === "user" ? "Tú" : esc(businessName)}</small>`;
  root.appendChild(div);
  root.scrollTop = root.scrollHeight;
  return div;
}

function speak(text) {
  if (!("speechSynthesis" in window) || !text) return;
  window.speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = "es-CL";
  utterance.rate = 1;
  window.speechSynthesis.speak(utterance);
}

async function loadTrace(targetSessionId = sessionId) {
  if (!targetSessionId) return;
  try {
    const detail = await api(`/api/v1/calls/${targetSessionId}`);
    if (targetSessionId !== sessionId) return;
    $("#resolution").textContent = detail.call?.resolution || "SIN ACCIÓN";
    const actions = detail.actions || [];
    $("#actionCount").textContent = String(actions.length);
    const root = $("#traceList");
    if (!actions.length) {
      root.innerHTML = '<div class="empty">Todavía no se usaron herramientas.</div>';
      return;
    }
    root.innerHTML = actions.slice().reverse().map(a => `
      <div class="trace-item">
        <div class="row"><strong>${esc(a.actionType)}</strong><span class="pill ${a.success ? "" : "bad"}">${a.success ? "OK" : "FALLÓ"}</span></div>
        ${a.detail ? `<p>${esc(a.detail)}</p>` : ""}
        ${a.errorCode ? `<p>${esc(a.errorCode)}</p>` : ""}
      </div>`).join("");
  } catch (err) {
    toast(err.message);
  }
}

async function finishCurrent(silent = false, nested = false) {
  if (!sessionId || !active) return true;
  if ((operation || listening) && !nested) return false;
  if (!nested) {
    operation = "finish";
    updateControls();
  }
  const finishingSessionId = sessionId;
  try {
    await api(`/api/v1/simulator/sessions/${finishingSessionId}/finish`, {method:"POST"});
    setActive(false);
    await loadTrace(finishingSessionId);
    if (!silent) toast("Prueba finalizada. Ningún dato comercial real fue modificado.");
    return true;
  } catch (err) {
    if (!silent) toast(err.message);
    return false;
  } finally {
    if (!nested) {
      operation = null;
      updateControls();
    }
  }
}

async function startSession() {
  if (operation || listening) return;
  operation = "start";
  updateControls();
  try {
    if (active && !(await finishCurrent(true, true))) {
      toast("No pude finalizar la prueba actual. Intenta nuevamente.");
      return;
    }
    const result = await api("/api/v1/simulator/sessions", {method:"POST"});
    sessionId = result.sessionId;
    resetChat();
    setActive(true);
    bubble("assistant", result.greeting);
    speak(result.greeting);
    await loadTrace();
  } catch (err) {
    toast(err.message);
  } finally {
    operation = null;
    updateControls();
    if (active) $("#messageInput").focus();
  }
}

async function sendMessage(text) {
  if (!active || !sessionId || operation) return;
  const clean = String(text || "").trim();
  if (!clean) return;
  const messageSessionId = sessionId;
  operation = "send";
  updateControls();
  const pendingBubble = bubble("user", clean);
  $("#messageInput").value = "";
  try {
    const result = await api(`/api/v1/simulator/sessions/${messageSessionId}/messages`, {
      method:"POST",
      body:JSON.stringify({message:clean})
    });
    if (messageSessionId !== sessionId) return;
    bubble("assistant", result.reply);
    speak(result.reply);
    await loadTrace(messageSessionId);
    if (result.ended) setActive(false);
  } catch (err) {
    pendingBubble.remove();
    $("#messageInput").value = clean;
    toast(err.message);
  } finally {
    operation = null;
    updateControls();
    if (active) {
      $("#messageInput").focus();
    }
  }
}

function setupRecognition() {
  const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!Recognition) {
    $("#voiceHint").textContent = "Tu navegador no ofrece reconocimiento de voz. Puedes usar el chat igualmente.";
    return;
  }
  recognition = new Recognition();
  recognition.lang = "es-CL";
  recognition.interimResults = false;
  recognition.continuous = false;
  recognition.onstart = () => {
    listening = true;
    recognitionSessionId = sessionId;
    $("#micBtn").classList.add("listening");
    $("#voiceHint").textContent = "Escuchando… habla como si estuvieras llamando al negocio.";
    updateControls();
  };
  recognition.onend = () => {
    listening = false;
    recognitionSessionId = null;
    $("#micBtn").classList.remove("listening");
    $("#voiceHint").textContent = "Puedes escribir o hablar. Las respuestas se leen en voz alta desde el navegador.";
    updateControls();
  };
  recognition.onerror = event => {
    listening = false;
    recognitionSessionId = null;
    $("#micBtn").classList.remove("listening");
    updateControls();
    toast(`Micrófono: ${event.error || "no disponible"}`);
  };
  recognition.onresult = event => {
    const resultSessionId = recognitionSessionId;
    const text = event.results?.[0]?.[0]?.transcript || "";
    listening = false;
    recognitionSessionId = null;
    $("#micBtn").classList.remove("listening");
    updateControls();
    if (!active || resultSessionId !== sessionId) return;
    $("#messageInput").value = text;
    sendMessage(text);
  };
}

$("#startBtn").addEventListener("click", startSession);
$("#finishBtn").addEventListener("click", () => finishCurrent(false));
$("#messageForm").addEventListener("submit", event => {
  event.preventDefault();
  sendMessage($("#messageInput").value);
});
$("#micBtn").addEventListener("click", () => {
  if (!recognition || !active) return;
  try {
    if (listening) recognition.stop();
    else {
      listening = true;
      recognitionSessionId = sessionId;
      updateControls();
      recognition.start();
    }
  } catch (err) {
    listening = false;
    recognitionSessionId = null;
    updateControls();
    toast(err.message);
  }
});
window.addEventListener("beforeunload", () => {
  if ("speechSynthesis" in window) window.speechSynthesis.cancel();
});

async function loadBusinessIdentity() {
  try {
    const business = await api("/api/v1/business");
    businessName = business?.name || "Tu negocio";
    document.querySelector(".topbar > div strong")?.replaceChildren(document.createTextNode(businessName.toUpperCase()));
    document.title = `${businessName} · Probar recepcionista`;
  } catch (_) {
    businessName = "Tu negocio";
  }
}

async function initialize() {
  setupRecognition();
  setActive(false);
  if (!token) return;
  operation = "identity";
  updateControls();
  await loadBusinessIdentity();
  operation = null;
  updateControls();
}

initialize();
