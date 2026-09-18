(() => {
  const TOKEN_KEY = "helvoca_access_token";
  const BOOKING_STATUS_LABELS = { CONFIRMED: "Confirmada", CANCELLED: "Cancelada" };
  const ORDER_STATUS_LABELS = { CONFIRMED: "Confirmado", PREPARING: "Preparando", READY: "Listo", DISPATCHED: "Despachado", COMPLETED: "Completado", CANCELLED: "Cancelado" };
  const REQUEST_STATUS_LABELS = { OPEN: "Abierta", IN_PROGRESS: "En curso", RESOLVED: "Resuelta", CANCELLED: "Cancelada" };
  const PRIORITY_LABELS = { LOW: "Baja", NORMAL: "Normal", HIGH: "Alta", URGENT: "Urgente" };
  const SOURCE_LABELS = { VOICE: "Llamada", AI_CALL: "Llamada", AI_WHATSAPP: "WhatsApp", WHATSAPP: "WhatsApp", ADMIN: "Manual", MANUAL: "Manual", API: "API" };
  const EVENT_LABELS = {
    BOOKING_CREATED: "Reserva creada",
    BOOKING_RESCHEDULED: "Reserva reprogramada",
    BOOKING_CANCELLED: "Reserva cancelada",
    CUSTOMER_REGISTERED: "Cliente registrado",
    SERVICES_LISTED: "Consultó servicios",
    AVAILABILITY_LISTED: "Consultó horarios disponibles",
    AVAILABILITY_CHECKED: "Verificó disponibilidad",
    CALLER_LOOKUP: "Cliente identificado",
    FIND_CALLER: "Cliente identificado"
  };

  const host = document.querySelector("[data-helvoca-business-workspace]");
  if (!host) return;

  const state = {
    bookings: [], customers: [], services: [], orders: [], requests: [], questions: [], dashboard: {},
    bookingFilters: { search: "", date: "all", service: "all", status: "all", source: "all" }
  };
  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

  function esc(value) {
    return String(value ?? "").replace(/[&<>'"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
  }
  function humanize(value) {
    if (!value) return "";
    if (EVENT_LABELS[value]) return EVENT_LABELS[value];
    const text = String(value);
    if (!/^[A-Z0-9_]+$/.test(text)) return text;
    return text.toLowerCase().split("_").filter(Boolean).map(word => word.charAt(0).toUpperCase() + word.slice(1)).join(" ");
  }
  function fmtDate(value) {
    if (!value) return "";
    try { return new Intl.DateTimeFormat("es-CL", {dateStyle:"short", timeStyle:"short"}).format(new Date(value)); }
    catch (_) { return String(value); }
  }
  function money(value, currency = "CLP") {
    try { return new Intl.NumberFormat("es-CL", {style:"currency", currency:currency || "CLP", maximumFractionDigits:currency === "CLP" ? 0 : 2}).format(Number(value || 0)); }
    catch (_) { return `${currency || ""} ${Number(value || 0)}`.trim(); }
  }
  function sourceLabel(value) { return SOURCE_LABELS[value] || humanize(value) || "Sin origen"; }
  function shortId(value) { const text = String(value || ""); return text ? `#${text.slice(0, 8)}` : "Pedido"; }

  async function request(path, options = {}) {
    if (typeof window.api === "function") return window.api(path, options);
    const token = sessionStorage.getItem(TOKEN_KEY);
    const headers = new Headers(options.headers || {});
    if (token) headers.set("Authorization", `Bearer ${token}`);
    if (options.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
    const response = await fetch(path, {...options, headers});
    let payload = null;
    try { payload = await response.json(); } catch (_) {}
    if (!response.ok) {
      if (response.status === 401) { sessionStorage.removeItem(TOKEN_KEY); location.replace("/"); }
      throw new Error(payload?.message || payload?.detail || payload?.error || `HTTP ${response.status}`);
    }
    return payload;
  }

  function toast(text) {
    let node = $("#workspaceToast", host);
    if (!node) {
      node = document.createElement("div");
      node.id = "workspaceToast";
      node.className = "workspace-toast hidden";
      host.appendChild(node);
    }
    node.textContent = text;
    node.classList.remove("hidden");
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => node.classList.add("hidden"), 3200);
  }

  host.innerHTML = `
    <div id="businessTabs" class="business-tabs" role="tablist" aria-label="Operaciones del negocio">
      <button class="business-tab active" type="button" role="tab" aria-selected="true" data-business-tab="bookings"><span>Reservas</span><strong id="businessBookingsCount">0</strong></button>
      <button class="business-tab" type="button" role="tab" aria-selected="false" data-business-tab="orders"><span>Pedidos</span><strong id="businessOrdersCount">0</strong></button>
      <button class="business-tab" type="button" role="tab" aria-selected="false" data-business-tab="requests"><span>Solicitudes</span><strong id="businessRequestsCount">0</strong></button>
      <button class="business-tab" type="button" role="tab" aria-selected="false" data-business-tab="customers"><span>Clientes</span><strong id="businessCustomersCount">0</strong></button>
    </div>
    <section class="business-surface" data-business-panel="bookings">
      <div class="business-surface-head"><div><span class="eyebrow">AGENDA</span><h2>Reservas</h2><p>Quién reservó, qué servicio y cuándo será atendido.</p></div></div>
      <div id="bookingsList" class="business-list"><div class="loading-line">Cargando reservas…</div></div>
    </section>
    <section class="business-surface hidden" data-business-panel="orders">
      <div class="business-surface-head"><div><span class="eyebrow">VENTAS</span><h2>Pedidos</h2><p>Qué compraron, quién hizo el pedido y en qué estado está.</p></div></div>
      <div id="ordersList" class="business-list"><div class="loading-line">Cargando pedidos…</div></div>
    </section>
    <section class="business-surface hidden" data-business-panel="requests">
      <div class="workspace-two-col">
        <article>
          <div class="business-surface-head"><div><span class="eyebrow">TRABAJO</span><h2>Solicitudes</h2><p>Lo que necesita seguimiento humano.</p></div><button id="newRequestBtn" class="button secondary" type="button">+ Nueva</button></div>
          <form id="requestForm" class="workspace-form hidden">
            <input name="requestType" placeholder="Tipo, ej. cotización" required maxlength="80">
            <input name="title" placeholder="Título" required maxlength="200">
            <textarea name="description" placeholder="Descripción"></textarea>
            <div class="workspace-form-row"><input name="contactName" placeholder="Nombre"><input name="contactPhone" placeholder="Teléfono"></div>
            <select name="priority"><option>NORMAL</option><option>LOW</option><option>HIGH</option><option>URGENT</option></select>
            <div class="workspace-actions"><button type="submit">Guardar solicitud</button><button type="button" id="cancelRequestBtn" class="ghost">Cancelar</button></div>
          </form>
          <div id="requestsList" class="business-list"></div>
        </article>
        <article>
          <div class="business-surface-head"><div><span class="eyebrow">APRENDIZAJE</span><h2>Preguntas pendientes</h2><p>Responde una vez y Helvoca aprenderá.</p></div></div>
          <div id="questionsList" class="business-list"></div>
        </article>
      </div>
    </section>
    <section class="business-surface hidden" data-business-panel="customers">
      <div class="business-surface-head"><div><span class="eyebrow">CLIENTES</span><h2>Clientes</h2><p>Personas registradas por voz, WhatsApp o gestión manual.</p></div></div>
      <div id="customersList" class="business-list"><div class="loading-line">Cargando clientes…</div></div>
    </section>
  `;

  function setTab(tab) {
    $$("[data-business-tab]", host).forEach(button => {
      const active = button.dataset.businessTab === tab;
      button.classList.toggle("active", active);
      button.setAttribute("aria-selected", active ? "true" : "false");
    });
    $$("[data-business-panel]", host).forEach(panel => panel.classList.toggle("hidden", panel.dataset.businessPanel !== tab));
  }

  function businessDateKey(value) {
    if (!value) return "";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "";
    const timeZone = state.dashboard?.timezone || Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
    try {
      const parts = new Intl.DateTimeFormat("en-CA", {timeZone, year:"numeric", month:"2-digit", day:"2-digit"}).formatToParts(date);
      const map = Object.fromEntries(parts.map(part => [part.type, part.value]));
      return `${map.year}-${map.month}-${map.day}`;
    } catch (_) {
      return date.toISOString().slice(0,10);
    }
  }

  function bookingDateMatch(booking, filter) {
    if (filter === "all") return true;
    const target = new Date(booking.startAt || 0);
    if (Number.isNaN(target.getTime())) return false;
    const nowValue = state.dashboard?.localNow || new Date().toISOString();
    const todayKey = businessDateKey(nowValue);
    const todayDate = new Date(`${todayKey}T12:00:00`);
    const targetKey = businessDateKey(booking.startAt);
    if (filter === "today") return targetKey === todayKey;
    const tomorrow = new Date(todayDate); tomorrow.setDate(tomorrow.getDate()+1);
    const tomorrowKey = tomorrow.toISOString().slice(0,10);
    if (filter === "tomorrow") return targetKey === tomorrowKey;
    const nowMs = new Date(nowValue).getTime();
    if (filter === "upcoming") return target.getTime() >= nowMs;
    if (filter === "past") return target.getTime() < nowMs;
    if (filter === "week") {
      const end = new Date(todayDate); end.setDate(end.getDate()+7);
      return target.getTime() >= todayDate.getTime() && target.getTime() < end.getTime();
    }
    return true;
  }

  function filteredBookings(customers, services) {
    const filters = state.bookingFilters;
    const query = filters.search.trim().toLowerCase();
    return state.bookings.filter(booking => {
      const customer = customers.get(String(booking.customerId)) || {};
      const service = services.get(String(booking.serviceId)) || {};
      const searchText = [customer.name, customer.phone, service.name].filter(Boolean).join(" ").toLowerCase();
      return (!query || searchText.includes(query))
        && (filters.service === "all" || String(booking.serviceId) === filters.service)
        && (filters.status === "all" || booking.status === filters.status)
        && (filters.source === "all" || sourceLabel(booking.source) === filters.source)
        && bookingDateMatch(booking, filters.date);
    });
  }

  function renderBookingFilters(total, visible) {
    const filters = state.bookingFilters;
    const services = [...state.services].filter(item => item.active !== false);
    const sourceOptions = [...new Set(state.bookings.map(item => sourceLabel(item.source)).filter(Boolean))].sort((a,b)=>a.localeCompare(b,"es"));
    return `
      <div class="booking-filter-bar">
        <label class="booking-search"><span>Buscar</span><input id="bookingFilterSearch" value="${esc(filters.search)}" placeholder="Cliente, teléfono o servicio"></label>
        <label><span>Fecha</span><select id="bookingFilterDate">
          <option value="all" ${filters.date==="all"?"selected":""}>Todas</option>
          <option value="today" ${filters.date==="today"?"selected":""}>Hoy</option>
          <option value="tomorrow" ${filters.date==="tomorrow"?"selected":""}>Mañana</option>
          <option value="week" ${filters.date==="week"?"selected":""}>Próximos 7 días</option>
          <option value="upcoming" ${filters.date==="upcoming"?"selected":""}>Próximas</option>
          <option value="past" ${filters.date==="past"?"selected":""}>Pasadas</option>
        </select></label>
        <label><span>Servicio</span><select id="bookingFilterService"><option value="all">Todos</option>${services.map(service=>`<option value="${esc(service.id)}" ${String(service.id)===filters.service?"selected":""}>${esc(service.name)}</option>`).join("")}</select></label>
        <label><span>Estado</span><select id="bookingFilterStatus"><option value="all">Todos</option><option value="CONFIRMED" ${filters.status==="CONFIRMED"?"selected":""}>Confirmadas</option><option value="CANCELLED" ${filters.status==="CANCELLED"?"selected":""}>Canceladas</option></select></label>
        <label><span>Origen</span><select id="bookingFilterSource"><option value="all">Todos</option>${sourceOptions.map(source=>`<option value="${esc(source)}" ${source===filters.source?"selected":""}>${esc(source)}</option>`).join("")}</select></label>
        <button id="bookingFilterClear" class="booking-filter-clear ghost" type="button">Limpiar</button>
      </div>
      <div class="booking-filter-summary"><span id="bookingFilterCount">Mostrando ${visible} de ${total}</span></div>
    `;
  }

  function bindBookingFilters() {
    const mapping = [
      ["#bookingFilterSearch","search","input"],
      ["#bookingFilterDate","date","change"],
      ["#bookingFilterService","service","change"],
      ["#bookingFilterStatus","status","change"],
      ["#bookingFilterSource","source","change"]
    ];
    mapping.forEach(([selector,key,event]) => {
      const control = $(selector,host);
      control?.addEventListener(event, () => { state.bookingFilters[key]=control.value; renderBookings(); });
    });
    $("#bookingFilterClear",host)?.addEventListener("click",()=>{state.bookingFilters={search:"",date:"all",service:"all",status:"all",source:"all"};renderBookings();});
  }

  function ensureDetailDrawer() {
    if ($("#businessDetailBackdrop")) return;
    document.body.insertAdjacentHTML("beforeend", `
      <div id="businessDetailBackdrop" class="business-detail-backdrop hidden" aria-hidden="true">
        <aside id="businessDetailDrawer" class="business-detail-drawer" role="dialog" aria-modal="true" aria-labelledby="businessDetailTitle">
          <div class="business-detail-head">
            <div><span id="businessDetailEyebrow" class="eyebrow">DETALLE</span><h2 id="businessDetailTitle">Detalle</h2><p id="businessDetailMeta" class="muted"></p></div>
            <button id="businessDetailClose" class="ghost business-detail-close" type="button">Cerrar</button>
          </div>
          <div id="businessDetailBody" class="business-detail-body"></div>
        </aside>
      </div>
    `);
    $("#businessDetailClose").addEventListener("click",closeDetailDrawer);
    $("#businessDetailBackdrop").addEventListener("click",event=>{if(event.target===event.currentTarget)closeDetailDrawer();});
    document.addEventListener("keydown",event=>{if(event.key==="Escape")closeDetailDrawer();});
  }

  function closeDetailDrawer() {
    const backdrop=$("#businessDetailBackdrop");
    if(!backdrop)return;
    backdrop.classList.add("hidden");
    backdrop.setAttribute("aria-hidden","true");
    document.body.classList.remove("business-detail-open");
  }

  function showDetailDrawer(title,meta,body) {
    ensureDetailDrawer();
    $("#businessDetailTitle").textContent=title;
    $("#businessDetailMeta").textContent=meta||"";
    $("#businessDetailBody").innerHTML=body;
    const backdrop=$("#businessDetailBackdrop");
    backdrop.classList.remove("hidden");
    backdrop.setAttribute("aria-hidden","false");
    document.body.classList.add("business-detail-open");
  }

  function bookingBaseDetail(booking,customer,service,status) {
    return `
      <div class="business-detail-facts">
        <div><span>Servicio</span><strong>${esc(service.name||"Servicio")}</strong></div>
        <div><span>Teléfono</span><strong>${esc(customer.phone||"Sin teléfono")}</strong></div>
        <div><span>Inicio</span><strong>${esc(fmtDate(booking.startAt))}</strong></div>
        <div><span>Fin</span><strong>${esc(fmtDate(booking.endAt))}</strong></div>
        <div><span>Origen</span><strong>${esc(sourceLabel(booking.source))}</strong></div>
        <div><span>Estado</span><strong>${esc(status)}</strong></div>
      </div>
      ${booking.notes?`<section class="business-detail-section"><h3>Notas</h3><p>${esc(booking.notes)}</p></section>`:""}
      <section id="bookingTraceSection" class="business-detail-section"><h3>Conversación de origen</h3><div class="loading-line">Cargando contexto…</div></section>
      <section id="bookingHistorySection" class="business-detail-section"><h3>Historial</h3><div class="loading-line">Cargando historial…</div></section>
    `;
  }

  function renderHistory(trace) {
    const root=$("#bookingHistorySection");
    if(!root)return;
    const items=Array.isArray(trace.history)?trace.history:[];
    root.innerHTML=`<h3>Historial</h3>`+(items.length?`<div class="business-history">${items.map(item=>`<div><span>${esc(humanize(item.eventType))}</span><small>${esc(fmtDate(item.createdAt))}${item.channel?` · ${esc(sourceLabel(item.channel))}`:""}</small></div>`).join("")}</div>`:'<div class="empty">No hay cambios adicionales registrados.</div>');
  }

  function renderCallContext(trace,data) {
    const root=$("#bookingTraceSection"); if(!root)return;
    const transcript=Array.isArray(data.transcript)?data.transcript:[];
    const actions=Array.isArray(data.actions)?data.actions:[];
    root.innerHTML=`
      <h3>Conversación de origen</h3>
      <div class="trace-summary"><span>Resumen</span><p>${esc(data.summary||"Sin resumen disponible.")}</p></div>
      <div class="trace-transcript">${transcript.length?transcript.map(item=>`<div class="trace-message ${String(item.speaker||"").toLowerCase()}"><strong>${item.speaker==="USER"?"Cliente":item.speaker==="ASSISTANT"?"Helvoca":esc(humanize(item.speaker))}</strong><p>${esc(item.content)}</p><small>${esc(fmtDate(item.createdAt))}</small></div>`).join(""):'<div class="empty">No hay transcripción disponible.</div>'}</div>
      <div class="trace-actions"><h4>Qué hizo Helvoca</h4>${actions.length?actions.map(action=>`<div class="trace-action"><span>${action.success?"✓":"×"}</span><strong>${esc(humanize(action.actionType))}</strong></div>`).join(""):'<span class="muted">No hay acciones registradas.</span>'}</div>
      <a id="businessDetailConversationLink" class="button secondary trace-link" href="/conversations.html?call=${encodeURIComponent(trace.callId)}">Ver conversación completa</a>
    `;
  }

  function renderWhatsAppContext(trace,data) {
    const root=$("#bookingTraceSection"); if(!root)return;
    const messages=Array.isArray(data.messages)?data.messages:[];
    root.innerHTML=`
      <h3>Conversación de origen</h3>
      <div class="trace-transcript">${messages.length?messages.map(item=>`<div class="trace-message ${String(item.role||item.direction||"").toLowerCase()}"><strong>${item.role==="USER"?"Cliente":item.role==="ASSISTANT"?"Helvoca":esc(humanize(item.role||item.direction))}</strong><p>${esc(item.content)}</p><small>${esc(fmtDate(item.createdAt))}</small></div>`).join(""):'<div class="empty">No hay mensajes guardados.</div>'}</div>
      <a id="businessDetailConversationLink" class="button secondary trace-link" href="/conversations.html?whatsapp=${encodeURIComponent(trace.conversationId)}">Ver conversación completa</a>
    `;
  }

  async function openBookingDetail(id) {
    const booking=state.bookings.find(item=>String(item.id)===String(id)); if(!booking)return;
    const customers=new Map(state.customers.map(x=>[String(x.id),x])), services=new Map(state.services.map(x=>[String(x.id),x]));
    const customer=customers.get(String(booking.customerId))||{}, service=services.get(String(booking.serviceId))||{};
    const status=BOOKING_STATUS_LABELS[booking.status]||humanize(booking.status);
    showDetailDrawer(customer.name||customer.phone||"Cliente",`${fmtDate(booking.startAt)} · ${status}`,bookingBaseDetail(booking,customer,service,status));
    try{
      const trace=await request(`/api/v1/bookings/${encodeURIComponent(id)}/trace`);
      renderHistory(trace);
      if(trace.callId){
        try{renderCallContext(trace,await request(`/api/v1/calls/${encodeURIComponent(trace.callId)}`));}
        catch(e){$("#bookingTraceSection").innerHTML=`<h3>Conversación de origen</h3><div class="business-error">La transcripción no está disponible ahora.</div><a id="businessDetailConversationLink" class="button secondary trace-link" href="/conversations.html?call=${encodeURIComponent(trace.callId)}">Ver conversación completa</a>`;}
      }else if(trace.conversationId){
        try{renderWhatsAppContext(trace,await request(`/api/v1/messaging/conversations/${encodeURIComponent(trace.conversationId)}`));}
        catch(e){$("#bookingTraceSection").innerHTML=`<h3>Conversación de origen</h3><div class="business-error">Los mensajes no están disponibles ahora.</div><a id="businessDetailConversationLink" class="button secondary trace-link" href="/conversations.html?whatsapp=${encodeURIComponent(trace.conversationId)}">Ver conversación completa</a>`;}
      }else{
        $("#bookingTraceSection").innerHTML=`<h3>Conversación de origen</h3><div class="empty">${trace.origin==="MANUAL"?"Creada manualmente.":"No existe una conversación enlazada a esta reserva."}</div>`;
      }
    }catch(e){
      $("#bookingTraceSection").innerHTML='<h3>Conversación de origen</h3><div class="business-error">No pude cargar la trazabilidad de esta reserva.</div>';
      $("#bookingHistorySection").innerHTML='<h3>Historial</h3><div class="empty">El detalle básico de la reserva sigue disponible.</div>';
    }
  }

  function bindBookingOpeners() {
    $$("[data-booking-open]",host).forEach(node=>{
      const open=()=>openBookingDetail(node.dataset.entityId);
      node.addEventListener("click",open);
      node.addEventListener("keydown",event=>{if(event.key==="Enter"||event.key===" "){event.preventDefault();open();}});
    });
  }

  function renderBookings(error = null) {
    const root = $("#bookingsList", host);
    const customers = new Map(state.customers.map(x => [String(x.id), x]));
    const services = new Map(state.services.map(x => [String(x.id), x]));
    const now = Date.now();
    const allItems = [...state.bookings].sort((a,b) => {
      const at = new Date(a.startAt || 0).getTime(), bt = new Date(b.startAt || 0).getTime();
      const af = at >= now, bf = bt >= now;
      if (af !== bf) return af ? -1 : 1;
      return af ? at - bt : bt - at;
    });
    const visibleSet = new Set(filteredBookings(customers,services).map(item=>String(item.id)));
    const items = allItems.filter(item=>visibleSet.has(String(item.id)));
    $("#businessBookingsCount", host).textContent = String(state.bookings.length);
    if (error && !state.bookings.length) { root.innerHTML = `<div class="business-error">${esc(error)}</div>`; return; }
    if (!state.bookings.length) { root.innerHTML = '<div class="empty">Todavía no hay reservas registradas.</div>'; return; }

    const filterMarkup = renderBookingFilters(state.bookings.length, items.length);
    if (!items.length) {
      root.innerHTML = filterMarkup + '<div class="empty">No hay reservas que coincidan con estos filtros.</div>';
      bindBookingFilters();
      return;
    }

    const rows = items.map(booking => {
      const customer = customers.get(String(booking.customerId)) || {};
      const service = services.get(String(booking.serviceId)) || {};
      const status = BOOKING_STATUS_LABELS[booking.status] || humanize(booking.status);
      return `<tr class="business-table-row" tabindex="0" role="button" data-booking-open data-entity-id="${esc(booking.id)}">
        <td><strong>${esc(fmtDate(booking.startAt))}</strong></td>
        <td><strong>${esc(customer.name || customer.phone || "Cliente")}</strong></td>
        <td>${esc(service.name || "Servicio")}</td>
        <td>${esc(customer.phone || "Sin teléfono")}</td>
        <td>${esc(sourceLabel(booking.source))}</td>
        <td><span class="pill ${booking.status === "CANCELLED" ? "bad" : ""}">${esc(status)}</span></td>
      </tr>`;
    }).join("");
    const cards = items.map(booking => {
      const customer = customers.get(String(booking.customerId)) || {};
      const service = services.get(String(booking.serviceId)) || {};
      const status = BOOKING_STATUS_LABELS[booking.status] || humanize(booking.status);
      return `<article class="business-mobile-card" tabindex="0" role="button" data-booking-open data-entity-id="${esc(booking.id)}">
        <div><small>${esc(fmtDate(booking.startAt))}</small><strong>${esc(customer.name || customer.phone || "Cliente")}</strong><span>${esc(service.name || "Servicio")}</span></div>
        <span class="pill ${booking.status === "CANCELLED" ? "bad" : ""}">${esc(status)}</span>
      </article>`;
    }).join("");
    root.innerHTML = filterMarkup + `<div class="business-table-shell"><table class="business-data-table" data-table="bookings"><thead><tr><th>Fecha / hora</th><th>Cliente</th><th>Servicio</th><th>Contacto</th><th>Origen</th><th>Estado</th></tr></thead><tbody>${rows}</tbody></table></div><div class="business-mobile-list">${cards}</div>`;
    bindBookingFilters();
    bindBookingOpeners();
  }

  function orderActions(order) {
    switch(order.status) {
      case "CONFIRMED": return [["PREPARING","Empezar preparación"],["CANCELLED","Cancelar"]];
      case "PREPARING": return [["READY","Marcar listo"],["CANCELLED","Cancelar"]];
      case "READY": return order.fulfillmentType === "DELIVERY" ? [["DISPATCHED","Marcar despachado"]] : [["COMPLETED","Completar"]];
      case "DISPATCHED": return [["COMPLETED","Completar"]];
      default: return [];
    }
  }

  function renderOrders(error = null) {
    const root = $("#ordersList", host);
    const items = [...state.orders].sort((a,b) => new Date(b.createdAt || 0)-new Date(a.createdAt || 0));
    $("#businessOrdersCount", host).textContent = String(items.length);
    if (error && !items.length) { root.innerHTML = `<div class="business-error">${esc(error)}</div>`; return; }
    if (!items.length) { root.innerHTML = '<div class="empty">Todavía no hay pedidos confirmados.</div>'; return; }
    const rows = items.map(order => {
      const status = ORDER_STATUS_LABELS[order.status] || humanize(order.status);
      return `<tr class="business-table-row" tabindex="0" role="button" data-order-open data-entity-id="${esc(order.id)}"><td><strong>${esc(shortId(order.id))}</strong><small>${esc(fmtDate(order.createdAt))}</small></td><td><strong>${esc(order.contactName || order.contactPhone || "Cliente")}</strong><small>${esc(order.contactPhone || "")}</small></td><td><strong>${esc(money(order.total,order.currency))}</strong></td><td>${order.fulfillmentType === "DELIVERY" ? "Delivery" : "Retiro"}</td><td><span class="pill ${order.status === "CANCELLED" ? "bad" : order.status === "COMPLETED" ? "" : "high"}">${esc(status)}</span></td><td><div class="workspace-actions">${orderActions(order).map(([next,label]) => `<button type="button" class="${next === "CANCELLED" ? "ghost" : "button secondary"}" data-order-id="${esc(order.id)}" data-order-status="${next}">${esc(label)}</button>`).join("")}</div></td></tr>`;
    }).join("");
    const cards = items.map(order => `<article class="business-mobile-card" tabindex="0" role="button" data-order-open data-entity-id="${esc(order.id)}"><div><small>${esc(shortId(order.id))} · ${esc(fmtDate(order.createdAt))}</small><strong>${esc(order.contactName || order.contactPhone || "Cliente")}</strong><span>${esc(money(order.total,order.currency))}</span></div><span class="pill">${esc(ORDER_STATUS_LABELS[order.status] || humanize(order.status))}</span></article>`).join("");
    root.innerHTML = `<div class="business-table-shell"><table class="business-data-table" data-table="orders"><thead><tr><th>Pedido</th><th>Cliente</th><th>Total</th><th>Entrega</th><th>Estado</th><th>Acción</th></tr></thead><tbody>${rows}</tbody></table></div><div class="business-mobile-list">${cards}</div>`;
    $$("[data-order-status]",root).forEach(button => button.addEventListener("click", async event => {
      event.stopPropagation();
      if (button.dataset.orderStatus === "CANCELLED" && !window.confirm("¿Cancelar este pedido?")) return;
      button.disabled=true;
      try {
        await request(`/api/v1/commercial/orders/${encodeURIComponent(button.dataset.orderId)}/status`,{method:"PATCH",body:JSON.stringify({status:button.dataset.orderStatus})});
        await load(); setTab("orders"); toast("Pedido actualizado.");
      } catch(e) { toast(e.message || "No pude actualizar el pedido."); button.disabled=false; }
    }));
    $$("[data-order-open]",root).forEach(node=>{
      const open=()=>openOrderDetail(node.dataset.entityId);
      node.addEventListener("click",event=>{if(event.target.closest("button"))return;open();});
      node.addEventListener("keydown",event=>{if(event.key==="Enter"||event.key===" "){event.preventDefault();open();}});
    });
  }

  async function renderOperationConversation(operationId) {
    if(!operationId)return {events:[],link:null,content:'<div class="empty">No hay una operación enlazada.</div>'};
    const events=await request(`/api/v1/operation-events?operationId=${encodeURIComponent(operationId)}`);
    const source=(events||[]).find(item=>item.sourceReferenceId&&(item.channel==="VOICE"||item.channel==="WHATSAPP"));
    if(!source)return {events,link:null,content:'<div class="empty">No hay una conversación enlazada a esta operación.</div>'};
    if(source.channel==="VOICE"){
      const call=await request(`/api/v1/calls/${encodeURIComponent(source.sourceReferenceId)}`);
      const transcript=Array.isArray(call.transcript)?call.transcript:[];
      return {events,link:`/conversations.html?call=${source.sourceReferenceId}`,content:`<div class="trace-summary"><span>Resumen</span><p>${esc(call.summary||"Sin resumen disponible.")}</p></div><div class="trace-transcript">${transcript.map(item=>`<div class="trace-message ${String(item.speaker||"").toLowerCase()}"><strong>${item.speaker==="USER"?"Cliente":item.speaker==="ASSISTANT"?"Helvoca":esc(humanize(item.speaker))}</strong><p>${esc(item.content)}</p><small>${esc(fmtDate(item.createdAt))}</small></div>`).join("")||'<div class="empty">No hay transcripción disponible.</div>'}</div>`};
    }
    const convo=await request(`/api/v1/messaging/conversations/${encodeURIComponent(source.sourceReferenceId)}`);
    const messages=Array.isArray(convo.messages)?convo.messages:[];
    return {events,link:`/conversations.html?whatsapp=${source.sourceReferenceId}`,content:`<div class="trace-transcript">${messages.map(item=>`<div class="trace-message ${String(item.role||item.direction||"").toLowerCase()}"><strong>${item.role==="USER"?"Cliente":item.role==="ASSISTANT"?"Helvoca":esc(humanize(item.role||item.direction))}</strong><p>${esc(item.content)}</p><small>${esc(fmtDate(item.createdAt))}</small></div>`).join("")||'<div class="empty">No hay mensajes guardados.</div>'}</div>`};
  }

  async function openOrderDetail(id) {
    const order=state.orders.find(item=>String(item.id)===String(id));if(!order)return;
    const status=ORDER_STATUS_LABELS[order.status]||humanize(order.status);
    const lines=Array.isArray(order.lines)?order.lines:[];
    showDetailDrawer(`${shortId(order.id)} · ${order.contactName||order.contactPhone||"Cliente"}`,`${fmtDate(order.createdAt)} · ${status}`,`
      <div class="business-detail-facts">
        <div><span>Cliente</span><strong>${esc(order.contactName||"Cliente")}</strong></div>
        <div><span>Teléfono</span><strong>${esc(order.contactPhone||"Sin teléfono")}</strong></div>
        <div><span>Entrega</span><strong>${order.fulfillmentType==="DELIVERY"?"Delivery":"Retiro"}</strong></div>
        <div><span>Estado</span><strong>${esc(status)}</strong></div>
      </div>
      <section class="business-detail-section"><h3>Productos</h3><div class="business-detail-lines">${lines.map(line=>`<div><span>${esc(line.quantity||0)} × ${esc(line.name||"Producto")}</span><strong>${esc(money(line.lineTotal??(Number(line.quantity||0)*Number(line.unitPrice||0)),order.currency))}</strong></div>`).join("")||'<div class="empty">Sin líneas de detalle.</div>'}</div><div class="business-detail-total"><span>Total</span><strong>${esc(money(order.total,order.currency))}</strong></div></section>
      ${order.deliveryAddress?`<section class="business-detail-section"><h3>Dirección</h3><p>${esc(order.deliveryAddress)}</p></section>`:""}
      <section id="orderConversationSection" class="business-detail-section"><h3>Conversación de origen</h3><div class="loading-line">Cargando contexto…</div></section>
    `);
    try{
      const ctx=await renderOperationConversation(order.operationId);
      const section=$("#orderConversationSection");
      section.innerHTML=`<h3>Conversación de origen</h3>${ctx.content}${ctx.link?`<a id="businessDetailConversationLink" class="button secondary trace-link" href="${esc(ctx.link)}">Ver conversación completa</a>`:""}`;
    }catch(e){
      $("#orderConversationSection").innerHTML='<h3>Conversación de origen</h3><div class="business-error">No pude cargar la conversación de este pedido.</div>';
    }
  }

  function renderCustomers(error = null) {
    const root=$("#customersList",host);
    const items=[...state.customers].sort((a,b)=>String(a.name||a.phone||"").localeCompare(String(b.name||b.phone||""),"es"));
    $("#businessCustomersCount",host).textContent=String(items.length);
    if(error && !items.length){root.innerHTML=`<div class="business-error">${esc(error)}</div>`;return;}
    if(!items.length){root.innerHTML='<div class="empty">Todavía no hay clientes registrados.</div>';return;}
    root.innerHTML=items.map(customer=>`<article class="workspace-card"><div><strong>${esc(customer.name||customer.phone||"Cliente")}</strong><span>${esc(customer.phone||"")}${customer.email?` · ${esc(customer.email)}`:""}</span></div><small>Registrado ${esc(fmtDate(customer.createdAt))}</small></article>`).join("");
  }

  function renderRequests() {
    const root=$("#requestsList",host), items=state.requests || [];
    $("#businessRequestsCount",host).textContent=String(items.length);
    if(!items.length){root.innerHTML='<div class="empty">No hay solicitudes abiertas. ✨</div>';return;}
    root.innerHTML=items.map(item=>`<article class="workspace-card" data-request-id="${esc(item.id)}"><div><strong>${esc(item.title)}</strong><span>${esc(humanize(item.type))} · ${esc(REQUEST_STATUS_LABELS[item.status]||humanize(item.status))}</span></div><span class="pill ${item.priority==="URGENT"||item.priority==="HIGH"?"high":""}">${esc(PRIORITY_LABELS[item.priority]||humanize(item.priority))}</span>${item.status!=="RESOLVED"&&item.status!=="CANCELLED"?`<div class="workspace-actions full"><button type="button" data-request-status="IN_PROGRESS">En curso</button><button type="button" class="ghost" data-request-status="RESOLVED">Resolver</button></div>`:""}</article>`).join("");
    $$("[data-request-status]",root).forEach(button=>button.addEventListener("click",async()=>{
      const item=button.closest("[data-request-id]"); button.disabled=true;
      try{await request(`/api/v1/requests/${encodeURIComponent(item.dataset.requestId)}/status`,{method:"PATCH",body:JSON.stringify({status:button.dataset.requestStatus})});await load();setTab("requests");toast("Solicitud actualizada.");}
      catch(e){toast(e.message||"No pude actualizar la solicitud.");button.disabled=false;}
    }));
  }

  function renderQuestions() {
    const root=$("#questionsList",host), items=state.questions || [];
    if(!items.length){root.innerHTML='<div class="empty">Helvoca no tiene preguntas pendientes. ✨</div>';return;}
    root.innerHTML=items.map(q=>`<article class="workspace-card" data-question-id="${esc(q.id)}"><div><strong>${esc(q.question)}</strong><span>Última vez ${esc(fmtDate(q.lastSeenAt))}</span></div><span class="pill">${Number(q.occurrences||1)}×</span><div class="question-actions full"><input data-answer placeholder="Respuesta oficial"><button type="button" data-answer-btn>Enseñar</button><button type="button" data-dismiss-btn class="ghost">Descartar</button></div></article>`).join("");
    $$("[data-answer-btn]",root).forEach(button=>button.addEventListener("click",async()=>{
      const item=button.closest("[data-question-id]"), answer=$("[data-answer]",item).value.trim();
      if(!answer){toast("Escribe una respuesta.");return;} button.disabled=true;
      try{await request(`/api/v1/learning/questions/${encodeURIComponent(item.dataset.questionId)}/answer`,{method:"POST",body:JSON.stringify({answer})});await load();setTab("requests");toast("Respuesta aprendida.");}
      catch(e){toast(e.message||"No pude guardar la respuesta.");button.disabled=false;}
    }));
    $$("[data-dismiss-btn]",root).forEach(button=>button.addEventListener("click",async()=>{
      const item=button.closest("[data-question-id]"); button.disabled=true;
      try{await request(`/api/v1/learning/questions/${encodeURIComponent(item.dataset.questionId)}/dismiss`,{method:"POST"});await load();setTab("requests");toast("Pregunta descartada.");}
      catch(e){toast(e.message||"No pude descartar la pregunta.");button.disabled=false;}
    }));
  }

  async function load() {
    const results=await Promise.allSettled([
      request("/api/v1/bookings"),
      request("/api/v1/customers"),
      request("/api/v1/services"),
      request("/api/v1/commercial/orders"),
      request("/api/v1/operations/dashboard")
    ]);
    const [bookings,customers,services,orders,dashboard]=results;
    state.bookings=bookings.status==="fulfilled"&&Array.isArray(bookings.value)?bookings.value:[];
    state.customers=customers.status==="fulfilled"&&Array.isArray(customers.value)?customers.value:[];
    state.services=services.status==="fulfilled"&&Array.isArray(services.value)?services.value:[];
    state.orders=orders.status==="fulfilled"&&Array.isArray(orders.value)?orders.value:[];
    state.dashboard=dashboard.status==="fulfilled"?dashboard.value||{}:{};
    state.requests=dashboard.status==="fulfilled"?dashboard.value?.recentRequests||[]:[];
    state.questions=dashboard.status==="fulfilled"?dashboard.value?.unanswered||[]:[];
    renderBookings(bookings.status==="rejected"?bookings.reason?.message:null);
    renderOrders(orders.status==="rejected"?orders.reason?.message:null);
    renderCustomers(customers.status==="rejected"?customers.reason?.message:null);
    renderRequests();
    renderQuestions();
  }

  $$("[data-business-tab]",host).forEach(button=>button.addEventListener("click",()=>setTab(button.dataset.businessTab)));
  $("#newRequestBtn",host)?.addEventListener("click",()=>$("#requestForm",host)?.classList.remove("hidden"));
  $("#cancelRequestBtn",host)?.addEventListener("click",()=>$("#requestForm",host)?.classList.add("hidden"));
  $("#requestForm",host)?.addEventListener("submit",async event=>{
    event.preventDefault();
    const form=event.currentTarget, data=Object.fromEntries(new FormData(form).entries());
    form.querySelectorAll("button").forEach(b=>b.disabled=true);
    try{await request("/api/v1/requests",{method:"POST",body:JSON.stringify(data)});form.reset();form.classList.add("hidden");await load();setTab("requests");toast("Solicitud creada.");}
    catch(e){toast(e.message||"No pude crear la solicitud.");}
    finally{form.querySelectorAll("button").forEach(b=>b.disabled=false);}
  });

  const requestedTab=new URLSearchParams(window.location.search).get("tab");
  setTab(["bookings","orders","requests","customers"].includes(requestedTab)?requestedTab:"bookings");
  const maybeLoad = () => {
    if (!sessionStorage.getItem(TOKEN_KEY)) return;
    load();
  };
  new MutationObserver(() => {
    if (!host.classList.contains("hidden")) maybeLoad();
  }).observe(host, {attributes:true, attributeFilter:["class"]});
  maybeLoad();
  window.HelvocaBusinessWorkspace={reload:load,setTab};
})();