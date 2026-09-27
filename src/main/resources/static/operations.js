(() => {
  const TOKEN_KEY = 'helvoca_access_token';
  const dashboard = document.querySelector('#dashboardView');
  const guardMessage = document.querySelector('#operationsGuardMessage');
  let token = sessionStorage.getItem(TOKEN_KEY) || '';

  function redirectHome() {
    location.replace('/');
  }

  window.api = async function api(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
    if (token) headers.set('Authorization', `Bearer ${token}`);
    const response = await fetch(path, { ...options, headers });
    let payload = null;
    try { payload = await response.json(); } catch (_) {}
    if (!response.ok) {
      const error = new Error(payload?.message || payload?.detail || payload?.error || `Error HTTP ${response.status}`);
      error.status = response.status;
      if (response.status === 401) {
        sessionStorage.removeItem(TOKEN_KEY);
        token = '';
        redirectHome();
      }
      throw error;
    }
    return payload;
  };

  async function guard() {
    if (!token) {
      redirectHome();
      return;
    }

    try {
      const me = await window.api('/api/v1/auth/me');
      const roles = Array.isArray(me?.roles) ? me.roles.map(String) : [];
      const allowed = roles.includes('BUSINESS_ADMIN') || roles.includes('OPERATOR');
      if (!allowed) {
        redirectHome();
        return;
      }

      dashboard.classList.remove('hidden');
      guardMessage.classList.add('hidden');
    } catch (error) {
      if (error.status === 401) return;
      guardMessage.textContent = 'No fue posible validar el acceso a operación interna.';
      guardMessage.classList.remove('hidden');
    }
  }

  document.querySelector('#operationsLogout')?.addEventListener('click', () => {
    sessionStorage.removeItem(TOKEN_KEY);
    token = '';
    location.href = '/';
  });

  guard();
})();
