(() => {
  const root = document.querySelector("#homeBusinessWorkspace");
  const dashboard = document.querySelector("#dashboardView");
  const statusGrid = document.querySelector("#statusGrid");
  if (!root || !dashboard || !statusGrid || typeof api !== "function") return;

  const state = { bookings: [], customers: [], services: [], orders: [], requests: [], audit: [], auditCatalog: [], roles: [], businessName: "Tu negocio", businessTimezone: "America/Santiago" };
  const bookingFilters = { query: "", date: "all", serviceId: "all", status: "all", source: "all" };
  const EVENT_LABELS = {
    BOOKING_CREATE: "Reserva creada",
    BOOKING_RESCHEDULE: "Reserva reprogramada",
    BOOKING_CANCEL: "Reserva cancelada",
    BOOKING_CREATED: "Reserva creada",
    BOOKING_RESCHEDULED: "Reserva reprogramada",
    BOOKING_CANCELLED: "Reserva cancelada",
    CUSTOMER_REGISTERED: "Cliente registrado",
    CALLER_LOOKUP: "Cliente identificado",
    FIND_CALLER: "Cliente identificado",
    SERVICES_LISTED: "Consultó servicios",
    LIST_SERVICES: "Consultó servicios",
    AVAILABILITY_LISTED: "Consultó horarios disponibles",
    LIST_AVAILABLE_SLOTS: "Consultó horarios disponibles",
    AVAILABILITY_CHECKED: "Verificó disponibilidad",
    CHECK_BOOKING_AVAILABILITY: "Verificó disponibilidad",
    ORDER_QUOTED: "Pedido cotizado",
    ORDER_UPDATED: "Pedido actualizado",
    ORDER_CONFIRMED: "Pedido confirmado",
    ORDER_CREATED: "Pedido creado",
    ORDER_STATUS_CHECKED: "Consultó estado del pedido",
    ORDER_CANCELLED: "Pedido cancelado"
  };
  let loading = false;
  let incidentDraft = null;

  const esc = value => String(value ?? "").replace(/[&<>'"]/g, c => ({ "&":"&amp;","<":"&lt;",">":"&gt;","'":"&#39;",'"':"&quot;" }[c]));
  const fmt = value => {
    if (!value) return "";
    try {
      return new Intl.DateTimeFormat("es-CL", {
        day:"2-digit", month:"2-digit", year:"2-digit", hour:"2-digit", minute:"2-digit",
        timeZone: state.businessTimezone || "America/Santiago"
      }).format(new Date(value));
    } catch (_) { return String(value); }
  };
  const fmtCompact = value => {
    if (!value) return "";
    try {
      return new Intl.DateTimeFormat("es-CL", {
        day:"numeric", month:"short", hour:"numeric", minute:"2-digit",
        timeZone: state.businessTimezone || "America/Santiago"
      })
        .format(new Date(value))
        .replace(",", " ·");
    } catch (_) { return String(value); }
  };
  const money = (value,currency="CLP") => {
    try { return new Intl.NumberFormat("es-CL",{style:"currency",currency:currency||"CLP",maximumFractionDigits:currency==="CLP"?0:2}).format(Number(value||0)); }
    catch (_) { return String(value||0); }
  };
  const source = value => ({VOICE:"Voz",AI_CALL:"Llamada",WHATSAPP:"WhatsApp",AI_WHATSAPP:"WhatsApp",MANUAL:"Manual",API:"API",ADMIN:"Manual"})[value] || value || "Sin origen";
  const sourceGroup = value => ({VOICE:"CALL",AI_CALL:"CALL",WHATSAPP:"WHATSAPP",AI_WHATSAPP:"WHATSAPP",MANUAL:"MANUAL",ADMIN:"MANUAL",API:"API"})[value] || value || "";
  const status = value => ({CONFIRMED:"Confirmada",CANCELLED:"Cancelada",PREPARING:"Preparando",READY:"Listo",DISPATCHED:"Despachado",COMPLETED:"Completado",OPEN:"Abierta",IN_PROGRESS:"En curso"})[value] || value || "";
  const orderStatus = value => ({CONFIRMED:"Confirmado",CANCELLED:"Cancelado",PREPARING:"Preparando",READY:"Listo",DISPATCHED:"Despachado",COMPLETED:"Completado"})[value] || status(value);

  function eventLabel(value) {
    return EVENT_LABELS[value] || String(value || "").toLowerCase().replaceAll("_", " ").replace(/\b\w/g, c => c.toUpperCase());
  }

  function bookingActorRole(value) {
    return ({ BUSINESS_ADMIN:"Administrador", OPERATOR:"Operador" })[value] || value || "";
  }

  function bookingActivityChange(item) {
    const before = item?.beforeState || {};
    const after = item?.afterState || {};
    if (item?.action === "BOOKING_RESCHEDULE" && before.startAt && after.startAt) {
      return `${fmtCompact(before.startAt)} → ${fmtCompact(after.startAt)}`;
    }
    if (item?.action === "BOOKING_CANCEL" && before.status && after.status) {
      return `${status(before.status)} → ${status(after.status)}`;
    }
    if (item?.action === "BOOKING_CREATE" && after.startAt) {
      return `Reserva para ${fmtCompact(after.startAt)}`;
    }
    return "";
  }

  function renderBookingActivity(items, unavailable = false) {
    if (unavailable) {
      return '<section class="home-detail-section"><h3>Actividad</h3><p class="home-detail-muted">No pude cargar la actividad de esta reserva.</p></section>';
    }
    const activity = Array.isArray(items) ? items : [];
    if (!activity.length) {
      return '<section class="home-detail-section"><h3>Actividad</h3><p class="home-detail-muted">Todavía no hay actividad auditada para esta reserva.</p></section>';
    }
    return `<section class="home-detail-section"><h3>Actividad</h3><div class="home-detail-history">${activity.map(item => {
      const actor = item.actorName || (item.actorType === "HUMAN" ? "Usuario" : "Sistema");
      const role = bookingActorRole(item.actorRole);
      const change = bookingActivityChange(item);
      const detail = [actor, role, change].filter(Boolean).join(" · ");
      return `<div><span>${esc(fmtCompact(item.createdAt))}</span><strong>${esc(eventLabel(item.action))}${detail ? ` · ${esc(detail)}` : ""}</strong></div>`;
    }).join("")}</div></section>`;
  }

  function bookingActivityLoading() {
    return '<div id="homeBookingActivitySlot" class="home-detail-loading">Cargando actividad…</div>';
  }

  let bookingDrawerReturnFocus = null;

  function rememberDrawerOpener(element) {
    if (!(element instanceof HTMLElement)) return null;
    for (const kind of ["booking", "order", "customer"]) {
      const id = element.dataset[`home${kind[0].toUpperCase()}${kind.slice(1)}Id`];
      if (id) return { element, kind, id };
    }
    return { element, kind: null, id: null };
  }

  function restoreDrawerOpener() {
    const saved = bookingDrawerReturnFocus;
    bookingDrawerReturnFocus = null;
    if (!saved) return;

    const focusVisibleTarget = node => {
      if (!(node instanceof HTMLElement) || !node.isConnected || node.getClientRects().length === 0) return false;
      node.focus({ preventScroll: true });
      return document.activeElement === node;
    };

    if (saved.kind && saved.id) {
      const key = `home${saved.kind[0].toUpperCase()}${saved.kind.slice(1)}Id`;
      const candidates = [...document.querySelectorAll(`[data-home-${saved.kind}-id]`)]
        .filter(node => node.dataset[key] === saved.id && node.getClientRects().length > 0);
      const samePresentation = candidates.find(node => node.tagName === saved.element?.tagName);
      if (focusVisibleTarget(samePresentation || candidates[0])) return;
    }

    if (focusVisibleTarget(saved.element)) return;

    const fallbackTab = saved.kind === "order"
      ? document.querySelector('[data-home-tab="orders"]')
      : saved.kind === "customer"
        ? document.querySelector('[data-home-tab="customers"]')
        : document.querySelector('[data-home-tab="bookings"]');
    focusVisibleTarget(fallbackTab);
  }

  function drawerFocusableElements() {
    const drawer = document.querySelector("#homeBookingDetailDrawer");
    if (!drawer) return [];
    return [...drawer.querySelectorAll('a[href], button:not([disabled]), select:not([disabled]), input:not([disabled]), [tabindex]:not([tabindex="-1"])')]
      .filter(node => !node.hidden && !node.closest(".hidden") && window.getComputedStyle(node).visibility !== "hidden");
  }

  function trapDrawerFocus(event) {
    if (event.key !== "Tab") return;
    const backdrop = document.querySelector("#homeBookingDetailBackdrop");
    const drawer = document.querySelector("#homeBookingDetailDrawer");
    if (!backdrop || !drawer || backdrop.classList.contains("hidden")) return;

    const focusable = drawerFocusableElements();
    if (!focusable.length) {
      event.preventDefault();
      document.querySelector("#homeBookingDetailClose")?.focus({ preventScroll: true });
      return;
    }

    const currentIndex = focusable.indexOf(document.activeElement);
    const nextIndex = event.shiftKey
      ? (currentIndex <= 0 ? focusable.length - 1 : currentIndex - 1)
      : (currentIndex < 0 || currentIndex === focusable.length - 1 ? 0 : currentIndex + 1);

    event.preventDefault();
    focusable[nextIndex].focus({ preventScroll: true });
  }

  function showBookingDrawer() {
    const backdrop = document.querySelector("#homeBookingDetailBackdrop");
    if (!backdrop) return;
    if (backdrop.classList.contains("hidden")) {
      bookingDrawerReturnFocus = rememberDrawerOpener(document.activeElement);
    }
    backdrop.classList.remove("hidden");
    backdrop.setAttribute("aria-hidden", "false");
    document.body.classList.add("home-detail-open");
    document.querySelector("#homeBookingDetailClose")?.focus({ preventScroll: true });
  }

  function ensureBookingDrawer() {
    if (document.querySelector("#homeBookingDetailBackdrop")) return;
    document.body.insertAdjacentHTML("beforeend", `
      <div id="homeBookingDetailBackdrop" class="home-detail-backdrop hidden" aria-hidden="true">
        <aside id="homeBookingDetailDrawer" class="home-detail-drawer" role="dialog" aria-modal="true" aria-labelledby="homeBookingDetailTitle">
          <div class="home-detail-head">
            <div><span class="eyebrow">RESERVA</span><h2 id="homeBookingDetailTitle">Detalle</h2><p id="homeBookingDetailMeta"></p></div>
            <button id="homeBookingDetailClose" class="home-detail-close" type="button">Cerrar</button>
          </div>
          <div id="homeBookingDetailBody" class="home-detail-body"></div>
        </aside>
      </div>
    `);
    document.querySelector("#homeBookingDetailClose").addEventListener("click", closeBookingDrawer);
    document.querySelector("#homeBookingDetailBackdrop").addEventListener("click", event => {
      if (event.target === event.currentTarget) closeBookingDrawer();
    });
    document.addEventListener("keydown", event => {
      const backdrop = document.querySelector("#homeBookingDetailBackdrop");
      if (!backdrop || backdrop.classList.contains("hidden")) return;
      if (event.key === "Escape") {
        event.preventDefault();
        closeBookingDrawer();
        return;
      }
      trapDrawerFocus(event);
    });
    const drawer = document.querySelector("#homeBookingDetailDrawer");
    const body = document.querySelector("#homeBookingDetailBody");
    new MutationObserver(() => {
      const backdrop = document.querySelector("#homeBookingDetailBackdrop");
      if (!backdrop || backdrop.classList.contains("hidden") || drawer?.contains(document.activeElement)) return;
      document.querySelector("#homeBookingDetailClose")?.focus({ preventScroll: true });
    }).observe(body, { childList: true, subtree: true });
  }

  function closeBookingDrawer() {
    const backdrop = document.querySelector("#homeBookingDetailBackdrop");
    if (!backdrop || backdrop.classList.contains("hidden")) return;
    backdrop.classList.add("hidden");
    backdrop.setAttribute("aria-hidden", "true");
    document.body.classList.remove("home-detail-open");
    restoreDrawerOpener();
  }

  function bookingFacts(booking, customer, service, context = null) {
    const channel = context?.channel || "";
    const contactAt = channel === "VOICE"
      ? context?.call?.call?.startedAt
      : channel === "WHATSAPP"
        ? (context?.whatsapp?.conversation?.openedAt || context?.whatsapp?.messages?.[0]?.createdAt)
        : booking.createdAt;
    const contactLabel = channel === "VOICE" ? "Llamada" : channel === "WHATSAPP" ? "WhatsApp" : "Contacto";
    const originValue = esc(source(booking.source));
    const originCard = `<div><span>Origen</span><strong>${originValue}</strong></div>`;

    return `<div class="home-detail-facts">
      <div><span>Servicio</span><strong>${esc(service.name || "Servicio")}</strong></div>
      <div><span>Teléfono</span><strong>${esc(customer.phone || "Sin teléfono")}</strong></div>
      <div><span>${contactLabel}</span><strong>${esc(fmtCompact(contactAt) || "Sin fecha")}</strong></div>
      <div><span>Reserva</span><strong>${esc(fmtCompact(booking.startAt))}</strong></div>
      ${originCard}
      <div><span>Estado</span><strong>${esc(status(booking.status))}</strong></div>
    </div>${booking.notes ? `<div class="home-detail-note"><span>Notas</span><p>${esc(booking.notes)}</p></div>` : ""}`;
  }

  function resetBookingCreateAvailability() {
    const confirm = document.querySelector("#homeBookingCreateConfirm");
    const message = document.querySelector("#homeBookingCreateMessage");
    const check = document.querySelector("#homeBookingCreateCheck");
    const customerId = document.querySelector("#homeBookingCreateCustomer")?.value || "";
    const serviceId = document.querySelector("#homeBookingCreateService")?.value || "";
    const date = document.querySelector("#homeBookingCreateDate")?.value || "";
    const time = document.querySelector("#homeBookingCreateTime")?.value || "";

    if (confirm) {
      confirm.classList.add("hidden");
      confirm.disabled = false;
      confirm.textContent = "Crear reserva";
      delete confirm.dataset.startAt;
    }
    if (message) message.textContent = "";
    if (check) {
      const complete = Boolean(customerId && serviceId && date && time);
      check.disabled = !complete;
      check.title = complete
        ? "Comprobar si el horario está disponible."
        : "Completa cliente, servicio, fecha y hora.";
    }
  }

  function populateBookingCreateControls() {
    const customer = document.querySelector("#homeBookingCreateCustomer");
    const service = document.querySelector("#homeBookingCreateService");
    const date = document.querySelector("#homeBookingCreateDate");
    const time = document.querySelector("#homeBookingCreateTime");
    const message = document.querySelector("#homeBookingCreateMessage");
    if (!customer || !service || !date || !time) return;

    const customerOptions = [...state.customers]
      .sort((a, b) => String(a.name || a.phone || "").localeCompare(String(b.name || b.phone || ""), "es"))
      .map(item => `<option value="${esc(item.id)}">${esc(item.name || item.phone || "Cliente")}</option>`)
      .join("");
    const serviceOptions = state.services
      .filter(item => item.active !== false)
      .map(item => `<option value="${esc(item.id)}">${esc(item.name || "Servicio")}</option>`)
      .join("");

    customer.innerHTML = '<option value="">Elegir cliente</option>' + customerOptions;
    service.innerHTML = '<option value="">Elegir servicio</option>' + serviceOptions;
    date.innerHTML = '<option value="">Elegir fecha</option>' + bookingRescheduleDateOptions();
    time.innerHTML = '<option value="">Elegir hora</option>' + bookingRescheduleTimeOptions();

    if (message) {
      if (!state.customers.length) message.textContent = "Primero registra un cliente.";
      else if (!state.services.some(item => item.active !== false)) message.textContent = "Primero configura un servicio activo.";
      else message.textContent = "";
    }
    resetBookingCreateAvailability();
  }

  async function checkManualBookingAvailability() {
    const toggle = document.querySelector("#homeBookingCreateToggle");
    const customer = document.querySelector("#homeBookingCreateCustomer");
    const service = document.querySelector("#homeBookingCreateService");
    const dateControl = document.querySelector("#homeBookingCreateDate");
    const timeControl = document.querySelector("#homeBookingCreateTime");
    const serviceId = document.querySelector("#homeBookingCreateService")?.value || "";
    const date = document.querySelector("#homeBookingCreateDate")?.value || "";
    const time = document.querySelector("#homeBookingCreateTime")?.value || "";
    const startAt = businessLocalDateTimeToIso(date, time);
    const message = document.querySelector("#homeBookingCreateMessage");
    const confirm = document.querySelector("#homeBookingCreateConfirm");
    const check = document.querySelector("#homeBookingCreateCheck");
    const controls = [customer, service, dateControl, timeControl].filter(Boolean);
    if (!serviceId || !startAt || !message || !confirm || !check || check.disabled || controls.length !== 4) return;

    controls.forEach(control => { control.disabled = true; });
    if (toggle) toggle.disabled = true;
    check.disabled = true;
    check.title = "Comprobando disponibilidad…";
    message.textContent = "Comprobando disponibilidad…";
    confirm.classList.add("hidden");
    delete confirm.dataset.startAt;

    try {
      const params = new URLSearchParams({ serviceId, startAt });
      const result = await api(`/api/v1/bookings/availability?${params.toString()}`);
      if (result?.available === true) {
        message.textContent = "Horario disponible ✓";
        confirm.dataset.startAt = startAt;
        confirm.classList.remove("hidden");
      } else {
        message.textContent = "Ese horario no está disponible.";
      }
    } catch (error) {
      message.textContent = error.message || "No pude comprobar la disponibilidad.";
    } finally {
      controls.forEach(control => { control.disabled = false; });
      if (toggle) toggle.disabled = false;
      const customerId = document.querySelector("#homeBookingCreateCustomer")?.value || "";
      const currentServiceId = document.querySelector("#homeBookingCreateService")?.value || "";
      const currentDate = document.querySelector("#homeBookingCreateDate")?.value || "";
      const currentTime = document.querySelector("#homeBookingCreateTime")?.value || "";
      const complete = Boolean(customerId && currentServiceId && currentDate && currentTime);
      check.disabled = !complete;
      check.title = complete
        ? "Comprobar si el horario está disponible."
        : "Completa cliente, servicio, fecha y hora.";
    }
  }

  async function createManualBooking() {
    const customerId = document.querySelector("#homeBookingCreateCustomer")?.value || "";
    const serviceId = document.querySelector("#homeBookingCreateService")?.value || "";
    const confirm = document.querySelector("#homeBookingCreateConfirm");
    const message = document.querySelector("#homeBookingCreateMessage");
    const startAt = confirm?.dataset.startAt || "";
    if (!customerId || !serviceId || !confirm || !message || !startAt) return;

    const customer = state.customers.find(item => String(item.id) === String(customerId)) || {};
    const service = state.services.find(item => String(item.id) === String(serviceId)) || {};
    const customerName = customer.name || customer.phone || "Cliente";
    const serviceName = service.name || "Servicio";
    const confirmed = window.confirm(`¿Crear reserva para ${customerName}, ${serviceName}, el ${fmtCompact(startAt)}?`);
    if (!confirmed) return;

    confirm.disabled = true;
    confirm.textContent = "Creando…";
    try {
      const created = await api("/api/v1/bookings", {
        method: "POST",
        body: JSON.stringify({
          customerId,
          serviceId,
          startAt,
          source: "ADMIN",
          notes: null
        })
      });
      if (created?.id) {
        state.bookings = [created, ...state.bookings.filter(item => String(item.id) !== String(created.id))];
      }
      renderBookings();
      populateIncidentDateSelector();
      syncIncidentImpact();
      populateBookingCreateControls();
      message.textContent = `Reserva creada para ${customerName} ✓`;
    } catch (error) {
      confirm.disabled = false;
      confirm.textContent = "Crear reserva";
      confirm.classList.add("hidden");
      message.textContent = error.message || "No pude crear la reserva.";
    }
  }

  function bindBookingCreate() {
    const toggle = document.querySelector("#homeBookingCreateToggle");
    const panel = document.querySelector("#homeBookingCreatePanel");
    const check = document.querySelector("#homeBookingCreateCheck");
    const confirm = document.querySelector("#homeBookingCreateConfirm");
    if (!toggle || !panel || !check || !confirm || toggle.dataset.bound === "true") return;
    toggle.dataset.bound = "true";

    toggle.addEventListener("click", () => {
      const opening = panel.classList.contains("hidden");
      panel.classList.toggle("hidden", !opening);
      toggle.setAttribute("aria-expanded", opening ? "true" : "false");
      if (opening) populateBookingCreateControls();
    });

    [
      "#homeBookingCreateCustomer",
      "#homeBookingCreateService",
      "#homeBookingCreateDate",
      "#homeBookingCreateTime"
    ].forEach(selector => document.querySelector(selector)?.addEventListener("change", resetBookingCreateAvailability));

    check.addEventListener("click", checkManualBookingAvailability);
    confirm.addEventListener("click", createManualBooking);
  }

  function renderBookingActions(booking) {
    if (booking.status === "CANCELLED") {
      return '<section class="home-detail-section"><h3>Gestionar reserva</h3><p class="home-detail-muted">Esta reserva está cancelada. No hay acciones disponibles.</p></section>';
    }
    return `
      <section class="home-detail-section home-booking-actions">
        <h3>Gestionar reserva</h3>
        <div class="home-booking-action-buttons">
          <button id="homeBookingRescheduleToggle" class="home-booking-primary" type="button">Reprogramar</button>
          <button id="homeBookingCancel" class="home-booking-danger" type="button">Cancelar reserva</button>
        </div>
        <div id="homeBookingReschedulePanel" class="home-booking-reschedule hidden">
          <label>Fecha
            <select id="homeBookingRescheduleDate"></select>
          </label>
          <label>Hora
            <select id="homeBookingRescheduleTime"></select>
          </label>
          <button id="homeBookingCheckAvailability" class="home-booking-primary" type="button">Comprobar disponibilidad</button>
          <button id="homeBookingConfirmReschedule" class="home-booking-primary hidden" type="button">Confirmar cambio</button>
          <p id="homeBookingAvailabilityMessage" class="home-detail-muted"></p>
        </div>
        <p id="homeBookingActionMessage" class="home-detail-muted hidden"></p>
      </section>
    `;
  }

  function bookingRescheduleDateOptions() {
    const today = businessDateKey(new Date());
    const values = [];
    for (let offset = 0; offset < 31; offset += 1) {
      const key = addDaysToDateKey(today, offset);
      const [year, month, day] = key.split("-").map(Number);
      const label = new Intl.DateTimeFormat("es-CL", {
        weekday: "short", day: "numeric", month: "short",
        timeZone: "UTC"
      }).format(new Date(Date.UTC(year, month - 1, day)));
      values.push(`<option value="${key}">${esc(label)}</option>`);
    }
    return values.join("");
  }

  function bookingRescheduleTimeOptions() {
    const values = [];
    for (let minutes = 0; minutes < 1440; minutes += 30) {
      const hours = String(Math.floor(minutes / 60)).padStart(2, "0");
      const mins = String(minutes % 60).padStart(2, "0");
      const value = `${hours}:${mins}`;
      values.push(`<option value="${value}">${value}</option>`);
    }
    return values.join("");
  }

  function businessLocalDateTimeToIso(dateKey, timeValue) {
    const [year, month, day] = String(dateKey || "").split("-").map(Number);
    const [hour, minute] = String(timeValue || "").split(":").map(Number);
    if (!year || !month || !day || Number.isNaN(hour) || Number.isNaN(minute)) return null;

    const desiredUtc = Date.UTC(year, month - 1, day, hour, minute);
    let guess = desiredUtc;
    for (let i = 0; i < 3; i += 1) {
      const parts = businessParts(new Date(guess));
      const observedUtc = Date.UTC(
        Number(parts.year), Number(parts.month) - 1, Number(parts.day),
        Number(parts.hour), Number(parts.minute)
      );
      guess += desiredUtc - observedUtc;
    }

    const resolved = new Date(guess);
    const parts = businessParts(resolved);
    const sameLocalTime =
      Number(parts.year) === year &&
      Number(parts.month) === month &&
      Number(parts.day) === day &&
      Number(parts.hour) === hour &&
      Number(parts.minute) === minute;
    return sameLocalTime ? resolved.toISOString() : null;
  }

  function populateBookingRescheduleControls(booking) {
    const date = document.querySelector("#homeBookingRescheduleDate");
    const time = document.querySelector("#homeBookingRescheduleTime");
    if (!date || !time) return;

    date.innerHTML = bookingRescheduleDateOptions();
    time.innerHTML = bookingRescheduleTimeOptions();

    const currentParts = businessParts(booking.startAt);
    const currentDate = `${currentParts.year}-${currentParts.month}-${currentParts.day}`;
    const currentTime = `${currentParts.hour}:${currentParts.minute}`;
    if ([...date.options].some(option => option.value === currentDate)) date.value = currentDate;
    if ([...time.options].some(option => option.value === currentTime)) time.value = currentTime;
  }

  function resetBookingAvailabilityState() {
    const confirm = document.querySelector("#homeBookingConfirmReschedule");
    const message = document.querySelector("#homeBookingAvailabilityMessage");
    if (confirm) confirm.classList.add("hidden");
    if (message) message.textContent = "";
  }

  async function checkBookingAvailability(booking) {
    const toggle = document.querySelector("#homeBookingRescheduleToggle");
    const dateControl = document.querySelector("#homeBookingRescheduleDate");
    const timeControl = document.querySelector("#homeBookingRescheduleTime");
    const check = document.querySelector("#homeBookingCheckAvailability");
    const date = dateControl?.value || "";
    const time = timeControl?.value || "";
    const startAt = businessLocalDateTimeToIso(date, time);
    const message = document.querySelector("#homeBookingAvailabilityMessage");
    const confirm = document.querySelector("#homeBookingConfirmReschedule");
    if (!startAt || !message || !confirm || !dateControl || !timeControl || !check || check.disabled) return;

    if (new Date(startAt).getTime() === new Date(booking.startAt).getTime()) {
      message.textContent = "Selecciona una fecha u hora diferente a la actual.";
      confirm.classList.add("hidden");
      return;
    }

    dateControl.disabled = true;
    timeControl.disabled = true;
    if (toggle) toggle.disabled = true;
    check.disabled = true;
    check.title = "Comprobando disponibilidad…";
    message.textContent = "Comprobando disponibilidad…";
    confirm.classList.add("hidden");
    try {
      const params = new URLSearchParams({
        serviceId: booking.serviceId,
        startAt,
        excludeBookingId: booking.id
      });
      const result = await api(`/api/v1/bookings/availability?${params.toString()}`);
      if (result?.available === true) {
        message.textContent = "Horario disponible ✓";
        confirm.dataset.startAt = startAt;
        confirm.classList.remove("hidden");
      } else {
        message.textContent = "Ese horario ya no está disponible.";
        confirm.classList.add("hidden");
      }
    } catch (error) {
      message.textContent = error.message || "No pude comprobar la disponibilidad.";
      confirm.classList.add("hidden");
    } finally {
      dateControl.disabled = false;
      timeControl.disabled = false;
      if (toggle) toggle.disabled = false;
      check.disabled = false;
      check.title = "Comprobar si el nuevo horario está disponible.";
    }
  }

  async function rescheduleBookingFromDrawer(booking) {
    const button = document.querySelector("#homeBookingConfirmReschedule");
    const message = document.querySelector("#homeBookingAvailabilityMessage");
    const startAt = button?.dataset.startAt || "";
    if (!button || !message || !startAt) return;

    const customer = state.customers.find(item => String(item.id) === String(booking.customerId)) || {};
    const label = customer.name || customer.phone || "este cliente";
    const confirmed = window.confirm(`¿Reprogramar la reserva de ${label} para ${fmtCompact(startAt)}?`);
    if (!confirmed) return;

    button.disabled = true;
    button.textContent = "Reprogramando…";
    try {
      const updated = await api(`/api/v1/bookings/${encodeURIComponent(booking.id)}`, {
        method: "PATCH",
        body: JSON.stringify({ startAt, notes: booking.notes || null })
      });
      booking.startAt = updated?.startAt || startAt;
      booking.endAt = updated?.endAt || booking.endAt;
      booking.notes = updated?.notes ?? booking.notes;
      booking.status = updated?.status || booking.status;
      renderBookings();
      await openBookingDetail(booking.id);
    } catch (error) {
      button.disabled = false;
      button.textContent = "Confirmar cambio";
      message.textContent = error.message || "No pude reprogramar la reserva.";
      button.classList.add("hidden");
    }
  }

  function bindBookingRescheduleActions(booking) {
    const toggle = document.querySelector("#homeBookingRescheduleToggle");
    const panel = document.querySelector("#homeBookingReschedulePanel");
    if (!toggle || !panel) return;

    toggle.addEventListener("click", () => {
      const opening = panel.classList.contains("hidden");
      panel.classList.toggle("hidden", !opening);
      if (opening) {
        populateBookingRescheduleControls(booking);
        resetBookingAvailabilityState();
      }
    });
    document.querySelector("#homeBookingRescheduleDate")?.addEventListener("change", resetBookingAvailabilityState);
    document.querySelector("#homeBookingRescheduleTime")?.addEventListener("change", resetBookingAvailabilityState);
    document.querySelector("#homeBookingCheckAvailability")?.addEventListener("click", () => checkBookingAvailability(booking));
    document.querySelector("#homeBookingConfirmReschedule")?.addEventListener("click", () => rescheduleBookingFromDrawer(booking));
  }

  async function cancelBookingFromDrawer(id) {
    const booking = state.bookings.find(item => String(item.id) === String(id));
    if (!booking || booking.status === "CANCELLED") return;

    const customer = state.customers.find(item => String(item.id) === String(booking.customerId)) || {};
    const label = customer.name || customer.phone || "este cliente";
    const confirmed = window.confirm(`¿Cancelar la reserva de ${label}? Esta acción cambiará su estado a Cancelada.`);
    if (!confirmed) return;

    const button = document.querySelector("#homeBookingCancel");
    const message = document.querySelector("#homeBookingActionMessage");
    if (button) {
      button.disabled = true;
      button.textContent = "Cancelando…";
    }
    if (message) {
      message.classList.add("hidden");
      message.textContent = "";
    }

    try {
      await api(`/api/v1/bookings/${encodeURIComponent(id)}`, { method: "DELETE" });
      booking.status = "CANCELLED";
      renderBookings();
      await openBookingDetail(id);
    } catch (error) {
      if (button) {
        button.disabled = false;
        button.textContent = "Cancelar reserva";
      }
      if (message) {
        message.textContent = error.message || "No pude cancelar la reserva.";
        message.classList.remove("hidden");
      }
    }
  }

  function bindBookingActions(booking) {
    bindBookingRescheduleActions(booking);
    document.querySelector("#homeBookingCancel")?.addEventListener("click", () => cancelBookingFromDrawer(booking.id));
  }

  function renderContext(context, entityKind = "reserva", personName = "Cliente") {
    if (!context) return '<section class="home-detail-section"><h3>Conversación</h3><p class="home-detail-muted">No hay contexto conversacional disponible.</p></section>';
    const customerLabel = personName || "Cliente";

    if (context.channel === "VOICE" && context.call) {
      const detail = context.call;
      const transcript = Array.isArray(detail.transcript) ? detail.transcript : [];
      return `
        <section class="home-detail-section"><h3>Resumen</h3><p class="home-detail-summary">${esc(detail.summary || "La llamada no tiene resumen guardado.")}</p></section>
        <section class="home-detail-section"><h3>Conversación</h3><div class="home-detail-transcript">${transcript.length ? transcript.map(line =>
          `<article class="home-detail-message ${String(line.speaker || "").toUpperCase() === "ASSISTANT" ? "assistant" : ""}"><strong>${esc(String(line.speaker || "").toUpperCase() === "ASSISTANT" ? state.businessName : customerLabel)}</strong><p>${esc(line.content)}</p><span>${esc(fmt(line.createdAt))}</span></article>`
        ).join("") : '<p class="home-detail-muted">No hay transcripción guardada.</p>'}</div></section>
      `;
    }

    if (context.channel === "WHATSAPP" && context.whatsapp) {
      const messages = Array.isArray(context.whatsapp.messages) ? context.whatsapp.messages : [];
      return `
        <section class="home-detail-section"><h3>Conversación de WhatsApp</h3><div class="home-detail-transcript">${messages.length ? messages.map(message => {
          const assistant = String(message.role || "").toLowerCase() === "assistant" || String(message.direction || "").toLowerCase() === "outbound";
          return `<article class="home-detail-message ${assistant ? "assistant" : ""}"><strong>${esc(assistant ? state.businessName : customerLabel)}</strong><p>${esc(message.content)}</p><span>${esc(fmt(message.createdAt))}</span></article>`;
        }).join("") : '<p class="home-detail-muted">No hay mensajes guardados.</p>'}</div></section>
      `;
    }

    const entityText = entityKind === "pedido" ? "Este pedido" : "Esta reserva";
    const createdSuffix = entityKind === "pedido" ? "" : "a";
    const originText = context.channel === "MANUAL"
      ? `${entityText} fue creado${createdSuffix} manualmente. No existe una conversación asociada.`
      : context.channel === "API"
        ? `${entityText} fue creado${createdSuffix} por API. No existe una conversación asociada.`
        : `No se encontró una conversación enlazada a ${entityKind === "pedido" ? "este pedido" : "esta reserva"}.`;
    return `<section class="home-detail-section"><h3>Origen</h3><p class="home-detail-muted">${originText}</p></section>`;
  }
  async function openBookingDetail(id) {
    const booking = state.bookings.find(item => String(item.id) === String(id));
    if (!booking) return;
    const customers = new Map(state.customers.map(x => [String(x.id), x]));
    const services = new Map(state.services.map(x => [String(x.id), x]));
    const customer = customers.get(String(booking.customerId)) || {};
    const service = services.get(String(booking.serviceId)) || {};
    ensureBookingDrawer();
    document.querySelector("#homeBookingDetailDrawer .eyebrow").textContent = "RESERVA";
    document.querySelector("#homeBookingDetailTitle").textContent = customer.name || customer.phone || "Cliente";
    document.querySelector("#homeBookingDetailMeta").textContent = `Reservada para ${fmtCompact(booking.startAt)} · ${status(booking.status)}`;
    const body = document.querySelector("#homeBookingDetailBody");
    body.innerHTML = bookingFacts(booking, customer, service) + renderBookingActions(booking) +
      '<div class="home-detail-loading">Cargando conversación…</div>' + bookingActivityLoading();
    bindBookingActions(booking);
    showBookingDrawer();

    try {
      const context = await api(`/api/v1/bookings/${encodeURIComponent(id)}/context`);
      body.innerHTML = bookingFacts(booking, customer, service, context) + renderBookingActions(booking) +
        renderContext(context, "reserva", customer.name || customer.phone || "Cliente") + bookingActivityLoading();
      bindBookingActions(booking);
    } catch (error) {
      body.innerHTML = bookingFacts(booking, customer, service) + renderBookingActions(booking) +
        '<section class="home-detail-section"><h3>Conversación</h3><p class="home-detail-muted">No pude cargar la conversación asociada en este momento.</p></section>' +
        bookingActivityLoading();
      bindBookingActions(booking);
    }

    try {
      const activity = await api(`/api/v1/bookings/${encodeURIComponent(id)}/activity`);
      const slot = document.querySelector("#homeBookingActivitySlot");
      if (slot) slot.outerHTML = renderBookingActivity(activity);
    } catch (error) {
      const slot = document.querySelector("#homeBookingActivitySlot");
      if (slot) slot.outerHTML = renderBookingActivity([], true);
    }
  }

  function bindBookingOpeners() {
    document.querySelectorAll("[data-home-booking-id]").forEach(node => {
      const open = () => openBookingDetail(node.dataset.homeBookingId);
      node.addEventListener("click", open);
      node.addEventListener("keydown", event => {
        if (event.key === "Enter" || event.key === " ") { event.preventDefault(); open(); }
      });
    });
  }

  function ready() {
    const cards=[...statusGrid.querySelectorAll(".status-card")];
    return cards.length>=4 && cards.every(card=>card.classList.contains("done"));
  }

  function isBusinessAdmin() {
    return state.roles.includes("BUSINESS_ADMIN");
  }

  function applyRoleVisibility() {
    const admin = isBusinessAdmin();
    document.querySelector('[data-home-tab="audit"]')?.classList.toggle("hidden", !admin);
    document.querySelector("#homeCustomersExportActions")?.classList.toggle("hidden", !admin);
    if (!admin && !document.querySelector('[data-home-panel="audit"]')?.classList.contains("hidden")) {
      setTab("bookings");
    }
  }

  function setTab(name) {
    const allowed = name !== "audit" || isBusinessAdmin();
    const next = allowed ? name : "bookings";
    root.querySelectorAll("[data-home-tab]").forEach(btn=>{
      const active=btn.dataset.homeTab===next;
      btn.classList.toggle("active",active);
      btn.setAttribute("aria-selected",active?"true":"false");
    });
    root.querySelectorAll("[data-home-panel]").forEach(panel=>panel.classList.toggle("hidden",panel.dataset.homePanel!==next));
  }

  function addDaysToDateKey(dateKey, days) {
    const [year, month, day] = String(dateKey || "").split("-").map(Number);
    if (!year || !month || !day) return "";
    const value = new Date(Date.UTC(year, month - 1, day + days));
    return `${value.getUTCFullYear()}-${String(value.getUTCMonth() + 1).padStart(2, "0")}-${String(value.getUTCDate()).padStart(2, "0")}`;
  }

  function bookingMatchesDate(item) {
    if (bookingFilters.date === "all") return true;
    const when = new Date(item.startAt || 0);
    if (Number.isNaN(when.getTime())) return false;

    const now = new Date();
    const todayKey = businessDateKey(now);
    const itemKey = businessDateKey(when);
    const tomorrowKey = addDaysToDateKey(todayKey, 1);

    const [year, month, day] = todayKey.split("-").map(Number);
    const todayUtc = new Date(Date.UTC(year, month - 1, day));
    const mondayOffset = (todayUtc.getUTCDay() + 6) % 7;
    const weekStartKey = addDaysToDateKey(todayKey, -mondayOffset);
    const weekEndKey = addDaysToDateKey(weekStartKey, 7);

    if (bookingFilters.date === "today") return itemKey === todayKey;
    if (bookingFilters.date === "tomorrow") return itemKey === tomorrowKey;
    if (bookingFilters.date === "week") return itemKey >= weekStartKey && itemKey < weekEndKey;
    if (bookingFilters.date === "upcoming") return when >= now;
    if (bookingFilters.date === "past") return when < now;
    return true;
  }

  function renderBookings() {
    const customers = new Map(state.customers.map(x => [String(x.id), x]));
    const services = new Map(state.services.map(x => [String(x.id), x]));
    const allItems = [...state.bookings].sort((a,b) => new Date(b.startAt || 0) - new Date(a.startAt || 0));
    const query = bookingFilters.query.trim().toLocaleLowerCase("es");

    const items = allItems.filter(item => {
      const customer = customers.get(String(item.customerId)) || {};
      const service = services.get(String(item.serviceId)) || {};
      const searchable = [customer.name, customer.phone, customer.email, service.name]
        .filter(Boolean).join(" ").toLocaleLowerCase("es");
      return (!query || searchable.includes(query))
        && bookingMatchesDate(item)
        && (bookingFilters.serviceId === "all" || String(item.serviceId) === bookingFilters.serviceId)
        && (bookingFilters.status === "all" || item.status === bookingFilters.status)
        && (bookingFilters.source === "all" || sourceGroup(item.source) === bookingFilters.source);
    });

    document.querySelector("#homeBusinessBookingsCount").textContent = String(allItems.length);
    const host = document.querySelector("#homeBookingsList");
    const serviceOptions = state.services.map(service =>
      `<option value="${esc(service.id)}" ${bookingFilters.serviceId === String(service.id) ? "selected" : ""}>${esc(service.name)}</option>`
    ).join("");
    const sourceOptions = [
      ["CALL", "Llamada"],
      ["WHATSAPP", "WhatsApp"],
      ["MANUAL", "Manual"],
      ["API", "API"]
    ].map(([value, label]) =>
      `<option value="${value}" ${bookingFilters.source === value ? "selected" : ""}>${label}</option>`
    ).join("");

    const controls = `
      <div class="home-booking-filters" aria-label="Filtros de reservas">
        <label class="home-filter-search"><span>Buscar</span><input id="homeBookingSearch" type="search" value="${esc(bookingFilters.query)}" placeholder="Cliente, teléfono o servicio"></label>
        <label><span>Fecha</span><select id="homeBookingDate">
          <option value="all">Todas</option><option value="today">Hoy</option><option value="tomorrow">Mañana</option>
          <option value="week">Esta semana</option><option value="upcoming">Próximas</option><option value="past">Pasadas</option>
        </select></label>
        <label><span>Servicio</span><select id="homeBookingService"><option value="all">Todos</option>${serviceOptions}</select></label>
        <label><span>Estado</span><select id="homeBookingStatus">
          <option value="all">Todos</option><option value="CONFIRMED">Confirmadas</option><option value="CANCELLED">Canceladas</option>
        </select></label>
        <label><span>Origen</span><select id="homeBookingSource"><option value="all">Todos</option>${sourceOptions}</select></label>
        <button id="homeBookingClearFilters" class="home-filter-clear" type="button">Limpiar</button>
      </div>
      <div class="home-filter-result">Mostrando <strong>${items.length}</strong> de <strong>${allItems.length}</strong> reservas</div>
    `;

    if (!allItems.length) {
      host.innerHTML = controls + '<div class="home-business-empty">Todavía no hay reservas registradas.</div>';
      bindBookingFilters();
      return;
    }
    if (!items.length) {
      host.innerHTML = controls + '<div class="home-business-empty">No hay reservas que coincidan con estos filtros.</div>';
      bindBookingFilters();
      return;
    }

    host.innerHTML = controls + `<div class="home-business-table-shell"><table class="home-business-table"><thead><tr><th>Fecha / hora</th><th>Cliente</th><th>Servicio</th><th>Contacto</th><th>Origen</th><th>Estado</th></tr></thead><tbody>${items.map(item => {
      const customer = customers.get(String(item.customerId)) || {};
      const service = services.get(String(item.serviceId)) || {};
      return `<tr tabindex="0" data-home-booking-id="${esc(item.id)}"><td><strong>${esc(fmt(item.startAt))}</strong></td><td><strong>${esc(customer.name || customer.phone || "Cliente")}</strong></td><td>${esc(service.name || "Servicio")}</td><td>${esc(customer.phone || "Sin teléfono")}</td><td>${esc(source(item.source))}</td><td><span class="home-pill ${item.status === "CANCELLED" ? "bad" : ""}">${esc(status(item.status))}</span></td></tr>`;
    }).join("")}</tbody></table></div>
    <div class="home-business-mobile-list">${items.map(item => {
      const customer = customers.get(String(item.customerId)) || {};
      const service = services.get(String(item.serviceId)) || {};
      return `<article class="home-business-mobile-card" tabindex="0" data-home-booking-id="${esc(item.id)}"><strong>${esc(customer.name || customer.phone || "Cliente")}</strong><span>${esc(fmt(item.startAt))} · ${esc(service.name || "Servicio")}</span><span class="home-pill ${item.status === "CANCELLED" ? "bad" : ""}">${esc(status(item.status))}</span></article>`;
    }).join("")}</div>`;
    bindBookingFilters();
    bindBookingOpeners();
  }

  function bindBookingFilters() {
    const search = document.querySelector("#homeBookingSearch");
    const date = document.querySelector("#homeBookingDate");
    const service = document.querySelector("#homeBookingService");
    const statusFilter = document.querySelector("#homeBookingStatus");
    const sourceFilter = document.querySelector("#homeBookingSource");
    if (date) date.value = bookingFilters.date;
    if (service) service.value = bookingFilters.serviceId;
    if (statusFilter) statusFilter.value = bookingFilters.status;
    if (sourceFilter) sourceFilter.value = bookingFilters.source;

    search?.addEventListener("input", event => { bookingFilters.query = event.target.value; renderBookings(); document.querySelector("#homeBookingSearch")?.focus(); });
    date?.addEventListener("change", event => { bookingFilters.date = event.target.value; renderBookings(); });
    service?.addEventListener("change", event => { bookingFilters.serviceId = event.target.value; renderBookings(); });
    statusFilter?.addEventListener("change", event => { bookingFilters.status = event.target.value; renderBookings(); });
    sourceFilter?.addEventListener("change", event => { bookingFilters.source = event.target.value; renderBookings(); });
    document.querySelector("#homeBookingClearFilters")?.addEventListener("click", () => {
      Object.assign(bookingFilters, { query: "", date: "all", serviceId: "all", status: "all", source: "all" });
      renderBookings();
    });
  }

  function orderActions(order) {
    switch (order.status) {
      case "CONFIRMED": return [["PREPARING", "Empezar preparación"], ["CANCELLED", "Cancelar"]];
      case "PREPARING": return [["READY", "Marcar listo"], ["CANCELLED", "Cancelar"]];
      case "READY": return order.fulfillmentType === "DELIVERY" ? [["DISPATCHED", "Marcar despachado"]] : [["COMPLETED", "Completar"]];
      case "DISPATCHED": return [["COMPLETED", "Completar"]];
      default: return [];
    }
  }

  async function loadOrderConversation(order) {
    let events = [];
    if (order.operationId) {
      try {
        const history = await api(`/api/v1/operation-events?operationId=${encodeURIComponent(order.operationId)}`);
        events = Array.isArray(history) ? history : [];
      } catch (_) {
        events = [];
      }
    }

    const context = {
      channel: order.source || null,
      sourceReferenceId: order.sourceReferenceId || null,
      events
    };
    if (!order.sourceReferenceId) return context;

    try {
      if (order.source === "VOICE") {
        context.call = await api(`/api/v1/calls/${encodeURIComponent(order.sourceReferenceId)}`);
      } else if (order.source === "WHATSAPP") {
        context.whatsapp = await api(`/api/v1/messaging/conversations/${encodeURIComponent(order.sourceReferenceId)}`);
      }
    } catch (_) {
      // Preserve the real order history even if the source conversation is temporarily unavailable.
    }
    return context;
  }

  async function updateOrderStatus(order, nextStatus) {
    if (nextStatus === "CANCELLED" && !window.confirm("¿Cancelar este pedido?")) return;
    await api(`/api/v1/commercial/orders/${encodeURIComponent(order.id)}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status: nextStatus })
    });
    await load();
    setTab("orders");
    closeBookingDrawer();
  }

  async function openOrderDetail(id) {
    const order = state.orders.find(item => String(item.id) === String(id));
    if (!order) return;
    ensureBookingDrawer();
    document.querySelector("#homeBookingDetailDrawer .eyebrow").textContent = "PEDIDO";
    document.querySelector("#homeBookingDetailTitle").textContent = `#${String(order.id || "").slice(0,8)} · ${order.contactName || order.contactPhone || "Cliente"}`;
    document.querySelector("#homeBookingDetailMeta").textContent = `${fmt(order.createdAt)} · ${orderStatus(order.status)}`;
    const body = document.querySelector("#homeBookingDetailBody");
    const lines = Array.isArray(order.lines) ? order.lines : [];
    const facts = `<div class="home-detail-facts">
      <div><span>Cliente</span><strong>${esc(order.contactName || "Cliente")}</strong></div>
      <div><span>Teléfono</span><strong>${esc(order.contactPhone || "Sin teléfono")}</strong></div>
      <div><span>Entrega</span><strong>${order.fulfillmentType === "DELIVERY" ? "Delivery" : "Retiro"}</strong></div>
      <div><span>Origen</span><strong>${esc(source(order.source))}</strong></div>
      <div><span>Estado</span><strong>${esc(orderStatus(order.status))}</strong></div>
      <div><span>Subtotal</span><strong>${esc(money(order.subtotal, order.currency))}</strong></div>
      <div><span>Despacho</span><strong>${esc(money(order.deliveryFee, order.currency))}</strong></div>
      <div><span>Total</span><strong>${esc(money(order.total, order.currency))}</strong></div>
    </div>
    <section class="home-detail-section"><h3>Productos</h3><div class="home-detail-lines">${lines.length ? lines.map(line =>
      `<div><span>${esc(line.quantity || 0)} × ${esc(line.name || "Producto")}${line.unitPrice != null ? ` · ${esc(money(line.unitPrice, order.currency))} c/u` : ""}</span><strong>${esc(money(line.lineTotal ?? (Number(line.quantity || 0) * Number(line.unitPrice || 0)), order.currency))}</strong></div>`
    ).join("") : '<p class="home-detail-muted">Sin líneas de detalle.</p>'}</div></section>
    ${order.deliveryAddress ? `<div class="home-detail-note"><span>Dirección</span><p>${esc(order.deliveryAddress)}</p></div>` : ""}`;

    body.innerHTML = facts + '<div class="home-detail-loading">Cargando conversación…</div>';
    showBookingDrawer();

    const context = await loadOrderConversation(order);
    const actions = orderActions(order);
    body.innerHTML = facts + renderContext(context, "pedido", order.contactName || order.contactPhone || "Cliente") +
      (actions.length ? `<div class="home-detail-order-actions">${actions.map(([next,label]) => `<button type="button" data-home-order-status="${next}" class="${next === "CANCELLED" ? "home-filter-clear" : "home-order-primary"}">${esc(label)}</button>`).join("")}</div>` : "");

    body.querySelectorAll("[data-home-order-status]").forEach(button => button.addEventListener("click", async event => {
      event.currentTarget.disabled = true;
      try { await updateOrderStatus(order, event.currentTarget.dataset.homeOrderStatus); }
      catch (error) { event.currentTarget.disabled = false; }
    }));
  }

  function bindOrderOpeners() {
    document.querySelectorAll("[data-home-order-id]").forEach(node => {
      const open = () => openOrderDetail(node.dataset.homeOrderId);
      node.addEventListener("click", open);
      node.addEventListener("keydown", event => {
        if (event.key === "Enter" || event.key === " ") { event.preventDefault(); open(); }
      });
    });
  }

  function renderOrders() {
    const items=[...state.orders].sort((a,b)=>new Date(b.createdAt||0)-new Date(a.createdAt||0));
    document.querySelector("#homeBusinessOrdersCount").textContent=String(items.length);
    const host=document.querySelector("#homeOrdersList");
    if(!items.length){ host.innerHTML='<div class="home-business-empty">Todavía no hay pedidos registrados.</div>'; return; }
    host.innerHTML=`<div class="home-business-table-shell"><table class="home-business-table"><thead><tr><th>Pedido</th><th>Cliente</th><th>Total</th><th>Entrega</th><th>Estado</th><th>Origen</th></tr></thead><tbody>${items.map(item=>`<tr tabindex="0" data-home-order-id="${esc(item.id)}"><td><strong>#${esc(String(item.id||"").slice(0,8))}</strong></td><td><strong>${esc(item.contactName||item.contactPhone||"Cliente")}</strong></td><td>${esc(money(item.total,item.currency))}</td><td>${item.fulfillmentType==="DELIVERY"?"Delivery":"Retiro"}</td><td><span class="home-pill">${esc(orderStatus(item.status))}</span></td><td>${esc(source(item.source))}</td></tr>`).join("")}</tbody></table></div>
    <div class="home-business-mobile-list">${items.map(item=>`<article class="home-business-mobile-card" tabindex="0" data-home-order-id="${esc(item.id)}"><strong>${esc(item.contactName||item.contactPhone||"Cliente")}</strong><span>${esc(money(item.total,item.currency))} · ${esc(orderStatus(item.status))}</span></article>`).join("")}</div>`;
    bindOrderOpeners();
  }

  function renderRequests() {
    const items=state.requests||[];
    document.querySelector("#homeBusinessRequestsCount").textContent=String(items.length);
    const host=document.querySelector("#homeRequestsList");
    if(!items.length){ host.innerHTML='<div class="home-business-empty">No hay solicitudes recientes.</div>'; return; }
    host.innerHTML=items.map(item=>`<article class="home-simple-row"><div><strong>${esc(item.title||item.type||"Solicitud")}</strong><span>${esc(item.description||item.contactName||"")}</span></div><span class="home-pill">${esc(status(item.status))}</span></article>`).join("");
  }

  function customerProfileFacts(customer) {
    return `<div class="home-detail-facts">
      <div><span>Teléfono</span><strong>${esc(customer.phone || "Sin teléfono")}</strong></div>
      <div><span>Email</span><strong>${esc(customer.email || "Sin email")}</strong></div>
      <div><span>Cliente desde</span><strong>${esc(fmtCompact(customer.createdAt) || "Sin fecha")}</strong></div>
      <div><span>Actualizado</span><strong>${esc(fmtCompact(customer.updatedAt) || "Sin fecha")}</strong></div>
    </div>${customer.notes ? `<div class="home-detail-note"><span>Notas</span><p>${esc(customer.notes)}</p></div>` : ""}`;
  }

  function renderCustomerBookingHistory(bookings) {
    const items = Array.isArray(bookings) ? bookings : [];
    if (!items.length) {
      return '<section class="home-detail-section"><h3>Historial de reservas</h3><p class="home-detail-muted">Este cliente todavía no tiene reservas.</p></section>';
    }
    const services = new Map(state.services.map(item => [String(item.id), item]));
    return `<section class="home-detail-section"><h3>Historial de reservas</h3><div class="home-detail-history">${items.map(item => {
      const service = services.get(String(item.serviceId)) || {};
      const label = [service.name || "Servicio", status(item.status), source(item.source)].filter(Boolean).join(" · ");
      return `<div><span>${esc(fmtCompact(item.startAt))}</span><strong>${esc(label)}</strong></div>`;
    }).join("")}</div></section>`;
  }

  async function openCustomerDetail(id) {
    const fallback = state.customers.find(item => String(item.id) === String(id));
    if (!fallback) return;

    ensureBookingDrawer();
    document.querySelector("#homeBookingDetailDrawer .eyebrow").textContent = "CLIENTE";
    document.querySelector("#homeBookingDetailTitle").textContent = fallback.name || fallback.phone || "Cliente";
    document.querySelector("#homeBookingDetailMeta").textContent = fallback.createdAt
      ? `Cliente desde ${fmtCompact(fallback.createdAt)}`
      : "Ficha de cliente";

    const body = document.querySelector("#homeBookingDetailBody");
    body.innerHTML = customerProfileFacts(fallback) + '<div class="home-detail-loading">Cargando historial…</div>';

    showBookingDrawer();

    try {
      const profile = await api(`/api/v1/customers/${encodeURIComponent(id)}/profile`);
      const customer = profile?.customer || fallback;
      document.querySelector("#homeBookingDetailTitle").textContent = customer.name || customer.phone || "Cliente";
      document.querySelector("#homeBookingDetailMeta").textContent = customer.createdAt
        ? `Cliente desde ${fmtCompact(customer.createdAt)}`
        : "Ficha de cliente";
      body.innerHTML = customerProfileFacts(customer) + renderCustomerBookingHistory(profile?.bookings);
    } catch (error) {
      body.innerHTML = customerProfileFacts(fallback) +
        '<section class="home-detail-section"><h3>Historial de reservas</h3><p class="home-detail-muted">No pude cargar el historial de este cliente.</p></section>';
    }
  }

  function bindCustomerOpeners() {
    document.querySelectorAll("[data-home-customer-id]").forEach(node => {
      const open = () => openCustomerDetail(node.dataset.homeCustomerId);
      node.addEventListener("click", open);
      node.addEventListener("keydown", event => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          open();
        }
      });
    });
  }

  async function downloadCustomerExport(format) {
    const normalized = format === "xlsx" ? "xlsx" : "csv";
    const button = document.querySelector(normalized === "xlsx" ? "#homeCustomersExportXlsx" : "#homeCustomersExportCsv");
    const message = document.querySelector("#homeCustomersExportMessage");
    const accessToken = sessionStorage.getItem("helvoca_access_token");
    if (!accessToken) {
      if (message) message.textContent = "Tu sesión expiró. Ingresa nuevamente.";
      return;
    }

    if (button) button.disabled = true;
    if (message) message.textContent = normalized === "xlsx" ? "Preparando Excel…" : "Preparando CSV…";
    try {
      const response = await fetch(`/api/v1/customers/export?format=${normalized}`, {
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      if (!response.ok) {
        let detail = `Error HTTP ${response.status}`;
        try {
          const type = response.headers.get("content-type") || "";
          if (type.includes("application/json")) {
            const payload = await response.json();
            detail = payload?.message || payload?.detail || payload?.error || detail;
          } else {
            const text = await response.text();
            if (text) detail = text;
          }
        } catch (_) {}
        throw new Error(detail);
      }

      const blob = await response.blob();
      const disposition = response.headers.get("content-disposition") || "";
      const filenameMatch = disposition.match(/filename="?([^";]+)"?/i);
      const filename = filenameMatch?.[1] || `helvoca-clientes.${normalized}`;
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      if (message) message.textContent = `${normalized.toUpperCase()} descargado · ${state.customers.length} clientes`;
    } catch (error) {
      if (message) message.textContent = error.message || "No pude exportar los clientes.";
    } finally {
      if (button) button.disabled = false;
    }
  }

  async function createManualCustomer() {
    const nameInput = document.querySelector("#homeCustomerCreateName");
    const phoneInput = document.querySelector("#homeCustomerCreatePhone");
    const emailInput = document.querySelector("#homeCustomerCreateEmail");
    const button = document.querySelector("#homeCustomerCreateConfirm");
    const message = document.querySelector("#homeCustomerCreateMessage");
    if (!button || !message) return;

    const name = nameInput?.value.trim() || "";
    const phone = phoneInput?.value.trim() || "";
    const email = emailInput?.value.trim() || "";
    if (!name && !phone && !email) {
      message.textContent = "Ingresa al menos un dato del cliente.";
      return;
    }

    button.disabled = true;
    button.textContent = "Creando…";
    message.textContent = "";
    try {
      const created = await api("/api/v1/customers", {
        method: "POST",
        body: JSON.stringify({
          name: name || null,
          phone: phone || null,
          email: email || null,
          notes: null
        })
      });
      if (created?.id) {
        state.customers = [created, ...state.customers.filter(item => String(item.id) !== String(created.id))];
      }
      if (nameInput) nameInput.value = "";
      if (phoneInput) phoneInput.value = "";
      if (emailInput) emailInput.value = "";
      renderCustomers();
      populateBookingCreateControls();
      message.textContent = `Cliente creado ✓`;
    } catch (error) {
      message.textContent = error.message || "No pude crear el cliente.";
    } finally {
      button.disabled = false;
      button.textContent = "Crear cliente";
    }
  }

  function bindCustomerCreate() {
    const toggle = document.querySelector("#homeCustomerCreateToggle");
    const panel = document.querySelector("#homeCustomerCreatePanel");
    const confirm = document.querySelector("#homeCustomerCreateConfirm");
    if (!toggle || !panel || !confirm || toggle.dataset.bound === "true") return;
    toggle.dataset.bound = "true";

    toggle.addEventListener("click", () => {
      const opening = panel.classList.contains("hidden");
      panel.classList.toggle("hidden", !opening);
      toggle.setAttribute("aria-expanded", opening ? "true" : "false");
      if (opening) {
        document.querySelector("#homeCustomerCreateName")?.focus();
      }
    });

    confirm.addEventListener("click", createManualCustomer);
  }

  function bindCustomerExports() {
    const csv = document.querySelector("#homeCustomersExportCsv");
    const xlsx = document.querySelector("#homeCustomersExportXlsx");
    if (csv && csv.dataset.bound !== "true") {
      csv.dataset.bound = "true";
      csv.addEventListener("click", () => downloadCustomerExport("csv"));
    }
    if (xlsx && xlsx.dataset.bound !== "true") {
      xlsx.dataset.bound = "true";
      xlsx.addEventListener("click", () => downloadCustomerExport("xlsx"));
    }
  }

  function renderCustomers() {
    const items=[...state.customers].sort((a,b)=>String(a.name||a.phone||"").localeCompare(String(b.name||b.phone||""),"es"));
    document.querySelector("#homeBusinessCustomersCount").textContent=String(items.length);
    const host=document.querySelector("#homeCustomersList");
    if(!items.length){ host.innerHTML='<div class="home-business-empty">Todavía no hay clientes registrados.</div>'; return; }
    host.innerHTML=items.map(item=>`<article class="home-simple-row" tabindex="0" data-home-customer-id="${esc(item.id)}"><div><strong>${esc(item.name||item.phone||"Cliente")}</strong><span>${esc([item.phone,item.email].filter(Boolean).join(" · "))}</span></div><span class="home-pill">Ver ficha</span></article>`).join("");
    bindCustomerOpeners();
  }

  function auditActionLabel(value) {
    const labels = {
      BOOKING_CREATE: "Reserva creada",
      BOOKING_RESCHEDULE: "Reserva reprogramada",
      BOOKING_CANCEL: "Reserva cancelada",
      CUSTOMER_CREATE: "Cliente creado",
      CUSTOMER_UPDATE: "Cliente actualizado",
      CUSTOMER_EXPORT: "Clientes exportados",
      AUDIT_EXPORT: "Auditoría exportada",
      BUSINESS_UPDATE: "Negocio actualizado",
      SERVICE_CREATE: "Servicio creado",
      SERVICE_UPDATE: "Servicio actualizado",
      SERVICE_DEACTIVATE: "Servicio desactivado"
    };
    return labels[value] || eventLabel(value);
  }

  function auditResourceLabel(item) {
    const type = ({ BOOKING:"Reserva", CUSTOMER:"Cliente", BUSINESS:"Negocio", SERVICE:"Servicio" })[item?.resourceType]
      || item?.resourceType || "Recurso";
    const id = item?.resourceId ? String(item.resourceId).slice(0, 8) : "";
    return id ? `${type} #${id}` : type;
  }

  function auditResourceTypeLabel(value) {
    return ({ BOOKING:"Reserva", CUSTOMER:"Cliente", BUSINESS:"Negocio", SERVICE:"Servicio", USER:"Usuario", AI_AGENT:"Agente IA", KNOWLEDGE_ITEM:"Conocimiento" })[value]
      || value || "Recurso";
  }
  function auditChangeLabel(item) {
    const before = item?.beforeState || {};
    const after = item?.afterState || {};
    if (before.startAt && after.startAt && before.startAt !== after.startAt) {
      return `${fmtCompact(before.startAt)} → ${fmtCompact(after.startAt)}`;
    }
    if (before.status && after.status && before.status !== after.status) {
      return `${status(before.status)} → ${status(after.status)}`;
    }
    if (!item?.beforeState && after.startAt) {
      return `Para ${fmtCompact(after.startAt)}`;
    }
    return "";
  }

  function renderAudit() {
    const items = Array.isArray(state.audit) ? state.audit : [];
    const count = document.querySelector("#homeBusinessAuditCount");
    if (count) count.textContent = String(items.length);
    const host = document.querySelector("#homeAuditList");
    if (!host) return;
    if (!items.length) {
      host.innerHTML = '<div class="home-business-empty">Todavía no hay eventos de auditoría.</div>';
      return;
    }
    host.innerHTML = `<div class="home-audit-list">${items.map(item => {
      const actor = item.actorName || (item.actorType === "HUMAN" ? "Usuario" : "Sistema");
      const role = bookingActorRole(item.actorRole);
      const email = item.actorEmail || "";
      const change = auditChangeLabel(item);
      return `<article class="home-audit-row">
        <div class="home-audit-main">
          <strong>${esc(auditActionLabel(item.action))}</strong>
          <span>${esc(auditResourceLabel(item))}${change ? ` · ${esc(change)}` : ""}</span>
        </div>
        <div class="home-audit-actor">
          <strong>${esc(actor)}</strong>
          <span>${esc([role, email].filter(Boolean).join(" · ") || "Registro del sistema")}</span>
        </div>
        <time datetime="${esc(item.createdAt || "")}">${esc(fmt(item.createdAt))}</time>
      </article>`;
    }).join("")}</div>`;
  }

  function populateAuditFilterOptions() {
    if (!isBusinessAdmin()) return;
    const catalog = Array.isArray(state.auditCatalog) ? state.auditCatalog : [];
    const action = document.querySelector("#homeAuditAction");
    const resource = document.querySelector("#homeAuditResource");
    if (action) {
      const current = action.value;
      const values = [...new Set(catalog.map(item => item?.action).filter(Boolean))]
        .sort((a, b) => auditActionLabel(a).localeCompare(auditActionLabel(b), "es"));
      action.innerHTML = '<option value="">Todas</option>' + values
        .map(value => '<option value="' + esc(value) + '">' + esc(auditActionLabel(value)) + '</option>').join("");
      if (values.includes(current)) action.value = current;
    }
    if (resource) {
      const current = resource.value;
      const values = [...new Set(catalog.map(item => item?.resourceType).filter(Boolean))]
        .sort((a, b) => auditResourceTypeLabel(a).localeCompare(auditResourceTypeLabel(b), "es"));
      resource.innerHTML = '<option value="">Todos</option>' + values
        .map(value => '<option value="' + esc(value) + '">' + esc(auditResourceTypeLabel(value)) + '</option>').join("");
      if (values.includes(current)) resource.value = current;
    }
  }

  function auditDateStartIso(value) {
    if (!value) return "";
    const date = new Date(value + "T00:00:00");
    return Number.isNaN(date.getTime()) ? "" : date.toISOString();
  }

  function auditDateEndExclusiveIso(value) {
    if (!value) return "";
    const date = new Date(value + "T00:00:00");
    if (Number.isNaN(date.getTime())) return "";
    date.setDate(date.getDate() + 1);
    return date.toISOString();
  }

  function currentAuditParams() {
    const actor = document.querySelector("#homeAuditActor")?.value?.trim() || "";
    const action = document.querySelector("#homeAuditAction")?.value || "";
    const resourceType = document.querySelector("#homeAuditResource")?.value || "";
    const from = document.querySelector("#homeAuditFrom")?.value || "";
    const to = document.querySelector("#homeAuditTo")?.value || "";

    if (from && to && from > to) {
      throw new Error("La fecha Desde no puede ser posterior a Hasta.");
    }

    const params = new URLSearchParams();
    if (actor) params.set("actor", actor);
    if (action) params.set("action", action);
    if (resourceType) params.set("resourceType", resourceType);
    const fromIso = auditDateStartIso(from);
    const toIso = auditDateEndExclusiveIso(to);
    if (fromIso) params.set("from", fromIso);
    if (toIso) params.set("to", toIso);
    return params;
  }

  async function applyAuditFilters() {
    if (!isBusinessAdmin()) return;
    const message = document.querySelector("#homeAuditFilterMessage");
    let params;
    try {
      params = currentAuditParams();
    } catch (error) {
      if (message) message.textContent = error.message;
      return;
    }

    if (message) message.textContent = "Filtrando auditoría…";
    try {
      const url = "/api/v1/audit" + (params.size ? "?" + params.toString() : "");
      const items = await api(url);
      state.audit = Array.isArray(items) ? items : [];
      renderAudit();
      if (message) {
        const plural = state.audit.length === 1 ? "" : "s";
        message.textContent = state.audit.length + " evento" + plural + " encontrado" + plural + ".";
      }
    } catch (error) {
      if (message) message.textContent = error?.message || "No pude filtrar la auditoría.";
    }
  }

  async function downloadAuditExport(format) {
    if (!isBusinessAdmin()) return;
    const normalized = format === "xlsx" ? "xlsx" : "csv";
    const button = document.querySelector(normalized === "xlsx" ? "#homeAuditExportXlsx" : "#homeAuditExportCsv");
    const message = document.querySelector("#homeAuditFilterMessage");
    const accessToken = sessionStorage.getItem("helvoca_access_token");
    if (!accessToken) {
      if (message) message.textContent = "Tu sesión expiró. Ingresa nuevamente.";
      return;
    }

    let params;
    try {
      params = currentAuditParams();
    } catch (error) {
      if (message) message.textContent = error.message;
      return;
    }
    params.set("format", normalized);

    if (button) button.disabled = true;
    if (message) message.textContent = normalized === "xlsx" ? "Preparando Excel de auditoría…" : "Preparando CSV de auditoría…";
    try {
      const response = await fetch("/api/v1/audit/export?" + params.toString(), {
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      if (!response.ok) {
        let detail = `Error HTTP ${response.status}`;
        try {
          const type = response.headers.get("content-type") || "";
          if (type.includes("application/json")) {
            const payload = await response.json();
            detail = payload?.message || payload?.detail || payload?.error || detail;
          } else {
            const text = await response.text();
            if (text) detail = text;
          }
        } catch (_) {}
        throw new Error(detail);
      }

      const blob = await response.blob();
      const disposition = response.headers.get("content-disposition") || "";
      const filenameMatch = disposition.match(/filename="?([^";]+)"?/i);
      const filename = filenameMatch?.[1] || `helvoca-auditoria.${normalized}`;
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      if (message) message.textContent = `${normalized.toUpperCase()} de auditoría descargado · ${state.audit.length} eventos visibles`;
    } catch (error) {
      if (message) message.textContent = error.message || "No pude exportar la auditoría.";
    } finally {
      if (button) button.disabled = false;
    }
  }

  function bindAuditFilters() {
    const form = document.querySelector("#homeAuditFilters");
    if (!form || form.dataset.bound === "true" || !isBusinessAdmin()) return;
    form.dataset.bound = "true";
    form.addEventListener("submit", event => {
      event.preventDefault();
      applyAuditFilters();
    });
    document.querySelector("#homeAuditClear")?.addEventListener("click", () => {
      form.reset();
      const message = document.querySelector("#homeAuditFilterMessage");
      if (message) message.textContent = "";
      applyAuditFilters();
    });
    document.querySelector("#homeAuditExportCsv")?.addEventListener("click", () => downloadAuditExport("csv"));
    document.querySelector("#homeAuditExportXlsx")?.addEventListener("click", () => downloadAuditExport("xlsx"));
  }
  function businessParts(value = new Date()) {
    try {
      const parts = new Intl.DateTimeFormat("en-CA", {
        timeZone: state.businessTimezone || "America/Santiago",
        year: "numeric", month: "2-digit", day: "2-digit",
        hour: "2-digit", minute: "2-digit", hourCycle: "h23"
      }).formatToParts(new Date(value));
      return Object.fromEntries(parts.filter(part => part.type !== "literal").map(part => [part.type, part.value]));
    } catch (_) {
      const date = new Date(value);
      return {
        year: String(date.getFullYear()),
        month: String(date.getMonth() + 1).padStart(2, "0"),
        day: String(date.getDate()).padStart(2, "0"),
        hour: String(date.getHours()).padStart(2, "0"),
        minute: String(date.getMinutes()).padStart(2, "0")
      };
    }
  }

  function businessDateKey(value) {
    const parts = businessParts(value);
    return `${parts.year}-${parts.month}-${parts.day}`;
  }

  function businessMinuteOfDay(value) {
    const parts = businessParts(value);
    return Number(parts.hour || 0) * 60 + Number(parts.minute || 0);
  }

  const incidentTimeMinutes = value => {
    if (!value || !/^\d{2}:\d{2}$/.test(value)) return null;
    const [hour, minute] = value.split(":").map(Number);
    return (hour * 60) + minute;
  };

  const incidentTimeValue = minutes => {
    const safe = Math.max(0, Math.min(1440, Number(minutes || 0)));
    const hour = Math.floor(safe / 60);
    const minute = safe % 60;
    return `${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`;
  };

  const incidentTimeLabel = value => {
    if (value === "24:00") return "12:00 a. m. · fin del día";
    const minutes = incidentTimeMinutes(value);
    if (minutes == null) return value || "";
    const date = new Date(Date.UTC(2026, 0, 1, Math.floor(minutes / 60), minutes % 60));
    return new Intl.DateTimeFormat("es-CL", { hour: "numeric", minute: "2-digit", timeZone: "UTC" }).format(date);
  };

  function populateIncidentTimeSelectors() {
    const from = document.querySelector("#homeIncidentTimeFrom");
    const to = document.querySelector("#homeIncidentTimeTo");
    if (!from || !to || from.dataset.ready === "true") return;
    const values = [];
    for (let minutes = 0; minutes <= 1440; minutes += 30) values.push(incidentTimeValue(minutes));
    from.innerHTML = '<option value="">Elegir hora</option>' + values.slice(0, -1)
      .map(value => `<option value="${value}">${esc(incidentTimeLabel(value))}</option>`).join("");
    to.innerHTML = '<option value="">Elegir hora</option>' + values.slice(1)
      .map(value => `<option value="${value}">${esc(incidentTimeLabel(value))}</option>`).join("");
    from.dataset.ready = "true";
  }

  function incidentBookingsForDate(dateKey) {
    if (!dateKey) return [];
    return state.bookings
      .filter(item => item.status === "CONFIRMED")
      .filter(item => businessDateKey(item.startAt) === dateKey)
      .sort((a, b) => new Date(a.startAt || 0) - new Date(b.startAt || 0));
  }

  function populateIncidentDateSelector() {
    const select = document.querySelector("#homeIncidentDate");
    if (!select) return;
    const current = select.value;
    const counts = new Map();
    state.bookings
      .filter(item => item.status === "CONFIRMED")
      .forEach(item => {
        const key = businessDateKey(item.startAt);
        counts.set(key, (counts.get(key) || 0) + 1);
      });

    const keys = [...counts.keys()].sort();
    select.innerHTML = '<option value="">Elegir fecha</option>' + keys.map(key => {
      const [year, month, day] = key.split("-").map(Number);
      const label = new Intl.DateTimeFormat("es-CL", {
        weekday: "short", day: "numeric", month: "short", year: "numeric", timeZone: "UTC"
      }).format(new Date(Date.UTC(year, month - 1, day)));
      const count = counts.get(key);
      return `<option value="${key}">${esc(label)} · ${count} reserva${count === 1 ? "" : "s"}</option>`;
    }).join("");
    if (current && counts.has(current)) select.value = current;
  }

  function incidentBookingsInRange() {
    const dateKey = document.querySelector("#homeIncidentDate")?.value || "";
    const from = incidentTimeMinutes(document.querySelector("#homeIncidentTimeFrom")?.value || "");
    const to = incidentTimeMinutes(document.querySelector("#homeIncidentTimeTo")?.value || "");
    if (!dateKey || from == null || to == null || from >= to) return [];
    return incidentBookingsForDate(dateKey).filter(item => {
      const start = businessMinuteOfDay(item.startAt);
      const end = item.endAt ? businessMinuteOfDay(item.endAt) : start + 1;
      return start < to && end > from;
    });
  }

  function incidentFormComplete() {
    const reason = document.querySelector("#homeIncidentReason")?.value || "";
    const date = document.querySelector("#homeIncidentDate")?.value || "";
    const from = incidentTimeMinutes(document.querySelector("#homeIncidentTimeFrom")?.value || "");
    const to = incidentTimeMinutes(document.querySelector("#homeIncidentTimeTo")?.value || "");
    return Boolean(reason && date && from != null && to != null && from < to);
  }

  function invalidateIncidentPreview() {
    incidentDraft = null;
    const preview = document.querySelector("#homeIncidentPreview");
    if (preview) {
      preview.classList.add("hidden");
      preview.innerHTML = "";
    }
  }

  function syncIncidentTimeBounds() {
    const from = document.querySelector("#homeIncidentTimeFrom");
    const to = document.querySelector("#homeIncidentTimeTo");
    if (!from || !to) return;
    const fromMinutes = incidentTimeMinutes(from.value);
    [...to.options].forEach(option => {
      if (!option.value) {
        option.disabled = false;
        return;
      }
      const value = incidentTimeMinutes(option.value);
      option.disabled = fromMinutes != null && value <= fromMinutes;
    });
    if (to.value && fromMinutes != null && incidentTimeMinutes(to.value) <= fromMinutes) to.value = "";
  }

  function setIncidentDefaultRange(dateKey, force = false) {
    const from = document.querySelector("#homeIncidentTimeFrom");
    const to = document.querySelector("#homeIncidentTimeTo");
    if (!from || !to || !dateKey) return;
    if (!force && (from.value || to.value)) return;

    const bookings = incidentBookingsForDate(dateKey);
    if (!bookings.length) {
      from.value = "";
      to.value = "";
      syncIncidentTimeBounds();
      return;
    }

    const starts = bookings.map(item => businessMinuteOfDay(item.startAt));
    const ends = bookings.map(item => item.endAt ? businessMinuteOfDay(item.endAt) : businessMinuteOfDay(item.startAt) + 30);
    const first = Math.max(0, Math.floor(Math.min(...starts) / 30) * 30);
    const last = Math.min(1440, Math.ceil(Math.max(...ends) / 30) * 30);
    from.value = incidentTimeValue(first);
    to.value = incidentTimeValue(Math.max(first + 30, last));
    syncIncidentTimeBounds();
  }

  function syncIncidentImpact() {
    syncIncidentTimeBounds();
    const summary = document.querySelector("#homeIncidentImpactSummary");
    const button = document.querySelector("#homeIncidentPreviewBtn");
    if (!summary || !button) return;

    if (!incidentFormComplete()) {
      button.disabled = true;
      summary.classList.add("hidden");
      summary.innerHTML = "";
      button.title = "Completa motivo, fecha y un rango Desde/Hasta válido.";
      return;
    }

    const affected = incidentBookingsInRange();
    const customers = new Set(affected.map(item => String(item.customerId || item.id)));
    button.disabled = false;
    summary.classList.remove("hidden");
    button.title = "Revisar los clientes afectados antes de preparar la campaña.";
    summary.innerHTML = affected.length
      ? `<strong>${customers.size} cliente${customers.size === 1 ? "" : "s"} afectado${customers.size === 1 ? "" : "s"}</strong>`
      : '<strong>Sin clientes afectados</strong>';
  }

  function handleIncidentControlChange(event) {
    const target = event?.target;
    if (!target) return;

    const reason = document.querySelector("#homeIncidentReason")?.value || "";
    const date = document.querySelector("#homeIncidentDate")?.value || "";
    const from = document.querySelector("#homeIncidentTimeFrom");
    const to = document.querySelector("#homeIncidentTimeTo");

    if (reason === "Cierre del día" && (target.id === "homeIncidentReason" || target.id === "homeIncidentDate")) {
      if (from && to) {
        from.value = "00:00";
        to.value = "24:00";
      }
    } else if (target.id === "homeIncidentDate" || target.id === "homeIncidentReason") {
      setIncidentDefaultRange(date, true);
    }

    invalidateIncidentPreview();
    syncIncidentImpact();
  }

  function incidentChannelLabel(strategy, bookings) {
    if (strategy === "CALL") return "Llamada";
    if (strategy === "WHATSAPP") return "WhatsApp";
    return bookings.some(item => sourceGroup(item.source) === "WHATSAPP") ? "WhatsApp" : "WhatsApp primero";
  }

  function renderIncidentPreview() {
    const preview = document.querySelector("#homeIncidentPreview");
    const reason = document.querySelector("#homeIncidentReason")?.value.trim() || "";
    const targetDate = document.querySelector("#homeIncidentDate")?.value || "";
    const targetTimeFrom = document.querySelector("#homeIncidentTimeFrom")?.value || "";
    const targetTimeTo = document.querySelector("#homeIncidentTimeTo")?.value || "";
    const goal = document.querySelector("#homeIncidentGoal")?.value || "RESCHEDULE";
    const strategy = document.querySelector("#homeIncidentStrategy")?.value || "CHEAPEST";
    if (!preview) return;

    preview.classList.remove("hidden");
    if (!incidentFormComplete()) {
      incidentDraft = null;
      preview.innerHTML = '<div class="home-incident-empty">Completa motivo, fecha y horario.</div>';
      syncIncidentImpact();
      return;
    }

    const affected = incidentBookingsInRange();
    const customers = new Map(state.customers.map(item => [String(item.id), item]));
    const services = new Map(state.services.map(item => [String(item.id), item]));
    const grouped = new Map();
    affected.forEach(booking => {
      const key = String(booking.customerId || booking.id);
      if (!grouped.has(key)) grouped.set(key, []);
      grouped.get(key).push(booking);
    });
    const groups = [...grouped.values()];

    if (!groups.length) {
      incidentDraft = null;
      preview.innerHTML = '<div class="home-incident-empty">No hay clientes afectados en ese horario.</div>';
      return;
    }

    const draftRecipients = groups.map(bookings => {
      const first = bookings[0];
      const customer = customers.get(String(first.customerId)) || {};
      const customerName = customer.name || customer.phone || "Cliente";
      const bookingText = bookings.map(booking => {
        const service = services.get(String(booking.serviceId)) || {};
        return `${service.name || "Servicio"} · ${fmtCompact(booking.startAt)}`;
      }).join(" / ");
      const noun = bookings.length === 1 ? "tu reserva" : "tus reservas";
      const actionText = goal === "RESCHEDULE" ? " Podemos ayudarte a reprogramarla." : "";
      const message = `Hola ${customerName}, ${state.businessName} necesita informarte de un cambio que afecta ${noun}: ${bookingText}. Motivo: ${reason}.${actionText}`;
      return {
        customerId: first.customerId,
        customerName,
        bookingIds: bookings.map(item => item.id),
        bookingText,
        content: message,
        channelPreference: strategy,
        channelLabel: incidentChannelLabel(strategy, bookings)
      };
    });

    incidentDraft = { reason, goal, strategy, date: targetDate, timeFrom: targetTimeFrom, timeTo: targetTimeTo, recipients: draftRecipients };
    const rows = draftRecipients.map((recipient, index) => `<article class="home-incident-item">
      <label class="home-incident-choice">
        <input class="home-incident-select" type="checkbox" checked data-incident-index="${index}" aria-label="Incluir ${esc(recipient.customerName)}">
        <span><strong>${esc(recipient.customerName)}</strong><small>${esc(recipient.bookingText)}</small></span>
      </label>
      <span class="home-incident-channel">${esc(recipient.channelLabel)}</span>
      <details class="home-incident-message-details">
        <summary>Ver mensaje</summary>
        <p class="home-incident-message">${esc(recipient.content)}</p>
      </details>
    </article>`).join("");

    preview.innerHTML = `
      <div class="home-incident-preview-head">
        <div><strong>${groups.length} cliente${groups.length === 1 ? "" : "s"} afectado${groups.length === 1 ? "" : "s"}</strong></div>
        <label class="home-incident-select-all"><input id="homeIncidentSelectAll" type="checkbox" checked> Todos</label>
      </div>
      <div class="home-incident-list">${rows}</div>
      <div class="home-incident-footer">
        <span id="homeIncidentSelectionSummary">${draftRecipients.length} seleccionado${draftRecipients.length === 1 ? "" : "s"}</span>
        <button id="homeIncidentPrepareCampaign" type="button">Continuar</button>
      </div>
    `;
    bindIncidentSelection();
  }

  function selectedIncidentRecipients() {
    if (!incidentDraft) return [];
    const selected = new Set(
      [...document.querySelectorAll(".home-incident-select:checked")]
        .map(input => Number(input.dataset.incidentIndex))
    );
    return incidentDraft.recipients.filter((_, index) => selected.has(index));
  }

  function syncIncidentSelection() {
    const boxes = [...document.querySelectorAll(".home-incident-select")];
    const checked = boxes.filter(input => input.checked);
    const summary = document.querySelector("#homeIncidentSelectionSummary");
    const button = document.querySelector("#homeIncidentPrepareCampaign");
    const selectAll = document.querySelector("#homeIncidentSelectAll");
    if (summary) summary.textContent = `${checked.length} seleccionado${checked.length === 1 ? "" : "s"}`;
    if (button) {
      button.disabled = checked.length === 0;
      button.title = checked.length === 0
        ? "Selecciona al menos un cliente para preparar la campaña."
        : "Preparar la campaña sin enviar mensajes todavía.";
    }
    if (selectAll) {
      selectAll.checked = boxes.length > 0 && checked.length === boxes.length;
      selectAll.indeterminate = checked.length > 0 && checked.length < boxes.length;
    }
  }

  async function prepareIncidentCampaign() {
    if (!incidentDraft) return;
    const selected = selectedIncidentRecipients();
    if (!selected.length) return;

    const confirmed = window.confirm(
      `Preparar avisos para ${selected.length} cliente${selected.length === 1 ? "" : "s"}?`
    );
    if (!confirmed) return;

    const button = document.querySelector("#homeIncidentPrepareCampaign");
    if (button) {
      button.disabled = true;
      button.textContent = "Revisando…";
    }

    try {
      const result = await api("/api/v1/booking-incident-campaigns", {
        method: "POST",
        body: JSON.stringify({
          reason: incidentDraft.reason,
          goal: incidentDraft.goal,
          strategy: incidentDraft.strategy,
          recipients: selected.map(item => ({
            customerId: item.customerId,
            bookingIds: item.bookingIds,
            channelPreference: item.channelPreference,
            content: item.content
          }))
        })
      });

      let readiness = { ready: false, blockers: [] };
      try {
        readiness = await api(`/api/v1/booking-incident-campaigns/${encodeURIComponent(result.id)}/activation-readiness`);
      } catch (_) {
        readiness = { ready: false, blockers: [] };
      }

      const footer = document.querySelector(".home-incident-footer");
      if (footer) {
        if (readiness?.ready === true) {
          footer.innerHTML = `
            <div class="home-incident-ready">
              <div><strong>Listo para enviar</strong><span>${selected.length} aviso${selected.length === 1 ? "" : "s"} revisado${selected.length === 1 ? "" : "s"}.</span></div>
              <button id="homeIncidentSendNow" type="button">Enviar ${selected.length} aviso${selected.length === 1 ? "" : "s"}</button>
            </div>
          `;
          document.querySelector("#homeIncidentSendNow")?.addEventListener("click", event =>
            activateIncidentCampaign(result.id, selected.length, event.currentTarget)
          );
        } else {
          footer.innerHTML = `
            <div class="home-incident-blocked">
              <strong>Envíos desactivados</strong>
              <span>Puedes revisar los avisos, pero todavía no enviarlos.</span>
            </div>
          `;
        }
      }
      await loadIncidentHistory();
    } catch (error) {
      if (button) {
        button.disabled = false;
        button.textContent = "Continuar";
      }
      const summary = document.querySelector("#homeIncidentSelectionSummary");
      if (summary) summary.textContent = error.message || "No pude preparar los avisos.";
    }
  }

  function bindIncidentSelection() {
    document.querySelectorAll(".home-incident-select").forEach(input =>
      input.addEventListener("change", syncIncidentSelection)
    );
    document.querySelector("#homeIncidentSelectAll")?.addEventListener("change", event => {
      document.querySelectorAll(".home-incident-select").forEach(input => {
        input.checked = event.target.checked;
      });
      syncIncidentSelection();
    });
    document.querySelector("#homeIncidentPrepareCampaign")?.addEventListener("click", prepareIncidentCampaign);
    syncIncidentSelection();
  }

  const incidentGoalLabel = value => value === "RESCHEDULE" ? "Avisar y reprogramar" : value === "INFORM" ? "Solo avisar" : String(value || "");
  const incidentStrategyLabel = value => value === "CHEAPEST" ? "Más económico" : value === "WHATSAPP" ? "WhatsApp" : value === "CALL" ? "Llamada" : String(value || "");
  const incidentCampaignStatusLabel = value => ({
    PREPARED: "Pendiente",
    ACTIVATED: "En proceso",
    COMPLETED: "Completado",
    CANCELLED: "Cancelado"
  })[value] || "Pendiente";

  const incidentDeliveryLabel = value => ({
    PENDING: "Pendiente",
    QUEUED: "Enviando…",
    SENT: "Enviado",
    DELIVERED: "Entregado",
    READ: "Leído",
    FAILED: "Error",
    CANCELLED: "Cancelado"
  })[value] || "Pendiente";

  const incidentDeliveryClass = value => ({
    PENDING: "is-pending",
    QUEUED: "is-sending",
    SENT: "is-sent",
    DELIVERED: "is-delivered",
    READ: "is-delivered",
    FAILED: "is-error",
    CANCELLED: "is-cancelled"
  })[value] || "is-pending";

  function incidentCampaignDisplayStatus(campaign) {
    const recipients = Array.isArray(campaign?.recipients) ? campaign.recipients : [];
    if (recipients.length && recipients.every(item => ["DELIVERED", "READ"].includes(item?.status))) return "Completado";
    if (recipients.some(item => item?.status === "FAILED")) return "Con errores";
    return incidentCampaignStatusLabel(campaign?.status);
  }

  function incidentDeliveryRows(campaign) {
    const recipients = Array.isArray(campaign?.recipients) ? campaign.recipients : [];
    if (!recipients.length) return "";
    return `<div class="home-incident-delivery-list">${recipients.map(item => {
      const status = incidentDeliveryLabel(item?.status);
      const statusClass = incidentDeliveryClass(item?.status);
      const time = item?.statusAt ? fmtCompact(item.statusAt) : "";
      const channel = item?.channel === "WHATSAPP" ? "WhatsApp" : incidentStrategyLabel(item?.channel);
      return `<div class="home-incident-delivery-row" data-incident-recipient="${esc(item?.recipientId || "")}">
        <div class="home-incident-delivery-who">
          <strong>${esc(item?.customerName || "Cliente")}</strong>
          <span>${esc(channel)}${time ? ` · ${esc(time)}` : ""}</span>
        </div>
        <div class="home-incident-delivery-result">
          <span class="home-incident-delivery-status ${statusClass}">${esc(status)}</span>
          ${item?.retryable ? `<button type="button"
            data-incident-retry="${esc(item.recipientId)}"
            data-incident-campaign="${esc(campaign.id)}">Reintentar</button>` : ""}
        </div>
      </div>`;
    }).join("")}</div>`;
  }

  async function loadIncidentHistory() {
    const host = document.querySelector("#homeIncidentHistoryList");
    const count = document.querySelector("#homeIncidentHistoryCount");
    if (!host) return;
    host.innerHTML = '<div class="home-incident-empty">Cargando historial…</div>';
    try {
      const campaigns = await api("/api/v1/booking-incident-campaigns");
      const items = Array.isArray(campaigns) ? campaigns : [];
      if (count) count.textContent = String(items.length);
      if (!items.length) {
        host.innerHTML = '<div class="home-incident-empty">Sin avisos anteriores.</div>';
        return;
      }
      host.innerHTML = items.map(campaign => {
        const blockers = Array.isArray(campaign.activationBlockers) ? campaign.activationBlockers : [];
        const deliveries = Array.isArray(campaign.recipients) ? campaign.recipients : [];
        const canActivate = campaign.status === "PREPARED" && campaign.activationReady === true;
        const alreadyActivated = campaign.status === "ACTIVATED";
        const deliveryDisabled = blockers.some(item => item?.code === "OUTBOUND_DELIVERY_DISABLED");
        const displayStatus = incidentCampaignDisplayStatus(campaign);
        const helper = deliveries.length
          ? ""
          : alreadyActivated
            ? "Envío iniciado."
            : canActivate
              ? "Listo para enviar."
              : deliveryDisabled
                ? "Envíos desactivados."
                : "No disponible para enviar.";
        return `
          <article class="home-incident-history-item">
            <div class="home-incident-history-main">
              <div><strong>${esc(campaign.reason || "Imprevisto")}</strong><span>${esc(fmtCompact(campaign.createdAt))}</span></div>
              <span class="home-incident-history-status">${esc(displayStatus)}</span>
            </div>
            <div class="home-incident-history-meta">
              <span>${esc(incidentGoalLabel(campaign.goal))}</span>
              <span>${esc(incidentStrategyLabel(campaign.strategy))}</span>
              <span>${esc(campaign.recipientCount)} cliente${Number(campaign.recipientCount) === 1 ? "" : "s"}</span>
            </div>
            ${incidentDeliveryRows(campaign)}
            ${helper || canActivate ? `<div class="home-incident-history-actions">
              <small>${esc(helper)}</small>
              ${canActivate ? `<button type="button"
                data-incident-activate="${esc(campaign.id)}"
                data-incident-count="${esc(campaign.recipientCount)}">Enviar avisos</button>` : ""}
            </div>` : ""}
          </article>
        `;
      }).join("");
      bindIncidentActivation();
      bindIncidentRetries();
    } catch (error) {
      host.innerHTML = `<div class="home-incident-empty">${esc(error.message || "No pude cargar el historial.")}</div>`;
    }
  }

  async function activateIncidentCampaign(campaignId, recipientCount, button) {
    if (!campaignId) return;
    const count = Number(recipientCount || 0);
    const confirmed = window.confirm(
      `¿Enviar avisos a ${count} cliente${count === 1 ? "" : "s"} ahora?`
    );
    if (!confirmed) return;

    if (button) {
      button.disabled = true;
      button.textContent = "Enviando…";
    }
    try {
      const result = await api(`/api/v1/booking-incident-campaigns/${encodeURIComponent(campaignId)}/activate`, {
        method: "POST",
        body: JSON.stringify({ confirmed: true })
      });
      await loadIncidentHistory();
      const preview = document.querySelector("#homeIncidentPreview");
      if (preview) {
        preview.classList.remove("hidden");
        preview.innerHTML = `<div class="home-incident-success"><strong>Envío iniciado ✓</strong><span>${esc(result.queuedRecipients)} aviso${Number(result.queuedRecipients) === 1 ? "" : "s"} en proceso.</span></div>`;
      }
    } catch (error) {
      if (button) {
        button.disabled = false;
        button.textContent = "Enviar avisos";
      }
      window.alert(error.message || "No pude enviar los avisos.");
      await loadIncidentHistory();
    }
  }

  function bindIncidentActivation() {
    document.querySelectorAll("[data-incident-activate]").forEach(button => {
      button.addEventListener("click", () =>
        activateIncidentCampaign(button.dataset.incidentActivate, button.dataset.incidentCount, button)
      );
    });
  }

  async function retryIncidentRecipient(campaignId, recipientId, button) {
    if (!campaignId || !recipientId) return;
    const confirmed = window.confirm("¿Reintentar este aviso ahora?");
    if (!confirmed) return;

    if (button) {
      button.disabled = true;
      button.textContent = "Reintentando…";
    }
    try {
      await api(`/api/v1/booking-incident-campaigns/${encodeURIComponent(campaignId)}/recipients/${encodeURIComponent(recipientId)}/retry`, {
        method: "POST",
        body: JSON.stringify({ confirmed: true })
      });
      await loadIncidentHistory();
    } catch (error) {
      if (button) {
        button.disabled = false;
        button.textContent = "Reintentar";
      }
      window.alert(error.message || "No pude reintentar el aviso.");
    }
  }

  function bindIncidentRetries() {
    document.querySelectorAll("[data-incident-retry]").forEach(button => {
      button.addEventListener("click", () =>
        retryIncidentRecipient(button.dataset.incidentCampaign, button.dataset.incidentRetry, button)
      );
    });
  }

  function bindIncidentResolver() {
    const toggle = document.querySelector("#homeIncidentToggle");
    const panel = document.querySelector("#homeIncidentPanel");
    const previewButton = document.querySelector("#homeIncidentPreviewBtn");
    const historyToggle = document.querySelector("#homeIncidentHistoryToggle");
    const historyBody = document.querySelector("#homeIncidentHistoryBody");
    if (!toggle || !panel || !previewButton || toggle.dataset.bound === "true") return;
    toggle.dataset.bound = "true";

    populateIncidentTimeSelectors();
    populateIncidentDateSelector();
    syncIncidentImpact();

    [
      "#homeIncidentReason",
      "#homeIncidentDate",
      "#homeIncidentTimeFrom",
      "#homeIncidentTimeTo",
      "#homeIncidentGoal",
      "#homeIncidentStrategy"
    ].forEach(selector => document.querySelector(selector)?.addEventListener("change", handleIncidentControlChange));

    toggle.addEventListener("click", () => {
      panel.classList.toggle("hidden");
      const open = !panel.classList.contains("hidden");
      toggle.setAttribute("aria-expanded", open ? "true" : "false");
      if (open) {
        populateIncidentDateSelector();
        syncIncidentImpact();
        loadIncidentHistory();
      }
    });

    historyToggle?.addEventListener("click", () => {
      if (!historyBody) return;
      historyBody.classList.toggle("hidden");
      const open = !historyBody.classList.contains("hidden");
      historyToggle.setAttribute("aria-expanded", open ? "true" : "false");
      if (open) loadIncidentHistory();
    });

    previewButton.addEventListener("click", renderIncidentPreview);
  }

  async function load() {
    if(loading || dashboard.classList.contains("hidden") || !ready()) return;
    loading=true;
    root.classList.remove("hidden");
    try{
      const me = await api("/api/v1/auth/me");
      state.roles = Array.isArray(me?.roles) ? me.roles.map(String) : [];
      applyRoleVisibility();
      const auditRequest = isBusinessAdmin() ? api("/api/v1/audit") : Promise.resolve([]);
      const [bookings,customers,services,orders,ops,audit]=await Promise.allSettled([
        api("/api/v1/bookings"),
        api("/api/v1/customers"),
        api("/api/v1/services"),
        api("/api/v1/commercial/orders"),
        api("/api/v1/operations/dashboard"),
        auditRequest
      ]);
      state.bookings=bookings.status==="fulfilled"&&Array.isArray(bookings.value)?bookings.value:[];
      state.customers=customers.status==="fulfilled"&&Array.isArray(customers.value)?customers.value:[];
      state.services=services.status==="fulfilled"&&Array.isArray(services.value)?services.value:[];
      state.orders=orders.status==="fulfilled"&&Array.isArray(orders.value)?orders.value:[];
      state.requests=ops.status==="fulfilled"&&Array.isArray(ops.value?.recentRequests)?ops.value.recentRequests:[];
      state.audit=audit.status==="fulfilled"&&Array.isArray(audit.value)?audit.value:[];
      state.auditCatalog=[...state.audit];
      state.businessName=ops.status==="fulfilled"&&ops.value?.businessName?String(ops.value.businessName):(window.helvocaBusinessName||state.businessName||"Tu negocio");
      state.businessTimezone=ops.status==="fulfilled"&&ops.value?.timezone?String(ops.value.timezone):"America/Santiago";
      renderBookings(); renderOrders(); renderRequests(); renderCustomers(); renderAudit(); populateAuditFilterOptions(); if (isBusinessAdmin()) { bindCustomerExports(); bindAuditFilters(); } bindCustomerCreate(); bindBookingCreate(); bindIncidentResolver(); populateIncidentDateSelector(); syncIncidentImpact();
    } finally { loading=false; }
  }

  root.querySelectorAll("[data-home-tab]").forEach(btn=>btn.addEventListener("click",()=>setTab(btn.dataset.homeTab)));
  new MutationObserver(()=>queueMicrotask(load)).observe(statusGrid,{subtree:true,attributes:true,attributeFilter:["class"]});
  new MutationObserver(()=>{ if(!dashboard.classList.contains("hidden")) queueMicrotask(load); }).observe(dashboard,{attributes:true,attributeFilter:["class"]});
  document.querySelector("#refreshBtn")?.addEventListener("click",load);
  ensureBookingDrawer();
  const requestedTab = new URLSearchParams(window.location.search).get("tab");
  setTab(["bookings","orders","requests","customers"].includes(requestedTab) ? requestedTab : "bookings");
  queueMicrotask(load);
})();
