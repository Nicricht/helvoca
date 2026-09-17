(() => {
    const setupForm = document.querySelector('#setupForm');
    const dashboard = document.querySelector('#dashboardView');
    const advancedToggle = document.querySelector('#advancedToggleBtn');
    if (!setupForm || !dashboard || typeof api !== 'function') return;

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
        if (hub) hub.dataset.uxEnhanced = 'true';
    }).observe(document.body, { childList: true, subtree: true });

    if (sessionStorage.getItem('helvoca_access_token')) load();
})();