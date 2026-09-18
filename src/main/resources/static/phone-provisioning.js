(() => {
    const sideCard = document.querySelector('.side-card');
    const manualForm = document.querySelector('#phoneForm');
    const advancedToggle = document.querySelector('#advancedToggleBtn');
    if (!sideCard || !manualForm) return;

    const style = document.createElement('style');
    style.textContent = `
        .provisioning-panel { margin: 8px 0 10px; padding: 10px; border: 1px solid rgba(124,92,255,.24); border-radius: 11px; background: rgba(124,92,255,.055); }
        .provisioning-panel h3 { margin: 0 0 4px; font-size: 13px; }
        .provisioning-panel p { margin: 0 0 8px; font-size: 11px; line-height: 1.4; }
        .provisioning-status { display: inline-flex; min-height: 26px; margin-bottom: 8px; font-size: 10px; }
        .provisioning-form { display: grid; grid-template-columns: 1fr 1fr; gap: 7px; }
        .provisioning-form label { gap: 4px; font-size: 11px; }
        .provisioning-form input { min-height: 34px; padding: 7px 9px; border-radius: 9px; font-size: 12px; }
        .provisioning-form .button { grid-column: 1 / -1; min-height: 34px; font-size: 11px; }
        .provisioning-results { display: flex; flex-direction: column; gap: 7px; margin-top: 8px; }
        .provisioning-result { display: grid; grid-template-columns: 1fr auto; gap: 8px; align-items: center; padding: 8px; border: 1px solid rgba(255,255,255,.08); border-radius: 9px; background: rgba(0,0,0,.12); }
        .provisioning-result strong, .provisioning-result small { display: block; }
        .provisioning-result small { margin-top: 2px; color: var(--muted); font-size: 10px; line-height: 1.3; }
        .manual-phone-divider { border-top: 1px solid var(--border); padding-top: 12px; margin-top: 12px; }
        @media (max-width: 520px) { .provisioning-form { grid-template-columns: 1fr; } .provisioning-form .button { grid-column: auto; } }
    `;
    document.head.appendChild(style);

    const intro = sideCard.querySelector('p');
    if (intro) intro.textContent = 'Conecta un número o busca uno nuevo.';

    const panel = document.createElement('section');
    panel.className = 'provisioning-panel';
    panel.innerHTML = `
        <h3>Número nuevo con Twilio</h3>
        <p>Buscar no cobra. Aprovisionar requiere confirmación y puede generar cargos.</p>
        <span id="provisioningStatus" class="badge muted provisioning-status">Comprobando disponibilidad</span>
        <form id="provisioningSearchForm" class="provisioning-form">
            <label>País ISO<input name="country" required maxlength="2" value="CL" placeholder="CL"></label>
            <label>Código de área <span class="optional">opcional</span><input name="areaCode" inputmode="numeric" maxlength="8" placeholder="2"></label>
            <button id="provisioningSearchBtn" class="button secondary" type="submit">Buscar números</button>
        </form>
        <div id="provisioningMessage" class="message hidden"></div>
        <div id="provisioningResults" class="provisioning-results"></div>
    `;
    sideCard.insertBefore(panel, manualForm);

    const divider = document.createElement('div');
    divider.className = 'manual-phone-divider';
    divider.innerHTML = '<div class="eyebrow">Número existente</div><p class="muted-text">Conecta un número que ya tengas.</p>';
    sideCard.insertBefore(divider, manualForm);

    const statusBadge = panel.querySelector('#provisioningStatus');
    const searchForm = panel.querySelector('#provisioningSearchForm');
    const searchButton = panel.querySelector('#provisioningSearchBtn');
    const message = panel.querySelector('#provisioningMessage');
    const results = panel.querySelector('#provisioningResults');
    let statusLoaded = false;
    let provisioningAvailable = false;

    function setMessage(text, kind = 'error') {
        message.textContent = text;
        message.classList.remove('hidden', 'error', 'success');
        message.classList.add(kind);
    }

    function clearMessage() {
        message.textContent = '';
        message.classList.add('hidden');
        message.classList.remove('error', 'success');
    }

    async function loadStatus(force = false) {
        if (statusLoaded && !force) return provisioningAvailable;
        try {
            const status = await api('/api/v1/phone-numbers/provisioning/status');
            statusLoaded = true;
            provisioningAvailable = Boolean(status.purchaseAvailable);
            searchButton.disabled = !provisioningAvailable;
            statusBadge.textContent = provisioningAvailable ? 'APROVISIONAMIENTO DISPONIBLE' : 'APROVISIONAMIENTO NO DISPONIBLE';
            statusBadge.className = `badge provisioning-status ${provisioningAvailable ? 'online' : 'muted'}`;
            if (!provisioningAvailable && status.message) setMessage(status.message);
            else clearMessage();
            return provisioningAvailable;
        } catch (error) {
            searchButton.disabled = true;
            statusBadge.textContent = 'NO DISPONIBLE';
            statusBadge.className = 'badge muted provisioning-status';
            if (error.status !== 401) setMessage(error.message || 'No pude comprobar el aprovisionamiento telefónico.');
            return false;
        }
    }

    function renderAvailable(numbers) {
        results.innerHTML = '';
        if (!numbers.length) {
            results.innerHTML = '<span class="muted-text">No encontré números con esos filtros. Prueba otro código de área o país.</span>';
            return;
        }
        numbers.forEach(item => {
            const row = document.createElement('div');
            row.className = 'provisioning-result';
            const info = document.createElement('div');
            const number = document.createElement('strong');
            number.textContent = item.phoneNumber;
            const detail = document.createElement('small');
            const location = [item.locality, item.region, item.isoCountry].filter(Boolean).join(' · ');
            const requirement = item.addressRequirements && item.addressRequirements !== 'none'
                ? ` · Requisito de dirección: ${item.addressRequirements}` : '';
            detail.textContent = `${location || 'Número de voz disponible'}${requirement}`;
            info.append(number, detail);

            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'button small primary';
            button.textContent = 'Aprovisionar';
            button.addEventListener('click', () => provision(item.phoneNumber, button));
            row.append(info, button);
            results.appendChild(row);
        });
    }

    async function provision(phoneNumber, button) {
        const accepted = window.confirm(`Vas a aprovisionar ${phoneNumber} con Twilio. El proveedor puede aplicar cargos a tu cuenta. ¿Confirmas?`);
        if (!accepted) return;
        clearMessage();
        button.disabled = true;
        try {
            await api('/api/v1/phone-numbers/provisioning', {
                method: 'POST',
                body: JSON.stringify({ phoneNumber, confirmed: true })
            });
            results.innerHTML = '';
            if (typeof refreshPhoneState === 'function') await refreshPhoneState();
            setMessage(`${phoneNumber} quedó conectado y activo para este negocio.`, 'success');
        } catch (error) {
            if (error.status !== 401) setMessage(error.message || 'No fue posible aprovisionar ese número.');
        } finally {
            button.disabled = false;
        }
    }

    searchForm.addEventListener('submit', async event => {
        event.preventDefault();
        clearMessage();
        results.innerHTML = '';
        if (!(await loadStatus())) return;
        searchButton.disabled = true;
        const form = new FormData(searchForm);
        const country = String(form.get('country') || '').trim().toUpperCase();
        const areaCode = String(form.get('areaCode') || '').trim();
        const query = new URLSearchParams({ country, limit: '5' });
        if (areaCode) query.set('areaCode', areaCode);
        try {
            const numbers = await api(`/api/v1/phone-numbers/provisioning/available?${query}`);
            renderAvailable(numbers || []);
        } catch (error) {
            if (error.status !== 401) setMessage(error.message || 'No fue posible buscar números disponibles.');
        } finally {
            searchButton.disabled = !provisioningAvailable;
        }
    });

    if (advancedToggle) advancedToggle.addEventListener('click', () => loadStatus());
    if (sessionStorage.getItem('helvoca_access_token')) loadStatus();
})();

(() => {
    const voices = document.createElement('script');
    voices.src = '/voice-selector.js?v=20260917-4';
    voices.async = false;
    document.head.appendChild(voices);

    const ux = document.createElement('script');
    ux.src = '/ux-simplification.js?v=20260917-4';
    ux.async = false;
    document.head.appendChild(ux);
})();

(() => {
    const style = document.createElement('style');
    style.id = 'helvoca-phone-summary-styles';
    style.textContent = `
        #phoneCompactSummary {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 12px;
            min-height: 38px;
            padding: 4px 0 10px;
        }
        #phoneCompactSummary .phone-summary-copy {
            min-width: 0;
            display: flex;
            align-items: center;
            gap: 7px;
            font-size: 12px;
        }
        #phoneCompactSummary .phone-summary-number {
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            font-weight: 800;
        }
        #phoneCompactSummary .phone-summary-state { color: var(--muted); }
        #phoneCompactSummary .phone-summary-state.active { color: var(--success); }
        #phoneCompactSummary .button {
            flex: 0 0 auto;
            min-height: 28px;
            padding: 0 4px;
            border-color: transparent;
            background: transparent;
            color: var(--muted);
            box-shadow: none;
            font-size: 11px;
        }
        #phoneCompactSummary .button:hover { color: var(--text); }
        @media (max-width: 520px) {
            #phoneCompactSummary { align-items: flex-start; }
            #phoneCompactSummary .phone-summary-copy { align-items: flex-start; flex-direction: column; gap: 2px; }
        }
    `;
    document.head.appendChild(style);

    function syncPhoneSummary() {
        const panel = document.querySelector('#configPhonePanel');
        const modes = panel?.querySelector('.ux-phone-modes');
        const existingPanel = panel?.querySelector('#phoneExistingPanel');
        const newPanel = panel?.querySelector('#phoneNewPanel');
        const list = panel?.querySelector('#phoneList');
        if (!panel || !modes || !existingPanel || !newPanel || !list) return;

        const rows = [...list.querySelectorAll('.phone-item')];
        let summary = panel.querySelector('#phoneCompactSummary');

        if (!rows.length) {
            const hadSummary = Boolean(summary);
            summary?.remove();
            modes.classList.remove('hidden');
            if (hadSummary) {
                existingPanel.classList.remove('hidden');
                newPanel.classList.add('hidden');
            }
            return;
        }

        if (!summary) {
            summary = document.createElement('div');
            summary.id = 'phoneCompactSummary';
            summary.dataset.editing = 'false';
            summary.innerHTML = `
                <div class="phone-summary-copy">
                    <span class="phone-summary-number"></span>
                    <span class="phone-summary-state"></span>
                </div>
                <button class="button small ghost" type="button" aria-expanded="false">Cambiar</button>
            `;
            panel.insertBefore(summary, modes);

            const button = summary.querySelector('button');
            button.addEventListener('click', () => {
                const opening = summary.dataset.editing !== 'true';
                summary.dataset.editing = String(opening);
                button.setAttribute('aria-expanded', String(opening));
                button.textContent = opening ? 'Cerrar' : 'Cambiar';

                modes.classList.toggle('hidden', !opening);
                if (opening) {
                    const modeButtons = [...modes.querySelectorAll('button')];
                    modeButtons[0]?.classList.add('active');
                    modeButtons[1]?.classList.remove('active');
                    existingPanel.classList.remove('hidden');
                    newPanel.classList.add('hidden');
                } else {
                    existingPanel.classList.add('hidden');
                    newPanel.classList.add('hidden');
                }
            });
        }

        const row = rows.find(item => item.querySelector('.phone-state.active')) || rows[0];
        const number = row.querySelector('.phone-number')?.textContent?.trim() || 'Número conectado';
        const active = Boolean(row.querySelector('.phone-state.active'));
        const numberNode = summary.querySelector('.phone-summary-number');
        const stateNode = summary.querySelector('.phone-summary-state');
        if (numberNode && numberNode.textContent !== number) numberNode.textContent = number;
        if (stateNode) {
            const state = active ? '· Activo' : '· Inactivo';
            if (stateNode.textContent !== state) stateNode.textContent = state;
            stateNode.classList.toggle('active', active);
        }

        if (summary.dataset.editing !== 'true') {
            modes.classList.add('hidden');
            existingPanel.classList.add('hidden');
            newPanel.classList.add('hidden');
        }
    }

    const observerOptions = { childList: true, subtree: true };
    const observer = new MutationObserver(() => {
        observer.disconnect();
        try {
            syncPhoneSummary();
        } finally {
            observer.observe(document.body, observerOptions);
        }
    });
    observer.observe(document.body, observerOptions);
    syncPhoneSummary();
})();
