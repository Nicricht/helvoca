(() => {
  const root = document.querySelector("#homeBusinessWorkspace");
  const dashboard = document.querySelector("#dashboardView");
  const statusGrid = document.querySelector("#statusGrid");
  if (!root || !dashboard || !statusGrid || typeof api !== "function") return;

  const state = { bookings: [], customers: [], services: [], orders: [], requests: [] };
  const bookingFilters = { query: "", date: "all", serviceId: "all", status: "all", source: "all" };
  const EVENT_LABELS = {
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

  const esc = value => String(value ?? "").replace(/[&<>'"]/g, c => ({ "&":"&amp;","<":"&lt;",">":"&gt;","'":"&#39;",'"':"&quot;" }[c]));
  const fmt = value => {
    if (!value) return "";
    try { return new Intl.DateTimeFormat("es-CL",{day:"2-digit",month:"2-digit",year:"2-digit",hour:"2-digit",minute:"2-digit"}).format(new Date(value)); }
    catch (_) { return String(value); }
  };
  const money = (value,currency="CLP") => {
    try { return new Intl.NumberFormat("es-CL",{style:"currency",currency:currency||"CLP",maximumFractionDigits:currency==="CLP"?0:2}).format(Number(value||0)); }
    catch (_) { return String(value||0); }
  };
  const source = value => ({VOICE:"Voz",AI_CALL:"Llamada",WHATSAPP:"WhatsApp",AI_WHATSAPP:"WhatsApp",MANUAL:"Manual",API:"API",ADMIN:"Manual"})[value] || value || "Sin origen";
  const sourceGroup = value => ({VOICE:"CALL",AI_CALL:"CALL",WHATSAPP:"WHATSAPP",AI_WHATSAPP:"WHATSAPP",MANUAL:"MANUAL",ADMIN:"MANUAL",API:"API"})[value] || value || "";
  const status = value => ({CONFIRMED:"Confirmada",CANCELLED:"Cancelada",PREPARING:"Preparando",READY:"Listo",DISPATCHED:"Despachado",COMPLETED:"Completado",OPEN:"Abierta",IN_PROGRESS:"En curso"})[value] || value || "";

  function eventLabel(value) {
    return EVENT_LABELS[value] || String(value || "").toLowerCase().replaceAll("_", " ").replace(/\b\w/g, c => c.toUpperCase());
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
    document.addEventListener("keydown", event => { if (event.key === "Escape") closeBookingDrawer(); });
  }

  function closeBookingDrawer() {
    const backdrop = document.querySelector("#homeBookingDetailBackdrop");
    if (!backdrop) return;
    backdrop.classList.add("hidden");
    backdrop.setAttribute("aria-hidden", "true");
    document.body.classList.remove("home-detail-open");
  }

  function bookingFacts(booking, customer, service) {
    return `<div class="home-detail-facts">
      <div><span>Servicio</span><strong>${esc(service.name || "Servicio")}</strong></div>
      <div><span>Teléfono</span><strong>${esc(customer.phone || "Sin teléfono")}</strong></div>
      <div><span>Inicio</span><strong>${esc(fmt(booking.startAt))}</strong></div>
      <div><span>Fin</span><strong>${esc(fmt(booking.endAt))}</strong></div>
      <div><span>Origen</span><strong>${esc(source(booking.source))}</strong></div>
      <div><span>Estado</span><strong>${esc(status(booking.status))}</strong></div>
    </div>${booking.notes ? `<div class="home-detail-note"><span>Notas</span><p>${esc(booking.notes)}</p></div>` : ""}`;
  }

  function renderContext(context, entityKind = "reserva") {
    if (!context) return '<section class="home-detail-section"><h3>Conversación</h3><p class="home-detail-muted">No hay contexto conversacional disponible.</p></section>';
    const events = Array.isArray(context.events) ? context.events : [];
    const history = events.length ? `<section class="home-detail-section"><h3>Historial</h3><div class="home-detail-history">${events.map(item =>
      `<div><span>${esc(fmt(item.createdAt))}</span><strong>${esc(eventLabel(item.eventType))}</strong></div>`
    ).join("")}</div></section>` : "";
    const aiEvents = events.filter(item => String(item.actorType || "").toUpperCase() === "AI");
    const eventActions = aiEvents.length ? `<section class="home-detail-section"><h3>Qué hizo Helvoca</h3><div class="home-detail-actions-list">${aiEvents.map(item =>
      `<div><span>✓</span><strong>${esc(eventLabel(item.eventType))}</strong></div>`
    ).join("")}</div></section>` : "";

    if (context.channel === "VOICE" && context.call) {
      const detail = context.call;
      const transcript = Array.isArray(detail.transcript) ? detail.transcript : [];
      const actions = Array.isArray(detail.actions) ? detail.actions : [];
      return `
        <section class="home-detail-section"><h3>Resumen</h3><p class="home-detail-summary">${esc(detail.summary || "La llamada no tiene resumen guardado.")}</p></section>
        <section class="home-detail-section"><h3>Conversación</h3><div class="home-detail-transcript">${transcript.length ? transcript.map(line =>
          `<article class="home-detail-message ${String(line.speaker || "").toUpperCase() === "ASSISTANT" ? "assistant" : ""}"><strong>${String(line.speaker || "").toUpperCase() === "ASSISTANT" ? "Helvoca" : "Cliente"}</strong><p>${esc(line.content)}</p><span>${esc(fmt(line.createdAt))}</span></article>`
        ).join("") : '<p class="home-detail-muted">No hay transcripción guardada.</p>'}</div></section>
        <section class="home-detail-section"><h3>Qué hizo Helvoca</h3><div class="home-detail-actions-list">${actions.length ? actions.filter(a => a.success !== false).map(action =>
          `<div><span>✓</span><strong>${esc(eventLabel(action.actionType))}</strong></div>`
        ).join("") : '<p class="home-detail-muted">No hay acciones registradas.</p>'}</div></section>
        ${history}
        ${context.sourceReferenceId ? `<a class="home-detail-link" href="/conversations.html?channel=calls&conversation=${encodeURIComponent(context.sourceReferenceId)}">Ver conversación completa</a>` : ""}
      `;
    }

    if (context.channel === "WHATSAPP" && context.whatsapp) {
      const messages = Array.isArray(context.whatsapp.messages) ? context.whatsapp.messages : [];
      return `
        <section class="home-detail-section"><h3>Conversación de WhatsApp</h3><div class="home-detail-transcript">${messages.length ? messages.map(message => {
          const assistant = String(message.role || "").toLowerCase() === "assistant" || String(message.direction || "").toLowerCase() === "outbound";
          return `<article class="home-detail-message ${assistant ? "assistant" : ""}"><strong>${assistant ? "Helvoca" : "Cliente"}</strong><p>${esc(message.content)}</p><span>${esc(fmt(message.createdAt))}</span></article>`;
        }).join("") : '<p class="home-detail-muted">No hay mensajes guardados.</p>'}</div></section>
        ${eventActions}
        ${history}
        ${context.sourceReferenceId ? `<a class="home-detail-link" href="/conversations.html?channel=whatsapp&conversation=${encodeURIComponent(context.sourceReferenceId)}">Ver conversación completa</a>` : ""}
      `;
    }

    const entityText = entityKind === "pedido" ? "Este pedido" : "Esta reserva";
    const createdSuffix = entityKind === "pedido" ? "" : "a";
    const originText = context.channel === "MANUAL"
      ? `${entityText} fue creado${createdSuffix} manualmente. No existe una conversación asociada.`
      : context.channel === "API"
        ? `${entityText} fue creado${createdSuffix} por API. No existe una conversación asociada.`
        : `No se encontró una conversación enlazada a ${entityKind === "pedido" ? "este pedido" : "esta reserva"}.`;
    return `${eventActions}<section class="home-detail-section"><h3>Origen</h3><p class="home-detail-muted">${originText}</p></section>${history}`;
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
    document.querySelector("#homeBookingDetailMeta").textContent = `${fmt(booking.startAt)} · ${status(booking.status)}`;
    const body = document.querySelector("#homeBookingDetailBody");
    body.innerHTML = bookingFacts(booking, customer, service) + '<div class="home-detail-loading">Cargando conversación…</div>';
    const backdrop = document.querySelector("#homeBookingDetailBackdrop");
    backdrop.classList.remove("hidden");
    backdrop.setAttribute("aria-hidden", "false");
    document.body.classList.add("home-detail-open");
    try {
      const context = await api(`/api/v1/bookings/${encodeURIComponent(id)}/context`);
      body.innerHTML = bookingFacts(booking, customer, service) + renderContext(context);
    } catch (error) {
      body.innerHTML = bookingFacts(booking, customer, service) +
        '<section class="home-detail-section"><h3>Conversación</h3><p class="home-detail-muted">No pude cargar la conversación asociada en este momento.</p></section>';
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

  function setTab(name) {
    root.querySelectorAll("[data-home-tab]").forEach(btn=>{
      const active=btn.dataset.homeTab===name;
      btn.classList.toggle("active",active);
      btn.setAttribute("aria-selected",active?"true":"false");
    });
    root.querySelectorAll("[data-home-panel]").forEach(panel=>panel.classList.toggle("hidden",panel.dataset.homePanel!==name));
  }

  function bookingMatchesDate(item) {
    if (bookingFilters.date === "all") return true;
    const when = new Date(item.startAt || 0);
    if (Number.isNaN(when.getTime())) return false;
    const now = new Date();
    const startToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const startTomorrow = new Date(startToday); startTomorrow.setDate(startTomorrow.getDate() + 1);
    const afterTomorrow = new Date(startTomorrow); afterTomorrow.setDate(afterTomorrow.getDate() + 1);
    const weekStart = new Date(startToday);
    weekStart.setDate(weekStart.getDate() - ((weekStart.getDay() + 6) % 7));
    const weekEnd = new Date(weekStart); weekEnd.setDate(weekEnd.getDate() + 7);
    if (bookingFilters.date === "today") return when >= startToday && when < startTomorrow;
    if (bookingFilters.date === "tomorrow") return when >= startTomorrow && when < afterTomorrow;
    if (bookingFilters.date === "week") return when >= weekStart && when < weekEnd;
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
    closeBookingDrawer();
    await load();
    setTab("orders");
  }

  async function openOrderDetail(id) {
    const order = state.orders.find(item => String(item.id) === String(id));
    if (!order) return;
    ensureBookingDrawer();
    document.querySelector("#homeBookingDetailDrawer .eyebrow").textContent = "PEDIDO";
    document.querySelector("#homeBookingDetailTitle").textContent = `#${String(order.id || "").slice(0,8)} · ${order.contactName || order.contactPhone || "Cliente"}`;
    document.querySelector("#homeBookingDetailMeta").textContent = `${fmt(order.createdAt)} · ${status(order.status)}`;
    const body = document.querySelector("#homeBookingDetailBody");
    const lines = Array.isArray(order.lines) ? order.lines : [];
    const facts = `<div class="home-detail-facts">
      <div><span>Cliente</span><strong>${esc(order.contactName || "Cliente")}</strong></div>
      <div><span>Teléfono</span><strong>${esc(order.contactPhone || "Sin teléfono")}</strong></div>
      <div><span>Entrega</span><strong>${order.fulfillmentType === "DELIVERY" ? "Delivery" : "Retiro"}</strong></div>
      <div><span>Origen</span><strong>${esc(source(order.source))}</strong></div>
      <div><span>Estado</span><strong>${esc(status(order.status))}</strong></div>
      <div><span>Subtotal</span><strong>${esc(money(order.subtotal, order.currency))}</strong></div>
      <div><span>Despacho</span><strong>${esc(money(order.deliveryFee, order.currency))}</strong></div>
      <div><span>Total</span><strong>${esc(money(order.total, order.currency))}</strong></div>
    </div>
    <section class="home-detail-section"><h3>Productos</h3><div class="home-detail-lines">${lines.length ? lines.map(line =>
      `<div><span>${esc(line.quantity || 0)} × ${esc(line.name || "Producto")}${line.unitPrice != null ? ` · ${esc(money(line.unitPrice, order.currency))} c/u` : ""}</span><strong>${esc(money(line.lineTotal ?? (Number(line.quantity || 0) * Number(line.unitPrice || 0)), order.currency))}</strong></div>`
    ).join("") : '<p class="home-detail-muted">Sin líneas de detalle.</p>'}</div></section>
    ${order.deliveryAddress ? `<div class="home-detail-note"><span>Dirección</span><p>${esc(order.deliveryAddress)}</p></div>` : ""}`;

    body.innerHTML = facts + '<div class="home-detail-loading">Cargando conversación…</div>';
    const backdrop = document.querySelector("#homeBookingDetailBackdrop");
    backdrop.classList.remove("hidden");
    backdrop.setAttribute("aria-hidden", "false");
    document.body.classList.add("home-detail-open");

    const context = await loadOrderConversation(order);
    const actions = orderActions(order);
    body.innerHTML = facts + renderContext(context, "pedido") +
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
    host.innerHTML=`<div class="home-business-table-shell"><table class="home-business-table"><thead><tr><th>Pedido</th><th>Cliente</th><th>Total</th><th>Entrega</th><th>Estado</th><th>Origen</th></tr></thead><tbody>${items.map(item=>`<tr tabindex="0" data-home-order-id="${esc(item.id)}"><td><strong>#${esc(String(item.id||"").slice(0,8))}</strong></td><td><strong>${esc(item.contactName||item.contactPhone||"Cliente")}</strong></td><td>${esc(money(item.total,item.currency))}</td><td>${item.fulfillmentType==="DELIVERY"?"Delivery":"Retiro"}</td><td><span class="home-pill">${esc(status(item.status))}</span></td><td>${esc(source(item.source))}</td></tr>`).join("")}</tbody></table></div>
    <div class="home-business-mobile-list">${items.map(item=>`<article class="home-business-mobile-card" tabindex="0" data-home-order-id="${esc(item.id)}"><strong>${esc(item.contactName||item.contactPhone||"Cliente")}</strong><span>${esc(money(item.total,item.currency))} · ${esc(status(item.status))}</span></article>`).join("")}</div>`;
    bindOrderOpeners();
  }

  function renderRequests() {
    const items=state.requests||[];
    document.querySelector("#homeBusinessRequestsCount").textContent=String(items.length);
    const host=document.querySelector("#homeRequestsList");
    if(!items.length){ host.innerHTML='<div class="home-business-empty">No hay solicitudes recientes.</div>'; return; }
    host.innerHTML=items.map(item=>`<article class="home-simple-row"><div><strong>${esc(item.title||item.type||"Solicitud")}</strong><span>${esc(item.description||item.contactName||"")}</span></div><span class="home-pill">${esc(status(item.status))}</span></article>`).join("");
  }

  function renderCustomers() {
    const items=[...state.customers].sort((a,b)=>String(a.name||a.phone||"").localeCompare(String(b.name||b.phone||""),"es"));
    document.querySelector("#homeBusinessCustomersCount").textContent=String(items.length);
    const host=document.querySelector("#homeCustomersList");
    if(!items.length){ host.innerHTML='<div class="home-business-empty">Todavía no hay clientes registrados.</div>'; return; }
    host.innerHTML=items.map(item=>`<article class="home-simple-row"><div><strong>${esc(item.name||item.phone||"Cliente")}</strong><span>${esc([item.phone,item.email].filter(Boolean).join(" · "))}</span></div><span class="home-pill">Cliente</span></article>`).join("");
  }

  async function load() {
    if(loading || dashboard.classList.contains("hidden") || !ready()) return;
    loading=true;
    root.classList.remove("hidden");
    try{
      const [bookings,customers,services,orders,ops]=await Promise.allSettled([
        api("/api/v1/bookings"),
        api("/api/v1/customers"),
        api("/api/v1/services"),
        api("/api/v1/commercial/orders"),
        api("/api/v1/operations/dashboard")
      ]);
      state.bookings=bookings.status==="fulfilled"&&Array.isArray(bookings.value)?bookings.value:[];
      state.customers=customers.status==="fulfilled"&&Array.isArray(customers.value)?customers.value:[];
      state.services=services.status==="fulfilled"&&Array.isArray(services.value)?services.value:[];
      state.orders=orders.status==="fulfilled"&&Array.isArray(orders.value)?orders.value:[];
      state.requests=ops.status==="fulfilled"&&Array.isArray(ops.value?.recentRequests)?ops.value.recentRequests:[];
      renderBookings(); renderOrders(); renderRequests(); renderCustomers();
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