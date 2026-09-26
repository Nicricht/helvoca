(() => {
  const dashboard = document.querySelector('#dashboardView');
  const nextBanner = document.querySelector('#nextStepBanner');
  if (!dashboard || !nextBanner || typeof api !== 'function') return;

  const labels = {
    CORE_SETUP: 'Negocio configurado',
    TECHNICAL_READINESS: 'Canales y operación listos',
    PRICES_CONFIRMED: 'Precios confirmados',
    FAQ_REVIEWED: 'FAQ revisada',
    POLICIES_APPROVED: 'Políticas aprobadas',
    AGENT_INSTRUCTIONS_APPROVED: 'Recepcionista aprobada',
    PILOT_SCOPE_APPROVED: 'Alcance del piloto aprobado',
    CONVERSATION_TEST_COMPLETED: 'Conversación de prueba completada',
    MUTATION_TESTS_COMPLETED: 'Reservas / pedidos / pagos probados',
    HUMAN_HANDOFF_TESTED: 'Derivación humana probada'
  };

  const fieldByCode = {
    PRICES_CONFIRMED: 'pricesConfirmed',
    FAQ_REVIEWED: 'faqReviewed',
    POLICIES_APPROVED: 'policiesApproved',
    AGENT_INSTRUCTIONS_APPROVED: 'agentInstructionsApproved',
    PILOT_SCOPE_APPROVED: 'pilotScopeApproved',
    CONVERSATION_TEST_COMPLETED: 'conversationTestCompleted',
    MUTATION_TESTS_COMPLETED: 'mutationTestsCompleted',
    HUMAN_HANDOFF_TESTED: 'humanHandoffTested'
  };

  const style = document.createElement('style');
  style.textContent = `
    #pilotActivationCard { margin: 18px 0; padding: 16px 18px; }
    .pilot-activation-head { display:flex; justify-content:space-between; gap:14px; align-items:flex-start; }
    .pilot-activation-head h2 { margin:3px 0 4px; font-size:18px; }
    .pilot-activation-head p { margin:0; color:var(--muted); font-size:12px; line-height:1.45; }
    .pilot-activation-score { min-width:84px; text-align:right; }
    .pilot-activation-score strong { display:block; font-size:22px; line-height:1; }
    .pilot-activation-score span { color:var(--muted); font-size:9px; }
    .pilot-activation-progress { height:7px; border-radius:999px; background:rgba(255,255,255,.06); overflow:hidden; margin:12px 0; }
    .pilot-activation-progress > span { display:block; height:100%; background:#37cd91; width:0; transition:width .2s ease; }
    .pilot-activation-list { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:8px; }
    .pilot-activation-item { display:flex; gap:9px; align-items:flex-start; padding:10px 11px; border:1px solid var(--border); border-radius:10px; background:rgba(255,255,255,.02); }
    .pilot-activation-item.complete { border-color:rgba(55,205,145,.3); background:rgba(55,205,145,.04); }
    .pilot-activation-item input { margin-top:2px; }
    .pilot-activation-item strong { display:block; font-size:11px; line-height:1.35; }
    .pilot-activation-item small { display:block; margin-top:3px; color:var(--muted); font-size:9px; }
    .pilot-activation-item .auto-dot { width:11px; height:11px; margin-top:2px; border-radius:50%; border:2px solid #f4a636; flex:0 0 auto; }
    .pilot-activation-item.complete .auto-dot { border-color:#37cd91; background:#37cd91; }
    .pilot-activation-actions { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-top:12px; }
    #pilotActivationMessage { color:var(--muted); font-size:10px; }
    #pilotActivationMessage.error { color:#f28d8d; }
    #pilotActivationMessage.success { color:#37cd91; }
    @media (max-width:680px) {
      .pilot-activation-list { grid-template-columns:1fr; }
      .pilot-activation-head, .pilot-activation-actions { flex-direction:column; }
      .pilot-activation-score { text-align:left; }
    }
  `;
  document.head.appendChild(style);

  const card = document.createElement('section');
  card.id = 'pilotActivationCard';
  card.className = 'card hidden';
  card.setAttribute('aria-label', 'Activación del piloto');
  card.innerHTML = `
    <div class="pilot-activation-head">
      <div>
        <div class="eyebrow">Primer cliente</div>
        <h2>Checklist de activación</h2>
        <p>Lo técnico se valida automáticamente. Marca solo lo que el negocio ya revisó y aprobó contigo.</p>
      </div>
      <div class="pilot-activation-score"><strong id="pilotActivationScore">–</strong><span>LISTO PARA ACTIVAR</span></div>
    </div>
    <div class="pilot-activation-progress"><span id="pilotActivationProgress"></span></div>
    <div id="pilotActivationList" class="pilot-activation-list"></div>
    <div class="pilot-activation-actions">
      <span id="pilotActivationMessage"></span>
      <button id="pilotActivationSave" class="button small primary" type="button">Guardar confirmaciones</button>
    </div>
  `;
  nextBanner.insertAdjacentElement('afterend', card);

  const list = card.querySelector('#pilotActivationList');
  const score = card.querySelector('#pilotActivationScore');
  const progress = card.querySelector('#pilotActivationProgress');
  const message = card.querySelector('#pilotActivationMessage');
  const save = card.querySelector('#pilotActivationSave');
  let canManage = false;
  let current = null;
  let loading = false;

  function render(data) {
    current = data || {};
    const steps = Array.isArray(current.steps) ? current.steps : [];
    score.textContent = `${Number(current.completed || 0)}/${Number(current.total || steps.length || 0)}`;
    progress.style.width = `${Math.max(0, Math.min(100, Number(current.progressPercent || 0)))}%`;
    list.replaceChildren();

    steps.forEach(step => {
      const row = document.createElement('label');
      row.className = `pilot-activation-item ${step.complete ? 'complete' : ''}`;
      row.dataset.code = step.code || '';

      if (step.automatic) {
        const dot = document.createElement('span');
        dot.className = 'auto-dot';
        dot.setAttribute('aria-hidden', 'true');
        row.appendChild(dot);
      } else {
        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.checked = Boolean(step.complete);
        checkbox.disabled = !canManage;
        checkbox.dataset.field = fieldByCode[step.code] || '';
        row.appendChild(checkbox);
      }

      const copy = document.createElement('span');
      const title = document.createElement('strong');
      title.textContent = step.label || labels[step.code] || step.code || 'Paso';
      const detail = document.createElement('small');
      detail.textContent = step.automatic
        ? (step.complete ? 'Verificado automáticamente por RecepVoz.' : 'Pendiente en la configuración técnica.')
        : (step.complete ? 'Confirmado por el negocio.' : 'Requiere confirmación antes de activar.');
      copy.append(title, detail);
      row.appendChild(copy);
      list.appendChild(row);
    });

    save.classList.toggle('hidden', !canManage);
    if (current.ready) {
      message.textContent = 'Checklist completo. El negocio puede pasar al control del piloto.';
      message.className = 'success';
    } else {
      const pending = steps.filter(step => step.required && !step.complete).length;
      message.textContent = pending ? `Faltan ${pending} paso${pending === 1 ? '' : 's'}.` : '';
      message.className = '';
    }
    card.classList.remove('hidden');
  }

  async function load() {
    if (loading || dashboard.classList.contains('hidden') || !sessionStorage.getItem('helvoca_access_token')) return;
    loading = true;
    try {
      const [me, data] = await Promise.all([
        api('/api/v1/auth/me'),
        api('/api/v1/onboarding/activation')
      ]);
      canManage = Array.isArray(me?.roles) && me.roles.includes('BUSINESS_ADMIN');
      render(data);
    } catch (error) {
      if (error.status === 401 || error.status === 403) card.classList.add('hidden');
    } finally {
      loading = false;
    }
  }

  save.addEventListener('click', async () => {
    save.disabled = true;
    message.textContent = 'Guardando…';
    message.className = '';
    try {
      const payload = {};
      Object.values(fieldByCode).forEach(field => payload[field] = false);
      list.querySelectorAll('input[data-field]').forEach(input => {
        if (input.dataset.field) payload[input.dataset.field] = input.checked;
      });
      const data = await api('/api/v1/onboarding/activation', {
        method: 'PUT',
        body: JSON.stringify(payload)
      });
      render(data);
      message.textContent = data.ready
        ? 'Confirmaciones guardadas. Checklist completo.'
        : 'Confirmaciones guardadas.';
      message.className = 'success';
    } catch (error) {
      message.textContent = error.message || 'No fue posible guardar el checklist.';
      message.className = 'error';
    } finally {
      save.disabled = false;
    }
  });

  new MutationObserver(() => {
    if (!dashboard.classList.contains('hidden')) load();
  }).observe(dashboard, { attributes: true, attributeFilter: ['class'] });

  document.querySelector('#refreshBtn')?.addEventListener('click', load);
  load();
})();
