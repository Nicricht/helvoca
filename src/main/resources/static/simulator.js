const TOKEN_KEY = "helvoca_access_token";
const token = sessionStorage.getItem(TOKEN_KEY) || "";
const $ = s => document.querySelector(s);
let sessionId = null;
let active = false;
let recognition = null;
let listening = false;

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

function setActive(value) {
  active = value;
  $("#messageInput").disabled = !value;
  $("#sendBtn").disabled = !value;
  $("#finishBtn").disabled = !value;
  $("#micBtn").disabled = !value || !recognition;
  $("#sessionBadge").textContent = value ? "Prueba activa" : (sessionId ? "Prueba finalizada" : "Sin prueba activa");
  if (value) $("#messageInput").focus();
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
  div.innerHTML = `${esc(text)}<small>${role === "user" ? "Tú" : "Helvoca"}</small>`;
  root.appendChild(div);
  root.scrollTop = root.scrollHeight;
}

function speak(text) {
  if (!("speechSynthesis" in window) || !text) return;
  window.speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = "es-CL";
  utterance.rate = 1;
  window.speechSynthesis.speak(utterance);
}

async function loadTrace() {
  if (!sessionId) return;
  try {
    const detail = await api(`/api/v1/calls/${sessionId}`);
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

async function finishCurrent(silent = false) {
  if (!sessionId || !active) return;
  try {
    await api(`/api/v1/simulator/sessions/${sessionId}/finish`, {method:"POST"});
    setActive(false);
    await loadTrace();
    if (!silent) toast("Prueba finalizada. Ningún dato comercial real fue modificado.");
  } catch (err) {
    if (!silent) toast(err.message);
  }
}

async function startSession() {
  $("#startBtn").disabled = true;
  try {
    if (active) await finishCurrent(true);
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
    $("#startBtn").disabled = false;
  }
}

async function sendMessage(text) {
  if (!active || !sessionId) return;
  const clean = String(text || "").trim();
  if (!clean) return;
  bubble("user", clean);
  $("#messageInput").value = "";
  $("#sendBtn").disabled = true;
  $("#messageInput").disabled = true;
  $("#micBtn").disabled = true;
  try {
    const result = await api(`/api/v1/simulator/sessions/${sessionId}/messages`, {
      method:"POST",
      body:JSON.stringify({message:clean})
    });
    bubble("assistant", result.reply);
    speak(result.reply);
    await loadTrace();
    if (result.ended) setActive(false);
  } catch (err) {
    toast(err.message);
  } finally {
    if (active) {
      $("#sendBtn").disabled = false;
      $("#messageInput").disabled = false;
      $("#micBtn").disabled = !recognition;
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
    $("#micBtn").classList.add("listening");
    $("#voiceHint").textContent = "Escuchando… habla como si estuvieras llamando al negocio.";
  };
  recognition.onend = () => {
    listening = false;
    $("#micBtn").classList.remove("listening");
    $("#voiceHint").textContent = "Puedes escribir o hablar. Las respuestas se leen en voz alta desde el navegador.";
  };
  recognition.onerror = event => toast(`Micrófono: ${event.error || "no disponible"}`);
  recognition.onresult = event => {
    const text = event.results?.[0]?.[0]?.transcript || "";
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
    else recognition.start();
  } catch (err) { toast(err.message); }
});
window.addEventListener("beforeunload", () => {
  if ("speechSynthesis" in window) window.speechSynthesis.cancel();
});

setupRecognition();
setActive(false);
