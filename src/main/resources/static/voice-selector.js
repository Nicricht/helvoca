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
            gap: 6px !important;
            padding: 0 0 10px !important;
            border-bottom: 0 !important;
            background: transparent !important;
        }
        #advancedPanel .ux-config-nav button {
            flex: 0 0 auto;
            min-height: 30px;
            padding: 6px 10px !important;
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
        #dashboardView .status-card { min-height: 52px !important; }
        #dashboardView .ai-onboarding-card { padding-top: 16px !important; }
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
        toggle.textContent = 'Mostrar ajustes avanzados';
        toggle.setAttribute('aria-expanded', 'false');

        toggle.addEventListener('click', () => {
            const opening = toggle.getAttribute('aria-expanded') !== 'true';
            advancedFields.forEach(node => node.classList.toggle('hidden', !opening));
            toggle.setAttribute('aria-expanded', String(opening));
            toggle.textContent = opening ? 'Ocultar ajustes avanzados' : 'Mostrar ajustes avanzados';
        });

        if (agentToggle) agentToggle.insertAdjacentElement('afterend', toggle);
        else panel.appendChild(toggle);
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

    // The UX layer replaces the original advanced panel with a new shell.
    // Mark that generated shell as already enhanced before the UX observer can
    // attempt a second transformation pass.
    new MutationObserver(() => {
        const hub = document.querySelector('#advancedPanel.ux-config-hub');
        if (hub) {
            hub.dataset.uxEnhanced = 'true';
            cleanBusinessHeadings();
            setupBusinessDisclosure();
        }
    }).observe(document.body, { childList: true, subtree: true });

    cleanBusinessHeadings();
    setupBusinessDisclosure();
    if (sessionStorage.getItem('helvoca_access_token')) load();
})();