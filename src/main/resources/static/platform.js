(() => {
  const TOKEN_KEY = 'helvoca_access_token';
  let token = sessionStorage.getItem(TOKEN_KEY) || '';

  const form = document.querySelector('#platformProvisionForm');
  const submit = document.querySelector('#platformProvisionSubmit');
  const message = document.querySelector('#platformProvisionMessage');
  const empty = document.querySelector('#platformProvisionEmpty');
  const result = document.querySelector('#platformProvisionResult');
  const inviteUrl = document.querySelector('#platformInviteUrl');

  const demoState = document.querySelector('#platformDemoState');
  const demoProfiles = document.querySelector('#platformDemoProfiles');
  const demoFormWrap = document.querySelector('#platformDemoFormWrap');
  const demoForm = document.querySelector('#platformDemoForm');
  const demoSave = document.querySelector('#platformDemoSave');
  const demoMessage = document.querySelector('#platformDemoMessage');

  function showMessage(text, kind = '') {
    message.textContent = text || '';
    message.className = text ? `platform-message ${kind}` : 'platform-message hidden';
  }

  function showDemoMessage(text, kind = '') {
    if (!demoMessage) return;
    demoMessage.textContent = text || '';
    demoMessage.className = text ? `platform-message ${kind}` : 'platform-message hidden';
  }

  async function api(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
    if (token) headers.set('Authorization', `Bearer ${token}`);
    const response = await fetch(path, { ...options, headers });
    let payload = null;
    try { payload = await response.json(); } catch (_) {}
    if (!response.ok) {
      if (response.status === 401) {
        sessionStorage.removeItem(TOKEN_KEY);
        location.replace('/');
      }
      throw new Error(payload?.message || payload?.detail || payload?.error || `Error HTTP ${response.status}`);
    }
    return payload;
  }

  function clp(value) {
    if (value === null || value === undefined) return 'No disponible';
    return new Intl.NumberFormat('es-CL', {
      style: 'currency', currency: 'CLP', maximumFractionDigits: 0
    }).format(Number(value || 0));
  }

  function usd(value) {
    if (value === null || value === undefined) return 'No disponible';
    return 'US$ ' + new Intl.NumberFormat('es-CL', {
      minimumFractionDigits: 2, maximumFractionDigits: 4
    }).format(Number(value || 0));
  }

  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>'"]/g, char => ({
      '&':'&amp;', '<':'&lt;', '>':'&gt;', "'":'&#39;', '"':'&quot;'
    }[char]));
  }

  function renderDemoReadiness(data = {}) {
    const state = document.querySelector('#platformDemoReadinessState');
    const root = document.querySelector('#platformDemoReadiness');
    if (!state || !root) return;

    const labels = {
      runtime: 'Runtime demo',
      voiceNumber: 'Número de voz',
      voiceAi: 'IA de voz',
      businessData: 'Datos del negocio',
      operations: 'Operaciones',
      whatsapp: 'WhatsApp',
      payment: 'Pago',
      externalEffects: 'Efectos externos'
    };
    const order = Object.keys(labels);
    root.innerHTML = order.map(key => {
      const item = data?.[key] || { state: 'NOT_CONFIGURED', detail: '' };
      const status = String(item.state || 'NOT_CONFIGURED').toUpperCase();
      return `
        <article class="platform-demo-readiness-item">
          <span>${escapeHtml(labels[key])}</span>
          <strong class="platform-demo-readiness-state ${escapeHtml(status.toLowerCase())}">${escapeHtml(status)}</strong>
          <small>${escapeHtml(item.detail || '')}</small>
        </article>`;
    }).join('');
    state.classList.add('hidden');
    root.classList.remove('hidden');
  }

  async function loadDemoReadiness() {
    const state = document.querySelector('#platformDemoReadinessState');
    const root = document.querySelector('#platformDemoReadiness');
    if (!state || !root) return;
    state.textContent = 'Revisando readiness del runtime demo…';
    state.classList.remove('hidden');
    root.classList.add('hidden');
    try {
      renderDemoReadiness(await api('/api/v1/platform/demos/readiness'));
    } catch (error) {
      state.textContent = error.message || 'No fue posible comprobar el runtime demo.';
    }
  }

  function renderDemoProfiles(items = []) {
    const profiles = Array.isArray(items) ? items : [];
    if (!demoProfiles || !demoState) return;

    demoProfiles.innerHTML = profiles.length
      ? profiles.map(profile => {
          const capabilities = Array.isArray(profile.capabilities) ? profile.capabilities : [];
          const notes = String(profile.presenterNotes || '').trim();
          return `
            <article class="platform-demo-profile" data-demo-profile-id="${escapeHtml(profile.id || '')}">
              <div class="platform-demo-profile-top">
                <div>
                  <h3>${escapeHtml(profile.displayName || 'Demo')}</h3>
                  <p>${escapeHtml(profile.businessName || 'Negocio demo')} · ${escapeHtml(profile.language || 'es')} · ${escapeHtml(profile.timezone || '')}</p>
                </div>
                <span class="platform-demo-badge">DEMO</span>
              </div>
              <div class="platform-demo-capabilities">
                ${capabilities.length
                  ? capabilities.map(capability => `<span>${escapeHtml(capability)}</span>`).join('')
                  : '<span>Sin capacidades activadas</span>'}
              </div>
              ${notes ? `<p><strong>Nota:</strong> ${escapeHtml(notes)}</p>` : ''}
            </article>`;
        }).join('')
      : '<div class="platform-empty">Todavía no hay perfiles. Crea uno para preparar una demo con datos aprobados.</div>';

    demoState.classList.add('hidden');
    demoProfiles.classList.remove('hidden');
  }

  async function loadDemos() {
    if (!demoState || !demoProfiles) return;
    demoState.textContent = 'Cargando perfiles de demo…';
    demoState.classList.remove('hidden');
    demoProfiles.classList.add('hidden');
    try {
      renderDemoProfiles(await api('/api/v1/platform/demos'));
    } catch (error) {
      demoState.textContent = error.message || 'No fue posible cargar los perfiles de demo.';
    }
  }

  function openDemoForm() {
    showDemoMessage('');
    demoFormWrap?.classList.remove('hidden');
    demoForm?.elements.demoDisplayName?.focus();
  }

  function closeDemoForm() {
    showDemoMessage('');
    demoForm?.reset();
    if (demoForm) {
      demoForm.elements.demoTimezone.value = 'America/Santiago';
      demoForm.elements.demoLanguage.value = 'es';
    }
    demoFormWrap?.classList.add('hidden');
  }

  function renderEconomics(data = {}) {
    const state = document.querySelector('#platformEconomicsState');
    const content = document.querySelector('#platformEconomicsContent');
    const commercial = Number(data.estimatedCommercialValueClp || 0);
    document.querySelector('#platformCommercialValue').textContent = clp(commercial);
    document.querySelector('#platformPlatformCost').textContent =
      data.estimatedPlatformCostClp == null ? usd(data.estimatedPlatformCostUsd) : clp(data.estimatedPlatformCostClp);
    document.querySelector('#platformGrossMargin').textContent =
      data.estimatedGrossMarginClp == null ? 'Configurar USD/CLP' : clp(data.estimatedGrossMarginClp);
    document.querySelector('#platformGrossMarginPercent').textContent =
      data.estimatedGrossMarginPercent == null ? 'No disponible' : `${Number(data.estimatedGrossMarginPercent).toFixed(1)}%`;

    const businesses = Array.isArray(data.businesses) ? data.businesses : [];
    document.querySelector('#platformEconomicsBusinesses').innerHTML = businesses.length
      ? businesses.map(item => `
        <article class="platform-economics-row">
          <strong>${escapeHtml(item.businessName || 'Negocio')}<span class="platform-alert">${escapeHtml(item.usageAlertLevel || 'NORMAL')}</span></strong>
          <span>${escapeHtml(item.planName || item.planCode || 'Sin plan')} · ${Number(item.usedMinutes || 0)}/${Number(item.includedMinutes || 0)} min</span>
          <span>${item.estimatedCommercialValueClp == null ? 'Valor personalizado' : clp(item.estimatedCommercialValueClp)}</span>
          <span>${item.estimatedGrossMarginClp == null ? 'Margen no disponible' : clp(item.estimatedGrossMarginClp)}</span>
        </article>`).join('')
      : '<div class="platform-empty">Aún no hay negocios con economía de período disponible.</div>';

    const providers = Array.isArray(data.providers) ? data.providers : [];
    document.querySelector('#platformEconomicsProviders').innerHTML = providers.length
      ? providers.map(item => `
        <article class="platform-provider-row">
          <strong>${escapeHtml(item.aiProvider || 'unknown')} · ${escapeHtml(item.aiModel || 'unknown')}</strong>
          <small>${Number(item.callCount || 0)} llamadas · ${Number(item.minutes || 0)} min</small>
          <span>${item.estimatedTotalCostClp == null ? usd(item.estimatedTotalCostUsd) : clp(item.estimatedTotalCostClp)} estimados</span>
        </article>`).join('')
      : '<div class="platform-empty">Aún no hay llamadas de voz terminadas en los períodos actuales.</div>';

    const unknown = Number(data.businessesWithUnknownCommercialValue || 0);
    const rate = Number(data.usdToClpRate || 0);
    document.querySelector('#platformEconomicsNote').textContent =
      (unknown > 0 ? `${unknown} negocio(s) tienen valor comercial personalizado y no se incluyen como ingreso estimado automático. ` : '') +
      (rate > 0 ? `Conversión operativa usada: ${new Intl.NumberFormat('es-CL').format(rate)} CLP/USD.` :
        'Configura HELVOCA_COST_USD_TO_CLP para convertir costos USD y calcular margen CLP.');

    state.classList.add('hidden');
    content.classList.remove('hidden');
  }

  async function loadEconomics() {
    const state = document.querySelector('#platformEconomicsState');
    const content = document.querySelector('#platformEconomicsContent');
    state.textContent = 'Cargando economía del período…';
    state.classList.remove('hidden');
    content.classList.add('hidden');
    try {
      renderEconomics(await api('/api/v1/platform/economics'));
    } catch (error) {
      state.textContent = error.message || 'No fue posible cargar la economía interna.';
    }
  }

  async function guard() {
    if (!token) {
      location.replace('/');
      return false;
    }
    try {
      const me = await api('/api/v1/auth/me');
      const roles = Array.isArray(me?.roles) ? me.roles : [];
      if (!roles.includes('PLATFORM_ADMIN')) {
        location.replace('/');
        return false;
      }
      return true;
    } catch (_) {
      return false;
    }
  }

  function render(data) {
    document.querySelector('#platformResultBusiness').textContent =
      `${data.businessName || 'Negocio'} · ${data.businessId || ''}`;
    document.querySelector('#platformResultAdmin').textContent =
      `${data.adminName || ''} · ${data.adminEmail || ''}`;
    document.querySelector('#platformResultStatus').textContent =
      data.invitationStatus === 'PENDING' ? 'Tenant creado · invitación pendiente' : (data.invitationStatus || 'Creado');
    inviteUrl.value = new URL(data.invitePath, location.origin).href;
    empty.classList.add('hidden');
    result.classList.remove('hidden');
  }

  demoForm?.addEventListener('submit', async event => {
    event.preventDefault();
    showDemoMessage('');
    demoSave.disabled = true;
    try {
      const fields = new FormData(demoForm);
      const payload = {
        displayName: String(fields.get('demoDisplayName') || '').trim(),
        businessName: String(fields.get('demoBusinessName') || '').trim(),
        timezone: String(fields.get('demoTimezone') || 'America/Santiago').trim(),
        language: String(fields.get('demoLanguage') || 'es').trim(),
        catalog: {},
        hours: {},
        knowledge: {},
        greeting: String(fields.get('demoGreeting') || '').trim(),
        instructions: String(fields.get('demoInstructions') || '').trim() || null,
        capabilities: fields.getAll('demoCapabilities').map(String),
        presenterNotes: String(fields.get('demoPresenterNotes') || '').trim() || null,
        sourceMetadata: { source: 'manual' }
      };

      await api('/api/v1/platform/demos', {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      showDemoMessage('Demo guardada. Ya puedes seguir completando su configuración aprobada.', 'success');
      await loadDemos();
      demoForm.reset();
      demoForm.elements.demoTimezone.value = 'America/Santiago';
      demoForm.elements.demoLanguage.value = 'es';
    } catch (error) {
      showDemoMessage(error.message || 'No fue posible guardar la demo.', 'error');
    } finally {
      demoSave.disabled = false;
    }
  });

  document.querySelector('#platformDemoCreateOpen')?.addEventListener('click', openDemoForm);
  document.querySelector('#platformDemoCancel')?.addEventListener('click', closeDemoForm);

  form?.addEventListener('submit', async event => {
    event.preventDefault();
    showMessage('');
    submit.disabled = true;
    try {
      const fields = new FormData(form);
      const payload = {
        businessName: String(fields.get('businessName') || '').trim(),
        timezone: String(fields.get('timezone') || '').trim(),
        language: String(fields.get('language') || 'es').trim(),
        humanTransferPhone: String(fields.get('humanTransferPhone') || '').trim() || null,
        adminName: String(fields.get('adminName') || '').trim(),
        adminEmail: String(fields.get('adminEmail') || '').trim()
      };
      const created = await api('/api/v1/platform/businesses', {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      render(created);
      showMessage('Negocio creado. Comparte la invitación con el administrador inicial.', 'success');
      form.reset();
      form.elements.timezone.value = Intl.DateTimeFormat().resolvedOptions().timeZone || 'America/Santiago';
      form.elements.language.value = 'es';
    } catch (error) {
      showMessage(error.message || 'No fue posible crear el negocio.', 'error');
    } finally {
      submit.disabled = false;
    }
  });

  document.querySelector('#platformCopyInvite')?.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(inviteUrl.value);
      showMessage('Enlace de invitación copiado.', 'success');
    } catch (_) {
      inviteUrl.select();
      showMessage('Seleccioné el enlace para que puedas copiarlo.', 'success');
    }
  });

  document.querySelector('#platformLogout')?.addEventListener('click', () => {
    sessionStorage.removeItem(TOKEN_KEY);
    token = '';
    location.href = '/';
  });

  document.querySelector('#platformEconomicsRefresh')?.addEventListener('click', loadEconomics);

  (async () => {
    if (await guard()) await Promise.all([loadDemoReadiness(), loadDemos(), loadEconomics()]);
  })();
})();
