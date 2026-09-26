(() => {
  const dashboard = document.querySelector('#dashboardView');
  if (!dashboard || typeof api !== 'function') return;

  const style = document.createElement('style');
  style.textContent = `
    #pilotControlCard { margin:0 0 18px; padding:16px 18px; }
    .pilot-control-head { display:flex; align-items:flex-start; justify-content:space-between; gap:14px; }
    .pilot-control-head h2 { margin:3px 0 4px; font-size:18px; }
    .pilot-control-head p { margin:0; color:var(--muted); font-size:12px; line-height:1.45; }
    .pilot-control-badge { padding:6px 9px; border-radius:999px; border:1px solid var(--border); font-size:10px; font-weight:900; letter-spacing:.04em; }
    .pilot-control-badge.go, .pilot-control-badge.running { border-color:rgba(55,205,145,.38); background:rgba(55,205,145,.08); }
    .pilot-control-badge.no-go, .pilot-control-badge.paused { border-color:rgba(244,166,54,.35); background:rgba(244,166,54,.07); }
    .pilot-control-grid { display:grid; grid-template-columns:1fr 1fr; gap:9px; margin-top:13px; }
    .pilot-control-field { display:flex; flex-direction:column; gap:5px; }
    .pilot-control-field.wide { grid-column:1/-1; }
    .pilot-control-field label { font-size:10px; color:var(--muted); font-weight:800; }
    .pilot-control-field input, .pilot-control-field textarea { width:100%; box-sizing:border-box; border:1px solid var(--border); border-radius:9px; padding:9px 10px; background:rgba(255,255,255,.025); color:var(--text); font:inherit; font-size:12px; }
    .pilot-control-field textarea { min-height:68px; resize:vertical; }
    .pilot-control-actions { display:flex; flex-wrap:wrap; gap:7px; margin-top:11px; }
    .pilot-control-blockers { margin-top:10px; color:var(--muted); font-size:10px; line-height:1.5; }
    .pilot-control-message { margin-top:10px; font-size:11px; }
    .pilot-control-message.error { color:#f28d8d; }
    .pilot-control-message.success { color:#37cd91; }
    @media (max-width:620px) { .pilot-control-grid { grid-template-columns:1fr; } .pilot-control-field.wide { grid-column:auto; } .pilot-control-head { flex-direction:column; } }
  `;
  document.head.appendChild(style);

  const card = document.createElement('section');
  card.id = 'pilotControlCard';
  card.className = 'card hidden';
  card.innerHTML = `
    <div class="pilot-control-head">
      <div>
        <div class="eyebrow">Control operativo</div>
        <h2>Piloto real</h2>
        <p>Configura un responsable y un objetivo antes de abrir tráfico real al piloto.</p>
      </div>
      <span id="pilotControlBadge" class="pilot-control-badge">CARGANDO</span>
    </div>
    <div class="pilot-control-grid">
      <div class="pilot-control-field"><label for="pilotResponsibleName">Responsable</label><input id="pilotResponsibleName" maxlength="180" placeholder="Nombre del responsable"></div>
      <div class="pilot-control-field"><label for="pilotResponsibleContact">Contacto</label><input id="pilotResponsibleContact" maxlength="180" placeholder="+56 9 ... o email"></div>
      <div class="pilot-control-field wide"><label for="pilotGoal">Objetivo medible</label><textarea id="pilotGoal" placeholder="Ej.: reducir llamadas perdidas y convertir reservas"></textarea></div>
      <div class="pilot-control-field"><label for="pilotPlannedEnd">Cierre planificado</label><input id="pilotPlannedEnd" type="datetime-local"></div>
    </div>
    <div class="pilot-control-actions">
      <button id="pilotSave" class="button small secondary" type="button">Guardar configuración</button>
      <button id="pilotStart" class="button small primary hidden" type="button">Iniciar piloto</button>
      <button id="pilotPause" class="button small secondary hidden" type="button">Pausar</button>
      <button id="pilotResume" class="button small primary hidden" type="button">Reanudar</button>
      <button id="pilotComplete" class="button small ghost hidden" type="button">Completar piloto</button>
    </div>
    <div id="pilotControlBlockers" class="pilot-control-blockers"></div>
    <div id="pilotControlMessage" class="pilot-control-message hidden"></div>
  `;

  const anchor = document.querySelector('#pilotReadinessCard') || document.querySelector('#operationalOverview');
  anchor?.insertAdjacentElement('afterend', card);

  const badge = card.querySelector('#pilotControlBadge');
  const blockers = card.querySelector('#pilotControlBlockers');
  const message = card.querySelector('#pilotControlMessage');
  const responsibleName = card.querySelector('#pilotResponsibleName');
  const responsibleContact = card.querySelector('#pilotResponsibleContact');
  const goal = card.querySelector('#pilotGoal');
  const plannedEnd = card.querySelector('#pilotPlannedEnd');
  let current = null;
  let loading = false;

  function localDateTime(value) {
    if (!value) return '';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '';
    const pad = number => String(number).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth()+1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }

  function setMessage(text, kind = '') {
    message.textContent = text || '';
    message.className = `pilot-control-message ${kind}${text ? '' : ' hidden'}`;
  }

  function render(data) {
    current = data || {};
    badge.textContent = current.launchDecision || current.status || 'NO_GO';
    badge.className = `pilot-control-badge ${String(current.launchDecision || '').toLowerCase().replace('_','-')}`;

    if (document.activeElement !== responsibleName) responsibleName.value = current.responsibleName || '';
    if (document.activeElement !== responsibleContact) responsibleContact.value = current.responsibleContact || '';
    if (document.activeElement !== goal) goal.value = current.goal || '';
    if (document.activeElement !== plannedEnd) plannedEnd.value = localDateTime(current.plannedEndAt);

    card.querySelector('#pilotStart').classList.toggle('hidden', !current.canStart);
    card.querySelector('#pilotPause').classList.toggle('hidden', !current.canPause);
    card.querySelector('#pilotResume').classList.toggle('hidden', !current.canResume);
    card.querySelector('#pilotComplete').classList.toggle('hidden', !current.canComplete);

    const list = Array.isArray(current.blockers) ? current.blockers : [];
    blockers.textContent = list.length
      ? `Bloqueos: ${list.join(', ')}.`
      : current.status === 'RUNNING'
        ? 'Piloto en ejecución. Usa las métricas del panel para decidir si mantener, pausar o completar.'
        : current.status === 'COMPLETED'
          ? 'Piloto completado.'
          : 'Sin bloqueos. Puedes iniciar el piloto cuando estés listo.';
    card.classList.remove('hidden');
  }

  async function load() {
    if (loading || dashboard.classList.contains('hidden') || !sessionStorage.getItem('helvoca_access_token')) return;
    loading = true;
    try {
      render(await api('/api/v1/operations/pilot-control'));
    } catch (error) {
      if (error.status === 401 || error.status === 403) card.classList.add('hidden');
    } finally {
      loading = false;
    }
  }

  async function mutate(path, options = {}) {
    setMessage('');
    try {
      const result = await api(`/api/v1/operations/pilot-control${path}`, options);
      render(result);
      setMessage('Estado del piloto actualizado.', 'success');
    } catch (error) {
      setMessage(error.message || 'No fue posible actualizar el piloto.', 'error');
    }
  }

  card.querySelector('#pilotSave').addEventListener('click', async () => {
    const endValue = plannedEnd.value ? new Date(plannedEnd.value) : null;
    await mutate('', {
      method: 'PUT',
      body: JSON.stringify({
        responsibleName: responsibleName.value.trim(),
        responsibleContact: responsibleContact.value.trim(),
        goal: goal.value.trim(),
        plannedEndAt: endValue && !Number.isNaN(endValue.getTime()) ? endValue.toISOString() : null
      })
    });
  });

  card.querySelector('#pilotStart').addEventListener('click', () => mutate('/start', { method: 'POST' }));
  card.querySelector('#pilotPause').addEventListener('click', () => mutate('/pause', { method: 'POST' }));
  card.querySelector('#pilotResume').addEventListener('click', () => mutate('/resume', { method: 'POST' }));
  card.querySelector('#pilotComplete').addEventListener('click', () => mutate('/complete', { method: 'POST' }));

  new MutationObserver(() => {
    if (!dashboard.classList.contains('hidden')) load();
  }).observe(dashboard, { attributes:true, attributeFilter:['class'] });

  document.querySelector('#refreshBtn')?.addEventListener('click', load);
  load();
})();
