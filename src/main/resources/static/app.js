const TOKEN_KEY = "helvoca_access_token";
const DAYS = [
    [1, "Lunes"], [2, "Martes"], [3, "Miércoles"], [4, "Jueves"],
    [5, "Viernes"], [6, "Sábado"], [7, "Domingo"]
];

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

const authView = $("#authView");
const dashboardView = $("#dashboardView");
const registerForm = $("#registerForm");
const loginForm = $("#loginForm");
const setupForm = $("#setupForm");
const phoneForm = $("#phoneForm");
const authMessage = $("#authMessage");
const setupMessage = $("#setupMessage");
const phoneMessage = $("#phoneMessage");
const servicesList = $("#servicesList");
const knowledgeList = $("#knowledgeList");
const hoursGrid = $("#hoursGrid");
const phoneList = $("#phoneList");

let token = sessionStorage.getItem(TOKEN_KEY) || "";

function setToken(value) {
    token = value || "";
    if (token) sessionStorage.setItem(TOKEN_KEY, token);
    else sessionStorage.removeItem(TOKEN_KEY);
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
        throw error;
    }
    return payload;
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

function addServiceRow(service = {}) {
    const node = $("#serviceTemplate").content.firstElementChild.cloneNode(true);
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
        <label>Abre<input data-field="openTime" type="time" required value="${String(hour.openTime || "09:00").slice(0,5)}"></label>
        <span class="sep">→</span>
        <label>Cierra<input data-field="closeTime" type="time" required value="${String(hour.closeTime || "18:00").slice(0,5)}"></label>
        <button class="icon-button remove-row" type="button" aria-label="Eliminar intervalo">×</button>`;
    $(".remove-row", row).addEventListener("click", () => row.remove());
    hoursGrid.appendChild(row);
}

function renderHours(hours = []) {
    hoursGrid.innerHTML = "";
    if (hours.length) {
        hours.forEach(addHourRow);
    } else {
        for (let day = 1; day <= 5; day++) addHourRow({ dayOfWeek: day, openTime: "09:00", closeTime: "18:00" });
    }
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
        number.textContent = phone.phoneNumber;
        const state = document.createElement("span");
        state.textContent = phone.active ? "ACTIVO" : "INACTIVO";
        if (!phone.active) state.style.color = "var(--muted)";
        row.append(number, state);
        phoneList.appendChild(row);
    });
}

const nextStepText = {
    CONFIGURE_BUSINESS: "Completa la información principal del negocio.",
    ADD_SERVICE: "Añade al menos un servicio que Helvoca pueda reservar.",
    CONFIGURE_HOURS: "Configura al menos un intervalo de atención.",
    CONNECT_PHONE_NUMBER: "Falta conectar un número telefónico activo.",
    OPTIONAL_HUMAN_TRANSFER: "El núcleo ya está listo. Opcional: configura un teléfono para transferencia humana.",
    OPTIONAL_KNOWLEDGE: "El núcleo ya está listo. Opcional: añade respuestas frecuentes para enriquecer la IA.",
    READY: "Configuración completa. Helvoca está lista para atender llamadas."
};

function applyStatus(status) {
    $$(".status-card", $("#statusGrid")).forEach(card => {
        card.classList.toggle("done", Boolean(status[card.dataset.key]));
    });
    $("#readyBanner").classList.toggle("hidden", !status.readyForCalls);
    $("#nextStepBanner").textContent = nextStepText[status.nextStep] || `Siguiente paso: ${status.nextStep}`;
}

async function loadDashboard() {
    showDashboardShell();
    try {
        const [me, business, status, services, hours, knowledge, phones] = await Promise.all([
            api("/api/v1/auth/me"),
            api("/api/v1/business"),
            api("/api/v1/onboarding/status"),
            api("/api/v1/services"),
            api("/api/v1/business/hours"),
            api("/api/v1/knowledge?activeOnly=false"),
            api("/api/v1/phone-numbers")
        ]);

        $("#welcomeText").textContent = `${me.email} · Configura los datos reales que la IA podrá utilizar.`;
        setupForm.elements.businessName.value = business.name || "";
        setupForm.elements.timezone.value = business.timezone || "America/Santiago";
        setupForm.elements.language.value = business.language || "es";
        setupForm.elements.humanTransferPhone.value = business.humanTransferPhone || "";
        renderServices(services);
        renderHours(hours);
        renderKnowledge(knowledge);
        renderPhones(phones);
        applyStatus(status);
        ensureAddHourButton();
    } catch (error) {
        if (error.status === 401) {
            setToken("");
            showAuth();
            showMessage(authMessage, "Tu sesión expiró. Ingresa nuevamente.");
            return;
        }
        showMessage(setupMessage, error.message || "No pude cargar la configuración.");
    }
}

function collectServices() {
    return $$(".service-row", servicesList).map(row => {
        const name = $("[data-field=name]", row).value.trim();
        const priceRaw = $("[data-field=price]", row).value;
        return {
            name,
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
        title: $("[data-field=title]", row).value.trim(),
        category: $("[data-field=category]", row).value.trim() || null,
        content: $("[data-field=content]", row).value.trim()
    })).filter(item => item.title && item.content);
}

registerForm.addEventListener("submit", async event => {
    event.preventDefault();
    clearMessage(authMessage);
    setBusy(registerForm, true);
    try {
        const f = new FormData(registerForm);
        const payload = {
            adminName: f.get("adminName").trim(),
            email: f.get("email").trim(),
            password: f.get("password"),
            businessName: f.get("businessName").trim(),
            timezone: f.get("timezone").trim(),
            language: f.get("language").trim(),
            humanTransferPhone: f.get("humanTransferPhone").trim() || null
        };
        const result = await api("/api/v1/auth/register", { method: "POST", body: JSON.stringify(payload) }, false);
        setToken(result.accessToken);
        await loadDashboard();
    } catch (error) {
        showMessage(authMessage, error.message || "No fue posible crear la empresa.");
    } finally {
        setBusy(registerForm, false);
    }
});

loginForm.addEventListener("submit", async event => {
    event.preventDefault();
    clearMessage(authMessage);
    setBusy(loginForm, true);
    try {
        const f = new FormData(loginForm);
        const result = await api("/api/v1/auth/login", {
            method: "POST",
            body: JSON.stringify({ email: f.get("email").trim(), password: f.get("password") })
        }, false);
        setToken(result.accessToken);
        await loadDashboard();
    } catch (error) {
        showMessage(authMessage, error.message || "Credenciales inválidas.");
    } finally {
        setBusy(loginForm, false);
    }
});

setupForm.addEventListener("submit", async event => {
    event.preventDefault();
    clearMessage(setupMessage);
    const services = collectServices();
    const hours = collectHours();
    if (!services.length) {
        showMessage(setupMessage, "Añade al menos un servicio antes de guardar.");
        return;
    }
    if (!hours.length) {
        showMessage(setupMessage, "Configura al menos un intervalo de atención.");
        return;
    }

    setBusy(setupForm, true);
    try {
        const payload = {
            businessName: setupForm.elements.businessName.value.trim(),
            timezone: setupForm.elements.timezone.value.trim(),
            language: setupForm.elements.language.value.trim(),
            humanTransferPhone: setupForm.elements.humanTransferPhone.value.trim() || null,
            services,
            hours,
            knowledge: collectKnowledge()
        };
        const status = await api("/api/v1/onboarding/setup", { method: "PUT", body: JSON.stringify(payload) });
        applyStatus(status);
        showMessage(setupMessage, "Configuración guardada correctamente.", "success");
    } catch (error) {
        showMessage(setupMessage, error.message || "No fue posible guardar la configuración.");
    } finally {
        setBusy(setupForm, false);
    }
});

phoneForm.addEventListener("submit", async event => {
    event.preventDefault();
    clearMessage(phoneMessage);
    setBusy(phoneForm, true);
    try {
        const f = new FormData(phoneForm);
        await api("/api/v1/phone-numbers", {
            method: "POST",
            body: JSON.stringify({
                phoneNumber: f.get("phoneNumber").trim(),
                externalId: f.get("externalId").trim() || null,
                active: true
            })
        });
        phoneForm.reset();
        const [phones, status] = await Promise.all([api("/api/v1/phone-numbers"), api("/api/v1/onboarding/status")]);
        renderPhones(phones);
        applyStatus(status);
        showMessage(phoneMessage, "Número conectado a este negocio.", "success");
    } catch (error) {
        showMessage(phoneMessage, error.message || "No fue posible conectar el número.");
    } finally {
        setBusy(phoneForm, false);
    }
});

$("#registerTab").addEventListener("click", () => switchAuth("register"));
$("#loginTab").addEventListener("click", () => switchAuth("login"));
$("#addServiceBtn").addEventListener("click", () => addServiceRow());
$("#addKnowledgeBtn").addEventListener("click", () => addKnowledgeRow());
$("#refreshBtn").addEventListener("click", loadDashboard);
$("#logoutBtn").addEventListener("click", () => { setToken(""); showAuth(); switchAuth("login"); });

(async function boot() {
    ensureAddHourButton();
    if (!token) {
        showAuth();
        return;
    }
    try {
        await api("/api/v1/auth/me");
        await loadDashboard();
    } catch (_) {
        setToken("");
        showAuth();
        switchAuth("login");
    }
})();