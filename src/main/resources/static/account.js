(() => {
  const TOKEN_KEY = "helvoca_access_token";
  const token = sessionStorage.getItem(TOKEN_KEY) || "";
  const $ = selector => document.querySelector(selector);

  if (!token) {
    location.replace("/");
    return;
  }

  const statusLabels = {
    ACTIVE: "Activo",
    TRIALING: "Período de prueba",
    PAST_DUE: "Pago pendiente",
    SUSPENDED: "Suspendido",
    CANCELLED: "Cancelado",
    CANCELED: "Cancelado",
    INACTIVE: "Inactivo"
  };
  const roleLabels = {
    BUSINESS_OWNER: "Propietario",
    BUSINESS_ADMIN: "Administrador",
    MANAGER: "Encargado",
    RECEPTION: "Recepción / Caja",
    STAFF: "Personal",
    KITCHEN: "Preparación / Cocina",
    DISPATCH: "Despacho",
    PROFESSIONAL: "Profesional",
    WAREHOUSE: "Bodega / Inventario",
    SALES: "Ventas",
    OPERATOR: "Operador",
    ADMIN: "Administrador"
  };
  const meterLabels = {
    VOICE_SECONDS: "Llamadas de voz",
    WHATSAPP_MESSAGES: "Mensajes de WhatsApp",
    SMS_MESSAGES: "Mensajes SMS",
    AI_TOKENS: "Procesamiento de IA"
  };

  function humanCode(value, labels = {}) {
    if (!value) return "Sin información";
    const raw = String(value).toUpperCase();
    if (labels[raw]) return labels[raw];
    return raw.toLowerCase().split("_").filter(Boolean)
      .map(word => word.charAt(0).toUpperCase() + word.slice(1)).join(" ");
  }

  function formatDate(value) {
    if (!value) return "Sin información";
    try {
      return new Intl.DateTimeFormat("es-CL", { day: "numeric", month: "short", year: "numeric" })
        .format(new Date(value));
    } catch (_) {
      return "Sin información";
    }
  }

  function formatQuantity(item = {}) {
    const quantity = Number(item.quantity || 0);
    const unit = String(item.unit || "").toUpperCase();
    if (unit === "SECONDS") return `${Math.ceil(quantity / 60)} min`;
    if (unit === "MINUTES") return `${Math.round(quantity)} min`;
    if (unit === "MESSAGES") return `${Math.round(quantity)} mensajes`;
    return new Intl.NumberFormat("es-CL", { maximumFractionDigits: 2 }).format(quantity);
  }

  async function api(path) {
    const response = await fetch(path, {
      headers: { Authorization: `Bearer ${token}`, Accept: "application/json" }
    });
    let payload = null;
    try { payload = await response.json(); } catch (_) {}
    if (!response.ok) {
      if (response.status === 401) {
        sessionStorage.removeItem(TOKEN_KEY);
        location.replace("/");
      }
      const error = new Error(payload?.message || payload?.detail || `HTTP ${response.status}`);
      error.status = response.status;
      throw error;
    }
    return payload;
  }

  function renderIdentity(me = {}) {
    $("#accountEmail").textContent = me.email || "Sin correo disponible";
    const roles = Array.isArray(me.roles) ? me.roles : [];
    $("#accountRole").textContent = roles.length
      ? roles.map(role => humanCode(role, roleLabels)).join(", ")
      : "Sin rol informado";
    return roles;
  }

  function renderSubscription(sub = {}) {
    $("#planName").textContent = sub.planName || humanCode(sub.publicPlanCode || sub.plan) || "Sin plan";
    $("#planStatus").textContent = humanCode(sub.status, statusLabels);

    const included = Math.max(0, Number(sub.includedMinutes || 0));
    const used = Math.max(0, Number(sub.usedMinutes || 0));
    const percentage = included > 0 ? Math.min(100, Math.round((used / included) * 100)) : 0;

    $("#voiceUsage").textContent = included > 0 ? `${used} de ${included} min` : `${used} min usados`;
    $("#voiceUsagePercent").textContent = included > 0 ? `${percentage}%` : "Sin límite informado";
    $("#voiceUsageProgress").setAttribute("aria-valuenow", String(percentage));
    $("#voiceUsageBar").style.width = `${percentage}%`;
    $("#billingPeriod").textContent = `${formatDate(sub.currentPeriodStart)} – ${formatDate(sub.currentPeriodEnd)}`;
    $("#concurrentCalls").textContent = String(Math.max(0, Number(sub.maxConcurrentCalls || 0)));
    $("#overageMinutes").textContent = `${Math.max(0, Number(sub.overageMinutes || 0))} min`;

    const connection = $("#billingConnection");
    connection.textContent = sub.billingProviderConnected
      ? "Facturación conectada. No mostramos credenciales del proveedor."
      : "Facturación no conectada. Esta pantalla no inicia conexiones ni cobros.";
    connection.classList.toggle("connected", Boolean(sub.billingProviderConnected));
    $("#usagePeriod").textContent = sub.currentPeriodStart && sub.currentPeriodEnd
      ? `${formatDate(sub.currentPeriodStart)} – ${formatDate(sub.currentPeriodEnd)}`
      : "";
  }

  function renderUsage(items = []) {
    const state = $("#usageState");
    const list = $("#usageList");
    if (!Array.isArray(items) || !items.length) {
      state.textContent = "Todavía no hay consumo detallado registrado en este período.";
      state.classList.remove("hidden");
      list.innerHTML = "";
      return;
    }
    state.classList.add("hidden");
    list.innerHTML = items.map(item => `
      <article class="usage-item">
        <div>
          <span>${escapeHtml(humanCode(item.meterKey, meterLabels))}</span>
          <small>${Math.max(0, Number(item.eventCount || 0))} eventos registrados</small>
        </div>
        <strong>${escapeHtml(formatQuantity(item))}</strong>
      </article>`).join("");
  }

  function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>'"]/g, char => ({
      "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;"
    }[char]));
  }

  function usageWindow(sub = {}) {
    const to = sub.currentPeriodEnd ? new Date(sub.currentPeriodEnd) : new Date();
    const from = sub.currentPeriodStart ? new Date(sub.currentPeriodStart) : new Date(to.getTime() - 30 * 86400000);
    return { from: from.toISOString(), to: to.toISOString() };
  }

  async function load() {
    $("#accountLoading").classList.remove("hidden");
    $("#accountError").classList.add("hidden");

    const [meResult, subscriptionResult] = await Promise.allSettled([
      api("/api/v1/auth/me"),
      api("/api/v1/subscription")
    ]);

    let roles = [];
    if (meResult.status === "fulfilled") roles = renderIdentity(meResult.value);
    else {
      $("#accountEmail").textContent = "No disponible";
      $("#accountRole").textContent = "No disponible";
    }

    if (subscriptionResult.status === "rejected") {
      $("#accountError").classList.remove("hidden");
      $("#usageState").textContent = "El detalle de uso depende del período de tu plan.";
      $("#accountLoading").classList.add("hidden");
      $("#accountApp").classList.remove("hidden");
      return;
    }

    const sub = subscriptionResult.value || {};
    renderSubscription(sub);
    $("#accountApp").classList.remove("hidden");
    $("#accountLoading").classList.add("hidden");

    if (!roles.includes("BUSINESS_ADMIN")) {
      $("#usageState").textContent = "El detalle de uso está disponible solo para administradores del negocio.";
      return;
    }

    const window = usageWindow(sub);
    try {
      const usage = await api(`/api/v1/usage/summary?from=${encodeURIComponent(window.from)}&to=${encodeURIComponent(window.to)}`);
      renderUsage(usage);
    } catch (error) {
      $("#usageState").textContent = error.status === 403
        ? "El detalle de uso está disponible solo para administradores del negocio."
        : "No pudimos cargar el detalle de uso. El resumen de tu plan sigue disponible.";
    }
  }

  $("#accountLogoutBtn").addEventListener("click", () => {
    sessionStorage.removeItem(TOKEN_KEY);
    location.assign("/");
  });

  load();
})();
