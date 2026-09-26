(() => {
  const dashboard = document.querySelector('#dashboardView');
  if (!dashboard || typeof api !== 'function') return;

  const style = document.createElement('style');
  style.textContent = `
    #managedPaymentSandbox {
      margin-top: 14px;
      padding-top: 14px;
      border-top: 1px solid rgba(255,255,255,.07);
    }
    #managedPaymentSandbox .payment-sandbox-row {
      display:flex;
      align-items:center;
      justify-content:space-between;
      gap:12px;
    }
    #managedPaymentSandbox .payment-sandbox-copy { min-width:0; }
    #managedPaymentSandbox .payment-sandbox-copy strong { display:block; font-size:13px; }
    #managedPaymentSandbox .payment-sandbox-copy span {
      display:block;
      margin-top:3px;
      color:var(--muted);
      font-size:11px;
      line-height:1.45;
    }
    #managedPaymentSandboxState {
      margin-top:9px;
      padding:9px 11px;
      border:1px solid var(--border);
      border-radius:10px;
      background:rgba(255,255,255,.025);
    }
    #managedPaymentSandboxState.ready {
      border-color:rgba(55,205,145,.34);
      background:rgba(55,205,145,.05);
    }
    #managedPaymentSandboxState strong { display:block; font-size:11px; }
    #managedPaymentSandboxState span { display:block; margin-top:3px; color:var(--muted); font-size:10px; line-height:1.4; }
    #managedPaymentSandboxActions { display:flex; flex-wrap:wrap; gap:7px; margin-top:9px; }
    #managedPaymentSandboxMessage { margin-top:7px; color:var(--muted); font-size:10px; line-height:1.4; }
    #managedPaymentSandboxMessage.error { color:#f28d8d; }
    #managedPaymentSandboxMessage.success { color:#37cd91; }
    @media (max-width:520px) {
      #managedPaymentSandbox .payment-sandbox-row { align-items:stretch; flex-direction:column; }
    }
  `;
  document.head.appendChild(style);

  let card = null;
  let canManage = false;
  let loading = false;

  function ensureCard() {
    const panel = document.querySelector('#configPhonePanel');
    if (!panel) return null;
    if (card?.isConnected) return card;

    card = document.createElement('section');
    card.id = 'managedPaymentSandbox';
    card.innerHTML = `
      <div class="payment-sandbox-row">
        <div class="payment-sandbox-copy">
          <strong>Pagos de prueba</strong>
          <span>Activa Mercado Pago Sandbox administrado por RecepVoz. No necesitas copiar tokens ni configurar Railway.</span>
        </div>
      </div>
      <div id="managedPaymentSandboxState">
        <strong>Comprobando pagos…</strong>
        <span>Validando disponibilidad del sandbox administrado.</span>
      </div>
      <div id="managedPaymentSandboxActions">
        <button id="managedPaymentSandboxEnable" class="button secondary hidden" type="button">Activar pagos de prueba</button>
        <button id="managedPaymentSandboxDisable" class="button ghost hidden" type="button">Desactivar pagos de prueba</button>
      </div>
      <div id="managedPaymentSandboxMessage" role="status"></div>
    `;
    panel.appendChild(card);

    card.querySelector('#managedPaymentSandboxEnable')?.addEventListener('click', async () => {
      if (!canManage) return;
      await mutate('/enable', 'Pagos de prueba activados.');
    });
    card.querySelector('#managedPaymentSandboxDisable')?.addEventListener('click', async () => {
      if (!canManage) return;
      await mutate('/disable', 'Pagos de prueba desactivados.');
    });
    return card;
  }

  function setMessage(text, kind = '') {
    const target = card?.querySelector('#managedPaymentSandboxMessage');
    if (!target) return;
    target.textContent = text || '';
    target.className = kind || '';
  }

  function render(status) {
    const target = ensureCard();
    if (!target) return;

    const state = target.querySelector('#managedPaymentSandboxState');
    const title = state.querySelector('strong');
    const copy = state.querySelector('span');
    const enable = target.querySelector('#managedPaymentSandboxEnable');
    const disable = target.querySelector('#managedPaymentSandboxDisable');

    state.classList.toggle('ready', Boolean(status?.enabled));

    if (status?.blockedByCustomConfiguration) {
      title.textContent = 'Configuración de pago avanzada detectada';
      copy.textContent = 'RecepVoz no la modificará desde el onboarding administrado.';
    } else if (!status?.available) {
      title.textContent = 'Sandbox administrado no disponible';
      copy.textContent = 'La plataforma todavía no tiene credenciales sandbox habilitadas.';
    } else if (status?.enabled) {
      title.textContent = 'Mercado Pago Sandbox activo';
      copy.textContent = 'El negocio puede generar links y completar pagos de prueba. No se usa dinero real.';
    } else if (status?.configured) {
      title.textContent = 'Mercado Pago Sandbox preparado';
      copy.textContent = 'La configuración está guardada pero los pagos de prueba están desactivados.';
    } else {
      title.textContent = 'Pagos de prueba disponibles';
      copy.textContent = 'Puedes habilitarlos sin ingresar credenciales ni secretos.';
    }

    const canEnable = canManage
      && status?.available
      && !status?.enabled
      && !status?.blockedByCustomConfiguration;
    const canDisable = canManage
      && status?.configured
      && status?.enabled
      && !status?.blockedByCustomConfiguration;

    enable.classList.toggle('hidden', !canEnable);
    disable.classList.toggle('hidden', !canDisable);
    enable.disabled = false;
    disable.disabled = false;
  }

  async function mutate(path, successMessage) {
    const enable = card?.querySelector('#managedPaymentSandboxEnable');
    const disable = card?.querySelector('#managedPaymentSandboxDisable');
    if (enable) enable.disabled = true;
    if (disable) disable.disabled = true;
    setMessage('Actualizando…');
    try {
      const status = await api('/api/v1/payment-provider/managed-sandbox' + path, { method: 'POST' });
      render(status);
      setMessage(successMessage, 'success');
    } catch (error) {
      setMessage(error.message || 'No fue posible actualizar los pagos de prueba.', 'error');
      if (enable) enable.disabled = false;
      if (disable) disable.disabled = false;
    }
  }

  async function load() {
    if (loading || dashboard.classList.contains('hidden') || !sessionStorage.getItem('helvoca_access_token')) return;
    const target = ensureCard();
    if (!target) return;
    loading = true;
    try {
      const [me, status] = await Promise.all([
        api('/api/v1/auth/me'),
        api('/api/v1/payment-provider/managed-sandbox')
      ]);
      canManage = Array.isArray(me?.roles) && me.roles.includes('BUSINESS_ADMIN');
      render(status);
    } catch (error) {
      if (error.status === 401 || error.status === 403) target.classList.add('hidden');
      else setMessage(error.message || 'No fue posible comprobar los pagos de prueba.', 'error');
    } finally {
      loading = false;
    }
  }

  new MutationObserver(() => {
    if (!dashboard.classList.contains('hidden')) load();
  }).observe(dashboard, { attributes:true, attributeFilter:['class'] });

  document.querySelector('#refreshBtn')?.addEventListener('click', load);
  queueMicrotask(load);
})();
