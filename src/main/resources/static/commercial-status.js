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
                <p class="muted-text">El estado mostrado viene confirmado por backend. Elegir un plan solo inicia el checkout; el plan no se activa hasta verificar un pago aprobado.</p>
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
            <p class="muted-text">Los precios se cargan desde el catálogo oficial. El pago se completa directamente en Mercado Pago.</p>
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
            'El plan no se activará hasta verificar el pago con el proveedor. ¿Continuar?'
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

        badge.textContent = subscription.serviceAllowed ? 'SUSCRIPCIÓN HABILITADA' : 'SUSCRIPCIÓN BLOQUEADA';
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

(() => {
    const dashboard = document.querySelector('#dashboardView');
    const statusGrid = document.querySelector('#statusGrid');
    const heading = dashboard?.querySelector('.dashboard-heading');
    const title = heading?.querySelector('h1');
    const summary = document.querySelector('#welcomeText');
    if (!dashboard || !statusGrid || !heading || !title || typeof api !== 'function') return;

    const style = document.createElement('style');
    style.id = 'helvoca-operational-home-styles';
    style.textContent = [
        '#dashboardView #statusGrid.ux-ready-hidden { display: none !important; }',
        'body.home-page.operational-ready #pilotMetricsCard { display: none !important; }'
    ].join('\n');
    document.head.appendChild(style);

    const overview = document.createElement('section');
    overview.id = 'operationalOverview';
    overview.className = 'card hidden owner-commercial-dashboard owner-pulse-dashboard';
    overview.dataset.state = 'loading';
    overview.setAttribute('aria-labelledby', 'ownerDashboardTitle');
    overview.setAttribute('aria-busy', 'true');
    overview.innerHTML = [
        '<div class="owner-dashboard-head">',
        '  <div><div class="eyebrow">EN VIVO</div><h2 id="ownerDashboardTitle">RecepVoz está trabajando</h2><p id="ownerHeroSummary">Midiendo lo que RecepVoz está resolviendo por ti…</p></div>',
        '  <span id="ownerDashboardState" class="badge muted" role="status" aria-live="polite">Cargando</span>',
        '</div>',
        '<div id="ownerDashboardMessage" class="owner-dashboard-message" aria-live="polite">Actualizando actividad confirmada…</div>',
        '<div class="owner-pulse-layout">',
        '  <main class="owner-pulse-main">',
        '    <section class="home-pulse-kpis" aria-label="Pulso del negocio">',
        '      <article class="home-pulse-kpi"><span>Conversaciones hoy</span><strong id="homeConversationsToday">–</strong><small id="homeConversationBreakdown">Voz + WhatsApp</small></article>',
        '      <article class="home-pulse-kpi"><span>Resultados conseguidos</span><strong id="ownerResultsToday">–</strong><small id="ownerResultsHint">Reservas y pedidos de hoy</small></article>',
        '      <article class="home-pulse-kpi home-pulse-kpi-value"><span>Ingresos confirmados</span><strong id="ownerConfirmedRevenue">–</strong><small id="ownerRevenueCurrencyHint">Últimos 7 días · solo cobros comprobados</small></article>',
        '    </section>',
        '    <section class="owner-recent owner-pulse-activity" aria-labelledby="ownerRecentTitle">',
        '      <div class="owner-section-heading"><div><div class="eyebrow">EN TIEMPO REAL</div><h3 id="ownerRecentTitle">Actividad reciente</h3><p>Lo último que RecepVoz hizo por tu negocio.</p></div></div>',
        '      <div id="homeRecentActivity" class="owner-activity-list" role="list"><div class="owner-empty">Cargando actividad…</div></div>',
        '    </section>',
        '  </main>',
        '  <aside class="owner-pulse-side" aria-label="Prioridades y resumen semanal">',
        '    <section id="ownerAttentionPanel" class="owner-attention-panel" aria-labelledby="ownerAttentionTitle" data-state="loading">',
        '      <div class="eyebrow">PARA TI</div>',
        '      <h3 id="ownerAttentionTitle">Necesita tu atención</h3>',
        '      <p id="ownerAttentionSummary" aria-live="polite">Revisando pendientes…</p>',
        '      <div id="ownerDashboardAttention" class="owner-dashboard-attention hidden" aria-label="Señales que requieren atención">',
        '        <a id="ownerRequestsAttentionChip" class="owner-dashboard-chip warn hidden" href="/?tab=requests#homeBusinessWorkspace"><strong id="ownerRequestsAttentionToday">–</strong> solicitudes abiertas</a>',
        '        <a id="ownerOrdersAttentionChip" class="owner-dashboard-chip warn hidden" href="/?tab=sales#homeBusinessWorkspace"><strong id="ownerOrdersAttentionToday">–</strong> ventas por revisar</a>',
        '        <span id="ownerQuestionsAttentionChip" class="owner-dashboard-chip hidden"><strong id="ownerQuestionsAttentionToday">–</strong> preguntas sin respuesta</span>',
        '        <span id="ownerFailuresChip" class="owner-dashboard-chip hidden"><strong id="ownerFailuresToday">–</strong> fallos de llamada</span>',
        '        <span id="ownerHandoffsChip" class="owner-dashboard-chip hidden"><strong id="ownerHandoffsToday">–</strong> derivaciones</span>',
        '        <span id="ownerBookingChangesChip" class="owner-dashboard-chip hidden"><strong id="ownerBookingChangesToday">–</strong> reprogramadas</span>',
        '        <span id="ownerBookingCancelsChip" class="owner-dashboard-chip hidden"><strong id="ownerBookingCancelsToday">–</strong> canceladas</span>',
        '      </div>',
        '    </section>',
        '    <section id="ownerValuePanel" class="owner-value-panel owner-pulse-week" aria-labelledby="ownerValueTitle" data-state="loading">',
        '      <div class="owner-value-head">',
        '        <div><div class="eyebrow">ÚLTIMOS 7 DÍAS</div><h3 id="ownerValueTitle">Esta semana</h3><p id="ownerValueNarrative">Midiendo resultados confirmados…</p></div>',
        '        <button id="ownerWorkspaceToggle" type="button" aria-controls="homeBusinessWorkspace" aria-expanded="false">Ver detalle</button>',
        '      </div>',
        '      <div id="ownerRevenueTrend" class="owner-value-chart" aria-label="Evolución de cobros confirmados"><div class="owner-empty">Cargando tendencia…</div></div>',
        '      <p id="ownerRevenueEvidence" class="owner-value-evidence">Verificando evidencia de pagos…</p>',
        '      <div class="owner-pulse-evidence" aria-hidden="true">',
        '        <span id="ownerPaidOutcomes">–</span><span id="ownerManagedRevenue">–</span>',
        '      </div>',
        '    </section>',
        '  </aside>',
        '</div>',
        '<div hidden aria-hidden="true">',
        '  <span id="ownerCallsTodayPrimary">–</span>',
        '  <span id="homeBookingsToday">–</span>',
        '  <span id="homeRequestsToday">–</span>',
        '  <span id="ownerOrdersToday">–</span>',
        '  <span id="ownerOrdersHint">–</span>',
        '  <span id="homeCallsToday">–</span>',
        '  <span id="homeWhatsAppToday">–</span>',
        '  <span id="homeCustomersToday">–</span>',
        '  <span id="homeQuestionsToday">–</span>',
        '  <span id="homeFailuresToday">–</span>',
        '  <span id="homeMinutesToday">–</span>',
        '  <span id="homeCostToday">–</span>',
        '</div>'
    ].join('');
    heading.insertAdjacentElement('afterend', overview);
    const isHomeDashboardSurface =
        window.location.pathname === '/' || window.location.pathname.endsWith('/index.html');
    if (isHomeDashboardSurface) {
        heading.classList.add('dashboard-heading-pulse');
        if (!heading.querySelector('.dashboard-hero-pulse')) {
            const heroVisual = document.createElement('div');
            heroVisual.className = 'dashboard-hero-pulse';
            heroVisual.setAttribute('aria-hidden', 'true');
            heroVisual.innerHTML = [
                '<img class="home-pulse-nebula" src="/assets/home/recepvoz-nebula.png" alt="">',
                '<img class="home-pulse-wave" src="/assets/home/recepvoz-wave.png" alt="">',
                '<img class="home-pulse-particles" src="/assets/home/recepvoz-particles.png" alt="">',
                '<img class="home-pulse-glow" src="/assets/home/recepvoz-glow.png" alt="">',
                '<img class="home-pulse-robot" src="/assets/home/recepvoz-robot.png" alt="">',
                '<img class="home-pulse-bubble" src="/assets/home/recepvoz-bubble.png" alt="">',
                '<img class="home-pulse-flare" src="/assets/home/recepvoz-flare.png" alt="">'
            ].join('');
            heading.appendChild(heroVisual);
        }
    }

    const stateBadge = overview.querySelector('#ownerDashboardState');
    const message = overview.querySelector('#ownerDashboardMessage');
    const recent = overview.querySelector('#homeRecentActivity');
    let loading = false;
    let lastLoadedAt = 0;
    let currentBusinessName = window.helvocaBusinessName || 'Tu negocio';

    function isReady() {
        const cards = [...statusGrid.querySelectorAll('.status-card')];
        return cards.length >= 4 && cards.every(card => card.classList.contains('done'));
    }

    function dateKey(value, timeZone) {
        if (!value) return '';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return '';
        const parts = new Intl.DateTimeFormat('en-US', {
            timeZone: timeZone || 'UTC', year: 'numeric', month: '2-digit', day: '2-digit'
        }).formatToParts(date);
        const values = Object.fromEntries(parts.map(part => [part.type, part.value]));
        return values.year + '-' + values.month + '-' + values.day;
    }

    function formatTime(value, timeZone) {
        if (!value) return '';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return '';
        return new Intl.DateTimeFormat('es-CL', {
            timeZone: timeZone || 'UTC', hour: '2-digit', minute: '2-digit'
        }).format(date);
    }

    function numeric(value) {
        const number = Number(value || 0);
        return Number.isFinite(number) ? number : 0;
    }

    function normalizedCurrency(value) {
        const currency = String(value || '').trim().toUpperCase();
        return /^[A-Z]{3}$/.test(currency) ? currency : '';
    }

    function formatMoney(value, currency) {
        const code = normalizedCurrency(currency);
        if (!code || !Number.isFinite(Number(value))) return '–';
        try {
            return new Intl.NumberFormat('es-CL', {
                style: 'currency',
                currency: code,
                maximumFractionDigits: code === 'CLP' ? 0 : 2
            }).format(Number(value));
        } catch (_) {
            return String(value) + ' ' + code;
        }
    }

    function totalsMap(items, fallbackCurrency, fallbackAmount) {
        const totals = new Map();
        (Array.isArray(items) ? items : []).forEach(item => {
            const currency = normalizedCurrency(item?.currency);
            const amount = numeric(item?.amount);
            if (!currency || amount <= 0) return;
            totals.set(currency, (totals.get(currency) || 0) + amount);
        });
        if (!totals.size) {
            const currency = normalizedCurrency(fallbackCurrency);
            const amount = numeric(fallbackAmount);
            if (currency && amount > 0) totals.set(currency, amount);
        }
        return totals;
    }

    function mergeTotals(...maps) {
        const merged = new Map();
        maps.forEach(map => map.forEach((amount, currency) => {
            merged.set(currency, (merged.get(currency) || 0) + numeric(amount));
        }));
        return merged;
    }

    function displayTotals(totals) {
        const entries = [...totals.entries()].filter(([, amount]) => amount > 0);
        if (!entries.length) return { primary: 'Sin cobros', detail: 'No hay pagos confirmados en el período.', mixed: false };
        if (entries.length === 1) {
            const [currency, amount] = entries[0];
            return { primary: formatMoney(amount, currency), detail: currency, mixed: false };
        }
        return {
            primary: 'Varias monedas',
            detail: entries.map(([currency, amount]) => formatMoney(amount, currency)).join(' · '),
            mixed: true
        };
    }

    function managedRevenueDisplay(analytics) {
        const orderCurrency = normalizedCurrency(analytics?.primaryCurrency);
        const bookingCurrency = normalizedCurrency(analytics?.bookingCurrency);
        const orderAmount = numeric(analytics?.recepVozRevenue);
        const bookingAmount = numeric(analytics?.recepVozBookingRevenue);
        const hasOrderImpact = numeric(analytics?.recepVozOrders) > 0 || orderAmount > 0;
        const hasBookingImpact = numeric(analytics?.recepVozPaidBookings) > 0 || bookingAmount > 0;

        // If a source-attributed cohort exists but its currency cannot be represented by one
        // authoritative currency, do not publish a misleading partial monetary total.
        if ((hasOrderImpact && !orderCurrency) || (hasBookingImpact && !bookingCurrency)) {
            return {
                primary: 'No consolidable',
                detail: 'Origen Voz / WhatsApp registrado en más de una moneda.',
                mixed: true
            };
        }

        const totals = new Map();
        if (orderCurrency && orderAmount > 0) totals.set(orderCurrency, orderAmount);
        if (bookingCurrency && bookingAmount > 0) {
            totals.set(bookingCurrency, (totals.get(bookingCurrency) || 0) + bookingAmount);
        }
        return displayTotals(totals);
    }

    function renderRevenueTrend(container, analytics) {
        if (!container) return;
        const orderCurrencies = totalsMap(analytics?.currencyTotals, analytics?.primaryCurrency, analytics?.totalRevenue);
        if (orderCurrencies.size > 1) {
            container.innerHTML = '<div class="owner-empty">La tendencia diaria no se consolida porque hay cobros en varias monedas.</div>';
            return;
        }
        const points = (Array.isArray(analytics?.salesOverTime) ? analytics.salesOverTime : [])
            .map(item => ({ date: String(item?.date || ''), revenue: Math.max(0, numeric(item?.revenue)) }))
            .filter(item => item.date);
        if (!points.length) {
            container.innerHTML = '<div class="owner-empty">Aún no hay cobros diarios para graficar.</div>';
            return;
        }

        const width = 560;
        const height = 126;
        const padX = 12;
        const padY = 14;
        const max = Math.max(1, ...points.map(item => item.revenue));
        const usableW = width - (padX * 2);
        const usableH = height - (padY * 2);
        const coords = points.map((item, index) => {
            const x = points.length === 1 ? width / 2 : padX + ((usableW * index) / (points.length - 1));
            const y = height - padY - ((item.revenue / max) * usableH);
            return { ...item, x, y };
        });
        const polyline = coords.map(point => point.x.toFixed(1) + ',' + point.y.toFixed(1)).join(' ');
        const currency = normalizedCurrency(analytics?.primaryCurrency);
        container.innerHTML =
            '<svg viewBox="0 0 ' + width + ' ' + height + '" role="img" aria-label="Cobros confirmados por día">' +
            '<polyline class="owner-value-line" fill="none" points="' + polyline + '"></polyline>' +
            coords.map(point => '<circle class="owner-value-point" cx="' + point.x.toFixed(1) + '" cy="' + point.y.toFixed(1) + '" r="3"><title>' +
                point.date + ': ' + formatMoney(point.revenue, currency) + '</title></circle>').join('') +
            '</svg>';
    }

    function renderOwnerValue(analytics) {
        const panel = overview.querySelector('#ownerValuePanel');
        const confirmed = overview.querySelector('#ownerConfirmedRevenue');
        const paid = overview.querySelector('#ownerPaidOutcomes');
        const managed = overview.querySelector('#ownerManagedRevenue');
        const hint = overview.querySelector('#ownerRevenueCurrencyHint');
        const narrative = overview.querySelector('#ownerValueNarrative');
        const evidence = overview.querySelector('#ownerRevenueEvidence');
        const trend = overview.querySelector('#ownerRevenueTrend');
        if (!panel || !confirmed || !paid || !managed || !hint || !narrative || !evidence || !trend) return;

        if (!analytics) {
            panel.dataset.state = 'unavailable';
            confirmed.textContent = 'No disponible';
            paid.textContent = '–';
            managed.textContent = 'No disponible';
            hint.textContent = 'No pudimos verificar los cobros';
            narrative.textContent = 'La operación sigue disponible, pero el resumen comercial no pudo actualizarse.';
            evidence.textContent = 'Los ingresos no se estiman cuando falta evidencia confirmada.';
            trend.innerHTML = '<div class="owner-empty">La tendencia comercial no está disponible.</div>';
            return;
        }

        const orderTotals = totalsMap(analytics.currencyTotals, analytics.primaryCurrency, analytics.totalRevenue);
        const bookingTotals = totalsMap(analytics.bookingCurrencyTotals, analytics.bookingCurrency, analytics.bookingRevenue);
        const combined = displayTotals(mergeTotals(orderTotals, bookingTotals));
        const managedDisplay = managedRevenueDisplay(analytics);
        const paidOrders = numeric(analytics.paidOrders);
        const paidBookings = numeric(analytics.paidBookings);
        const providerBooking = numeric(analytics.providerVerifiedBookingRevenue);
        const manualBooking = numeric(analytics.manualRecordedBookingRevenue);

        panel.dataset.state = combined.mixed ? 'mixed' : 'ready';
        confirmed.textContent = combined.primary;
        paid.textContent = String(paidOrders + paidBookings);
        managed.textContent = managedDisplay.primary;
        hint.textContent = combined.mixed
            ? 'Cobros confirmados separados por moneda'
            : (combined.detail === 'No hay pagos confirmados en el período.' ? combined.detail : 'Moneda ' + combined.detail);

        if (combined.mixed) {
            narrative.textContent = 'Hay cobros confirmados en distintas monedas. RecepVoz los mantiene separados para no inflar el resultado.';
        } else if ((paidOrders + paidBookings) > 0) {
            narrative.textContent = 'Pedidos pagados y servicios completados con pago confirmado, sin convertir reservas pendientes en ventas.';
        } else {
            narrative.textContent = 'Todavía no hay resultados pagados confirmados en este período.';
        }

        const evidenceParts = [
            String(paidOrders) + ' pedidos pagados',
            String(paidBookings) + ' servicios completados con cobro confirmado'
        ];
        if (paidBookings > 0 && analytics.bookingCurrency) {
            evidenceParts.push(
                formatMoney(providerBooking, analytics.bookingCurrency) + ' verificados por proveedor',
                formatMoney(manualBooking, analytics.bookingCurrency) + ' registrados por el negocio'
            );
        }
        evidence.textContent = 'Pagos confirmados: ' + evidenceParts.join(' · ') +
            '. El bloque Voz / WhatsApp describe el origen registrado, no una estimación causal de ROI.';
        renderRevenueTrend(trend, analytics);
    }

    function labelStage(value) {
        const labels = {
            PAID: 'Pagado',
            PAYMENT_LINK_SENT: 'Pago pendiente',
            PAYMENT_FAILED: 'Pago fallido',
            REQUIRES_ACTION: 'Requiere atención',
            SUCCEEDED: 'Pagado',
            COMPLETED: 'Completada',
            FAILED: 'Fallida',
            NO_ANSWER: 'Sin respuesta',
            OPEN: 'Abierta',
            IN_PROGRESS: 'En curso'
        };
        const key = String(value || '').toUpperCase();
        return labels[key] || String(value || '').replaceAll('_', ' ').toLowerCase();
    }

    function labelCallResolution(value, status) {
        const raw = String(value || '').trim();
        const labels = {
            INFORMATION_ONLY: 'Información entregada',
            CUSTOMER_REGISTERED: 'Cliente registrado',
            UNANSWERED_QUESTION: 'Pregunta pendiente registrada',
            REQUEST_CREATED: 'Solicitud creada',
            QUOTE_CREATED: 'Cotización creada',
            LEAD_CREATED: 'Oportunidad registrada',
            BOOKING_CREATED: 'Reserva creada',
            BOOKING_RESCHEDULED: 'Reserva reprogramada',
            BOOKING_CANCELLED: 'Reserva cancelada',
            ORDER_CREATED: 'Pedido creado',
            ORDER_CANCELLED: 'Pedido cancelado',
            HUMAN_TRANSFERRED: 'Derivada a una persona',
            FAILED: 'Llamada fallida'
        };
        const key = raw.toUpperCase();
        if (labels[key]) return labels[key];
        if (raw && !/^[A-Z0-9_]+$/.test(raw)) return raw;
        return labelStage(status) || 'Atención telefónica';
    }

    function setDashboardState(state, text, detail) {
        overview.dataset.state = state;
        overview.setAttribute('aria-busy', state === 'loading' ? 'true' : 'false');
        stateBadge.textContent = text;
        stateBadge.className = 'badge ' + (state === 'ready' ? 'online' : 'muted');
        message.textContent = detail || '';
        message.classList.toggle('hidden', !detail);
    }

    function renderRecentActivity(operations, pipeline, timeZone) {
        const events = [];
        (operations.recentCalls || []).forEach(item => events.push({
            at: item.startedAt,
            kind: 'Llamada',
            title: labelCallResolution(item.resolution, item.status),
            detail: item.callerNumber || ''
        }));
        (operations.recentRequests || []).forEach(item => events.push({
            at: item.createdAt,
            kind: 'Solicitud',
            title: item.title || labelStage(item.status) || 'Solicitud del cliente',
            detail: item.contactName || item.description || ''
        }));
        ((pipeline && pipeline.items) || []).forEach(item => events.push({
            at: item.updatedAt,
            kind: item.product ? 'Venta' : 'Conversación',
            title: [item.product || item.customerName || 'Actividad comercial', labelStage(item.commercialStage || item.paymentStatus)].filter(Boolean).join(' · '),
            detail: item.customerName || item.customerPhone || ''
        }));

        events.sort((a, b) => new Date(b.at || 0).getTime() - new Date(a.at || 0).getTime());
        recent.innerHTML = '';
        if (!events.length) {
            recent.innerHTML = '<div class="owner-empty" role="listitem">Todavía no hay actividad reciente.</div>';
            return 0;
        }
        events.slice(0, 5).forEach(item => {
            const row = document.createElement('div');
            row.className = 'owner-activity-row';
            row.setAttribute('role', 'listitem');
            const copy = document.createElement('div');
            const titleNode = document.createElement('strong');
            const meta = document.createElement('span');
            const time = document.createElement('time');
            titleNode.textContent = item.title;
            meta.textContent = [item.kind, item.detail].filter(Boolean).join(' · ');
            time.textContent = formatTime(item.at, timeZone);
            copy.append(titleNode, meta);
            row.append(copy, time);
            recent.appendChild(row);
        });
        return events.length;
    }

    function renderOperational(operations, pilotMetrics, pipeline, audit, analytics) {
        currentBusinessName = operations.businessName || window.helvocaBusinessName || currentBusinessName || 'Tu negocio';
        const timeZone = operations.timezone || pilotMetrics?.timezone || 'UTC';
        const todayKey = dateKey(operations.localNow || pilotMetrics?.localNow || new Date().toISOString(), timeZone);
        const pilotToday = pilotMetrics?.today || null;

        const calls = pilotToday ? numeric(pilotToday.calls) : numeric(operations.callsToday);
        const whatsapp = pilotToday ? numeric(pilotToday.whatsappConversations) : 0;
        const conversations = calls + whatsapp;
        const bookings = pilotToday ? numeric(pilotToday.bookings) : numeric(operations.bookingsToday);
        const orders = pilotToday ? numeric(pilotToday.orders) : numeric(pipeline?.total);
        const openRequests = numeric(operations.openRequests);
        const unanswered = numeric(operations.unansweredQuestions);
        const handoffs = pilotToday ? numeric(pilotToday.humanTransfers) : 0;
        const failures = pilotToday ? numeric(pilotToday.callFailures) : numeric(operations.callFailuresToday);
        const pipelineNeedsAction = numeric(pipeline?.needsAction);
        const resultsToday = bookings + orders;

        overview.querySelector('#ownerCallsTodayPrimary').textContent = String(calls);
        overview.querySelector('#ownerResultsToday').textContent = String(resultsToday);
        overview.querySelector('#ownerResultsHint').textContent =
            String(bookings) + ' reserva' + (bookings === 1 ? '' : 's') + ' · ' +
            String(orders) + ' pedido' + (orders === 1 ? '' : 's');
        overview.querySelector('#ownerHeroSummary').textContent =
            String(conversations) + (conversations === 1 ? ' conversación atendida' : ' conversaciones atendidas') + ' · ' +
            String(resultsToday) + (resultsToday === 1 ? ' resultado conseguido' : ' resultados conseguidos');
        overview.querySelector('#homeConversationsToday').textContent = String(conversations);
        overview.querySelector('#homeConversationBreakdown').textContent = pilotToday
            ? String(whatsapp) + ' WhatsApp · ' + String(conversations) + ' conversaciones'
            : String(conversations) + ' conversaciones registradas';
        overview.querySelector('#homeBookingsToday').textContent = String(bookings);
        overview.querySelector('#homeRequestsToday').textContent = String(openRequests);
        overview.querySelector('#ownerOrdersToday').textContent = String(orders);
        overview.querySelector('#ownerOrdersHint').textContent = pipelineNeedsAction > 0
            ? String(pipelineNeedsAction) + ' requiere' + (pipelineNeedsAction === 1 ? '' : 'n') + ' atención'
            : 'Generados hoy';

        const ordersMetric = overview.querySelector('#ownerOrdersMetric');
        ordersMetric?.classList.toggle('hidden', !(orders > 0 || numeric(pipeline?.total) > 0));
        overview.querySelector('#ownerRequestsAttentionToday').textContent = String(openRequests);
        overview.querySelector('#ownerRequestsAttentionChip').classList.toggle('hidden', openRequests <= 0);
        overview.querySelector('#ownerOrdersAttentionToday').textContent = String(pipelineNeedsAction);
        overview.querySelector('#ownerOrdersAttentionChip').classList.toggle('hidden', pipelineNeedsAction <= 0);
        overview.querySelector('#ownerQuestionsAttentionToday').textContent = String(unanswered);
        overview.querySelector('#ownerQuestionsAttentionChip').classList.toggle('hidden', unanswered <= 0);

        overview.querySelector('#ownerHandoffsToday').textContent = String(handoffs);
        overview.querySelector('#ownerFailuresToday').textContent = String(failures);
        overview.querySelector('#ownerHandoffsChip').classList.toggle('hidden', handoffs <= 0);
        overview.querySelector('#ownerFailuresChip').classList.toggle('hidden', failures <= 0);
        overview.querySelector('#ownerHandoffsChip').classList.toggle('warn', handoffs > 0);
        overview.querySelector('#ownerFailuresChip').classList.toggle('warn', failures > 0);

        const auditRows = Array.isArray(audit) ? audit : null;
        let rescheduled = 0;
        let cancelled = 0;
        if (auditRows) {
            auditRows.forEach(item => {
                if (String(item.resourceType || '').toUpperCase() !== 'BOOKING') return;
                if (dateKey(item.createdAt, timeZone) !== todayKey) return;
                const action = String(item.action || '').toUpperCase();
                if (action === 'BOOKING_RESCHEDULE') rescheduled += 1;
                if (action === 'BOOKING_CANCEL') cancelled += 1;
            });
            overview.querySelector('#ownerBookingChangesToday').textContent = String(rescheduled);
            overview.querySelector('#ownerBookingCancelsToday').textContent = String(cancelled);
            overview.querySelector('#ownerBookingChangesChip').classList.add('hidden');
            overview.querySelector('#ownerBookingCancelsChip').classList.add('hidden');
        } else {
            overview.querySelector('#ownerBookingChangesChip').classList.add('hidden');
            overview.querySelector('#ownerBookingCancelsChip').classList.add('hidden');
        }

        const attentionCount = openRequests + pipelineNeedsAction + unanswered + handoffs + failures;
        const attentionPanel = overview.querySelector('#ownerAttentionPanel');
        const attentionTitle = overview.querySelector('#ownerAttentionTitle');
        const attentionSummary = overview.querySelector('#ownerAttentionSummary');
        attentionPanel.dataset.state = attentionCount > 0 ? 'attention' : 'clear';
        attentionTitle.textContent = attentionCount > 0 ? 'Necesita tu atención' : 'Todo bajo control';
        attentionSummary.textContent = attentionCount > 0
            ? 'Hay asuntos que requieren revisión.'
            : 'No hay pendientes críticos detectados.';
        overview.querySelector('#ownerDashboardAttention').classList.toggle('hidden', attentionCount <= 0);

                renderOwnerValue(analytics);
        const recentCount = renderRecentActivity(operations, pipeline, timeZone);

        overview.querySelector('#homeCallsToday').textContent = String(numeric(operations.callsToday));
        overview.querySelector('#homeWhatsAppToday').textContent = String(whatsapp);
        overview.querySelector('#homeCustomersToday').textContent = String(numeric(operations.newCustomersToday));
        overview.querySelector('#homeQuestionsToday').textContent = String(numeric(operations.unansweredQuestions));
        overview.querySelector('#homeFailuresToday').textContent = String(numeric(operations.callFailuresToday));
        const seconds = numeric(operations.callDurationSecondsToday);
        overview.querySelector('#homeMinutesToday').textContent =
            String(Math.floor(seconds / 60)) + ':' + String(seconds % 60).padStart(2, '0');
        overview.querySelector('#homeCostToday').textContent = new Intl.NumberFormat('es-CL', {
            minimumFractionDigits: 2, maximumFractionDigits: 4
        }).format(numeric(operations.estimatedCallCostTodayUsd));

        const totalSignal = conversations + bookings + orders + openRequests + unanswered + handoffs + failures;
        if (totalSignal === 0 && recentCount === 0) {
            setDashboardState('empty', 'SIN ACTIVIDAD', 'Aún no hay actividad comercial hoy.');
        } else {
            setDashboardState('ready', 'AL DÍA', '');
        }
    }

    function renderError() {
        renderOwnerValue(null);
        setDashboardState('error', 'NO DISPONIBLE', 'No pudimos actualizar las métricas. Usa “Actualizar estado” para reintentar.');
        const attentionPanel = overview.querySelector('#ownerAttentionPanel');
        attentionPanel.dataset.state = 'error';
        overview.querySelector('#ownerAttentionTitle').textContent = 'Atención no verificada';
        overview.querySelector('#ownerAttentionSummary').textContent = 'No pudimos verificar qué requiere atención.';
        overview.querySelector('#ownerDashboardAttention').classList.add('hidden');
        recent.innerHTML = '<div class="owner-empty" role="listitem">La actividad reciente no está disponible.</div>';
    }

    async function loadOperational(force = false) {
        if (loading || dashboard.classList.contains('hidden') || !isReady() || !sessionStorage.getItem('helvoca_access_token')) return;
        if (!force && Date.now() - lastLoadedAt < 1500) return;
        loading = true;
        overview.querySelector('#ownerAttentionPanel').dataset.state = 'loading';
        overview.querySelector('#ownerAttentionTitle').textContent = 'Revisando prioridades';
        overview.querySelector('#ownerAttentionSummary').textContent = 'Revisando pendientes…';
        overview.querySelector('#ownerDashboardAttention').classList.add('hidden');
        setDashboardState('loading', 'CARGANDO', 'Actualizando actividad confirmada…');
        try {
            let canReadAudit = false;
            try {
                const me = await api('/api/v1/auth/me');
                canReadAudit = Array.isArray(me?.roles) && me.roles.map(String).includes('BUSINESS_ADMIN');
            } catch (_) {
                canReadAudit = false;
            }
            const auditRequest = canReadAudit ? api('/api/v1/audit') : Promise.resolve(null);
            const [operationsResult, pilotResult, pipelineResult, auditResult, analyticsResult] = await Promise.allSettled([
                api('/api/v1/operations/dashboard'),
                api('/api/v1/operations/pilot-metrics'),
                api('/api/v1/commercial/pipeline'),
                auditRequest,
                api('/api/v1/commercial/analytics?days=7')
            ]);
            if (operationsResult.status !== 'fulfilled') throw operationsResult.reason;
            renderOperational(
                operationsResult.value,
                pilotResult.status === 'fulfilled' ? pilotResult.value : null,
                pipelineResult.status === 'fulfilled' ? pipelineResult.value : null,
                auditResult.status === 'fulfilled' ? auditResult.value : null,
                analyticsResult.status === 'fulfilled' ? analyticsResult.value : null
            );
            lastLoadedAt = Date.now();
        } catch (_) {
            renderError();
        } finally {
            loading = false;
        }
    }

    function applyReadyState() {
        const ready = isReady();
        document.body.classList.toggle('operational-ready', ready);
        overview.classList.toggle('hidden', !ready);
        statusGrid.classList.toggle('ux-ready-hidden', ready);
        if (!ready || document.body.classList.contains('settings-page')) return;

        currentBusinessName = window.helvocaBusinessName || currentBusinessName || 'Tu negocio';
        const operationalTitle = 'RecepVoz está atendiendo por ti';
        if (title.textContent !== operationalTitle) title.textContent = operationalTitle;
        if (summary) {
            const operationalSummary = currentBusinessName + ' · Tu recepcionista IA sigue atendiendo mientras tú sigues con el negocio.';
            if (summary.textContent !== operationalSummary) summary.textContent = operationalSummary;
        }
        document.querySelector('#nextStepBanner')?.classList.add('hidden');
        loadOperational();
    }

    const statusObserver = new MutationObserver(() => queueMicrotask(applyReadyState));
    statusObserver.observe(statusGrid, { attributes: true, subtree: true, attributeFilter: ['class'] });
    new MutationObserver(() => {
        if (!dashboard.classList.contains('hidden')) queueMicrotask(applyReadyState);
    }).observe(dashboard, { attributes: true, attributeFilter: ['class'] });
    new MutationObserver(() => {
        if (isReady() && !document.body.classList.contains('settings-page')) {
            const operationalTitle = 'RecepVoz está atendiendo por ti';
            if (title.textContent !== operationalTitle) title.textContent = operationalTitle;
        }
    }).observe(title, { childList: true, characterData: true, subtree: true });

    const workspace = document.querySelector('#homeBusinessWorkspace');
    const workspaceToggle = overview.querySelector('#ownerWorkspaceToggle');
    function syncWorkspaceToggle() {
        if (!workspace || !workspaceToggle) return;
        const collapsed = workspace.classList.contains('owner-collapsed');
        workspaceToggle.setAttribute('aria-expanded', collapsed ? 'false' : 'true');
        workspaceToggle.textContent = collapsed ? 'Ver operación detallada' : 'Ocultar operación detallada';
    }
    workspaceToggle?.addEventListener('click', () => {
        if (!workspace) return;
        workspace.classList.toggle('owner-collapsed');
        syncWorkspaceToggle();
        if (!workspace.classList.contains('owner-collapsed')) {
            workspace.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    });
    if (workspace) {
        new MutationObserver(syncWorkspaceToggle).observe(workspace, { attributes: true, attributeFilter: ['class'] });
    }
    syncWorkspaceToggle();

    document.querySelector('#refreshBtn')?.addEventListener('click', () => loadOperational(true));
    queueMicrotask(applyReadyState);
})();
