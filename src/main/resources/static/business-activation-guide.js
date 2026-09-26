(() => {
  const dashboard = document.querySelector('#dashboardView');
  const nextBanner = document.querySelector('#nextStepBanner');
  if (!dashboard || !nextBanner || typeof api !== 'function') return;

  const style = document.createElement('style');
  style.textContent = `
    #businessActivationGuide { margin:0 0 18px; padding:16px 18px; }
    .activation-guide-head { display:flex; align-items:flex-start; justify-content:space-between; gap:14px; }
    .activation-guide-head h2 { margin:3px 0 4px; font-size:18px; }
    .activation-guide-head p { margin:0; color:var(--muted); font-size:12px; line-height:1.45; }
    .activation-guide-score { min-width:80px; text-align:right; }
    .activation-guide-score strong { display:block; font-size:22px; line-height:1; }
    .activation-guide-score span { color:var(--muted); font-size:9px; }
    .activation-guide-progress { height:7px; margin:12px 0; border-radius:999px; background:rgba(255,255,255,.06); overflow:hidden; }
    .activation-guide-progress > span { display:block; height:100%; width:0; background:#37cd91; transition:width .2s ease; }
    .activation-guide-list { display:grid; grid-template-columns:repeat(7,minmax(0,1fr)); gap:7px; }
    .activation-guide-step { min-width:0; padding:9px; border:1px solid var(--border); border-radius:10px; background:rgba(255,255,255,.02); }
    .activation-guide-step.complete { border-color:rgba(55,205,145,.3); background:rgba(55,205,145,.04); }
    .activation-guide-step.next { border-color:rgba(124,92,255,.45); background:rgba(124,92,255,.08); }
    .activation-guide-step span { display:flex; align-items:center; gap:5px; font-size:9px; font-weight:850; }
    .activation-guide-step span::before { content:''; width:7px; height:7px; border-radius:50%; background:#f4a636; flex:0 0 auto; }
    .activation-guide-step.complete span::before { background:#37cd91; }
    .activation-guide-step strong { display:block; margin-top:6px; font-size:10px; line-height:1.3; }
    .activation-guide-next { display:flex; align-items:center; justify-content:space-between; gap:14px; margin-top:11px; padding-top:10px; border-top:1px solid var(--border); }
    .activation-guide-next div { min-width:0; }
    .activation-guide-next strong { display:block; font-size:11px; }
    .activation-guide-next small { display:block; margin-top:3px; color:var(--muted); font-size:10px; line-height:1.4; }
    .activation-guide-next a { white-space:nowrap; }
    @media (max-width:1050px) { .activation-guide-list { grid-template-columns:repeat(4,minmax(0,1fr)); } }
    @media (max-width:650px) {
      .activation-guide-head, .activation-guide-next { flex-direction:column; }
      .activation-guide-score { text-align:left; }
      .activation-guide-list { grid-template-columns:repeat(2,minmax(0,1fr)); }
    }
  `;
  document.head.appendChild(style);

  const card = document.createElement('section');
  card.id = 'businessActivationGuide';
  card.className = 'card hidden';
  card.setAttribute('aria-label', 'Ruta de activación del negocio');
  card.innerHTML = `
    <div class="activation-guide-head">
      <div>
        <div class="eyebrow">Alta asistida</div>
        <h2>Ruta para activar tu negocio</h2>
        <p>RecepVoz te lleva desde la cuenta creada hasta el piloto listo, sin pasos técnicos.</p>
      </div>
      <div class="activation-guide-score"><strong id="activationGuideScore">–</strong><span>RUTA COMPLETADA</span></div>
    </div>
    <div class="activation-guide-progress"><span id="activationGuideProgress"></span></div>
    <div id="activationGuideList" class="activation-guide-list"></div>
    <div id="activationGuideNext" class="activation-guide-next"></div>
  `;
  nextBanner.insertAdjacentElement('afterend', card);

  const score = card.querySelector('#activationGuideScore');
  const progress = card.querySelector('#activationGuideProgress');
  const list = card.querySelector('#activationGuideList');
  const next = card.querySelector('#activationGuideNext');
  let loading = false;

  function render(data) {
    const steps = Array.isArray(data?.steps) ? data.steps : [];
    score.textContent = `${Number(data?.completed || 0)}/${Number(data?.total || steps.length || 0)}`;
    progress.style.width = `${Math.max(0, Math.min(100, Number(data?.progressPercent || 0)))}%`;
    list.replaceChildren();

    steps.forEach(step => {
      const item = document.createElement('article');
      const isNext = data?.nextStep?.code === step.code;
      item.className = `activation-guide-step ${step.complete ? 'complete' : ''} ${isNext ? 'next' : ''}`;
      item.dataset.code = step.code || '';

      const state = document.createElement('span');
      state.textContent = step.complete ? 'LISTO' : isNext ? 'SIGUIENTE' : 'PENDIENTE';
      const title = document.createElement('strong');
      title.textContent = step.label || step.code || 'Paso';

      item.append(state, title);
      list.appendChild(item);
    });

    next.replaceChildren();
    if (data?.readyForPilot) {
      const copy = document.createElement('div');
      copy.innerHTML = '<strong>Ruta de activación completa</strong><small>Ya puedes operar el control del piloto desde Inicio.</small>';
      const link = document.createElement('a');
      link.className = 'button small primary';
      link.href = '/#pilotControlCard';
      link.textContent = 'Ir al piloto';
      next.append(copy, link);
    } else if (data?.nextStep) {
      const copy = document.createElement('div');
      const title = document.createElement('strong');
      title.textContent = `Siguiente: ${data.nextStep.label || data.nextStep.code}`;
      const detail = document.createElement('small');
      detail.textContent = data.nextStep.detail || '';
      copy.append(title, detail);

      const link = document.createElement('a');
      link.className = 'button small primary';
      link.href = data.nextStep.actionHref || '/settings.html';
      link.textContent = data.nextStep.actionLabel || 'Continuar';
      next.append(copy, link);
    }

    card.classList.remove('hidden');

    if (location.hash === '#businessActivationGuide') {
      requestAnimationFrame(() => card.scrollIntoView({ behavior:'smooth', block:'start' }));
    }
  }

  async function load() {
    if (loading || dashboard.classList.contains('hidden') || !sessionStorage.getItem('helvoca_access_token')) return;
    loading = true;
    try {
      render(await api('/api/v1/onboarding/guide'));
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
