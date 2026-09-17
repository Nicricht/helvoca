(() => {
  const BOOKING_STATUS_LABELS = { CONFIRMED: "Confirmada", CANCELLED: "Cancelada" };
  const ORDER_STATUS_LABELS = { CONFIRMED: "Confirmado", PREPARING: "Preparando", READY: "Listo", DISPATCHED: "Despachado", COMPLETED: "Completado", CANCELLED: "Cancelado" };
  const SOURCE_LABELS = { VOICE: "Voz", WHATSAPP: "WhatsApp", MANUAL: "Manual", API: "API" };
  const state = { bookings: [], customers: [], services: [], orders: [] };

  function money(value, currency = "CLP") {
    const amount = Number(value || 0);
    try {
      return new Intl.NumberFormat("es-CL", {
        style: "currency",
        currency: currency || "CLP",
        maximumFractionDigits: currency === "CLP" ? 0 : 2
      }).format(amount);
    } catch (_) {
      return `${currency || ""} ${amount}`.trim();
    }
  }

  function setTab(tab) {
    document.querySelectorAll("[data-business-tab]").forEach(button => {
      const active = button.dataset.businessTab === tab;
      button.classList.toggle("active", active);
      button.setAttribute("aria-selected", active ? "true" : "false");
    });
    document.querySelectorAll("[data-business-panel]").forEach(panel => {
      panel.classList.toggle("hidden", panel.dataset.businessPanel !== tab);
    });
  }

  function renderBookings(error = null) {
    const root = $("#bookingsList");
    const customers = new Map(state.customers.map(item => [String(item.id), item]));
    const services = new Map(state.services.map(item => [String(item.id), item]));
    const now = Date.now();
    const items = [...state.bookings].sort((a, b) => {
      const at = new Date(a.startAt || 0).getTime();
      const bt = new Date(b.startAt || 0).getTime();
      const af = at >= now;
      const bf = bt >= now;
      if (af !== bf) return af ? -1 : 1;
      return af ? at - bt : bt - at;
    });

    $("#businessBookingsCount").textContent = String(items.length);
    if (error && !items.length) {
      root.innerHTML = `<div class="business-error">${esc(error)}</div>`;
      return;
    }
    if (!items.length) {
      root.innerHTML = '<div class="empty">Todavía no hay reservas registradas.</div>';
      return;
    }

    root.innerHTML = items.map(booking => {
      const customer = customers.get(String(booking.customerId)) || {};
      const service = services.get(String(booking.serviceId)) || {};
      const customerName = customer.name || customer.phone || "Cliente";
      const status = BOOKING_STATUS_LABELS[booking.status] || humanize(booking.status, {});
      const cancelled = booking.status === "CANCELLED";
      return `
        <article class="business-row" data-booking-id="${esc(booking.id)}">
          <div class="business-row-main">
            <div class="business-row-copy">
              <span class="business-kicker">${esc(fmtDate(booking.startAt))}</span>
              <strong>${esc(customerName)}</strong>
              <span>${esc(service.name || "Servicio")}${customer.phone && customer.name ? ` · ${esc(customer.phone)}` : ""}</span>
            </div>
            <span class="pill ${cancelled ? "bad" : ""}">${esc(status)}</span>
          </div>
          <div class="business-meta">
            ${booking.endAt ? `<span>Hasta ${esc(fmtDate(booking.endAt))}</span>` : ""}
            ${booking.source ? `<span>Origen: ${esc(SOURCE_LABELS[booking.source] || humanize(booking.source, {}))}</span>` : ""}
            ${booking.notes ? `<span>${esc(booking.notes)}</span>` : ""}
          </div>
        </article>`;
    }).join("");
  }

  function orderActions(order) {
    switch (order.status) {
      case "CONFIRMED": return [["PREPARING", "Empezar preparación"], ["CANCELLED", "Cancelar"]];
      case "PREPARING": return [["READY", "Marcar listo"], ["CANCELLED", "Cancelar"]];
      case "READY": return order.fulfillmentType === "DELIVERY"
        ? [["DISPATCHED", "Marcar despachado"]]
        : [["COMPLETED", "Completar"]];
      case "DISPATCHED": return [["COMPLETED", "Completar"]];
      default: return [];
    }
  }

  function renderOrders(error = null) {
    const root = $("#ordersList");
    const items = [...state.orders].sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0));
    $("#businessOrdersCount").textContent = String(items.length);

    if (error && !items.length) {
      root.innerHTML = `<div class="business-error">${esc(error)}</div>`;
      return;
    }
    if (!items.length) {
      root.innerHTML = '<div class="empty">Todavía no hay pedidos confirmados.</div>';
      return;
    }

    root.innerHTML = items.map(order => {
      const status = ORDER_STATUS_LABELS[order.status] || humanize(order.status, {});
      const terminal = order.status === "CANCELLED" || order.status === "COMPLETED";
      const lines = Array.isArray(order.lines) ? order.lines : [];
      const actions = orderActions(order);
      return `
        <article class="business-row order-card" data-order-id="${esc(order.id)}">
          <div class="business-row-main">
            <div class="business-row-copy">
              <span class="business-kicker">Pedido · ${esc(fmtDate(order.createdAt))}</span>
              <strong>${esc(order.contactName || order.contactPhone || "Cliente")}</strong>
              <span>${order.contactPhone ? esc(order.contactPhone) : "Sin teléfono"}${order.fulfillmentType ? ` · ${order.fulfillmentType === "DELIVERY" ? "Delivery" : "Retiro"}` : ""}</span>
            </div>
            <span class="pill ${order.status === "CANCELLED" ? "bad" : terminal ? "" : "high"}">${esc(status)}</span>
          </div>
          <div class="order-lines">
            ${lines.length ? lines.map(line => `<div class="order-line"><span>${esc(line.quantity || 0)} × ${esc(line.name || "Producto")}</span><span>${esc(money(line.lineTotal ?? (Number(line.quantity || 0) * Number(line.unitPrice || 0)), order.currency))}</span></div>`).join("") : '<span class="muted">Sin líneas de detalle.</span>'}
          </div>
          <div class="order-total"><span>Total</span><span>${esc(money(order.total, order.currency))}</span></div>
          <div class="business-meta">
            ${order.source ? `<span>Origen: ${esc(SOURCE_LABELS[order.source] || humanize(order.source, {}))}</span>` : ""}
            ${order.deliveryAddress ? `<span>${esc(order.deliveryAddress)}</span>` : ""}
          </div>
          ${actions.length ? `<div class="business-actions">${actions.map(([next, label]) => `<button type="button" class="${next === "CANCELLED" ? "ghost" : "button secondary"}" data-order-status="${next}">${label}</button>`).join("")}</div>` : ""}
        </article>`;
    }).join("");

    root.querySelectorAll("[data-order-status]").forEach(button => button.addEventListener("click", async event => {
      const card = event.currentTarget.closest("[data-order-id]");
      const nextStatus = event.currentTarget.dataset.orderStatus;
      if (nextStatus === "CANCELLED" && !window.confirm("¿Cancelar este pedido?")) return;
      event.currentTarget.disabled = true;
      try {
        await api(`/api/v1/commercial/orders/${encodeURIComponent(card.dataset.orderId)}/status`, {
          method: "PATCH",
          body: JSON.stringify({ status: nextStatus })
        });
        await loadBusinessWorkspace();
        setTab("orders");
        toast("Pedido actualizado.");
      } catch (err) {
        toast(err.message || "No pude actualizar el pedido.");
        event.currentTarget.disabled = false;
      }
    }));
  }

  function renderCustomers(error = null) {
    const root = $("#customersList");
    const items = [...state.customers].sort((a, b) => String(a.name || a.phone || "").localeCompare(String(b.name || b.phone || ""), "es"));
    $("#businessCustomersCount").textContent = String(items.length);

    if (error && !items.length) {
      root.innerHTML = `<div class="business-error">${esc(error)}</div>`;
      return;
    }
    if (!items.length) {
      root.innerHTML = '<div class="empty">Todavía no hay clientes registrados.</div>';
      return;
    }

    root.innerHTML = items.map(customer => `
      <article class="business-row" data-customer-id="${esc(customer.id)}">
        <div class="business-row-main">
          <div class="business-row-copy">
            <strong>${esc(customer.name || customer.phone || "Cliente")}</strong>
            <div class="customer-contact">
              ${customer.phone ? `<span>${esc(customer.phone)}</span>` : ""}
              ${customer.email ? `<span>${esc(customer.email)}</span>` : ""}
            </div>
          </div>
          <span class="pill">Cliente</span>
        </div>
        <div class="business-meta"><span>Registrado ${esc(fmtDate(customer.createdAt))}</span></div>
      </article>`).join("");
  }

  function syncRequestCount() {
    const count = document.querySelectorAll("#requestsList [data-request-id]").length;
    const target = $("#businessRequestsCount");
    if (target) target.textContent = String(count);
  }

  async function loadBusinessWorkspace() {
    const [bookingsResult, customersResult, servicesResult, ordersResult] = await Promise.allSettled([
      api("/api/v1/bookings"),
      api("/api/v1/customers"),
      api("/api/v1/services"),
      api("/api/v1/commercial/orders")
    ]);

    state.bookings = bookingsResult.status === "fulfilled" && Array.isArray(bookingsResult.value) ? bookingsResult.value : [];
    state.customers = customersResult.status === "fulfilled" && Array.isArray(customersResult.value) ? customersResult.value : [];
    state.services = servicesResult.status === "fulfilled" && Array.isArray(servicesResult.value) ? servicesResult.value : [];
    state.orders = ordersResult.status === "fulfilled" && Array.isArray(ordersResult.value) ? ordersResult.value : [];

    renderBookings(bookingsResult.status === "rejected" ? (bookingsResult.reason?.message || "No pude cargar las reservas.") : null);
    renderOrders(ordersResult.status === "rejected" ? (ordersResult.reason?.message || "No pude cargar los pedidos.") : null);
    renderCustomers(customersResult.status === "rejected" ? (customersResult.reason?.message || "No pude cargar los clientes.") : null);
    syncRequestCount();
  }

  document.querySelectorAll("[data-business-tab]").forEach(button => {
    button.addEventListener("click", () => setTab(button.dataset.businessTab));
  });
  const requests = $("#requestsList");
  if (requests) new MutationObserver(syncRequestCount).observe(requests, { childList: true, subtree: true });
  $("#refreshBtn")?.addEventListener("click", loadBusinessWorkspace);

  setTab("bookings");
  loadBusinessWorkspace();
})();
