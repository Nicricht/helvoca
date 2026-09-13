(() => {
    const dashboard = document.querySelector('#dashboardView');
    const nextStep = document.querySelector('#nextStepBanner');
    if (!dashboard || !nextStep || typeof api !== 'function') return;

    const style = document.createElement('style');
    style.textContent = `
        .commercial-card { margin: 18px 0; }
        .commercial-heading { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 14px; }
        .commercial-heading h2 { margin: 4px 0 6px; }
        .commercial-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; }
        .commercial-stat { padding: 12px; border: 1px solid rgba(255,255,255,.07); border-radius: 12px; background: rgba(255,255,255,.02); }
        .commercial-stat small, .commercial-stat strong { display: block; }
        .commercial-stat small { margin-bottom: 4px; color: var(--muted); }
        .commercial-pending { margin-top: 12px; padding: 12px 14px; border: 1px solid rgba(124,92,255,.28); border-radius: 12px; background: rgba(124,92,255,.055); }
        .commercial-pending strong, .commercial-pending span { display: block; }
        .commercial-pending span { margin-top: 4px; color: var(--muted); font-size: 12px; line-height: 1.5; }
        .commercial-foot { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-top: 12px; }
        .commercial-foot small { color: var(--muted); line-height: 1.5; }
        @media (max-width: 820px) { .commercial-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
        @media (max-width: 520px) { .commercial-grid { grid-template-columns: 1fr; } .commercial-heading, .commercial-foot { flex-direction: column; } }
    `;
    document.head.appendChild(style);

    const card = document.createElement('section');
    card.id = 'commercialStatusCard';
    card.className = 'card commercial-card hidden';
    card.innerHTML = `
        <div class="commercial-heading">
            <div>
                <div class="eyebrow">Suscripción</div>
                <h2>Estado comercial</h2>
                <p class="muted-text">Helvoca muestra el estado confirmado por backend. Esta pantalla no puede activar planes ni aprobar cobros.</p>
            </div>
            <span id="commercialStateBadge" class="badge muted">Cargando</span>
        </div>
        <div id="commercialMessage" class="message hidden"></div>
        <div id="commercialGrid" class="commercial-grid"></div>
        <div id="commercialPending"></div>
        <div class="commercial-foot">
            <small id="commercialBillingHint"></small>
            <a class="button small ghost" href="/pricing.html">Ver planes</a>
        </div>
    `;
    nextStep.insertAdjacentElement('afterend', card);

    const badge = card.querySelector('#commercialStateBadge');
    const message = card.querySelector('#commercialMessage');
    const grid = card.querySelector('#commercialGrid');
    const pending = card.querySelector('#commercialPending');
    const hint = card.querySelector('#commercialBillingHint');
    let loading = false;

    function showMessage(text) {
        message.textContent = text;
        message.classList.remove('hidden', 'success');
        message.classList.add('error');
    }

    function clearMessage() {
        message.textContent = '';
        message.classList.add('hidden');
        message.classList.remove('error', 'success');
    }

    function render(billing, subscription) {
        clearMessage();
        const used = Number(subscription.usedMinutes || 0);
        const included = Number(subscription.includedMinutes || 0);
        const overage = Number(subscription.overageMinutes || 0);
        const remaining = Math.max(0, included - used);

        grid.innerHTML = `
            <div class="commercial-stat"><small>Plan actual</small><strong id="commercialPlan"></strong></div>
            <div class="commercial-stat"><small>Estado</small><strong id="commercialSubscriptionStatus"></strong></div>
            <div class="commercial-stat"><small>Minutos</small><strong id="commercialMinutes"></strong></div>
            <div class="commercial-stat"><small>Excedente</small><strong id="commercialOverage"></strong></div>
        `;
        grid.querySelector('#commercialPlan').textContent = billing.currentPlanName || billing.currentPlanCode || subscription.plan;
        grid.querySelector('#commercialSubscriptionStatus').textContent = subscription.status;
        grid.querySelector('#commercialMinutes').textContent = `${used} usados · ${remaining} restantes`;
        grid.querySelector('#commercialOverage').textContent = `${overage} min`;

        badge.textContent = subscription.serviceAllowed ? 'SERVICIO HABILITADO' : 'SERVICIO BLOQUEADO';
        badge.className = `badge ${subscription.serviceAllowed ? 'online' : 'muted'}`;

        pending.innerHTML = '';
        if (billing.awaitingProviderVerification) {
            const box = document.createElement('div');
            box.className = 'commercial-pending';
            const title = document.createElement('strong');
            title.textContent = `Plan pendiente: ${billing.pendingPlanName || billing.pendingPlanCode}`;
            const detail = document.createElement('span');
            detail.textContent = 'El plan actual se mantiene hasta que el backend reciba y verifique un cobro aprobado del proveedor.';
            box.append(title, detail);
            pending.appendChild(box);
        }

        hint.textContent = billing.checkoutConfigured
            ? 'Facturación configurada. Los cambios de plan siguen sujetos a verificación del proveedor.'
            : 'Facturación externa todavía no está habilitada en esta instalación.';
    }

    async function load() {
        if (loading || dashboard.classList.contains('hidden') || !sessionStorage.getItem('helvoca_access_token')) return;
        loading = true;
        card.classList.remove('hidden');
        try {
            const [billing, subscription] = await Promise.all([
                api('/api/v1/billing/status'),
                api('/api/v1/subscription')
            ]);
            render(billing, subscription);
        } catch (error) {
            if (error.status === 403) {
                card.classList.add('hidden');
            } else if (error.status !== 401) {
                showMessage(error.message || 'No pude cargar el estado comercial.');
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
