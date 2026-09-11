(() => {
    const TOKEN_KEY = "helvoca_access_token";
    const CAPABILITIES = [
        ["INFORMATION", "Responder información"],
        ["SERVICES", "Informar servicios"],
        ["BOOKINGS", "Gestionar reservas o citas"],
        ["REQUESTS", "Registrar solicitudes y gestiones"],
        ["HUMAN_TRANSFER", "Transferir a una persona"]
    ];

    const state = { mounted: false, loading: false };

    function token() { return sessionStorage.getItem(TOKEN_KEY) || ""; }

    async function api(path, options = {}) {
        const headers = new Headers(options.headers || {});
        if (options.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
        if (token()) headers.set("Authorization", `Bearer ${token()}`);
        const response = await fetch(path, { ...options, headers });
        let payload = null;
        try { payload = await response.json(); } catch (_) { payload = null; }
        if (!response.ok) throw new Error(payload?.message || payload?.detail || `Error HTTP ${response.status}`);
        return payload;
    }

    function el(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text !== undefined && text !== null) node.textContent = String(text);
        return node;
    }

    function empty(container, text) {
        container.replaceChildren(el("span", "ops-muted", text));
    }

    function formatDate(value) {
        if (!value) return "—";
        try { return new Intl.DateTimeFormat(undefined, { dateStyle: "short", timeStyle: "short" }).format(new Date(value)); }
        catch (_) { return value; }
    }

    function formatDuration(seconds) {
        if (seconds === null || seconds === undefined) return "—";
        const min = Math.floor(seconds / 60);
        const sec = seconds % 60;
        return `${min}:${String(sec).padStart(2, "0")}`;
    }

    function mount() {
        if (state.mounted) return;
        const dashboard = document.querySelector("#dashboardView");
        if (!dashboard) return;

        const panel = document.createElement("section");
        panel.id = "operationsPanel";
        panel.className = "ops-shell";
        panel.innerHTML = `
            <div class="ops-heading">
                <div><div class="eyebrow">Operación</div><h2>Qué está resolviendo Helvoca</h2><p>Actividad real del negocio, no solo configuración.</p></div>
                <div id="voiceReadyBadge" class="ops-ready">Comprobando Realtime…</div>
            </div>
            <div class="ops-metrics">
                <article><strong id="metricCalls">0</strong><span>Llamadas hoy</span></article>
                <article><strong id="metricBookings">0</strong><span>Reservas/citas hoy</span></article>
                <article><strong id="metricRequests">0</strong><span>Solicitudes abiertas</span></article>
                <article><strong id="metricQuestions">0</strong><span>Preguntas pendientes</span></article>
                <article><strong id="metricCustomers">0</strong><span>Clientes</span></article>
                <article><strong id="metricFailures">0</strong><span>Fallos de herramientas hoy</span></article>
            </div>
            <div class="ops-grid">
                <article class="card ops-card"><div class="ops-title"><h3>Llamadas recientes</h3><span id="completedCalls"></span></div><div id="recentCalls" class="ops-list"></div></article>
                <article class="card ops-card"><div class="ops-title"><h3>Próximas reservas o citas</h3></div><div id="upcomingBookings" class="ops-list"></div></article>
                <article class="card ops-card"><div class="ops-title"><h3>Solicitudes</h3><span>cotizaciones, soporte, visitas…</span></div><div id="recentRequests" class="ops-list"></div></article>
                <article class="card ops-card"><div class="ops-title"><h3>Helvoca necesita aprender</h3><span>Responde una vez y queda en conocimiento.</span></div><div id="pendingQuestions" class="ops-list"></div></article>
            </div>
            <article class="card ops-capabilities">
                <div><div class="eyebrow">Motor horizontal</div><h3>Qué puede hacer Helvoca en este negocio</h3><p>Activa solo las capacidades que correspondan. No depende de un rubro específico.</p></div>
                <form id="capabilitiesForm" class="ops-cap-form"></form>
                <div id="capabilitiesMessage" class="ops-message"></div>
            </article>`;

        const heading = dashboard.querySelector(".dashboard-heading");
        if (heading?.nextSibling) heading.parentNode.insertBefore(panel, heading.nextSibling);
        else dashboard.prepend(panel);

        const form = panel.querySelector("#capabilitiesForm");
        for (const [code, label] of CAPABILITIES) {
            const item = el("label", "ops-capability");
            const checkbox = document.createElement("input");
            checkbox.type = "checkbox";
            checkbox.name = code;
            item.append(checkbox, el("span", "", label));
            form.append(item);
        }
        const save = el("button", "button secondary", "Guardar capacidades");
        save.type = "submit";
        form.append(save);
        form.addEventListener("submit", saveCapabilities);

        state.mounted = true;
    }

    async function load() {
        mount();
        const dashboard = document.querySelector("#dashboardView");
        if (!state.mounted || !token() || !dashboard || dashboard.classList.contains("hidden") || state.loading) return;
        state.loading = true;
        try {
            const [overview, capabilities, readiness] = await Promise.all([
                api("/api/v1/dashboard/overview"),
                api("/api/v1/capabilities"),
                api("/api/v1/voice/readiness")
            ]);
            renderOverview(overview);
            renderCapabilities(capabilities);
            renderReadiness(readiness);
        } catch (error) {
            const badge = document.querySelector("#voiceReadyBadge");
            if (badge) { badge.textContent = `No se pudo cargar operación: ${error.message}`; badge.className = "ops-ready error"; }
        } finally {
            state.loading = false;
        }
    }

    function renderOverview(data) {
        setText("metricCalls", data.callsToday);
        setText("metricBookings", data.bookingsCreatedToday);
        setText("metricRequests", data.openRequests);
        setText("metricQuestions", data.unansweredQuestions);
        setText("metricCustomers", data.customers);
        setText("metricFailures", data.toolFailuresToday);
        setText("completedCalls", `${data.completedCallsToday || 0} completadas hoy`);
        renderCalls(data.recentCalls || []);
        renderBookings(data.upcomingBookings || []);
        renderRequests(data.recentRequests || []);
        renderQuestions(data.pendingQuestions || []);
    }

    function renderCalls(items) {
        const target = document.querySelector("#recentCalls");
        if (!items.length) return empty(target, "Todavía no hay llamadas para mostrar.");
        target.replaceChildren(...items.map(item => {
            const row = el("div", "ops-row");
            const main = el("div", "ops-row-main");
            main.append(el("strong", "", item.callerNumber || "Número oculto"), el("span", "ops-muted", `${formatDate(item.startedAt)} · ${formatDuration(item.durationSeconds)}`));
            const outcome = el("div", "ops-outcome", item.resolution || item.status || "Sin resolución");
            row.append(main, outcome);
            return row;
        }));
    }

    function renderBookings(items) {
        const target = document.querySelector("#upcomingBookings");
        if (!items.length) return empty(target, "No hay reservas o citas próximas.");
        target.replaceChildren(...items.map(item => {
            const row = el("div", "ops-row");
            const main = el("div", "ops-row-main");
            main.append(el("strong", "", item.customerName || "Cliente"), el("span", "ops-muted", `${item.serviceName || "Servicio"} · ${formatDate(item.startAt)}`));
            row.append(main, el("span", "ops-pill", item.status));
            return row;
        }));
    }

    function renderRequests(items) {
        const target = document.querySelector("#recentRequests");
        if (!items.length) return empty(target, "No hay solicitudes registradas.");
        target.replaceChildren(...items.map(item => {
            const row = el("div", "ops-row stacked");
            const top = el("div", "ops-row-line");
            const title = el("strong", "", item.subject);
            const select = document.createElement("select");
            for (const status of ["OPEN", "IN_PROGRESS", "RESOLVED", "CANCELLED"]) {
                const option = new Option(status, status, status === item.status, status === item.status);
                select.add(option);
            }
            select.addEventListener("change", () => updateRequestStatus(item.id, select.value));
            top.append(title, select);
            row.append(top, el("span", "ops-muted", `${item.category || "General"} · ${item.priority} · ${formatDate(item.createdAt)}`), el("p", "", item.details));
            return row;
        }));
    }

    function renderQuestions(items) {
        const target = document.querySelector("#pendingQuestions");
        if (!items.length) return empty(target, "No hay preguntas pendientes. Buen signo.");
        target.replaceChildren(...items.map(item => {
            const row = el("div", "ops-row stacked");
            const line = el("div", "ops-row-line");
            line.append(el("strong", "", item.question), el("span", "ops-pill", `×${item.occurrences}`));
            const actions = el("div", "ops-actions");
            const answer = el("button", "button small secondary", "Responder y enseñar");
            answer.type = "button";
            answer.addEventListener("click", () => resolveQuestion(item.id, item.question));
            const ignore = el("button", "button small ghost", "Ignorar");
            ignore.type = "button";
            ignore.addEventListener("click", () => ignoreQuestion(item.id));
            actions.append(answer, ignore);
            row.append(line, el("span", "ops-muted", `Última vez: ${formatDate(item.lastAskedAt)}`), actions);
            return row;
        }));
    }

    function renderCapabilities(data) {
        const map = data.capabilities || {};
        for (const [code] of CAPABILITIES) {
            const input = document.querySelector(`#capabilitiesForm input[name="${code}"]`);
            if (input) input.checked = map[code] !== false;
        }
    }

    function renderReadiness(data) {
        const badge = document.querySelector("#voiceReadyBadge");
        if (!badge) return;
        if (data.realtimeReady) {
            badge.textContent = "Realtime listo";
            badge.className = "ops-ready online";
        } else {
            badge.textContent = `Realtime pendiente: ${(data.missing || []).join(", ")}`;
            badge.className = "ops-ready warning";
        }
    }

    async function saveCapabilities(event) {
        event.preventDefault();
        const enabled = CAPABILITIES.filter(([code]) => document.querySelector(`#capabilitiesForm input[name="${code}"]`)?.checked).map(([code]) => code);
        const message = document.querySelector("#capabilitiesMessage");
        try {
            await api("/api/v1/capabilities", { method: "PUT", body: JSON.stringify({ enabled }) });
            message.textContent = "Capacidades guardadas.";
            message.className = "ops-message success";
        } catch (error) {
            message.textContent = error.message;
            message.className = "ops-message error";
        }
    }

    async function updateRequestStatus(id, status) {
        try {
            await api(`/api/v1/requests/${id}/status`, { method: "PATCH", body: JSON.stringify({ status }) });
            await load();
        } catch (error) { alert(error.message); }
    }

    async function resolveQuestion(id, question) {
        const answer = prompt(`Respuesta oficial para:\n${question}`);
        if (!answer?.trim()) return;
        try {
            await api(`/api/v1/unanswered-questions/${id}/resolve`, { method: "POST", body: JSON.stringify({ answer: answer.trim() }) });
            await load();
        } catch (error) { alert(error.message); }
    }

    async function ignoreQuestion(id) {
        try {
            await api(`/api/v1/unanswered-questions/${id}/ignore`, { method: "POST" });
            await load();
        } catch (error) { alert(error.message); }
    }

    function setText(id, value) {
        const node = document.getElementById(id);
        if (node) node.textContent = value ?? 0;
    }

    document.addEventListener("DOMContentLoaded", () => {
        mount();
        const dashboard = document.querySelector("#dashboardView");
        if (dashboard) {
            new MutationObserver(() => {
                if (!dashboard.classList.contains("hidden")) load();
            }).observe(dashboard, { attributes: true, attributeFilter: ["class"] });
        }
        document.querySelector("#refreshBtn")?.addEventListener("click", () => setTimeout(load, 50));
        load();
    });
})();
