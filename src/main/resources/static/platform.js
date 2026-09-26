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

  guard();
})();
