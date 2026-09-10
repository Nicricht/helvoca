const TOKEN_KEY = "helvoca_access_token";
const DAYS = [
    [1, "Lunes"], [2, "Martes"], [3, "Miércoles"], [4, "Jueves"],
    [5, "Viernes"], [6, "Sábado"], [7, "Domingo"]
];
const DAY_NAMES = Object.fromEntries(DAYS);

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

const authView = $("#authView");
const dashboardView = $("#dashboardView");
const registerForm = $("#registerForm");
const loginForm = $("#loginForm");
const setupForm = $("#setupForm");
const phoneForm = $("#phoneForm");
const aiForm = $("#aiForm");
const authMessage = $("#authMessage");
const setupMessage = $("#setupMessage");
const phoneMessage = $("#phoneMessage");
const aiMessage = $("#aiMessage");
const servicesList = $("#servicesList");
const knowledgeList = $("#knowledgeList");
const hoursGrid = $("#hoursGrid");
const phoneList = $("#phoneList");
const proposalPanel = $("#proposalPanel");
const advancedPanel = $("#advancedPanel");

let token = sessionStorage.getItem(TOKEN_KEY) || "";
let currentProposal = null;
let currentStatus = null;

function setToken(value) {
    token = value || "";
    if (token) sessionStorage.setItem(TOKEN_KEY, token);
    else sessionStorage.removeItem(TOKEN_KEY);
}

function detectedTimezone() {
    try { return Intl.DateTimeFormat().resolvedOptions().timeZone || "America/Santiago"; }
    catch (_) { return "America/Santiago"; }
}

function detectedLanguage() {
    const value = (navigator.language || "es").toLowerCase().split("-")[0];
    return /^[a-z]{2,3}$/.test(value) ? value : "es";
}

function showMessage(element, text, kind = "error") {
    element.textContent = text;
    element.classList.remove("hidden", "error", "success");
    element.classList.add(kind);
}

function clearMessage(element) {
    element.textContent = "";
    element.classList.add("hidden");
    element.classList.remove("error", "success");
}

function setBusy(form, busy) {
    $$("button", form).forEach(el => el.disabled = busy);
    form.setAttribute("aria-busy", busy ? "true" : "false");
}

function switchAuth(mode) {
    const registering = mode === "register";
    registerForm.classList.toggle("hidden", !registering);
    loginForm.classList.toggle("hidden", registering);
    $("#registerTab").classList.toggle("active", registering);
    $("#loginTab").classList.toggle("active", !registering);
    $("#registerTab").setAttribute("aria-selected", registering ? "true" : "false");
    $("#loginTab").setAttribute("aria-selected", registering ? "false" : "true");
    clearMessage(authMessage);
}

function showAuth() {
    authView.classList.remove("hidden");
    dashboardView.classList.add("hidden");
    $("#logoutBtn").classList.add("hidden");
    const badge = $("#sessionBadge");
    badge.textContent = "Sin sesión";
    badge.className = "badge muted";
}

function showDashboardShell() {
    authView.classList.add("hidden");
    dashboardView.classList.remove("hidden");
    $("#logoutBtn").classList.remove("hidden");
    const badge = $("#sessionBadge");
    badge.textContent = "Sesión activa";
    badge.className = "badge online";
}

function handleExpiredSession() {
    setToken("");
    currentProposal = null;
    showAuth();
    switchAuth("login");
    showMessage(authMessage, "Tu sesión expiró. Ingresa nuevamente.");
}

async function api(path, options = {}, authenticated = true) {
    const headers = new Headers(options.headers || {});
    if (options.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
    if (authenticated && token) headers.set("Authorization", `Bearer ${token}`);
    const response = await fetch(path, { ...options, headers });
    let payload = null;
    const contentType = response.headers.get("content-type") || "";
    if (contentType.includes("application/json")) {
        try { payload = await response.json(); } catch (_) { payload = null; }
    } else if (response.status !== 204) {
        try { payload = await response.text(); } catch (_) { payload = null; }
    }
    if (!response.ok) {
        const message = payload?.message || payload?.detail || payload?.error ||
            (typeof payload === "string" && payload) || `Error HTTP ${response.status}`;
        const error = new Error(message);
        error.status = response.status;
        if (response.status === 401 && authenticated) handleExpiredSession();
        throw error;
    }
    return payload;
}

function addServiceRow(service = {}) {
    const node = $("#serviceTemplate").content.firstElementChild.cloneNode(true);
    if (service.id) node.dataset.id = service.id;
    $("[data-field=name]", node).value = service.name || "";
    $("[data-field=durationMinutes]", node).value = service.durationMinutes || 30;
    $("[data-field=price]", node).value = service.price ?? "";
    $("[data-field=description]", node).value = service.description || "";
    $(".remove-row", node).addEventListener("click", () => {
        if ($$(".service-row", servicesList).length > 1) node.remove();
    });
    servicesList.appendChild(node);
}

function renderServices(services = []) {
    servicesList.innerHTML = "";
    const active = services.filter(s => s.active !== false);
    (active.length ? active : [{}]).forEach(addServiceRow);
}

function addKnowledgeRow(item = {}) {
    const node = $("#knowledgeTemplate").content.firstElementChild.cloneNode(true);
    if (item.id) node.dataset.id = item.id;
    $("[data-field=title]", node).value = item.title || "";
    $("[data-field=category]", node).value = item.category || "";
    $("[data-field=content]", node).value = item.content || "";
    $(".remove-row", node).addEventListener("click", () => node.remove());
    knowledgeList.appendChild(node);
}

function renderKnowledge(items = []) {
    knowledgeList.innerHTML = "";
    items.filter(i => i.active !== false).forEach(addKnowledgeRow);
}

function dayOptions(selected) {
    return DAYS.map(([value, label]) =>
        `<option value="${value}" ${Number(selected) === value ? "selected" : ""}>${label}</option>`
    ).join("");
}

function addHourRow(hour = { dayOfWeek: 1, openTime: "09:00", closeTime: "18:00" }) {
    const row = document.createElement("div");
    row.className = "hour-row interval-row";
    row.innerHTML = `
        <label class="day-select">Día<select data-field="dayOfWeek">${dayOptions(hour.dayOfWeek)}</select></label>
        <label>Abre<input data-field="openTime" type="time" required value="${String(hour.openTime || "09:00").slice(0, 5)}"></label>
        <span class="sep">→</span>
        <label>Cierra<input data-field="closeTime" type="time" required value="${String(hour.closeTime || "18:00").slice(0, 5)}"></label>
        <button class="icon-button remove-row" type="button" aria-label="Eliminar intervalo">×</button>`;
    $(".remove-row", row).addEventListener("click", () => row.remove());
    hoursGrid.appendChild(row);
}

function renderHours(hours = []) {
    hoursGrid.innerHTML = "";
    if (hours.length) hours.forEach(addHourRow);
    else for (let day = 1; day <= 5; day++) addHourRow({ dayOfWeek: day, openTime: "09:00", closeTime: "18:00" });
}

function ensureAddHourButton() {
    if ($("#addHourBtn")) return;
    const heading = hoursGrid.previousElementSibling;
    const button = document.createElement("button");
    button.id = "addHourBtn";
    button.className = "button small ghost";
    button.type = "button";
    button.textContent = "+ Intervalo";
    button.addEventListener("click", () => addHourRow());
    heading.appendChild(button);
}

async function refreshPhoneState() {
    const [phones, status] = await Promise.all([api("/api/v1/phone-numbers"), api("/api/v1/onboarding/status")]);
    renderPhones(phones);
    applyStatus(status);
}

async function togglePhone(phone, button) {
    clearMessage(phoneMessage);
    button.disabled = true;
    try {
        await api(`/api/v1/phone-numbers/${phone.id}/active`, {
            method: "PATCH", body: JSON.stringify({ active: !phone.active })
        });
        await refreshPhoneState();
        showMessage(phoneMessage, phone.active ? "Número desactivado." : "Número activado.", "success");
    } catch (error) {
        if (error.status !== 401) showMessage(phoneMessage, error.message || "No fue posible cambiar el estado del número.");
    }
}

function renderPhones(phones = []) {
    phoneList.innerHTML = "";
    if (!phones.length) {
        phoneList.innerHTML = '<span class="muted-text">Aún no hay números registrados.</span>';
        return;
    }
    phones.forEach(phone => {
        const row = document.createElement("div");
        row.className = "phone-item";
        const number = document.createElement("span");
        number.className = "phone-number";
        number.textContent = phone.phoneNumber;
        const state = document.createElement("span");
        state.className = `phone-state ${phone.active ? "active" : "inactive"}`;
        state.textContent = phone.active ? "ACTIVO" : "INACTIVO";
        const action = document.createElement("button");
        action.type = "button";
        action.className = "button small ghost phone-toggle";
        action.textContent = phone.active ? "Desactivar" : "Activar";
        action.addEventListener("click", () => togglePhone(phone, action));
        row.append(number, state, action);
        phoneList.appendChild(row);
    });
}

const nextStepText = {
    CONFIGURE_BUSINESS: "Completa la información principal del negocio.",
    ADD_SERVICE: "Falta confirmar al menos un servicio reservable.",
    CONFIGURE_HOURS: "Falta confirmar el horario de atención.",
    CONNECT_PHONE_NUMBER: "La configuración ya casi está. Falta conectar un número telefónico activo.",
    OPTIONAL_HUMAN_TRANSFER: "Helvoca puede atender llamadas. Opcional: agrega un teléfono para transferencia humana.",
    OPTIONAL_KNOWLEDGE: "Helvoca puede atender llamadas. Opcional: agrega respuestas frecuentes.",
    READY: "Configuración completa. Helvoca está lista para atender llamadas."
};

function applyStatus(status) {
    currentStatus = status;
    $$(".status-card", $("#statusGrid")).forEach(card => card.classList.toggle("done", Boolean(status[card.dataset.key])));
    $("#readyBanner").classList.toggle("hidden", !status.readyForCalls);
    $("#nextStepBanner").textContent = nextStepText[status.nextStep] || `Siguiente paso: ${status.nextStep}`;
}

async function loadDashboard() {
    showDashboardShell();
    try {
        const [me, business, status, services, hours, knowledge, phones] = await Promise.all([
            api("/api/v1/auth/me"), api("/api/v1/business"), api("/api/v1/onboarding/status"),
            api("/api/v1/services"), api("/api/v1/business/hours"), api("/api/v1/knowledge?activeOnly=false"),
            api("/api/v1/phone-numbers")
        ]);
        $("#welcomeText").textContent = `${me.email} · Helvoca solo guardará lo que tú confirmes.`;
        setupForm.elements.businessName.value = business.name || "";
        setupForm.elements.timezone.value = business.timezone || detectedTimezone();
        setupForm.elements.language.value = business.language || detectedLanguage();
        setupForm.elements.humanTransferPhone.value = business.humanTransferPhone || "";
        renderServices(services);
        renderHours(hours);
        renderKnowledge(knowledge);
        renderPhones(phones);
        applyStatus(status);
        ensureAddHourButton();
    } catch (error) {
        if (error.status !== 401) showMessage(aiMessage, error.message || "No pude cargar la configuración.");
    }
}

function collectServices() {
    return $$(".service-row", servicesList).map(row => {
        const priceRaw = $("[data-field=price]", row).value;
        return {
            id: row.dataset.id || null,
            name: $("[data-field=name]", row).value.trim(),
            description: $("[data-field=description]", row).value.trim() || null,
            durationMinutes: Number($("[data-field=durationMinutes]", row).value),
            price: priceRaw === "" ? null : Number(priceRaw)
        };
    }).filter(service => service.name);
}

function collectHours() {
    return $$(".interval-row", hoursGrid).map(row => ({
        dayOfWeek: Number($("[data-field=dayOfWeek]", row).value),
        openTime: $("[data-field=openTime]", row).value,
        closeTime: $("[data-field=closeTime]", row).value
    }));
}

function collectKnowledge() {
    return $$(".knowledge-row", knowledgeList).map(row => ({
        id: row.dataset.id || null,
        title: $("[data-field=title]", row).value.trim(),
        category: $("[data-field=category]", row).value.trim() || null,
        content: $("[data-field=content]", row).value.trim()
    })).filter(item => item.title && item.content);
}

function addProposalLine(container, primary, secondary = "") {
    const row = document.createElement("div");
    row.className = "proposal-line";
    const strong = document.createElement("strong");
    strong.textContent = primary;
    row.appendChild(strong);
    if (secondary) {
        const small = document.createElement("small");
        small.textContent = secondary;
        row.appendChild(small);
    }
    container.appendChild(row);
}

function renderProposal(proposal) {
    currentProposal = proposal;
    $("#proposalBusinessName").textContent = proposal.businessName || "Propuesta de Helvoca";
    $("#proposalSummary").textContent = proposal.sourceSummary || "Revisa los datos detectados antes de confirmarlos.";
    const sourceBadge = $("#sourceBadge");
    sourceBadge.textContent = proposal.sourceReadable ? "Fuente leída" : "Fuente limitada";
    sourceBadge.className = proposal.sourceReadable ? "badge online" : "badge muted";

    const services = $("#proposalServices");
    const hours = $("#proposalHours");
    const knowledge = $("#proposalKnowledge");
    services.innerHTML = "";
    hours.innerHTML = "";
    knowledge.innerHTML = "";
    (proposal.services || []).forEach(item => addProposalLine(services, item.name, `${item.durationMinutes || 30} min${item.price != null ? ` · ${item.price}` : ""}`));
    (proposal.hours || []).forEach(item => addProposalLine(hours, DAY_NAMES[item.dayOfWeek] || `Día ${item.dayOfWeek}`, `${String(item.openTime).slice(0,5)} → ${String(item.closeTime).slice(0,5)}`));
    (proposal.knowledge || []).forEach(item => addProposalLine(knowledge, item.title, item.content));
    if (!(proposal.services || []).length) addProposalLine(services, "No detectado", "Revisa manualmente los servicios.");
    if (!(proposal.hours || []).length) addProposalLine(hours, "No detectado", "Revisa manualmente los horarios.");
    if (!(proposal.knowledge || []).length) addProposalLine(knowledge, "Sin respuestas adicionales", "Esta parte es opcional.");

    const warnings = $("#proposalWarnings");
    warnings.innerHTML = "";
    (proposal.warnings || []).forEach(text => {
        const p = document.createElement("p");
        p.textContent = `⚠ ${text}`;
        warnings.appendChild(p);
    });
    warnings.classList.toggle("hidden", !(proposal.warnings || []).length);
    proposalPanel.classList.remove("hidden");
}

async function analyzeBusiness(sourceUrl, businessName) {
    clearMessage(aiMessage);
    proposalPanel.classList.add("hidden");
    currentProposal = null;
    setBusy(aiForm, true);
    showMessage(aiMessage, "Analizando la fuente pública y preparando una propuesta…", "success");
    try {
        const proposal = await api("/api/v1/onboarding/analyze", {
            method: "POST",
            body: JSON.stringify({ businessName, sourceUrl })
        });
        clearMessage(aiMessage);
        renderProposal(proposal);
    } catch (error) {
        if (error.status !== 401) showMessage(aiMessage, error.message || "No pude analizar ese enlace. Prueba con el sitio web oficial.");
    } finally {
        setBusy(aiForm, false);
    }
}

function prefillProposal(proposal) {
    setupForm.elements.businessName.value = proposal.businessName || setupForm.elements.businessName.value;
    setupForm.elements.timezone.value = proposal.timezone || setupForm.elements.timezone.value || detectedTimezone();
    setupForm.elements.language.value = proposal.language || setupForm.elements.language.value || detectedLanguage();
    renderServices((proposal.services || []).map(item => ({ ...item, id: null })));
    if ((proposal.hours || []).length) renderHours(proposal.hours);
    renderKnowledge((proposal.knowledge || []).map(item => ({ ...item, id: null, active: true })));
}

function showAdvanced() {
    advancedPanel.classList.remove("hidden");
    $("#advancedToggleBtn").textContent = "Ocultar edición manual";
}

async function confirmProposal() {
    if (!currentProposal) return;
    if (!(currentProposal.services || []).length || !(currentProposal.hours || []).length) {
        prefillProposal(currentProposal);
        showAdvanced();
        showMessage(setupMessage, "La IA no encontró todos los datos obligatorios. Completa servicios y horarios y luego guarda.");
        advancedPanel.scrollIntoView({ behavior: "smooth", block: "start" });
        return;
    }
    if (currentStatus?.servicesConfigured || currentStatus?.scheduleConfigured) {
        const accepted = window.confirm("Ya existe configuración. ¿Quieres reemplazar los servicios y horarios actuales por esta propuesta?");
        if (!accepted) return;
    }
    const button = $("#confirmProposalBtn");
    button.disabled = true;
    clearMessage(aiMessage);
    try {
        const payload = {
            businessName: currentProposal.businessName,
            timezone: currentProposal.timezone || detectedTimezone(),
            language: currentProposal.language || detectedLanguage(),
            humanTransferPhone: setupForm.elements.humanTransferPhone.value.trim() || null,
            services: currentProposal.services.map(item => ({ id: null, ...item })),
            hours: currentProposal.hours,
            knowledge: (currentProposal.knowledge || []).map(item => ({ id: null, ...item }))
        };
        await api("/api/v1/onboarding/setup", { method: "PUT", body: JSON.stringify(payload) });
        proposalPanel.classList.add("hidden");
        currentProposal = null;
        await loadDashboard();
        showMessage(aiMessage, "Configuración confirmada. Si aún falta el teléfono, ese es el único paso obligatorio pendiente.", "success");
    } catch (error) {
        if (error.status !== 401) showMessage(aiMessage, error.message || "No pude guardar la propuesta.");
    } finally {
        button.disabled = false;
    }
}

registerForm.addEventListener("submit", async event => {
    event.preventDefault();
    clearMessage(authMessage);
    const f = new FormData(registerForm);
    const sourceUrl = String(f.get("sourceUrl") || "").trim();
    const businessName = String(f.get("businessName") || "").trim();
    setBusy(registerForm, true);
    try {
        const payload = {
            adminName: String(f.get("adminName") || "").trim(),
            email: String(f.get("email") || "").trim(),
            password: String(f.get("password") || ""),
            businessName,
            timezone: detectedTimezone(),
            language: detectedLanguage(),
            humanTransferPhone: null
        };
        const result = await api("/api/v1/auth/register", { method: "POST", body: JSON.stringify(payload) }, false);
        setToken(result.accessToken);
        await loadDashboard();
        if (sourceUrl) {
            aiForm.elements.sourceUrl.value = sourceUrl;
            await analyzeBusiness(sourceUrl, businessName);
        } else {
            showMessage(aiMessage, "Cuenta creada. Pega la web, Instagram o Google Maps del negocio para que Helvoca prepare la configuración.", "success");
        }
    } catch (error) {
        showMessage(authMessage, error.message || "No fue posible crear la empresa.");
    } finally {
        setBusy(registerForm, false);
    }
});

loginForm.addEventListener("submit", async event => {
    event.preventDefault();
    clearMessage(authMessage);
    const f = new FormData(loginForm);
    setBusy(loginForm, true);
    try {
        const result = await api("/api/v1/auth/login", {
            method: "POST",
            body: JSON.stringify({ email: String(f.get("email") || "").trim(), password: String(f.get("password") || "") })
        }, false);
        setToken(result.accessToken);
        await loadDashboard();
    } catch (error) {
        showMessage(authMessage, error.message || "Credenciales inválidas.");
    } finally {
        setBusy(loginForm, false);
    }
});

aiForm.addEventListener("submit", async event => {
    event.preventDefault();
    const sourceUrl = String(new FormData(aiForm).get("sourceUrl") || "").trim();
    const businessName = setupForm.elements.businessName.value.trim();
    await analyzeBusiness(sourceUrl, businessName);
});

setupForm.addEventListener("submit", async event => {
    event.preventDefault();
    clearMessage(setupMessage);
    const services = collectServices();
    const hours = collectHours();
    if (!services.length) { showMessage(setupMessage, "Añade al menos un servicio antes de guardar."); return; }
    if (!hours.length) { showMessage(setupMessage, "Configura al menos un intervalo de atención."); return; }
    setBusy(setupForm, true);
    try {
        const payload = {
            businessName: setupForm.elements.businessName.value.trim(),
            timezone: setupForm.elements.timezone.value.trim(),
            language: setupForm.elements.language.value.trim(),
            humanTransferPhone: setupForm.elements.humanTransferPhone.value.trim() || null,
            services, hours, knowledge: collectKnowledge()
        };
        await api("/api/v1/onboarding/setup", { method: "PUT", body: JSON.stringify(payload) });
        await loadDashboard();
        showMessage(setupMessage, "Cambios guardados correctamente.", "success");
    } catch (error) {
        if (error.status !== 401) showMessage(setupMessage, error.message || "No fue posible guardar la configuración.");
    } finally {
        setBusy(setupForm, false);
    }
});

phoneForm.addEventListener("submit", async event => {
    event.preventDefault();
    clearMessage(phoneMessage);
    const f = new FormData(phoneForm);
    setBusy(phoneForm, true);
    try {
        await api("/api/v1/phone-numbers", {
            method: "POST",
            body: JSON.stringify({
                phoneNumber: String(f.get("phoneNumber") || "").trim(),
                externalId: String(f.get("externalId") || "").trim() || null,
                active: true
            })
        });
        phoneForm.reset();
        await refreshPhoneState();
        showMessage(phoneMessage, "Número conectado a este negocio.", "success");
    } catch (error) {
        if (error.status !== 401) showMessage(phoneMessage, error.message || "No fue posible conectar el número.");
    } finally {
        setBusy(phoneForm, false);
    }
});

$("#registerTab").addEventListener("click", () => switchAuth("register"));
$("#loginTab").addEventListener("click", () => switchAuth("login"));
$("#addServiceBtn").addEventListener("click", () => addServiceRow());
$("#addKnowledgeBtn").addEventListener("click", () => addKnowledgeRow());
$("#confirmProposalBtn").addEventListener("click", confirmProposal);
$("#editProposalBtn").addEventListener("click", () => {
    if (!currentProposal) return;
    prefillProposal(currentProposal);
    showAdvanced();
    advancedPanel.scrollIntoView({ behavior: "smooth", block: "start" });
});
$("#advancedToggleBtn").addEventListener("click", () => {
    const hidden = advancedPanel.classList.toggle("hidden");
    $("#advancedToggleBtn").textContent = hidden ? "Editar manualmente" : "Ocultar edición manual";
});
$("#refreshBtn").addEventListener("click", async event => {
    const button = event.currentTarget;
    button.disabled = true;
    try { await loadDashboard(); } finally { button.disabled = false; }
});
$("#logoutBtn").addEventListener("click", () => {
    setToken("");
    currentProposal = null;
    showAuth();
    switchAuth("login");
});

(async function boot() {
    ensureAddHourButton();
    switchAuth("register");
    if (!token) { showAuth(); return; }
    try {
        await api("/api/v1/auth/me");
        await loadDashboard();
    } catch (_) {
        if (token) handleExpiredSession();
    }
})();
