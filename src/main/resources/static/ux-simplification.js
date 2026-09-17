(() => {
    const $ = (selector, root = document) => root.querySelector(selector);
    const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
    const setText = (node, value) => {
        if (node && node.textContent !== value) node.textContent = value;
    };

    const style = document.createElement('style');
    style.id = 'helvoca-ux-simplification-styles';
    style.textContent = `
        :root { --ux-surface: rgba(255,255,255,.028); --ux-surface-strong: rgba(255,255,255,.045); }
        .hero-card { min-height: 0 !important; padding: clamp(28px, 5vw, 54px) !important; justify-content: center; }
        .hero-card h1 { max-width: 660px; font-size: clamp(38px, 5vw, 66px) !important; }
        .hero-card p { max-width: 580px; font-size: clamp(15px, 1.4vw, 18px); line-height: 1.6; }
        .hero-card .feature-grid { display: none !important; }
        .auth-card .form-hint { opacity: .72; }

        #dashboardView { max-width: 1180px; margin: 0 auto; }
        .dashboard-heading { align-items: flex-end !important; gap: 24px; margin-bottom: 22px !important; }
        .dashboard-heading h1 { font-size: clamp(34px, 4.5vw, 54px) !important; letter-spacing: -.04em; margin: 6px 0 8px !important; }
        .dashboard-heading p { margin: 0; max-width: 620px; color: var(--muted); }
        #refreshBtn { white-space: nowrap; }
        #readyBanner { display: none !important; }
        #nextStepBanner.hidden { display: none !important; }
        #nextStepBanner { margin: 0 0 18px !important; }

        .status-grid { gap: 10px !important; margin-bottom: 18px !important; }
        .status-card { min-height: 68px; padding: 14px 16px !important; background: var(--ux-surface) !important; }
        .status-card small { margin-top: 2px; }

        .ai-onboarding-card { padding: 22px !important; margin-top: 18px !important; }
        .ai-onboarding-card .ai-heading { margin-bottom: 14px !important; }
        .ai-onboarding-card .ai-heading h2 { font-size: clamp(20px, 2.2vw, 28px) !important; margin: 4px 0 0 !important; }
        .ai-onboarding-card .ai-heading p { display: none !important; }
        .ai-onboarding-card .eyebrow { display: none; }
        .ai-onboarding-card .ai-form { align-items: end; }

        .secondary-actions { margin: 18px 0 10px !important; }
        .secondary-actions > span { display: none !important; }
        #advancedToggleBtn { font-size: 13px; }

        #advancedPanel.ux-config-hub { display: block !important; grid-template-columns: 1fr !important; margin-top: 12px; padding: 0; }
        #advancedPanel.ux-config-hub.hidden { display: block !important; }
        .ux-config-shell { border: 1px solid var(--border); border-radius: 18px; overflow: hidden; background: rgba(10,15,25,.42); }
        .ux-config-heading { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 20px 22px; border-bottom: 1px solid var(--border); }
        .ux-config-heading h2 { margin: 2px 0 0; font-size: 21px; }
        .ux-config-heading p { margin: 5px 0 0; color: var(--muted); font-size: 13px; }
        .ux-config-nav { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; padding: 12px; border-bottom: 1px solid var(--border); background: rgba(255,255,255,.015); }
        .ux-config-nav button { min-width: 0; text-align: left; border: 1px solid transparent; border-radius: 12px; padding: 13px 14px; color: var(--text); background: transparent; cursor: pointer; font: inherit; transition: .16s ease; }
        .ux-config-nav button:hover { background: var(--ux-surface); border-color: var(--border); }
        .ux-config-nav button[aria-expanded="true"] { background: rgba(124,92,255,.12); border-color: rgba(124,92,255,.38); }
        .ux-config-nav strong, .ux-config-nav small { display: block; }
        .ux-config-nav strong { font-size: 13px; }
        .ux-config-nav small { color: var(--muted); margin-top: 3px; font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
        .ux-config-body { padding: 0 22px 22px; }
        #setupForm.ux-config-form { border: 0 !important; border-radius: 0 !important; background: transparent !important; box-shadow: none !important; padding: 0 !important; margin: 0 !important; max-width: none !important; }
        .ux-config-panel { padding-top: 22px; }
        .ux-config-panel.hidden { display: none !important; }
        .ux-config-panel > .section-heading:first-child { margin-top: 0 !important; padding-top: 0 !important; border-top: 0 !important; }
        .ux-config-panel .section-heading h2 { font-size: 18px !important; }
        .ux-config-panel .section-heading p, .ux-config-panel .agent-help { display: none !important; }
        .ux-config-save { display: flex; justify-content: flex-end; gap: 12px; padding-top: 18px; }
        .ux-config-save .button.large { width: auto !important; min-width: 190px; }

        #configServicesPanel .section-heading { align-items: center !important; margin-bottom: 8px !important; }
        #configServicesPanel .section-heading .eyebrow { display: none !important; }
        #configServicesPanel .section-heading h2 { margin: 0 !important; font-size: 15px !important; }
        #configServicesPanel #addServiceBtn { min-height: 30px; padding: 0 9px; font-size: 11px; }
        #configServicesPanel #servicesList { gap: 6px !important; }
        #configServicesPanel .service-row {
            display: grid !important;
            grid-template-columns: minmax(170px, 1.35fr) 86px 105px minmax(180px, 1.55fr) 30px;
            gap: 7px !important;
            align-items: end !important;
            padding: 8px !important;
            border-radius: 10px !important;
        }
        #configServicesPanel .service-row label { min-width: 0; gap: 3px !important; font-size: 10px !important; color: var(--muted); }
        #configServicesPanel .service-row input { min-height: 34px !important; padding: 6px 8px !important; font-size: 12px !important; }
        #configServicesPanel .service-row .remove-row { width: 30px !important; height: 34px !important; font-size: 18px !important; border-color: transparent !important; }

        #configPhonePanel.side-card { width: auto !important; position: static !important; align-self: auto !important; margin: 0 !important; border: 0 !important; border-radius: 0 !important; background: transparent !important; box-shadow: none !important; padding: 16px 0 0 !important; }
        #configPhonePanel > .eyebrow { display: none; }
        #configPhonePanel > h2 { margin: 0 0 8px !important; font-size: 16px !important; }
        #configPhonePanel > p { display: none !important; }
        .ux-phone-modes { display: inline-flex; gap: 4px; padding: 3px; border: 1px solid var(--border); border-radius: 10px; background: rgba(255,255,255,.02); margin-bottom: 10px; }
        .ux-phone-modes button { min-height: 30px; border: 0; border-radius: 8px; padding: 6px 10px; background: transparent; color: var(--muted); font: inherit; font-size: 11px; font-weight: 700; cursor: pointer; }
        .ux-phone-modes button.active { color: var(--text); background: rgba(124,92,255,.16); }
        .ux-phone-path.hidden { display: none !important; }
        .provisioning-panel { margin: 0 !important; }
        .manual-phone-divider { display: none !important; }

        #commercialStatusCard.ux-commercial-card { padding: 0 !important; overflow: hidden; }
        .ux-commercial-summary { display: flex; justify-content: space-between; align-items: center; gap: 18px; padding: 18px 20px; }
        .ux-commercial-summary-copy { min-width: 0; }
        .ux-commercial-summary-copy .eyebrow { margin-bottom: 4px; }
        .ux-commercial-headline { display: block; font-size: 17px; letter-spacing: -.01em; }
        .ux-commercial-meta { display: block; color: var(--muted); margin-top: 3px; font-size: 12px; }
        #commercialDetails { border-top: 1px solid var(--border); }
        #commercialDetails.hidden { display: none !important; }
        #commercialDetails > * { margin-left: 0 !important; margin-right: 0 !important; }
        #commercialDetails .commercial-plan-section { border-top: 0 !important; }

        @media (max-width: 900px) {
            .ux-config-nav { grid-template-columns: repeat(2, minmax(0, 1fr)); }
            .dashboard-heading { align-items: flex-start !important; }
            #configServicesPanel .service-row { grid-template-columns: minmax(150px, 1.2fr) 80px 100px minmax(160px, 1.4fr) 30px; }
        }
        @media (max-width: 760px) {
            #configServicesPanel .service-row { grid-template-columns: 1fr 1fr; }
            #configServicesPanel .service-row > label:first-child,
            #configServicesPanel .service-row > label.grow { grid-column: 1 / -1; }
            #configServicesPanel .service-row .remove-row { grid-column: 2; justify-self: end; }
        }
        @media (max-width: 620px) {
            .ux-config-nav { grid-template-columns: 1fr; }
            .ux-config-body { padding: 0 14px 16px; }
            .ux-config-heading { padding: 17px 16px; }
            .ux-commercial-summary { align-items: flex-start; flex-direction: column; }
            .ux-phone-modes { width: 100%; display: grid; grid-template-columns: 1fr 1fr; }
            .ux-phone-modes button { padding: 7px 8px; }
        }
    `;
    document.head.appendChild(style);

    function simplifyAuth() {
        const hero = $('.hero-card');
        if (!hero || hero.dataset.uxSimplified) return;
        hero.dataset.uxSimplified = 'true';
        const eyebrow = $('.eyebrow', hero);
        const title = $('h1', hero);
        const copy = $('p', hero);
        setText(eyebrow, 'Tu recepcionista con IA');
        setText(title, 'Tu negocio, atendido por IA.');
        setText(copy, 'Pega el enlace de tu negocio y configura Helvoca en minutos.');
    }

    function simplifyDashboardCopy() {
        const heading = $('.dashboard-heading');
        if (!heading) return;
        const eyebrow = $('.eyebrow', heading);
        const title = $('h1', heading);
        const copy = $('#welcomeText', heading);
        setText(eyebrow, 'Tu Helvoca');
        if (title && !title.id) title.id = 'dashboardTitle';
        if (copy && !copy.id.includes('dashboardSummary')) copy.dataset.uxSummary = 'true';
        const refresh = $('#refreshBtn');
        setText(refresh, 'Actualizar');

        const aiCard = $('.ai-onboarding-card');
        if (aiCard && !aiCard.dataset.uxSimplified) {
            aiCard.dataset.uxSimplified = 'true';
            const aiTitle = $('.ai-heading h2', aiCard);
            const aiLabel = $('label.grow', aiCard);
            const aiButton = $('.ai-form .button', aiCard);
            setText(aiTitle, 'Actualizar negocio con IA');
            if (aiLabel) {
                const input = $('input', aiLabel);
                if (aiLabel.childNodes[0]?.textContent !== 'Web, Instagram o Google Maps') {
                    aiLabel.childNodes[0].textContent = 'Web, Instagram o Google Maps';
                }
                if (input) input.placeholder = 'https://tu-negocio.cl';
            }
            setText(aiButton, 'Analizar');
        }
    }

    function dashboardIsReady() {
        const cards = $$('.status-card', $('#statusGrid') || document);
        return cards.length >= 4 && cards.every(card => card.classList.contains('done'));
    }

    function renderDashboardState() {
        const title = $('#dashboardTitle') || $('.dashboard-heading h1');
        const copy = $('#welcomeText');
        const ready = dashboardIsReady();
        if (!title || !copy) return;

        if (ready) {
            setText(title, 'Helvoca está operativa');
            setText(copy, 'Tu negocio está listo para atender clientes.');
            const nextStep = $('#nextStepBanner');
            if (nextStep && !nextStep.classList.contains('hidden')) nextStep.classList.add('hidden');
        } else {
            setText(title, 'Termina de preparar Helvoca');
            setText(copy, 'Completa lo esencial para empezar a atender.');
            const nextStep = $('#nextStepBanner');
            if (nextStep?.classList.contains('hidden')) nextStep.classList.remove('hidden');
        }
        const readyBanner = $('#readyBanner');
        if (readyBanner && !readyBanner.classList.contains('hidden')) readyBanner.classList.add('hidden');

        const readyLabels = {
            businessProfileConfigured: 'Configurado',
            servicesConfigured: 'Configurados',
            scheduleConfigured: 'Configurados',
            phoneConfigured: 'Conectado'
        };
        $$('.status-card').forEach(card => {
            const small = $('small', card);
            if (!small) return;
            setText(small, card.classList.contains('done')
                ? (readyLabels[card.dataset.key] || 'Listo')
                : 'Pendiente');
        });
    }

    function wrapPanel(form, id, nodes) {
        const valid = nodes.filter(Boolean).filter(node => node.parentElement === form);
        if (!valid.length) return null;
        const panel = document.createElement('div');
        panel.id = id;
        panel.className = 'ux-config-panel hidden';
        form.insertBefore(panel, valid[0]);
        valid.forEach(node => panel.appendChild(node));
        return panel;
    }

    function enhanceConfiguration() {
        const advanced = $('#advancedPanel');
        const form = $('#setupForm');
        const sideCard = $('.side-card', advanced || document);
        if (!advanced || !form || !sideCard || advanced.dataset.uxEnhanced) return;
        advanced.dataset.uxEnhanced = 'true';
        advanced.classList.add('ux-config-hub');
        advanced.classList.remove('hidden');
        form.classList.add('ux-config-form');

        const businessHeading = $$('.section-heading', form)[0];
        const businessFields = $('.two-col', form);
        const agentHeading = $$('.section-heading', form)[1];
        const agentFields = agentHeading?.nextElementSibling;
        const greeting = form.elements.agentGreeting?.closest('label');
        const instructions = form.elements.agentInstructions?.closest('label');
        const agentToggle = $('.agent-toggle', form);
        const agentHelp = $('.agent-help', form);
        const capabilities = $('#agentCapabilities', form);
        const serviceHeading = $('#addServiceBtn')?.closest('.section-heading');
        const servicesList = $('#servicesList', form);
        const hoursGrid = $('#hoursGrid', form);
        const hoursHeading = hoursGrid?.previousElementSibling;
        const knowledgeHeading = $('#addKnowledgeBtn')?.closest('.section-heading');
        const knowledgeList = $('#knowledgeList', form);
        const setupMessage = $('#setupMessage', form);
        const submit = $('button[type="submit"]', form);

        const businessPanel = wrapPanel(form, 'configBusinessPanel', [businessHeading, businessFields, agentHeading, agentFields, greeting, instructions, agentToggle]);
        const permissionsPanel = wrapPanel(form, 'configPermissionsPanel', [agentHelp, capabilities]);
        const servicesPanel = wrapPanel(form, 'configServicesPanel', [serviceHeading, servicesList]);
        const hoursPanel = wrapPanel(form, 'configHoursPanel', [hoursHeading, hoursGrid]);
        const knowledgePanel = wrapPanel(form, 'configKnowledgePanel', [knowledgeHeading, knowledgeList]);

        if (serviceHeading) {
            setText($('h2', serviceHeading), 'Servicios');
            setText($('#addServiceBtn', serviceHeading), '+ Agregar');
        }

        const saveArea = document.createElement('div');
        saveArea.className = 'ux-config-save';
        if (setupMessage) saveArea.appendChild(setupMessage);
        if (submit) {
            setText(submit, 'Guardar cambios');
            saveArea.appendChild(submit);
        }
        form.appendChild(saveArea);

        sideCard.id = 'configPhonePanel';
        sideCard.classList.add('ux-config-panel', 'hidden');

        const shell = document.createElement('div');
        shell.className = 'ux-config-shell';
        shell.dataset.uxEnhanced = 'true';
        const heading = document.createElement('div');
        heading.className = 'ux-config-heading';
        heading.innerHTML = '<div><div class="eyebrow">Configuración</div><h2>Ajustes de Helvoca</h2><p>Abre solo lo que quieras cambiar.</p></div>';
        const nav = document.createElement('nav');
        nav.className = 'ux-config-nav';
        nav.setAttribute('aria-label', 'Configuración de Helvoca');
        const body = document.createElement('div');
        body.className = 'ux-config-body';

        advanced.parentElement.insertBefore(shell, advanced);
        shell.append(heading, nav, body);
        body.append(form, sideCard);
        advanced.remove();
        shell.id = 'advancedPanel';
        shell.classList.add('ux-config-hub');

        const items = [
            ['Negocio y agente', 'Identidad y voz', businessPanel],
            ['Permisos del agente', 'Acciones permitidas', permissionsPanel],
            ['Servicios', 'Qué puede ofrecer', servicesPanel],
            ['Horarios', 'Cuándo atiende', hoursPanel],
            ['Preguntas frecuentes', 'Lo que debe saber', knowledgePanel],
            ['Teléfono', 'Número y llamadas', sideCard]
        ];

        const buttons = [];
        function openPanel(target) {
            items.forEach(([, , panel], index) => {
                if (!panel) return;
                const active = panel === target;
                panel.classList.toggle('hidden', !active);
                buttons[index]?.setAttribute('aria-expanded', String(active));
            });
        }

        items.forEach(([label, summary, panel], index) => {
            const button = document.createElement('button');
            button.type = 'button';
            button.setAttribute('aria-expanded', 'false');
            if (panel?.id) button.setAttribute('aria-controls', panel.id);
            button.innerHTML = `<strong>${label}</strong><small data-ux-summary="${index}">${summary}</small>`;
            button.addEventListener('click', () => openPanel(panel));
            nav.appendChild(button);
            buttons.push(button);
        });

        const legacyToggle = $('#advancedToggleBtn');
        if (legacyToggle) {
            setText(legacyToggle, 'Configuración');
            legacyToggle.addEventListener('click', () => {
                setTimeout(() => {
                    shell.classList.remove('hidden');
                    openPanel(businessPanel);
                    shell.scrollIntoView({ behavior: 'smooth', block: 'start' });
                }, 0);
            }, true);
        }

        function updateSummaries() {
            const summaryNodes = $$('[data-ux-summary]', nav);
            const serviceCount = $$('.service-row', servicesList || document).length;
            const hourCount = $$('.hour-row', hoursGrid || document).length;
            const knowledgeCount = $$('.knowledge-row', knowledgeList || document).length;
            const phoneCount = $$('.phone-list .phone-item, .phone-list [data-phone-id], #phoneList > div').length;
            setText(summaryNodes[2], serviceCount ? `${serviceCount} configurado${serviceCount === 1 ? '' : 's'}` : 'Sin servicios');
            setText(summaryNodes[3], hourCount ? `${hourCount} intervalo${hourCount === 1 ? '' : 's'}` : 'Sin horarios');
            setText(summaryNodes[4], knowledgeCount ? `${knowledgeCount} respuesta${knowledgeCount === 1 ? '' : 's'}` : 'Sin respuestas');
            setText(summaryNodes[5], phoneCount ? 'Número conectado' : 'Sin número');
        }
        new MutationObserver(updateSummaries).observe(form, { childList: true, subtree: true });
        new MutationObserver(updateSummaries).observe(sideCard, { childList: true, subtree: true });
        updateSummaries();
        enhancePhonePaths(sideCard);
    }

    function enhancePhonePaths(sideCard = $('#configPhonePanel') || $('.side-card')) {
        if (!sideCard || sideCard.dataset.uxPhoneEnhanced) return;
        const provisioning = $('.provisioning-panel', sideCard);
        const manualForm = $('#phoneForm', sideCard);
        if (!provisioning || !manualForm) return;
        sideCard.dataset.uxPhoneEnhanced = 'true';

        const title = $('h2', sideCard);
        const intro = $('p', sideCard);
        setText(title, 'Teléfono');
        setText(intro, 'Elige cómo quieres conectar las llamadas.');

        const modes = document.createElement('div');
        modes.className = 'ux-phone-modes';
        const existingBtn = document.createElement('button');
        existingBtn.type = 'button';
        existingBtn.textContent = 'Conectar mi número';
        existingBtn.className = 'active';
        const newBtn = document.createElement('button');
        newBtn.type = 'button';
        newBtn.textContent = 'Buscar un número nuevo';
        modes.append(existingBtn, newBtn);

        const existingPanel = document.createElement('div');
        existingPanel.id = 'phoneExistingPanel';
        existingPanel.className = 'ux-phone-path';
        const newPanel = document.createElement('div');
        newPanel.id = 'phoneNewPanel';
        newPanel.className = 'ux-phone-path hidden';

        const divider = $('.manual-phone-divider', sideCard);
        const phoneMessage = $('#phoneMessage', sideCard);
        const phoneList = $('.phone-list-wrap', sideCard);
        if (divider) existingPanel.appendChild(divider);
        existingPanel.appendChild(manualForm);
        if (phoneMessage) existingPanel.appendChild(phoneMessage);
        if (phoneList) existingPanel.appendChild(phoneList);
        newPanel.appendChild(provisioning);

        const anchor = intro || title;
        anchor?.insertAdjacentElement('afterend', modes);
        modes.insertAdjacentElement('afterend', existingPanel);
        existingPanel.insertAdjacentElement('afterend', newPanel);

        function select(mode) {
            const existing = mode === 'existing';
            existingBtn.classList.toggle('active', existing);
            newBtn.classList.toggle('active', !existing);
            existingPanel.classList.toggle('hidden', !existing);
            newPanel.classList.toggle('hidden', existing);
        }
        existingBtn.addEventListener('click', () => select('existing'));
        newBtn.addEventListener('click', () => select('new'));
        select('existing');
    }

    async function enhanceCommercial() {
        const card = $('#commercialStatusCard');
        if (!card || card.dataset.uxEnhanced) return;
        card.dataset.uxEnhanced = 'true';
        card.classList.add('ux-commercial-card');

        const originalChildren = [...card.children];
        const summary = document.createElement('div');
        summary.className = 'ux-commercial-summary';
        summary.innerHTML = `
            <div class="ux-commercial-summary-copy">
                <div class="eyebrow">Suscripción</div>
                <strong id="uxCommercialHeadline" class="ux-commercial-headline">Cargando plan…</strong>
                <small id="uxCommercialMeta" class="ux-commercial-meta">Uso y facturación</small>
            </div>
            <button id="commercialManageBtn" class="button small ghost" type="button" aria-expanded="false">Gestionar plan</button>
        `;
        const details = document.createElement('div');
        details.id = 'commercialDetails';
        details.className = 'hidden';
        originalChildren.forEach(child => details.appendChild(child));
        card.append(summary, details);

        const manage = $('#commercialManageBtn', card);
        manage?.addEventListener('click', () => {
            const opening = details.classList.contains('hidden');
            details.classList.toggle('hidden', !opening);
            manage.setAttribute('aria-expanded', String(opening));
            setText(manage, opening ? 'Ocultar planes' : 'Gestionar plan');
        });

        try {
            if (typeof api === 'function' && sessionStorage.getItem('helvoca_access_token')) {
                const [subscription, billing] = await Promise.all([
                    api('/api/v1/subscription'),
                    api('/api/v1/billing/status')
                ]);
                const included = Number(subscription?.includedMinutes || 0);
                const used = Number(subscription?.usedMinutes || 0);
                const remaining = Math.max(0, included - used);
                const plan = billing?.currentPlanName || subscription?.plan || 'Plan actual';
                const headline = $('#uxCommercialHeadline', card);
                const meta = $('#uxCommercialMeta', card);
                setText(headline, `${plan} · ${remaining} min disponibles`);
                setText(meta, subscription?.serviceAllowed === false ? 'Servicio requiere atención' : 'Servicio activo');
            }
        } catch (_) {
            const headline = $('#uxCommercialHeadline', card);
            setText(headline, 'Plan actual');
        }
    }

    function runEnhancements() {
        simplifyAuth();
        simplifyDashboardCopy();
        enhanceConfiguration();
        enhancePhonePaths();
        enhanceCommercial();
        renderDashboardState();
    }

    const observerOptions = { childList: true, subtree: true, attributes: true, attributeFilter: ['class'] };
    let enhancementFrame = null;
    const observer = new MutationObserver(() => {
        if (enhancementFrame !== null) return;
        enhancementFrame = window.requestAnimationFrame(() => {
            enhancementFrame = null;
            observer.disconnect();
            try {
                runEnhancements();
            } finally {
                observer.observe(document.body, observerOptions);
            }
        });
    });
    observer.observe(document.body, observerOptions);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', runEnhancements, { once: true });
    } else {
        runEnhancements();
    }
})();