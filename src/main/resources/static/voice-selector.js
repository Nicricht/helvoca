(() => {
    const setupForm = document.querySelector('#setupForm');
    const dashboard = document.querySelector('#dashboardView');
    const advancedToggle = document.querySelector('#advancedToggleBtn');
    if (!setupForm || !dashboard || typeof api !== 'function') return;

    const actionStyle = document.createElement('style');
    actionStyle.id = 'helvoca-action-hierarchy-styles';
    actionStyle.textContent = `
        #dashboardView .ai-form .button.primary,
        #advancedPanel .ux-config-save .button.primary,
        #configPhonePanel form .button.primary {
            font-weight: 900 !important;
            box-shadow: 0 8px 22px rgba(124,92,255,.20) !important;
        }

        #dashboardView #refreshBtn,
        #dashboardView #advancedToggleBtn,
        #advancedPanel #addServiceBtn,
        #advancedPanel #addKnowledgeBtn,
        #commercialStatusCard #commercialManageBtn {
            background: transparent !important;
            border-color: transparent !important;
            color: var(--muted) !important;
            box-shadow: none !important;
        }

        #dashboardView #refreshBtn:hover,
        #dashboardView #advancedToggleBtn:hover,
        #advancedPanel #addServiceBtn:hover,
        #advancedPanel #addKnowledgeBtn:hover,
        #commercialStatusCard #commercialManageBtn:hover {
            color: var(--text) !important;
            background: rgba(255,255,255,.035) !important;
        }

        #configBusinessPanel > .section-heading:first-child h2::after,
        #configBusinessPanel > .section-heading.divider h2::after {
            content: none !important;
        }

        #businessAdvancedToggle {
            align-self: flex-start;
            min-height: 30px;
            padding: 0 4px;
            margin-top: -2px;
            background: transparent !important;
            border-color: transparent !important;
            color: var(--muted) !important;
            box-shadow: none !important;
        }
        #businessAdvancedToggle:hover { color: var(--text) !important; }
        #configBusinessPanel > .section-heading { display: none !important; }
        #configBusinessPanel .optional { display: none !important; }
        #configBusinessPanel .agent-toggle { font-size: 0 !important; }
        #configBusinessPanel .agent-toggle::after {
            content: "Agente activo";
            font-size: 12px;
            font-weight: 700;
        }

        .secondary-actions { display: none !important; }
        #advancedPanel.ux-config-hub {
            border: 0 !important;
            border-radius: 0 !important;
            background: transparent !important;
            box-shadow: none !important;
            overflow: visible !important;
        }
        #advancedPanel .ux-config-heading { display: none !important; }
        #advancedPanel .ux-config-nav {
            display: flex !important;
            flex-wrap: wrap;
            gap: 5px !important;
            padding: 0 0 9px !important;
            border-bottom: 0 !important;
            background: transparent !important;
        }
        #advancedPanel .ux-config-nav button {
            flex: 0 0 auto;
            min-height: 28px;
            padding: 5px 8px !important;
            border: 1px solid rgba(255,255,255,.09) !important;
            border-radius: 999px !important;
            text-align: center !important;
            background: transparent !important;
        }
        #advancedPanel .ux-config-nav button:hover {
            background: rgba(255,255,255,.035) !important;
            border-color: rgba(255,255,255,.14) !important;
        }
        #advancedPanel .ux-config-nav button[aria-expanded="true"] {
            background: rgba(124,92,255,.13) !important;
            border-color: rgba(124,92,255,.34) !important;
        }
        #advancedPanel .ux-config-nav button strong { font-size: 11px !important; }
        #advancedPanel .ux-config-nav button small { display: none !important; }
        #advancedPanel .ux-config-body { padding: 0 !important; }
        #advancedPanel .ux-config-panel { padding-top: 10px !important; }

        #dashboardView .dashboard-heading .eyebrow,
        #dashboardView #welcomeText,
        #dashboardView .status-card small,
        #dashboardView .ai-onboarding-card .ai-heading {
            display: none !important;
        }
        #dashboardView .dashboard-heading { margin-bottom: 12px !important; }

        #dashboardView .status-grid {
            display: flex !important;
            flex-wrap: wrap;
            gap: 6px !important;
            margin: 0 0 12px !important;
        }
        #dashboardView .status-card {
            flex: 0 0 auto;
            min-height: 30px !important;
            padding: 5px 9px !important;
            gap: 6px !important;
            border: 0 !important;
            border-radius: 999px !important;
            background: transparent !important;
            box-shadow: none !important;
        }
        #dashboardView .status-card .dot {
            flex: 0 0 7px !important;
            width: 7px !important;
            height: 7px !important;
            box-shadow: none !important;
        }
        #dashboardView .status-card.done .dot { box-shadow: none !important; }
        #dashboardView .status-card strong {
            font-size: 11px !important;
            font-weight: 700 !important;
        }

        #dashboardView .ai-onboarding-card {
            margin: 0 0 12px !important;
            padding: 0 !important;
            border: 0 !important;
            border-radius: 0 !important;
            background: transparent !important;
            box-shadow: none !important;
            overflow: visible !important;
        }
        #dashboardView .ai-onboarding-card::before { display: none !important; }
        #dashboardView .ai-onboarding-card .ai-form {
            margin-top: 0 !important;
            align-items: center !important;
            gap: 8px !important;
        }
        #dashboardView .ai-onboarding-card .ai-form label.grow {
            gap: 0 !important;
            font-size: 0 !important;
        }
        #dashboardView .ai-onboarding-card .ai-form input {
            min-height: 38px !important;
            font-size: 12px !important;
        }
        #dashboardView .ai-onboarding-card .ai-form .button {
            min-height: 38px !important;
            padding: 0 14px !important;
        }

        #commercialStatusCard.ux-commercial-card {
            margin-top: 8px !important;
            padding: 0 !important;
            border: 0 !important;
            border-radius: 0 !important;
            background: transparent !important;
            box-shadow: none !important;
            overflow: visible !important;
        }
        #commercialStatusCard .ux-commercial-summary {
            min-height: 32px;
            padding: 0 !important;
            gap: 10px !important;
        }
        #commercialStatusCard .ux-commercial-summary-copy .eyebrow,
        #commercialStatusCard #uxCommercialMeta {
            display: none !important;
        }
        #commercialStatusCard .ux-commercial-headline {
            margin: 0 !important;
            font-size: 12px !important;
            font-weight: 700 !important;
        }
        #commercialStatusCard #commercialManageBtn {
            min-height: 28px !important;
            padding: 0 4px !important;
            font-size: 11px !important;
        }

        #configHoursPanel > .section-heading { display: none !important; }
        #configHoursPanel #hoursGrid { gap: 2px !important; }
        #configHoursPanel .hour-row {
            grid-template-columns: minmax(105px, 1.1fr) minmax(92px, 1fr) 14px minmax(92px, 1fr) 28px !important;
            align-items: center !important;
            gap: 5px !important;
            padding: 5px 0 !important;
            border: 0 !important;
            border-bottom: 1px solid rgba(255,255,255,.055) !important;
            border-radius: 0 !important;
            background: transparent !important;
        }
        #configHoursPanel .hour-row label {
            gap: 0 !important;
            font-size: 0 !important;
        }
        #configHoursPanel .hour-row input,
        #configHoursPanel .hour-row select {
            min-height: 32px !important;
            font-size: 12px !important;
        }
        #configHoursPanel .hour-row .sep {
            padding-top: 0 !important;
            font-size: 0 !important;
        }
        #configHoursPanel .hour-row .sep::after {
            content: "→";
            font-size: 11px;
            color: var(--muted);
        }
        #configHoursPanel .hour-row .remove-row {
            width: 28px !important;
            height: 32px !important;
        }
    `;
    document.head.appendChild(actionStyle);

    function cleanBusinessHeadings() {
        const panel = document.querySelector('#configBusinessPanel');
        if (!panel) return;
        const headings = panel.querySelectorAll('.section-heading');
        const businessTitle = headings[0]?.querySelector('h2');
        const agentTitle = headings[1]?.querySelector('h2');
        if (businessTitle) businessTitle.textContent = 'Negocio';
        if (agentTitle) agentTitle.textContent = 'Agente';
    }

    function compactBusinessPanel() {
        const panel = document.querySelector('#configBusinessPanel');
        if (!panel) return;

        const businessFields = panel.querySelector('.two-col');
        const businessLabels = businessFields ? [...businessFields.children] : [];
        const phoneLabel = businessLabels[1];
        if (phoneLabel?.childNodes[0] && phoneLabel.childNodes[0].nodeType === Node.TEXT_NODE) {
            phoneLabel.childNodes[0].textContent = 'Teléfono';
        }

        const save = document.querySelector('#advancedPanel .ux-config-save button[type="submit"]');
        if (save && save.textContent !== 'Guardar') save.textContent = 'Guardar';
    }

    function compactConfigNav() {
        const labels = {
            configBusinessPanel: 'Negocio',
            configPermissionsPanel: 'Permisos',
            configServicesPanel: 'Servicios',
            configHoursPanel: 'Horarios',
            configKnowledgePanel: 'FAQ',
            configPhonePanel: 'Teléfono'
        };
        Object.entries(labels).forEach(([panelId, label]) => {
            const node = document.querySelector(`#advancedPanel .ux-config-nav button[aria-controls="${panelId}"] strong`);
            if (node && node.textContent !== label) node.textContent = label;
        });
    }

    function compactPermissionsPanel() {
        const panel = document.querySelector('#configPermissionsPanel');
        const grid = document.querySelector('#agentCapabilities');
        if (!panel || !grid) return;

        const navLabel = document.querySelector('#advancedPanel .ux-config-nav button[aria-controls="configPermissionsPanel"] strong');
        if (navLabel && navLabel.textContent !== 'Permisos') navLabel.textContent = 'Permisos';

        let summary = document.querySelector('#permissionsCompactSummary');
        if (!summary) {
            summary = document.createElement('div');
            summary.id = 'permissionsCompactSummary';
            summary.style.cssText = 'display:flex;align-items:center;justify-content:space-between;gap:10px;min-height:34px;padding:2px 0 8px;';

            const count = document.createElement('span');
            count.id = 'permissionsCompactCount';
            count.style.cssText = 'font-size:12px;color:var(--muted);';

            const toggle = document.createElement('button');
            toggle.id = 'permissionsCompactToggle';
            toggle.type = 'button';
            toggle.className = 'button small ghost';
            toggle.textContent = 'Editar';
            toggle.setAttribute('aria-expanded', 'false');
            toggle.style.cssText = 'min-height:28px;padding:0 4px;background:transparent;border-color:transparent;color:var(--muted);box-shadow:none;';

            toggle.addEventListener('click', () => {
                const opening = grid.classList.contains('hidden');
                grid.classList.toggle('hidden', !opening);
                toggle.setAttribute('aria-expanded', String(opening));
                toggle.textContent = opening ? 'Cerrar' : 'Editar';
            });

            grid.classList.add('hidden');
            grid.addEventListener('change', compactPermissionsPanel);
            summary.append(count, toggle);
            panel.insertBefore(summary, grid);
        }

        const selected = grid.querySelectorAll('input[name="agentCapability"]:checked').length;
        const total = grid.querySelectorAll('input[name="agentCapability"]').length;
        const count = document.querySelector('#permissionsCompactCount');
        if (count) count.textContent = `${selected} de ${total} habilitados`;
    }

    function setupBusinessDisclosure() {
        const panel = document.querySelector('#configBusinessPanel');
        if (!panel || document.querySelector('#businessAdvancedToggle')) return;

        const headings = panel.querySelectorAll('.section-heading');
        const businessFields = panel.querySelector('.two-col');
        const businessLabels = businessFields ? [...businessFields.children] : [];
        const agentHeading = headings[1];
        const agentFields = agentHeading?.nextElementSibling;
        const greeting = setupForm.elements.agentGreeting?.closest('label');
        const instructions = setupForm.elements.agentInstructions?.closest('label');
        const agentToggle = panel.querySelector('.agent-toggle');

        const advancedFields = [
            businessLabels[2],
            businessLabels[3],
            agentHeading,
            agentFields,
            greeting,
            instructions
        ].filter(Boolean);

        advancedFields.forEach(node => node.classList.add('hidden'));

        const toggle = document.createElement('button');
        toggle.id = 'businessAdvancedToggle';
        toggle.type = 'button';
        toggle.className = 'button small ghost';
        toggle.textContent = 'Más ajustes';
        toggle.setAttribute('aria-expanded', 'false');

        toggle.addEventListener('click', () => {
            const opening = toggle.getAttribute('aria-expanded') !== 'true';
            advancedFields.forEach(node => node.classList.toggle('hidden', !opening));
            toggle.setAttribute('aria-expanded', String(opening));
            toggle.textContent = opening ? 'Menos ajustes' : 'Más ajustes';
        });

        if (agentToggle) agentToggle.insertAdjacentElement('afterend', toggle);
        else panel.appendChild(toggle);
    }

    function compactCommercialStatus() {
        const card = document.querySelector('#commercialStatusCard');
        if (!card) return;

        const headline = card.querySelector('#uxCommercialHeadline');
        if (headline) {
            const compact = headline.textContent.replace(/\s+min disponibles\b/, ' min');
            if (headline.textContent !== compact) headline.textContent = compact;
        }

        const manage = card.querySelector('#commercialManageBtn');
        if (manage) {
            const label = manage.getAttribute('aria-expanded') === 'true' ? 'Cerrar' : 'Gestionar';
            if (manage.textContent !== label) manage.textContent = label;
        }
    }

    const original = setupForm.elements.agentVoice;
    if (!original) return;

    const select = document.createElement('select');
    select.name = 'agentVoice';
    select.id = 'agentVoiceSelect';
    select.setAttribute('aria-label', 'Voz del agente');

    const parent = original.parentElement;
    original.replaceWith(select);

    const help = document.createElement('small');
    help.id = 'agentVoiceHelp';
    help.className = 'form-hint';
    help.textContent = 'Voz predeterminada del proveedor.';
    parent?.appendChild(help);

    let profiles = [];
    let loading = false;
    let loaded = false;

    function option(value, label) {
        const item = document.createElement('option');
        item.value = value;
        item.textContent = label;
        return item;
    }

    function findProfile(selection) {
        const normalized = String(selection || '').trim().toLowerCase();
        return profiles.find(profile =>
            String(profile.selection || '').toLowerCase() === normalized ||
            String(profile.code || '').toLowerCase() === normalized);
    }

    function renderOptions(currentVoice = '') {
        select.innerHTML = '';
        select.appendChild(option('', 'Automática'));

        profiles.forEach(profile => {
            select.appendChild(option(profile.selection, profile.name));
        });

        const current = String(currentVoice || '').trim();
        if (current) {
            const profile = findProfile(current);
            if (profile) {
                select.value = profile.selection;
            } else {
                const legacy = option(current, `Actual · ${current}`);
                select.appendChild(legacy);
                select.value = current;
            }
        } else {
            select.value = '';
        }
        updateHelp();
    }

    function updateHelp() {
        if (!select.value) {
            help.textContent = 'Voz predeterminada del proveedor.';
            return;
        }
        const profile = findProfile(select.value);
        help.textContent = profile
            ? profile.description
            : 'Configuración existente; se validará al guardar.';
    }

    async function load(force = false) {
        if (loading || (loaded && !force) || !sessionStorage.getItem('helvoca_access_token')) return;
        loading = true;
        try {
            const [available, agent] = await Promise.all([
                api('/api/v1/ai-agent/voices'),
                api('/api/v1/ai-agent')
            ]);
            profiles = Array.isArray(available) ? available : [];
            renderOptions(agent?.voice || '');
            loaded = true;
        } catch (error) {
            if (error.status !== 401 && error.status !== 403) {
                help.textContent = 'No pude cargar las voces. Se conservará la configuración actual.';
            }
        } finally {
            loading = false;
        }
    }

    select.addEventListener('change', updateHelp);
    advancedToggle?.addEventListener('click', () => load());

    new MutationObserver(() => {
        if (!dashboard.classList.contains('hidden')) load();
    }).observe(dashboard, { attributes: true, attributeFilter: ['class'] });

    // The UX shell is built synchronously before this dynamically loaded script runs.
    // Normalize it once. A global DOM observer here can feed back into the UX observer
    // and keep the browser main thread busy after asynchronous dashboard updates.
    compactConfigNav();
    cleanBusinessHeadings();
    compactBusinessPanel();
    compactPermissionsPanel();
    setupBusinessDisclosure();
    if (sessionStorage.getItem('helvoca_access_token')) load();
})();