(() => {
  const dashboard = document.querySelector('#dashboardView');
  if (!dashboard || typeof api !== 'function') return;

  const style = document.createElement('style');
  style.textContent = `
    #pilotPreflightCard { margin:0 0 18px; padding:16px 18px; }
    .pilot-preflight-head { display:flex; align-items:flex-start; justify-content:space-between; gap:14px; }
    .pilot-preflight-head h2 { margin:3px 0 4px; font-size:18px; }
    .pilot-preflight-head p { margin:0; color:var(--muted); font-size:12px; line-height:1.45; }
    .pilot-preflight-badge { padding:7px 10px; border-radius:999px; border:1px solid var(--border); font-size:11px; font-weight:900; letter-spacing:.05em; }
    .pilot-preflight-badge.go { border-color:rgba(55,205,145,.4); background:rgba(55,205,145,.09); }
    .pilot-preflight-badge.no-go { border-color:rgba(242,141,141,.4); background:rgba(242,141,141,.08); }
    .pilot-preflight-safety { display:flex; flex-wrap:wrap; gap:7px; margin-top:11px; }
    .pilot-preflight-chip { padding:6px 8px; border:1px solid var(--border); border-radius:8px; font-size:10px; color:var(--muted); }
    .pilot-preflight-chip.safe { border-color:rgba(55,205,145,.3); color:var(--text); }
    .pilot-preflight-chip.live { border-color:rgba(244,166,54,.4); color:#f4a636; }
    .pilot-preflight-grid { display:grid; grid-template-columns:repeat(5,minmax(0,1fr)); gap:8px; margin-top:12px; }
    .pilot-preflight-stat { padding:10px; border:1px solid var(--border); border-radius:10px; background:rgba(255,255,255,.02); }
    .pilot-preflight-stat strong { display:block; font-size:19px; }
    .pilot-preflight-stat span { display:block; margin-top:3px; color:var(--muted); font-size:9px; }
    .pilot-preflight-checks { display:grid; grid-template-columns:1fr 1fr; gap:6px; margin-top:12px; }
    .pilot-preflight-check { padding:8px 10px; border:1px solid var(--border); border-radius:9px; font-size:10px; }
    .pilot-preflight-check strong { display:block; }
    .pilot-preflight-check span { display:block; margin-top:3px; color:var(--muted); line-height:1.35; }
    .pilot-preflight-check.pass { border-color:rgba(55,205,145,.25); }
    .pilot-preflight-check.fail { border-color:rgba(242,141,141,.35); }
    .pilot-preflight-footer { margin-top:10px; color:var(--muted); font-size:10px; line-height:1.5; }
    @media (max-width:900px) { .pilot-preflight-grid { grid-template-columns:repeat(2,minmax(0,1fr)); } }
    @media (max-width:620px) { .pilot-preflight-head { flex-direction:column; } .pilot-preflight-checks { grid-template-columns:1fr; } }
  `;
  document.head.appendChild(style);

  const card = document.createElement('section');
  card.id = 'pilotPreflightCard';
  card.className = 'card hidden';
  card.innerHTML = `
    <div class="pilot-preflight-head">
      <div>
        <div class="eyebrow">Launch cage</div>
        <h2>Preflight del primer negocio</h2>
        <p>Una sola decisión antes de abrir tráfico: configuración, seguridad, inventario y V6.</p>
      </div>
      <span id="pilotPreflightDecision" class="pilot-preflight-badge">CARGANDO</span>
    </div>
    <div class="pilot-preflight-safety">
      <span id="pilotPreflightStatus" class="pilot-preflight-chip"></span>
      <span id="pilotPreflightTraffic" class="pilot-preflight-chip"></span>
      <span id="pilotPreflightSwitch" class="pilot-preflight-chip"></span>
    </div>
    <div class="pilot-preflight-grid">
      <article class="pilot-preflight-stat"><strong id="pilotPreflightOrders">–</strong><span>Pedidos hoy</span></article>
      <article class="pilot-preflight-stat"><strong id="pilotPreflightPayments">–</strong><span>Pagos / exitosos</span></article>
      <article class="pilot-preflight-stat"><strong id="pilotPreflightInventory">–</strong><span>Unidades disponibles</span></article>
      <article class="pilot-preflight-stat"><strong id="pilotPreflightStockAlerts">–</strong><span>Alertas de stock</span></article>
      <article class="pilot-preflight-stat"><strong id="pilotPreflightAnomalies">–</strong><span>Anomalías V6</span></article>
    </div>
    <div id="pilotPreflightChecks" class="pilot-preflight-checks"></div>
    <div id="pilotPreflightFooter" class="pilot-preflight-footer"></div>
  `;

  const anchor = document.querySelector('#pilotControlCard')
    || document.querySelector('#pilotReadinessCard')
    || document.querySelector('#operationalOverview');
  anchor?.insertAdjacentElement('afterend', card);

  let loading = false;

  function trafficLabel(mode) {
    const labels = {
      BLOCKED_GLOBAL: 'Tráfico real bloqueado globalmente',
      BLOCKED_TENANT: 'Tráfico bloqueado por tenant',
      LIVE_ALLOWED: 'Tráfico real habilitado',
      NOT_ENROLLED: 'Tenant fuera del control piloto'
    };
    return labels[mode] || mode || 'Sin estado';
  }

  function render(data) {
    const decision = data?.decision || 'NO_GO';
    const badge = card.querySelector('#pilotPreflightDecision');
    badge.textContent = decision;
    badge.className = `pilot-preflight-badge ${decision === 'GO' ? 'go' : 'no-go'}`;

    const status = card.querySelector('#pilotPreflightStatus');
    status.textContent = `Piloto: ${data?.pilotStatus || 'DRAFT'}`;
    status.className = 'pilot-preflight-chip safe';

    const traffic = card.querySelector('#pilotPreflightTraffic');
    traffic.textContent = trafficLabel(data?.trafficMode);
    traffic.className = `pilot-preflight-chip ${data?.trafficMode === 'LIVE_ALLOWED' ? 'live' : 'safe'}`;

    const globalSwitch = card.querySelector('#pilotPreflightSwitch');
    globalSwitch.textContent = data?.globalExternalEffectsEnabled
      ? 'Switch global: ON'
      : 'Switch global: OFF';
    globalSwitch.className = `pilot-preflight-chip ${data?.globalExternalEffectsEnabled ? 'live' : 'safe'}`;

    const snapshot = data?.snapshot || {};
    card.querySelector('#pilotPreflightOrders').textContent = String(snapshot.ordersToday || 0);
    card.querySelector('#pilotPreflightPayments').textContent =
      `${Number(snapshot.paymentAttemptsToday || 0)} / ${Number(snapshot.successfulPaymentsToday || 0)}`;
    card.querySelector('#pilotPreflightInventory').textContent = String(snapshot.availableInventoryUnits || 0);
    card.querySelector('#pilotPreflightStockAlerts').textContent =
      String(Number(snapshot.lowStockAlerts || 0) + Number(snapshot.outOfStockAlerts || 0));
    card.querySelector('#pilotPreflightAnomalies').textContent = String(snapshot.reconciliationAnomalies || 0);

    const checks = Array.isArray(data?.checks) ? data.checks : [];
    card.querySelector('#pilotPreflightChecks').innerHTML = checks.map(check => `
      <div class="pilot-preflight-check ${check.passed ? 'pass' : 'fail'}">
        <strong>${check.passed ? '✓' : '✕'} ${escapeHtml(check.label || check.code)}</strong>
        <span>${escapeHtml(check.detail || '')}</span>
      </div>
    `).join('');

    const blockers = Array.isArray(data?.blockers) ? data.blockers : [];
    const warnings = Array.isArray(data?.warnings) ? data.warnings : [];
    card.querySelector('#pilotPreflightFooter').textContent = blockers.length
      ? `NO-GO: ${blockers.join(', ')}`
      : warnings.length
        ? `GO con observaciones: ${warnings.join(', ')}`
        : 'GO: no hay bloqueos detectados. La activación real sigue requiriendo autorización explícita.';
    card.classList.remove('hidden');
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  async function load() {
    if (loading || dashboard.classList.contains('hidden')
        || !sessionStorage.getItem('helvoca_access_token')) return;
    loading = true;
    try {
      render(await api('/api/v1/operations/pilot-preflight'));
    } catch (error) {
      if (error.status === 401 || error.status === 403) card.classList.add('hidden');
    } finally {
      loading = false;
    }
  }

  new MutationObserver(() => {
    if (!dashboard.classList.contains('hidden')) load();
  }).observe(dashboard, { attributes:true, attributeFilter:['class'] });

  document.querySelector('#refreshBtn')?.addEventListener('click', load);
  load();
})();
