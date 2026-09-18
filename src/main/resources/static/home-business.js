(() => {
  const root = document.querySelector("#homeBusinessWorkspace");
  const dashboard = document.querySelector("#dashboardView");
  const statusGrid = document.querySelector("#statusGrid");
  if (!root || !dashboard || !statusGrid || typeof api !== "function") return;

  const state = { bookings: [], customers: [], services: [], orders: [], requests: [] };
  const bookingFilters = { query: "", date: "all", serviceId: "all", status: "all", source: "all" };
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
  const source = value => ({VOICE:"Voz",AI_CALL:"Llamada",WHATSAPP:"WhatsApp",MANUAL:"Manual",API:"API",ADMIN:"Manual"})[value] || value || "Sin origen";
  const status = value => ({CONFIRMED:"Confirmada",CANCELLED:"Cancelada",PREPARING:"Preparando",READY:"Listo",DISPATCHED:"Despachado",COMPLETED:"Completado",OPEN:"Abierta",IN_PROGRESS:"En curso"})[value] || value || "";

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
    const weekEnd = new Date(startToday); weekEnd.setDate(weekEnd.getDate() + 7);
    if (bookingFilters.date === "today") return when >= startToday && when < startTomorrow;
    if (bookingFilters.date === "tomorrow") return when >= startTomorrow && when < afterTomorrow;
    if (bookingFilters.date === "week") return when >= startToday && when < weekEnd;
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
        && (bookingFilters.source === "all" || item.source === bookingFilters.source);
    });

    document.querySelector("#homeBusinessBookingsCount").textContent = String(allItems.length);
    const host = document.querySelector("#homeBookingsList");
    const serviceOptions = state.services.map(service =>
      `<option value="${esc(service.id)}" ${bookingFilters.serviceId === String(service.id) ? "selected" : ""}>${esc(service.name)}</option>`
    ).join("");
    const sourceOptions = [...new Set(allItems.map(item => item.source).filter(Boolean))].map(value =>
      `<option value="${esc(value)}" ${bookingFilters.source === value ? "selected" : ""}>${esc(source(value))}</option>`
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

  function renderOrders() {
    const items=[...state.orders].sort((a,b)=>new Date(b.createdAt||0)-new Date(a.createdAt||0));
    document.querySelector("#homeBusinessOrdersCount").textContent=String(items.length);
    const host=document.querySelector("#homeOrdersList");
    if(!items.length){ host.innerHTML='<div class="home-business-empty">Todavía no hay pedidos registrados.</div>'; return; }
    host.innerHTML=`<div class="home-business-table-shell"><table class="home-business-table"><thead><tr><th>Pedido</th><th>Cliente</th><th>Total</th><th>Entrega</th><th>Estado</th><th>Origen</th></tr></thead><tbody>${items.map(item=>`<tr><td><strong>#${esc(String(item.id||"").slice(0,8))}</strong></td><td><strong>${esc(item.contactName||item.contactPhone||"Cliente")}</strong></td><td>${esc(money(item.total,item.currency))}</td><td>${item.fulfillmentType==="DELIVERY"?"Delivery":"Retiro"}</td><td><span class="home-pill">${esc(status(item.status))}</span></td><td>${esc(source(item.source))}</td></tr>`).join("")}</tbody></table></div>
    <div class="home-business-mobile-list">${items.map(item=>`<article class="home-business-mobile-card"><strong>${esc(item.contactName||item.contactPhone||"Cliente")}</strong><span>${esc(money(item.total,item.currency))} · ${esc(status(item.status))}</span></article>`).join("")}</div>`;
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
  setTab("bookings");
  queueMicrotask(load);
})();