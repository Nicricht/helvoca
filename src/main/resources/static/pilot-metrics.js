(() => {
  const dashboard = document.querySelector('#dashboardView');
  if (!dashboard || typeof api !== 'function') return;

  const style = document.createElement('style');
  style.textContent = `
    #pilotMetricsCard { margin: 0 0 18px; padding: 16px 18px; }
    .pilot-metrics-head { display:flex; align-items:flex-start; justify-content:space-between; gap:14px; margin-bottom:12px; }
    .pilot-metrics-head h2 { margin:3px 0 4px; font-size:18px; }
    .pilot-metrics-head p { margin:0; color:var(--muted); font-size:12px; line-height:1.45; }
    .pilot-period-switch { display:inline-flex; gap:4px; padding:3px; border:1px solid var(--border); border-radius:10px; background:rgba(255,255,255,.02); }
    .pilot-period-switch button { border:0; border-radius:7px; padding:6px 9px; background:transparent; color:var(--muted); font:inherit; font-size:10px; font-weight:800; cursor:pointer; }
    .pilot-period-switch button.active { background:rgba(124,92,255,.16); color:var(--text); }
    .pilot-funnel { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:8px; }
    .pilot-funnel-step { position:relative; min-width:0; padding:12px; border:1px solid var(--border); border-radius:11px; background:rgba(255,255,255,.025); }
    .pilot-funnel-step strong { display:block; font-size:22px; line-height:1; }
    .pilot-funnel-step span { display:block; margin-top:6px; color:var(--muted); font-size:10px; }
    .pilot-funnel-step small { display:block; margin-top:3px; color:var(--muted); font-size:9px; }
    .pilot-funnel-step.good { border-color:rgba(55,205,145,.28); background:rgba(55,205,145,.04); }
    .pilot-health-row { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:8px; margin-top:8px; }
    .pilot-health { padding:9px 11px; border:1px solid var(--border); border-radius:10px; }
    .pilot-health strong { display:block; font-size:14px; }
    .pilot-health span { display:block; margin-top:3px; color:var(--muted); font-size:9px; }
    .pilot-health.warn strong { color:#f4a636; }
    .pilot-metrics-foot { margin-top:10px; color:var(--muted); font-size:10px; }
    @media (max-width:900px) { .pilot-funnel, .pilot-health-row { grid-template-columns:repeat(2,minmax(0,1fr)); } }
    @media (max-width:560px) {
      .pilot-metrics-head { flex-direction:column; }
      .pilot-funnel, .pilot-health-row { grid-template-columns:1fr 1fr; }
    }
  `;
  document.head.appendChild(style);

  const card = document.createElement('section');
  card.id = 'pilotMetricsCard';
  card.className = 'card hidden';
  card.setAttribute('aria-label', 'Métricas del piloto');
  card.innerHTML = `
    <div class="pilot-metrics-head">
      <div>
        <div class="eyebrow">Resultados</div>
        <h2>Embudo del piloto</h2>
        <p>Ventas y salud operativa confirmadas por backend.</p>
      </div>
      <div class="pilot-period-switch" role="group" aria-label="Periodo de métricas">
        <button type="button" class="active" data-pilot-period="today">Hoy</button>
        <button type="button" data-pilot-period="last7Days">7 días</button>
      </div>
    </div>
    <div class="pilot-funnel">
      <article class="pilot-funnel-step"><strong id="pilotMetricConversations">–</strong><span>Conversaciones</span><small>Llamadas + WhatsApp</small></article>
      <article class="pilot-funnel-step"><strong id="pilotMetricOrders">–</strong><span>Pedidos</span><small>Órdenes no canceladas</small></article>
      <article class="pilot-funnel-step good"><strong id="pilotMetricPaid">–</strong><span>Pagos exitosos</span><small id="pilotMetricConversion">– conversión</small></article>
      <article class="pilot-funnel-step good"><strong id="pilotMetricRevenue">–</strong><span>Ingresos confirmados</span><small>Solo pagos SUCCEEDED</small></article>
    </div>
    <div class="pilot-health-row">
      <article class="pilot-health"><strong id="pilotMetricBookings">–</strong><span>Reservas creadas</span></article>
      <article class="pilot-health warn"><strong id="pilotMetricPending">–</strong><span>Pagos pendientes</span></article>
      <article class="pilot-health warn"><strong id="pilotMetricFailures">–</strong><span>Pagos fallidos</span></article>
      <article class="pilot-health"><strong id="pilotMetricHandoffs">–</strong><span>Derivaciones humanas</span></article>
    </div>
    <div id="pilotMetricsFoot" class="pilot-metrics-foot"></div>
  `;

  const anchor = document.querySelector('#pilotReadinessCard') || document.querySelector('#operationalOverview');
  anchor?.insertAdjacentElement('afterend', card);

  let payload = null;
  let selectedPeriod = 'today';
  let loading = false;

  function money(revenue) {
    const entries = Object.entries(revenue || {});
    if (!entries.length) return '$0';
    return entries.map(([currency, value]) => {
      const amount = Number(value || 0);
      try {
        return new Intl.NumberFormat('es-CL', {
          style: 'currency',
          currency,
          maximumFractionDigits: currency === 'CLP' ? 0 : 2
        }).format(amount);
      } catch (_) {
        return `${currency} ${amount.toLocaleString('es-CL')}`;
      }
    }).join(' · ');
  }

  function pct(value) {
    return `${Number(value || 0).toLocaleString('es-CL', { maximumFractionDigits: 1 })}%`;
  }

  function render() {
    const data = payload?.[selectedPeriod];
    if (!data) return;

    const conversations = Number(data.calls || 0) + Number(data.whatsappConversations || 0);
    card.querySelector('#pilotMetricConversations').textContent = String(conversations);
    card.querySelector('#pilotMetricOrders').textContent = String(Number(data.orders || 0));
    card.querySelector('#pilotMetricPaid').textContent = String(Number(data.successfulPayments || 0));
    card.querySelector('#pilotMetricConversion').textContent = `${pct(data.paidOrderConversionPct)} de pedidos pagados`;
    card.querySelector('#pilotMetricRevenue').textContent = money(data.confirmedRevenueByCurrency);
    card.querySelector('#pilotMetricBookings').textContent = String(Number(data.bookings || 0));
    card.querySelector('#pilotMetricPending').textContent = String(Number(data.pendingPayments || 0));
    card.querySelector('#pilotMetricFailures').textContent = String(Number(data.failedPayments || 0));
    card.querySelector('#pilotMetricHandoffs').textContent = String(Number(data.humanTransfers || 0));
    card.querySelector('#pilotMetricsFoot').textContent =
      `Éxito de pago ${pct(data.paymentSuccessRatePct)} · Fallas de llamada ${pct(data.callFailureRatePct)} · Derivación humana ${pct(data.humanTransferRatePct)}`;
    card.classList.remove('hidden');
  }

  card.querySelectorAll('[data-pilot-period]').forEach(button => {
    button.addEventListener('click', () => {
      selectedPeriod = button.dataset.pilotPeriod;
      card.querySelectorAll('[data-pilot-period]').forEach(item =>
        item.classList.toggle('active', item === button));
      render();
    });
  });

  async function load() {
    if (loading || dashboard.classList.contains('hidden') || !sessionStorage.getItem('helvoca_access_token')) return;
    loading = true;
    try {
      payload = await api('/api/v1/operations/pilot-metrics');
      render();
    } catch (error) {
      if (error.status === 401 || error.status === 403) {
        card.classList.add('hidden');
      }
    } finally {
      loading = false;
    }
  }

  new MutationObserver(() => {
    if (!dashboard.classList.contains('hidden')) load();
  }).observe(dashboard, { attributes: true, attributeFilter: ['class'] });

  document.querySelector('#refreshBtn')?.addEventListener('click', load);
  load();
})();
