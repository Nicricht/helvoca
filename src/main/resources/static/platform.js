(() => {
  const TOKEN_KEY = 'helvoca_access_token';
  let token = sessionStorage.getItem(TOKEN_KEY) || '';

  const form = document.querySelector('#platformProvisionForm');
  const submit = document.querySelector('#platformProvisionSubmit');
  const message = document.querySelector('#platformProvisionMessage');
  const empty = document.querySelector('#platformProvisionEmpty');
  const result = document.querySelector('#platformProvisionResult');
  const inviteUrl = document.querySelector('#platformInviteUrl');

  function showMessage(text, kind = '') {
    message.textContent = text || '';
    message.className = text ? `platform-message ${kind}` : 'platform-message hidden';
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
    if (await guard()) await loadEconomics();
  })();
})();
