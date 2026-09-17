(() => {
  const BOOKING_STATUS_LABELS = { CONFIRMED: "Confirmada", CANCELLED: "Cancelada" };
  const ORDER_STATUS_LABELS = { CONFIRMED: "Confirmado", PREPARING: "Preparando", READY: "Listo", DISPATCHED: "Despachado", COMPLETED: "Completado", CANCELLED: "Cancelado" };
  const SOURCE_LABELS = { VOICE: "Voz", WHATSAPP: "WhatsApp", MANUAL: "Manual", API: "API", AI_CALL: "Llamada" };
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

  function sourceLabel(value) {
    return SOURCE_LABELS[value] || humanize(value, {}) || "Sin origen";
  }

  function shortId(value) {
    const text = String(value || "");
    return text ? `#${text.slice(0, 8)}` : "Pedido";
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

  function ensureDrawer() {
    if ($("#businessDetailBackdrop")) return;
    document.body.insertAdjacentHTML("beforeend", `
      <div id="businessDetailBackdrop" class="business-detail-backdrop hidden" aria-hidden="true">
        <aside id="businessDetailDrawer" class="business-detail-drawer" role="dialog" aria-modal="true" aria-labelledby="businessDetailTitle">
          <div class="business-detail-head">
            <div>
              <span id="businessDetailEyebrow" class="eyebrow">DETALLE</span>
              <h2 id="businessDetailTitle">Detalle</h2>
              <p id="businessDetailMeta" class="muted"></p>
            </div>
            <button id="businessDetailClose" class="ghost business-detail-close" type="button" aria-label="Cerrar detalle">Cerrar</button>
          </div>
          <div id="businessDetailBody" class="business-detail-body"></div>
          <div id="businessDetailActions" class="business-detail-actions"></div>
        </aside>
      </div>
    `);
    $("#businessDetailClose").addEventListener("click", closeDrawer);
    $("#businessDetailBackdrop").addEventListener("click", event => {
      if (event.target === event.currentTarget) closeDrawer();
    });
    document.addEventListener("keydown", event => {
      if (event.key === "Escape") closeDrawer();
    });
  }

  function closeDrawer() {
    const backdrop = $("#businessDetailBackdrop");
    if (!backdrop) return;
    backdrop.classList.add("hidden");
    backdrop.setAttribute("aria-hidden", "true");
    document.body.classList.remove("business-detail-open");
  }

  function showDrawer({ eyebrow, title, meta, body, actions = "" }) {
    ensureDrawer();
    $("#businessDetailEyebrow").textContent = eyebrow;
    $("#businessDetailTitle").textContent = title;
    $("#businessDetailMeta").textContent = meta || "";
    $("#businessDetailBody").innerHTML = body;
    $("#businessDetailActions").innerHTML = actions;
    const backdrop = $("#businessDetailBackdrop");
    backdrop.classList.remove("hidden");
    backdrop.setAttribute("aria-hidden", "false");
    document.body.classList.add("business-detail-open");
    $("#businessDetailClose").focus();
  }

  function bindOpeners(root, selector, handler) {
    root.querySelectorAll(selector).forEach(node => {
      const open = () => handler(node.dataset.entityId);
      node.addEventListener("click", event => {
        if (event.target.closest("button,a,input,select,textarea")) return;
        open();
      });
      node.addEventListener("keydown", event => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          open();
        }
      });
    });
  }

  function customerMap() {
    return new Map(state.customers.map(item => [String(item.id), item]));
  }

  function serviceMap() {
    return new Map(state.services.map(item => [String(item.id), item]));
  }

  function renderBookings(error = null) {
    const root = $("#bookingsList");
    const customers = customerMap();
    const services = serviceMap();
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

    const rows = items.map(booking => {
      const customer = customers.get(String(booking.customerId)) || {};
      const service = services.get(String(booking.serviceId)) || {};
      const customerName = customer.name || customer.phone || "Cliente";
      const status = BOOKING_STATUS_LABELS[booking.status] || humanize(booking.status, {});
      const cancelled = booking.status === "CANCELLED";
      return `
        <tr class="business-table-row" tabindex="0" role="button" data-booking-open data-entity-id="${esc(booking.id)}">
          <td><strong>${esc(fmtDate(booking.startAt))}</strong></td>
          <td><strong>${esc(customerName)}</strong></td>
          <td>${esc(service.name || "Servicio")}</td>
          <td>${esc(customer.phone || "Sin teléfono")}</td>
          <td>${esc(sourceLabel(booking.source))}</td>
          <td><span class="pill ${cancelled ? "bad" : ""}">${esc(status)}</span></td>
        </tr>`;
    }).join("");

    const cards = items.map(booking => {
      const customer = customers.get(String(booking.customerId)) || {};
      const service = services.get(String(booking.serviceId)) || {};
      const status = BOOKING_STATUS_LABELS[booking.status] || humanize(booking.status, {});
      return `
        <article class="business-row business-mobile-card" tabindex="0" role="button" data-booking-open data-entity-id="${esc(booking.id)}">
          <div class="business-row-main">
            <div class="business-row-copy">
              <span class="business-kicker">${esc(fmtDate(booking.startAt))}</span>
              <strong>${esc(customer.name || customer.phone || "Cliente")}</strong>
              <span>${esc(service.name || "Servicio")}${customer.phone && customer.name ? ` · ${esc(customer.phone)}` : ""}</span>
            </div>
            <span class="pill ${booking.status === "CANCELLED" ? "bad" : ""}">${esc(status)}</span>
          </div>
        </article>`;
    }).join("");

    root.innerHTML = `
      <div class="business-table-shell">
        <table class="business-data-table" data-table="bookings">
          <thead><tr><th>Fecha / hora</th><th>Cliente</th><th>Servicio</th><th>Contacto</th><th>Origen</th><th>Estado</th></tr></thead>
          <tbody>${rows}</tbody>
        </table>
      </div>
      <div class="business-mobile-list">${cards}</div>`;

    bindOpeners(root, "[data-booking-open]", openBookingDetail);
  }

  function openBookingDetail(id) {
    const booking = state.bookings.find(item => String(item.id) === String(id));
    if (!booking) return;
    const customer = customerMap().get(String(booking.customerId)) || {};
    const service = serviceMap().get(String(booking.serviceId)) || {};
    const status = BOOKING_STATUS_LABELS[booking.status] || humanize(booking.status, {});
    showDrawer({
      eyebrow: "RESERVA",
      title: customer.name || customer.phone || "Cliente",
      meta: `${fmtDate(booking.startAt)} · ${status}`,
      body: `
        <div class="business-detail-facts">
          <div><span>Servicio</span><strong>${esc(service.name || "Servicio")}</strong></div>
          <div><span>Teléfono</span><strong>${esc(customer.phone || "Sin teléfono")}</strong></div>
          <div><span>Inicio</span><strong>${esc(fmtDate(booking.startAt))}</strong></div>
          <div><span>Fin</span><strong>${esc(fmtDate(booking.endAt))}</strong></div>
          <div><span>Origen</span><strong>${esc(sourceLabel(booking.source))}</strong></div>
          <div><span>Estado</span><strong>${esc(status)}</strong></div>
        </div>
        ${booking.notes ? `<div class="business-detail-note"><span>Notas</span><p>${esc(booking.notes)}</p></div>` : ""}
      `
    });
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

  async function updateOrderStatus(orderId, nextStatus) {
    if (nextStatus === "CANCELLED" && !window.confirm("¿Cancelar este pedido?")) return;
    await api(`/api/v1/commercial/orders/${encodeURIComponent(orderId)}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status: nextStatus })
    });
    await loadBusinessWorkspace();
    setTab("orders");
    const refreshed = state.orders.find(item => String(item.id) === String(orderId));
    if (refreshed) openOrderDetail(orderId);
    toast("Pedido actualizado.");
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

    const rows = items.map(order => {
      const status = ORDER_STATUS_LABELS[order.status] || humanize(order.status, {});
      return `
        <tr class="business-table-row" tabindex="0" role="button" data-order-open data-entity-id="${esc(order.id)}">
          <td><strong>${esc(shortId(order.id))}</strong><small>${esc(fmtDate(order.createdAt))}</small></td>
          <td><strong>${esc(order.contactName || order.contactPhone || "Cliente")}</strong><small>${esc(order.contactPhone || "")}</small></td>
          <td><strong>${esc(money(order.total, order.currency))}</strong></td>
          <td>${order.fulfillmentType === "DELIVERY" ? "Delivery" : "Retiro"}</td>
          <td><span class="pill ${order.status === "CANCELLED" ? "bad" : order.status === "COMPLETED" ? "" : "high"}">${esc(status)}</span></td>
          <td>${esc(sourceLabel(order.source))}</td>
        </tr>`;
    }).join("");

    const cards = items.map(order => {
      const status = ORDER_STATUS_LABELS[order.status] || humanize(order.status, {});
      return `
        <article class="business-row business-mobile-card" tabindex="0" role="button" data-order-open data-entity-id="${esc(order.id)}">
          <div class="business-row-main">
            <div class="business-row-copy">
              <span class="business-kicker">${esc(shortId(order.id))} · ${esc(fmtDate(order.createdAt))}</span>
              <strong>${esc(order.contactName || order.contactPhone || "Cliente")}</strong>
              <span>${esc(money(order.total, order.currency))} · ${order.fulfillmentType === "DELIVERY" ? "Delivery" : "Retiro"}</span>
            </div>
            <span class="pill ${order.status === "CANCELLED" ? "bad" : order.status === "COMPLETED" ? "" : "high"}">${esc(status)}</span>
          </div>
        </article>`;
    }).join("");

    root.innerHTML = `
      <div class="business-table-shell">
        <table class="business-data-table" data-table="orders">
          <thead><tr><th>Pedido</th><th>Cliente</th><th>Total</th><th>Entrega</th><th>Estado</th><th>Origen</th></tr></thead>
          <tbody>${rows}</tbody>
        </table>
      </div>
      <div class="business-mobile-list">${cards}</div>`;

    bindOpeners(root, "[data-order-open]", openOrderDetail);
  }

  function openOrderDetail(id) {
    const order = state.orders.find(item => String(item.id) === String(id));
    if (!order) return;
    const status = ORDER_STATUS_LABELS[order.status] || humanize(order.status, {});
    const lines = Array.isArray(order.lines) ? order.lines : [];
    const actions = orderActions(order);
    showDrawer({
      eyebrow: "PEDIDO",
      title: `${shortId(order.id)} · ${order.contactName || order.contactPhone || "Cliente"}`,
      meta: `${fmtDate(order.createdAt)} · ${status}`,
      body: `
        <div class="business-detail-facts">
          <div><span>Cliente</span><strong>${esc(order.contactName || "Cliente")}</strong></div>
          <div><span>Teléfono</span><strong>${esc(order.contactPhone || "Sin teléfono")}</strong></div>
          <div><span>Entrega</span><strong>${order.fulfillmentType === "DELIVERY" ? "Delivery" : "Retiro"}</strong></div>
          <div><span>Origen</span><strong>${esc(sourceLabel(order.source))}</strong></div>
        </div>
        <div class="business-detail-section">
          <h3>Productos</h3>
          <div class="business-detail-lines">
            ${lines.length ? lines.map(line => `<div><span>${esc(line.quantity || 0)} × ${esc(line.name || "Producto")}</span><strong>${esc(money(line.lineTotal ?? (Number(line.quantity || 0) * Number(line.unitPrice || 0)), order.currency))}</strong></div>`).join("") : '<span class="muted">Sin líneas de detalle.</span>'}
          </div>
          <div class="business-detail-total"><span>Total</span><strong>${esc(money(order.total, order.currency))}</strong></div>
        </div>
        ${order.deliveryAddress ? `<div class="business-detail-note"><span>Dirección</span><p>${esc(order.deliveryAddress)}</p></div>` : ""}
      `,
      actions: actions.map(([next, label]) => `<button type="button" class="${next === "CANCELLED" ? "ghost" : "button secondary"}" data-detail-order-status="${next}">${esc(label)}</button>`).join("")
    });

    $("#businessDetailActions").querySelectorAll("[data-detail-order-status]").forEach(button => {
      button.addEventListener("click", async event => {
        event.currentTarget.disabled = true;
        try {
          await updateOrderStatus(order.id, event.currentTarget.dataset.detailOrderStatus);
        } catch (err) {
          toast(err.message || "No pude actualizar el pedido.");
          event.currentTarget.disabled = false;
        }
      });
    });
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

  ensureDrawer();
  const requestedTab = new URLSearchParams(window.location.search).get("tab");
  const initialTab = ["bookings", "orders", "requests", "customers"].includes(requestedTab) ? requestedTab : "bookings";
  setTab(initialTab);
  loadBusinessWorkspace();
})();