(() => {
  const title = document.querySelector('#inviteTitle');
  const subtitle = document.querySelector('#inviteSubtitle');
  const meta = document.querySelector('#inviteMeta');
  const form = document.querySelector('#inviteAcceptForm');
  const message = document.querySelector('#inviteMessage');
  const params = new URLSearchParams(location.search);
  const businessId = params.get('businessId') || '';
  const token = params.get('token') || '';

  function showMessage(text, kind = 'error') {
    message.textContent = text || '';
    message.className = text ? kind : 'hidden';
  }

  async function request(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
    const response = await fetch(path, { ...options, headers });
    let payload = null;
    try { payload = await response.json(); } catch (_) {}
    if (!response.ok) {
      throw new Error(payload?.message || payload?.detail || payload?.error || 'La invitación no está disponible.');
    }
    return payload;
  }

  async function load() {
    if (!businessId || !token) {
      title.textContent = 'Enlace incompleto';
      subtitle.textContent = 'Pide al administrador que genere una nueva invitación.';
      return;
    }

    try {
      const data = await request(
        `/api/v1/auth/invitations/${encodeURIComponent(businessId)}/${encodeURIComponent(token)}`
      );

      title.textContent = `Únete a ${data.businessName || 'este negocio'}`;
      subtitle.textContent = 'Crea tu contraseña para entrar a RecepVoz.';
      meta.innerHTML = '';
      const name = document.createElement('strong');
      name.textContent = data.name || data.email || 'Invitación';
      const detail = document.createElement('span');
      detail.textContent = `${data.email || ''} · ${data.role === 'BUSINESS_ADMIN' ? 'Administrador' : 'Operador'}`;
      meta.append(name, detail);
      meta.classList.remove('hidden');

      if (data.status === 'PENDING') {
        form.classList.remove('hidden');
      } else {
        form.classList.add('hidden');
        showMessage(
          data.status === 'ACCEPTED' ? 'Esta invitación ya fue utilizada.'
            : data.status === 'EXPIRED' ? 'Esta invitación expiró. Pide una nueva.'
            : 'Esta invitación fue revocada.'
        );
      }
    } catch (error) {
      title.textContent = 'Invitación no válida';
      subtitle.textContent = 'El enlace puede haber expirado o sido reemplazado.';
      showMessage(error.message);
    }
  }

  form?.addEventListener('submit', async event => {
    event.preventDefault();
    showMessage('');
    const data = new FormData(form);
    const password = String(data.get('password') || '');
    const confirm = String(data.get('confirmPassword') || '');
    if (password !== confirm) {
      showMessage('Las contraseñas no coinciden.');
      return;
    }

    const button = form.querySelector('button[type="submit"]');
    button.disabled = true;
    try {
      const result = await request(
        `/api/v1/auth/invitations/${encodeURIComponent(businessId)}/${encodeURIComponent(token)}/accept`,
        { method:'POST', body:JSON.stringify({ password }) }
      );
      sessionStorage.setItem('helvoca_access_token', result.accessToken);
      form.classList.add('hidden');
      showMessage('Invitación aceptada. Entrando a RecepVoz…', 'success');
      setTimeout(() => { location.href = '/'; }, 350);
    } catch (error) {
      showMessage(error.message);
      button.disabled = false;
    }
  });

  load();
})();
