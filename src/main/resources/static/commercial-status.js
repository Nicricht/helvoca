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
        .commercial-plan-section { margin-top: 18px; padding-top: 18px; border-top: 1px solid var(--border); }
        .commercial-plan-section h3 { margin: 0 0 6px; }
        .commercial-plan-section > p { margin: 0 0 12px; }
        .commercial-plans { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; }
        .commercial-plan { position: relative; padding: 14px; border: 1px solid rgba(255,255,255,.08); border-radius: 13px; background: rgba(255,255,255,.018); display: flex; flex-direction: column; gap: 8px; min-width: 0; }
        .commercial-plan.current { border-color: rgba(55,205,145,.42); background: rgba(55,205,145,.055); }
        .commercial-plan.pending { border-color: rgba(124,92,255,.42); background: rgba(124,92,255,.055); }
        .commercial-plan h4 { margin: 0; }
        .commercial-plan-price { font-size: 20px; font-weight: 800; }
        .commercial-plan-price small { display: inline; color: var(--muted); font-size: 11px; font-weight: 600; }
        .commercial-plan-meta { color: var(--muted); font-size: 12px; line-height: 1.45; min-height: 34px; }
        .commercial-plan .button { margin-top: auto; width: 100%; }
        .commercial-plan-tag { display: inline-flex; align-self: flex-start; font-size: 10px; font-weight: 800; letter-spacing: .04em; padding: 4px 7px; border-radius: 999px; border: 1px solid rgba(255,255,255,.1); }
        .commercial-plan-message { margin-top: 12px; }

        /* Compact summary stays lightweight; details remain one click away. */
        #commercialStatusCard.ux-commercial-card { margin: 8px 0 !important; border-radius: 14px !important; box-shadow: none !important; }
        #commercialStatusCard .ux-commercial-summary { padding: 10px 12px !important; gap: 10px !important; }
        #commercialStatusCard .ux-commercial-summary-copy .eyebrow { display: none !important; }
        #commercialStatusCard .ux-commercial-headline { font-size: 14px !important; }
        #commercialStatusCard .ux-commercial-meta { margin-top: 2px !important; font-size: 11px !important; }
        #commercialStatusCard #commercialManageBtn { min-height: 30px !important; padding: 0 9px !important; font-size: 11px !important; }

        @media (max-width: 980px) { .commercial-plans { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
        @media (max-width: 820px) { .commercial-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
        @media (max-width: 520px) { .commercial-grid, .commercial-plans { grid-template-columns: 1fr; } .commercial-heading, .commercial-foot { flex-direction: column; } }
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
                <p class="muted-text">Helvoca muestra el estado confirmado por backend. Elegir un plan solo inicia el checkout; el plan no se activa hasta verificar un pago aprobado.</p>
            </div>
            <span id="commercialStateBadge" class="badge muted">Cargando</span>
        </div>
        <div id="commercialMessage" class="message hidden"></div>
        <div id="commercialGrid" class="commercial-grid"></div>
        <div id="commercialPending"></div>
        <div class="commercial-foot">
            <small id="commercialBillingHint"></small>
            <a class="button small ghost" href="/pricing.html">Ver planes públicos</a>
        </div>
        <section class="commercial-plan-section">
            <h3>Elige tu plan</h3>
            <p class="muted-text">Los precios se cargan desde el catálogo oficial de Helvoca. El pago se completa directamente en Mercado Pago.</p>
            <div id="commercialPlans" class="commercial-plans"></div>
            <div id="commercialPlanMessage" class="message commercial-plan-message hidden"></div>
        </section>
    `;
    nextStep.insertAdjacentElement('afterend', card);

    const badge = card.querySelector('#commercialStateBadge');
    const message = card.querySelector('#commercialMessage');
    const grid = card.querySelector('#commercialGrid');
    const pending = card.querySelector('#commercialPending');
    const hint = card.querySelector('#commercialBillingHint');
    const plansRoot = card.querySelector('#commercialPlans');
    const planMessage = card.querySelector('#commercialPlanMessage');
    let loading = false;
    let currentBilling = null;
    let plans = [];

    const clp = value => new Intl.NumberFormat('es-CL', {
        style: 'currency', currency: 'CLP', maximumFractionDigits: 0
    }).format(Number(value || 0));

    function setMessage(element, text, kind = 'error') {
        element.textContent = text;
        element.classList.remove('hidden', 'error', 'success');
        element.classList.add(kind);
    }

    function clearMessage(element) {
        element.textContent = '';
        element.classList.add('hidden');
        element.classList.remove('error', 'success');
    }

    function openCheckout(url) {
        if (!url) throw new Error('El proveedor no entregó un enlace de checkout.');
        const opened = window.open(url, '_blank', 'noopener,noreferrer');
        if (!opened) window.location.assign(url);
    }

    async function startCheckout(plan, button) {
        if (!currentBilling?.checkoutConfigured) {
            setMessage(planMessage, 'La facturación todavía no está habilitada en esta instalación.');
            return;
        }

        if (currentBilling.pendingPlanCode === plan.code && currentBilling.checkoutUrl) {
            openCheckout(currentBilling.checkoutUrl);
            return;
        }

        const accepted = window.confirm(
            `Vas a continuar al checkout del plan ${plan.name} por ${clp(plan.monthlyPriceClp)} al mes. ` +
            'Helvoca no activará el plan hasta verificar el pago con el proveedor. ¿Continuar?'
        );
        if (!accepted) return;

        clearMessage(planMessage);
        button.disabled = true;
        try {
            const checkout = await api('/api/v1/billing/checkout', {
                method: 'POST',
                body: JSON.stringify({ plan: plan.code })
            });
            currentBilling = {
                ...currentBilling,
                pendingPlanCode: checkout.planCode,
                pendingPlanName: checkout.planName,
                pendingMonthlyPriceClp: checkout.monthlyPriceClp,
                checkoutUrl: checkout.checkoutUrl,
                awaitingProviderVerification: true
            };
            renderPlans();
            renderPending();
            setMessage(planMessage,
                checkout.reused
                    ? `Retomando el checkout pendiente del plan ${checkout.planName}.`
                    : `Checkout creado para ${checkout.planName}. Completa el pago en Mercado Pago.`,
                'success');
            openCheckout(checkout.checkoutUrl);
        } catch (error) {
            if (error.status !== 401) setMessage(planMessage, error.message || 'No fue posible iniciar el checkout.');
        } finally {
            button.disabled = false;
        }
    }

    function renderPending() {
        pending.innerHTML = '';
        if (!currentBilling?.awaitingProviderVerification) return;
        const box = document.createElement('div');
        box.className = 'commercial-pending';
        const title = document.createElement('strong');
        title.textContent = `Plan pendiente: ${currentBilling.pendingPlanName || currentBilling.pendingPlanCode}`;
        const detail = document.createElement('span');
        detail.textContent = 'El plan actual se mantiene hasta que el backend reciba y verifique un cobro aprobado del proveedor.';
        box.append(title, detail);
        if (currentBilling.checkoutUrl) {
            const resume = document.createElement('button');
            resume.type = 'button';
            resume.className = 'button small secondary';
            resume.style.marginTop = '10px';
            resume.textContent = 'Retomar checkout pendiente';
            resume.addEventListener('click', () => openCheckout(currentBilling.checkoutUrl));
            box.appendChild(resume);
        }
        pending.appendChild(box);
    }

    function renderPlans() {
        plansRoot.innerHTML = '';
        if (!plans.length) {
            plansRoot.innerHTML = '<span class="muted-text">No pude cargar el catálogo de planes.</span>';
            return;
        }

        plans.forEach(plan => {
            const isCurrent = currentBilling?.currentPlanCode === plan.code;
            const isPending = currentBilling?.pendingPlanCode === plan.code;
            const node = document.createElement('article');
            node.className = `commercial-plan${isCurrent ? ' current' : ''}${isPending ? ' pending' : ''}`;

            const tag = document.createElement('span');
            tag.className = 'commercial-plan-tag';
            tag.textContent = isPending ? 'PENDIENTE' : isCurrent ? 'PLAN ACTUAL' : plan.recommended ? 'RECOMENDADO' : 'DISPONIBLE';

            const title = document.createElement('h4');
            title.textContent = plan.name;
            const price = document.createElement('div');
            price.className = 'commercial-plan-price';
            price.textContent = plan.customPricing ? `Desde ${clp(plan.monthlyPriceClp)}` : clp(plan.monthlyPriceClp);
            const suffix = document.createElement('small');
            suffix.textContent = '/mes';
            price.appendChild(suffix);

            const meta = document.createElement('div');
            meta.className = 'commercial-plan-meta';
            meta.textContent = `${plan.includedMinutes} min incluidos · ${plan.maxConcurrentCalls} llamada${plan.maxConcurrentCalls === 1 ? '' : 's'} simultánea${plan.maxConcurrentCalls === 1 ? '' : 's'}`;

            const action = document.createElement('button');
            action.type = 'button';
            action.className = `button small ${isPending ? 'secondary' : 'primary'}`;
            if (plan.customPricing) {
                action.textContent = 'Cotización personalizada';
                action.disabled = true;
            } else if (isPending && currentBilling?.checkoutUrl) {
                action.textContent = 'Continuar checkout';
                action.addEventListener('click', () => openCheckout(currentBilling.checkoutUrl));
            } else if (isCurrent && !currentBilling?.awaitingProviderVerification) {
                action.textContent = 'Plan actual';
                action.disabled = true;
            } else {
                action.textContent = `Elegir ${plan.name}`;
                action.disabled = !currentBilling?.checkoutConfigured;
                action.addEventListener('click', () => startCheckout(plan, action));
            }

            node.append(tag, title, price, meta, action);
            plansRoot.appendChild(node);
        });
    }

    function render(billing, subscription) {
        currentBilling = billing;
        clearMessage(message);
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

        renderPending();
        renderPlans();

        hint.textContent = billing.checkoutConfigured
            ? 'Facturación configurada. Los cambios de plan siguen sujetos a verificación del proveedor.'
            : 'Facturación externa todavía no está habilitada en esta instalación.';
    }

    async function load() {
        if (loading || dashboard.classList.contains('hidden') || !sessionStorage.getItem('helvoca_access_token')) return;
        loading = true;
        card.classList.remove('hidden');
        try {
            const [billing, subscription, publicPlans] = await Promise.all([
                api('/api/v1/billing/status'),
                api('/api/v1/subscription'),
                api('/api/v1/public/pricing', {}, false)
            ]);
            plans = Array.isArray(publicPlans) ? publicPlans : [];
            render(billing, subscription);
        } catch (error) {
            if (error.status === 403) {
                card.classList.add('hidden');
            } else if (error.status !== 401) {
                setMessage(message, error.message || 'No pude cargar el estado comercial.');
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
