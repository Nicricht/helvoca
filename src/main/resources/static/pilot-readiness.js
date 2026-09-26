(() => {
  const dashboard = document.querySelector('#dashboardView');
  if (!dashboard || typeof api !== 'function') return;

  const style = document.createElement('style');
  style.textContent = `
    #pilotReadinessCard { margin: 0 0 18px; padding: 16px 18px; }
    .pilot-readiness-head { display:flex; align-items:flex-start; justify-content:space-between; gap:14px; margin-bottom:12px; }
    .pilot-readiness-head h2 { margin:3px 0 4px; font-size:18px; }
    .pilot-readiness-head p { margin:0; color:var(--muted); font-size:12px; line-height:1.5; }
    .pilot-readiness-score { min-width:72px; text-align:right; }
    .pilot-readiness-score strong { display:block; font-size:22px; line-height:1; }
    .pilot-readiness-score span { display:block; margin-top:5px; color:var(--muted); font-size:10px; }
    .pilot-readiness-list { display:grid; grid-template-columns:repeat(5,minmax(0,1fr)); gap:8px; }
    .pilot-readiness-item { min-width:0; padding:10px 11px; border:1px solid var(--border); border-radius:11px; background:rgba(255,255,255,.025); }
    .pilot-readiness-item.ready { border-color:rgba(55,205,145,.35); background:rgba(55,205,145,.05); }
    .pilot-readiness-item.blocked { border-color:rgba(244,166,54,.28); background:rgba(244,166,54,.045); }
    .pilot-readiness-item strong { display:block; font-size:12px; }
    .pilot-readiness-item small { display:block; margin-top:4px; color:var(--muted); font-size:10px; line-height:1.35; }
    .pilot-readiness-state { display:inline-flex; align-items:center; gap:5px; margin-bottom:6px; font-size:10px; font-weight:800; letter-spacing:.03em; }
    .pilot-readiness-state::before { content:''; width:7px; height:7px; border-radius:50%; background:#f4a636; }
    .pilot-readiness-item.ready .pilot-readiness-state::before { background:#37cd91; }
    .pilot-readiness-footer { display:flex; justify-content:space-between; gap:12px; align-items:center; margin-top:11px; padding-top:10px; border-top:1px solid var(--border); }
    .pilot-readiness-footer span { color:var(--muted); font-size:11px; line-height:1.4; }
    .pilot-readiness-footer a { white-space:nowrap; }
    @media (max-width:980px) { .pilot-readiness-list { grid-template-columns:repeat(2,minmax(0,1fr)); } }
    @media (max-width:560px) {
      .pilot-readiness-list { grid-template-columns:1fr; }
      .pilot-readiness-head, .pilot-readiness-footer { flex-direction:column; }
      .pilot-readiness-score { text-align:left; }
    }
  `;
  document.head.appendChild(style);

  const card = document.createElement('section');
  card.id = 'pilotReadinessCard';
  card.className = 'card hidden';
  card.setAttribute('aria-label', 'Preparación para piloto');
  card.innerHTML = `
    <div class="pilot-readiness-head">
      <div>
        <div class="eyebrow">Piloto real</div>
        <h2>Preparación para operar</h2>
        <p>Comprueba que el flujo completo, desde la llamada hasta el pago, puede funcionar en producción.</p>
      </div>
      <div class="pilot-readiness-score"><strong id="pilotReadinessScore">–</strong><span>COMPONENTES LISTOS</span></div>
    </div>
    <div id="pilotReadinessList" class="pilot-readiness-list"></div>
    <div class="pilot-readiness-footer">
      <span id="pilotReadinessSummary">Comprobando canales y operación comercial…</span>
      <a class="button small ghost" href="/settings.html">Corregir configuración</a>
    </div>
  `;

  const anchor = document.querySelector('#operationalOverview') || dashboard.querySelector('.dashboard-heading');
  anchor?.insertAdjacentElement('afterend', card);

  const list = card.querySelector('#pilotReadinessList');
  const score = card.querySelector('#pilotReadinessScore');
  const summary = card.querySelector('#pilotReadinessSummary');
  let loading = false;

  function render(data) {
    const checks = Array.isArray(data?.checks) ? data.checks : [];
    score.textContent = `${Number(data?.passed || 0)}/${Number(data?.total || checks.length || 0)}`;
    list.innerHTML = '';

    checks.forEach(check => {
      const item = document.createElement('article');
      item.className = `pilot-readiness-item ${check.ready ? 'ready' : 'blocked'}`;

      const state = document.createElement('span');
      state.className = 'pilot-readiness-state';
      state.textContent = check.ready ? 'LISTO' : 'PENDIENTE';

      const title = document.createElement('strong');
      title.textContent = check.label || check.code || 'Componente';

      const detail = document.createElement('small');
      detail.textContent = check.detail || '';

      item.append(state, title, detail);
      list.appendChild(item);
    });

    const blockers = Array.isArray(data?.blockers) ? data.blockers : [];
    if (data?.ready) {
      summary.textContent = 'Todo el circuito crítico está listo para una certificación con cliente piloto.';
    } else if (blockers.length) {
      summary.textContent = `Falta: ${blockers.join(', ')}.`;
    } else {
      summary.textContent = 'Aún faltan componentes por configurar antes del piloto.';
    }
    card.classList.remove('hidden');
  }

  async function load() {
    if (loading || dashboard.classList.contains('hidden') || !sessionStorage.getItem('helvoca_access_token')) return;
    loading = true;
    try {
      const data = await api('/api/v1/operations/pilot-readiness');
      render(data);
    } catch (error) {
      if (error.status === 401 || error.status === 403) {
        card.classList.add('hidden');
      } else {
        card.classList.remove('hidden');
        list.innerHTML = '';
        score.textContent = '–';
        summary.textContent = 'No pude comprobar el estado del piloto en este momento.';
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
