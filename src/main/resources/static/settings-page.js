(() => {
  const dashboard = document.querySelector("#dashboardView");
  const advanced = document.querySelector("#advancedPanel");
  if (!dashboard || !advanced) return;

  const style = document.createElement("style");
  style.textContent = `
    body.settings-page #statusGrid,
    body.settings-page #readyBanner,
    body.settings-page #nextStepBanner,
    body.settings-page #operationalOverview,
    body.settings-page #homeBusinessWorkspace,
    body.settings-page .secondary-actions { display: none !important; }
    body.settings-page #advancedPanel { display: block !important; }
    body.settings-page .dashboard-heading { margin-bottom: 16px !important; }
    body.settings-page .dashboard-heading h1 { font-size: clamp(32px, 4vw, 48px) !important; }
    body.settings-page .ai-onboarding-card { margin-bottom: 14px !important; }
  `;
  document.head.appendChild(style);

  function apply() {
    if (dashboard.classList.contains("hidden")) return;
    document.body.classList.remove("operational-ready");
    advanced.classList.remove("hidden");
    const heading = dashboard.querySelector(".dashboard-heading");
    const eyebrow = heading?.querySelector(".eyebrow");
    const title = heading?.querySelector("h1");
    const welcome = document.querySelector("#welcomeText");
    if (eyebrow) eyebrow.textContent = "Configuración";
    const businessName = window.helvocaBusinessName || document.querySelector('#setupForm [name="businessName"]')?.value?.trim() || "Tu negocio";
    if (title) title.textContent = "Mi negocio";
    if (welcome) welcome.textContent = businessName;
  }

  new MutationObserver(apply).observe(dashboard, { attributes: true, attributeFilter: ["class"] });
  new MutationObserver(() => {
    if (advanced.isConnected && advanced.classList.contains("hidden")) {
      advanced.classList.remove("hidden");
    }
  }).observe(advanced, { attributes: true, attributeFilter: ["class"] });
  queueMicrotask(apply);
})();


(() => {
  const dashboard = document.querySelector("#dashboardView");
  if (!dashboard) return;

  const style = document.createElement("style");
  style.textContent = `
    #metaWhatsAppConnect {
      margin-top: 14px;
      padding-top: 14px;
      border-top: 1px solid rgba(255,255,255,.07);
    }
    #metaWhatsAppConnect .meta-whatsapp-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
    }
    #metaWhatsAppConnect .meta-whatsapp-copy { min-width: 0; }
    #metaWhatsAppConnect .meta-whatsapp-copy strong { display: block; font-size: 13px; }
    #metaWhatsAppConnect .meta-whatsapp-copy span {
      display: block;
      margin-top: 2px;
      color: var(--muted);
      font-size: 11px;
      line-height: 1.45;
    }
    #metaWhatsAppConnectMessage {
      margin-top: 8px;
      color: var(--muted);
      font-size: 11px;
      line-height: 1.45;
    }
    #metaWhatsAppWabaCandidates {
      display: grid;
      gap: 8px;
      margin-top: 10px;
    }
    #metaWhatsAppWabaCandidates.hidden { display: none; }
    #metaWhatsAppWabaCandidates .meta-whatsapp-waba-title {
      color: var(--text);
      font-size: 12px;
      font-weight: 700;
    }
    #metaWhatsAppWabaCandidates .meta-whatsapp-waba-card {
      padding: 10px 12px;
      border: 1px solid rgba(255,255,255,.08);
      border-radius: 10px;
      background: rgba(255,255,255,.025);
    }
    #metaWhatsAppWabaCandidates .meta-whatsapp-waba-card strong {
      display: block;
      font-size: 12px;
    }
    #metaWhatsAppWabaCandidates .meta-whatsapp-waba-card span {
      display: block;
      margin-top: 3px;
      color: var(--muted);
      font-size: 11px;
      line-height: 1.4;
    }
    #metaWhatsAppWabaCandidates .meta-whatsapp-waba-card.selected {
      border-color: rgba(90, 180, 255, .55);
      background: rgba(90, 180, 255, .08);
      box-shadow: 0 0 0 1px rgba(90, 180, 255, .12);
    }
    #metaWhatsAppWabaCandidates .meta-whatsapp-waba-select {
      margin-top: 9px;
    }
    #metaWhatsAppWabaConfirm {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-top: 10px;
    }
    #metaWhatsAppWabaConfirm.hidden { display: none; }
    #metaWhatsAppWabaConfirmStatus {
      color: var(--muted);
      font-size: 11px;
      line-height: 1.4;
    }
    #metaWhatsAppPhoneCandidates {
      display: grid;
      gap: 8px;
      margin-top: 10px;
    }
    #metaWhatsAppPhoneCandidates.hidden { display: none; }
    #metaWhatsAppPhoneCandidates .meta-whatsapp-phone-title {
      color: var(--text);
      font-size: 12px;
      font-weight: 700;
    }
    #metaWhatsAppPhoneCandidates .meta-whatsapp-phone-card {
      padding: 10px 12px;
      border: 1px solid rgba(255,255,255,.08);
      border-radius: 10px;
      background: rgba(255,255,255,.025);
    }
    #metaWhatsAppPhoneCandidates .meta-whatsapp-phone-card strong {
      display: block;
      font-size: 12px;
    }
    #metaWhatsAppPhoneCandidates .meta-whatsapp-phone-card span {
      display: block;
      margin-top: 3px;
      color: var(--muted);
      font-size: 11px;
      line-height: 1.4;
    }
    #metaWhatsAppPhoneCandidates .meta-whatsapp-phone-card.selected {
      border-color: rgba(90, 180, 255, .55);
      background: rgba(90, 180, 255, .08);
      box-shadow: 0 0 0 1px rgba(90, 180, 255, .12);
    }
    #metaWhatsAppPhoneCandidates .meta-whatsapp-phone-select {
      margin-top: 9px;
    }
    #metaWhatsAppPhoneConfirm {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-top: 10px;
    }
    #metaWhatsAppPhoneConfirm.hidden { display: none; }
    #metaWhatsAppPhoneConfirmStatus {
      color: var(--muted);
      font-size: 11px;
      line-height: 1.4;
    }
    #metaWhatsAppPinSetup {
      display: grid;
      gap: 6px;
      margin-top: 10px;
      max-width: 320px;
    }
    #metaWhatsAppPinSetup.hidden { display: none; }
    #metaWhatsAppPinSetup label {
      color: var(--text);
      font-size: 12px;
      font-weight: 700;
    }
    #metaWhatsAppPinInput {
      width: 100%;
      box-sizing: border-box;
    }
    #metaWhatsAppPinHelp,
    #metaWhatsAppPinStatus {
      color: var(--muted);
      font-size: 11px;
      line-height: 1.4;
    }
    #metaWhatsAppPreparedState {
      margin-top: 10px;
      padding: 10px 12px;
      border: 1px solid rgba(90, 180, 255, .35);
      border-radius: 10px;
      background: rgba(90, 180, 255, .07);
    }
    #metaWhatsAppPreparedState.hidden { display: none; }
    #metaWhatsAppPreparedState strong {
      display: block;
      font-size: 12px;
    }
    #metaWhatsAppPreparedState span {
      display: block;
      margin-top: 3px;
      color: var(--muted);
      font-size: 11px;
      line-height: 1.4;
    }
    #metaWhatsAppCertificationState {
      margin-top: 8px;
      padding: 10px 12px;
      border: 1px solid rgba(255,255,255,.08);
      border-radius: 10px;
      background: rgba(255,255,255,.025);
    }
    #metaWhatsAppCertificationState.hidden { display: none; }
    #metaWhatsAppCertificationState strong {
      display: block;
      font-size: 12px;
    }
    #metaWhatsAppCertificationState span {
      display: block;
      margin-top: 3px;
      color: var(--muted);
      font-size: 11px;
      line-height: 1.4;
    }
    #metaWhatsAppDeploymentState {
      margin-top: 8px;
      padding: 10px 12px;
      border: 1px solid rgba(255,255,255,.08);
      border-radius: 10px;
      background: rgba(255,255,255,.025);
    }
    #metaWhatsAppDeploymentState.hidden { display: none; }
    #metaWhatsAppDeploymentState strong {
      display: block;
      font-size: 12px;
    }
    #metaWhatsAppDeploymentState span {
      display: block;
      margin-top: 3px;
      color: var(--muted);
      font-size: 11px;
      line-height: 1.4;
    }
    #metaWhatsAppActivationGate {
      margin-top: 8px;
      padding: 10px 12px;
      border: 1px solid rgba(255,255,255,.08);
      border-radius: 10px;
      background: rgba(255,255,255,.025);
    }
    #metaWhatsAppActivationGate.hidden { display: none; }
    #metaWhatsAppActivationGate strong {
      display: block;
      font-size: 12px;
    }
    #metaWhatsAppActivationGate span {
      display: block;
      margin-top: 3px;
      color: var(--muted);
      font-size: 11px;
      line-height: 1.4;
    }
    #metaWhatsAppActivateBtn {
      margin-top: 10px;
    }
    #metaWhatsAppActivateBtn.hidden { display: none; }
    @media (max-width: 520px) {
      #metaWhatsAppConnect .meta-whatsapp-row {
        align-items: stretch;
        flex-direction: column;
      }
    }
  `;
  document.head.appendChild(style);

  let requested = false;
  let facebookSdkPromise = null;
  let selectedPhoneDiscovery = null;
  let selectedPhoneValidation = null;
  let selectedPhoneFinalization = null;

  function initFacebookSdk(bootstrap) {
    if (!window.FB?.init) throw new Error("Facebook SDK unavailable");
    window.FB.init({
      appId: bootstrap.appId,
      xfbml: false,
      version: bootstrap.graphApiVersion
    });
  }

  function loadFacebookSdk(bootstrap) {
    if (window.FB?.init) {
      initFacebookSdk(bootstrap);
      return Promise.resolve();
    }
    if (facebookSdkPromise) return facebookSdkPromise;

    facebookSdkPromise = new Promise((resolve, reject) => {
      const timeout = window.setTimeout(() => {
        facebookSdkPromise = null;
        reject(new Error("Facebook SDK load timed out"));
      }, 10000);

      const previousAsyncInit = window.fbAsyncInit;
      window.fbAsyncInit = () => {
        try {
          if (typeof previousAsyncInit === "function") previousAsyncInit();
          initFacebookSdk(bootstrap);
          window.clearTimeout(timeout);
          resolve();
        } catch (error) {
          window.clearTimeout(timeout);
          facebookSdkPromise = null;
          reject(error);
        }
      };

      let script = document.querySelector("#facebook-jssdk");
      if (!script) {
        script = document.createElement("script");
        script.id = "facebook-jssdk";
        script.async = true;
        script.defer = true;
        script.crossOrigin = "anonymous";
        script.src = "https://connect.facebook.net/en_US/sdk.js";
        script.onerror = () => {
          window.clearTimeout(timeout);
          facebookSdkPromise = null;
          reject(new Error("Facebook SDK failed to load"));
        };
        document.head.appendChild(script);
      }
    });

    return facebookSdkPromise;
  }

  function renderWabaCandidates(handoff, container) {
    if (!container) return 0;
    container.replaceChildren();

    const wabas = Array.isArray(handoff?.wabas) ? handoff.wabas : [];
    if (!wabas.length) {
      container.classList.add("hidden");
      return 0;
    }

    const title = document.createElement("div");
    title.className = "meta-whatsapp-waba-title";
    title.textContent = "Cuentas de WhatsApp Business disponibles";
    container.appendChild(title);

    wabas.forEach((waba, index) => {
      const card = document.createElement("div");
      card.className = "meta-whatsapp-waba-card";
      card.dataset.selected = "false";
      if (waba?.id) card.dataset.wabaId = String(waba.id);

      const name = document.createElement("strong");
      name.textContent = String(waba?.name || "").trim() || `Cuenta de WhatsApp Business ${index + 1}`;

      const details = document.createElement("span");
      const detailParts = [];
      if (waba?.id) detailParts.push(`ID ${waba.id}`);
      if (waba?.currency) detailParts.push(String(waba.currency));
      if (waba?.timezoneId) detailParts.push(String(waba.timezoneId));
      details.textContent = detailParts.join(" · ");

      const access = document.createElement("span");
      access.textContent = waba?.systemUserAssigned
        ? "Acceso técnico de RecepVoz listo"
        : "Acceso técnico pendiente de asignación";

      const selectButton = document.createElement("button");
      selectButton.type = "button";
      selectButton.className = "button secondary meta-whatsapp-waba-select";
      selectButton.textContent = "Seleccionar";
      selectButton.setAttribute("aria-pressed", "false");
      if (!waba?.id) {
        selectButton.disabled = true;
      } else {
        selectButton.addEventListener("click", () => {
          const selectedWabaId = String(waba.id);
          selectedPhoneDiscovery = null;
          container.querySelectorAll(".meta-whatsapp-waba-card").forEach(candidate => {
            const selected = candidate.dataset.wabaId === selectedWabaId;
            candidate.classList.toggle("selected", selected);
            candidate.dataset.selected = selected ? "true" : "false";
            const candidateButton = candidate.querySelector(".meta-whatsapp-waba-select");
            if (candidateButton) {
              candidateButton.setAttribute("aria-pressed", selected ? "true" : "false");
              candidateButton.textContent = selected ? "Seleccionado" : "Seleccionar";
            }
          });
          container.dispatchEvent(new CustomEvent("meta-waba-selected", {
            detail: { wabaId: selectedWabaId }
          }));
        });
      }

      card.append(name, details, access, selectButton);
      container.appendChild(card);
    });

    if (handoff?.wabaAfterCursor) {
      const note = document.createElement("span");
      note.textContent = "Meta indica que existen más cuentas. Esta vista muestra la primera página.";
      container.appendChild(note);
    }

    container.classList.remove("hidden");
    return wabas.length;
  }

  function renderPhoneCandidates(discovery, container) {
    if (!container) return 0;
    container.replaceChildren();

    const phoneNumbers = Array.isArray(discovery?.phoneNumbers) ? discovery.phoneNumbers : [];
    if (!phoneNumbers.length) {
      container.classList.add("hidden");
      return 0;
    }

    const title = document.createElement("div");
    title.className = "meta-whatsapp-phone-title";
    title.textContent = "Números de WhatsApp Business disponibles";
    container.appendChild(title);

    phoneNumbers.forEach((phone, index) => {
      const card = document.createElement("div");
      card.className = "meta-whatsapp-phone-card";
      card.dataset.selected = "false";
      if (phone?.id) card.dataset.phoneNumberId = String(phone.id);

      const number = document.createElement("strong");
      number.textContent = String(phone?.displayPhoneNumber || "").trim() || `Número de WhatsApp ${index + 1}`;

      const name = document.createElement("span");
      name.textContent = String(phone?.verifiedName || "").trim() || "Nombre verificado no disponible";

      const details = document.createElement("span");
      const detailParts = [];
      if (phone?.id) detailParts.push(`ID ${phone.id}`);
      if (phone?.qualityRating) detailParts.push(`Calidad ${phone.qualityRating}`);
      if (phone?.codeVerificationStatus) detailParts.push(`Verificación ${phone.codeVerificationStatus}`);
      details.textContent = detailParts.join(" · ");

      const selectButton = document.createElement("button");
      selectButton.type = "button";
      selectButton.className = "button secondary meta-whatsapp-phone-select";
      selectButton.textContent = "Seleccionar";
      selectButton.setAttribute("aria-pressed", "false");
      if (!phone?.id) {
        selectButton.disabled = true;
      } else {
        selectButton.addEventListener("click", () => {
          const selectedPhoneNumberId = String(phone.id);
          container.querySelectorAll(".meta-whatsapp-phone-card").forEach(candidate => {
            const selected = candidate.dataset.phoneNumberId === selectedPhoneNumberId;
            candidate.classList.toggle("selected", selected);
            candidate.dataset.selected = selected ? "true" : "false";
            const candidateButton = candidate.querySelector(".meta-whatsapp-phone-select");
            if (candidateButton) {
              candidateButton.setAttribute("aria-pressed", selected ? "true" : "false");
              candidateButton.textContent = selected ? "Seleccionado" : "Seleccionar";
            }
          });
          container.dispatchEvent(new CustomEvent("meta-phone-selected", {
            detail: { phoneNumberId: selectedPhoneNumberId }
          }));
        });
      }

      card.append(number, name, details, selectButton);
      container.appendChild(card);
    });

    if (discovery?.afterCursor) {
      const note = document.createElement("span");
      note.textContent = "Meta indica que existen más números. Esta vista muestra la primera página.";
      container.appendChild(note);
    }

    container.classList.remove("hidden");
    return phoneNumbers.length;
  }

  function renderConnectButton(bootstrap) {
    if (!bootstrap?.available) return;
    const panel = document.querySelector("#configPhonePanel");
    if (!panel || panel.querySelector("#metaWhatsAppConnect")) return;

    const section = document.createElement("section");
    section.id = "metaWhatsAppConnect";
    section.innerHTML = `
      <div class="meta-whatsapp-row">
        <div class="meta-whatsapp-copy">
          <strong>WhatsApp</strong>
          <span>Conecta el WhatsApp Business de tu negocio con Meta.</span>
        </div>
        <button id="metaWhatsAppConnectBtn" class="button secondary" type="button">Conectar WhatsApp</button>
      </div>
      <div id="metaWhatsAppConnectMessage" class="hidden" role="status"></div>
      <div id="metaWhatsAppWabaCandidates" class="hidden" aria-live="polite"></div>
      <div id="metaWhatsAppWabaConfirm" class="hidden">
        <button id="metaWhatsAppWabaConfirmBtn" class="button secondary" type="button">Continuar con esta cuenta</button>
        <span id="metaWhatsAppWabaConfirmStatus" role="status"></span>
      </div>
      <div id="metaWhatsAppPhoneCandidates" class="hidden" aria-live="polite"></div>
      <div id="metaWhatsAppPhoneConfirm" class="hidden">
        <button id="metaWhatsAppPhoneConfirmBtn" class="button secondary" type="button">Continuar con este número</button>
        <span id="metaWhatsAppPhoneConfirmStatus" role="status"></span>
      </div>
      <div id="metaWhatsAppPinSetup" class="hidden">
        <label for="metaWhatsAppPinInput">PIN de Meta</label>
        <input
          id="metaWhatsAppPinInput"
          type="password"
          inputmode="numeric"
          pattern="[0-9]{6}"
          maxlength="6"
          autocomplete="off"
          spellcheck="false"
          aria-describedby="metaWhatsAppPinHelp metaWhatsAppPinStatus"
        />
        <span id="metaWhatsAppPinHelp">Ingresa exactamente 6 dígitos. El PIN se usa solo para registrar el número y guardar la configuración desactivada; luego se elimina del formulario.</span>
        <button id="metaWhatsAppFinalizePhoneBtn" class="button secondary" type="button" disabled>Finalizar configuración</button>
        <span id="metaWhatsAppPinStatus" role="status"></span>
      </div>
      <div id="metaWhatsAppPreparedState" class="hidden" role="status">
        <strong>WhatsApp preparado</strong>
        <span>Configuración guardada y desactivada. Todavía no se ha activado el tráfico real.</span>
      </div>
      <div id="metaWhatsAppCertificationState" class="hidden" role="status">
        <strong></strong>
        <span></span>
      </div>
      <div id="metaWhatsAppDeploymentState" class="hidden" role="status">
        <strong></strong>
        <span></span>
      </div>
      <div id="metaWhatsAppActivationGate" class="hidden" role="status">
        <strong></strong>
        <span></span>
        <button id="metaWhatsAppActivateBtn" class="button secondary hidden" type="button">Activar WhatsApp</button>
        <span id="metaWhatsAppActivationStatus" role="status"></span>
      </div>
    `;
    panel.appendChild(section);

    const button = section.querySelector("#metaWhatsAppConnectBtn");
    const message = section.querySelector("#metaWhatsAppConnectMessage");
    const wabaCandidates = section.querySelector("#metaWhatsAppWabaCandidates");
    const wabaConfirm = section.querySelector("#metaWhatsAppWabaConfirm");
    const wabaConfirmButton = section.querySelector("#metaWhatsAppWabaConfirmBtn");
    const wabaConfirmStatus = section.querySelector("#metaWhatsAppWabaConfirmStatus");
    const phoneCandidates = section.querySelector("#metaWhatsAppPhoneCandidates");
    const phoneConfirm = section.querySelector("#metaWhatsAppPhoneConfirm");
    const phoneConfirmButton = section.querySelector("#metaWhatsAppPhoneConfirmBtn");
    const phoneConfirmStatus = section.querySelector("#metaWhatsAppPhoneConfirmStatus");
    const pinSetup = section.querySelector("#metaWhatsAppPinSetup");
    const pinInput = section.querySelector("#metaWhatsAppPinInput");
    const finalizePhoneButton = section.querySelector("#metaWhatsAppFinalizePhoneBtn");
    const pinStatus = section.querySelector("#metaWhatsAppPinStatus");
    const preparedState = section.querySelector("#metaWhatsAppPreparedState");
    const certificationState = section.querySelector("#metaWhatsAppCertificationState");
    const certificationTitle = certificationState?.querySelector("strong");
    const certificationCopy = certificationState?.querySelector("span");
    const deploymentState = section.querySelector("#metaWhatsAppDeploymentState");
    const deploymentTitle = deploymentState?.querySelector("strong");
    const deploymentCopy = deploymentState?.querySelector("span");
    const activationGate = section.querySelector("#metaWhatsAppActivationGate");
    const activationGateTitle = activationGate?.querySelector("strong");
    const activationGateCopy = activationGate?.querySelector("span");
    const activationButton = section.querySelector("#metaWhatsAppActivateBtn");
    const activationStatus = section.querySelector("#metaWhatsAppActivationStatus");
    let onboardingInteractionStarted = false;

    function renderCertificationReadiness(readiness) {
      if (!certificationState || !certificationTitle || !certificationCopy) return;
      const blockers = Array.isArray(readiness?.blockers) ? readiness.blockers : [];
      if (readiness?.alreadyCertified === true && readiness?.ready === true) {
        certificationTitle.textContent = "WhatsApp certificado";
        certificationCopy.textContent = "La configuración ya superó la certificación técnica.";
      } else if (readiness?.ready === true) {
        certificationTitle.textContent = "Listo para certificación piloto";
        certificationCopy.textContent = "No hay bloqueos técnicos pendientes para iniciar la certificación.";
      } else {
        certificationTitle.textContent = "Certificación pendiente";
        certificationCopy.textContent = blockers.length
          ? `Hay ${blockers.length} bloqueo${blockers.length === 1 ? "" : "s"} técnico${blockers.length === 1 ? "" : "s"} pendiente${blockers.length === 1 ? "" : "s"}.`
          : "La certificación todavía no está lista.";
      }
      certificationState.classList.remove("hidden");
    }

    async function loadCertificationReadiness() {
      try {
        const readiness = await api("/api/v1/channels/whatsapp/meta/certification/readiness");
        renderCertificationReadiness(readiness);
        return readiness;
      } catch (error) {
        if (certificationTitle) certificationTitle.textContent = "Certificación pendiente";
        if (certificationCopy) certificationCopy.textContent = "No fue posible verificar la readiness técnica.";
        certificationState?.classList.remove("hidden");
        return null;
      }
    }

    function renderDeploymentReadiness(readiness) {
      if (!deploymentState || !deploymentTitle || !deploymentCopy) return;
      const blockers = Array.isArray(readiness?.blockers) ? readiness.blockers : [];
      if (readiness?.state === "READY_FOR_TENANT_STAGING"
          && readiness?.readyForTenantStaging === true) {
        deploymentTitle.textContent = "Infraestructura lista para staging";
        deploymentCopy.textContent = "Las compuertas de tráfico real siguen apagadas, como exige el staging seguro.";
      } else {
        deploymentTitle.textContent = "Staging técnico bloqueado";
        deploymentCopy.textContent = blockers.length
          ? `Hay ${blockers.length} bloqueo${blockers.length === 1 ? "" : "s"} de infraestructura pendiente${blockers.length === 1 ? "" : "s"}.`
          : "La infraestructura todavía no cumple las condiciones de staging seguro.";
      }
      deploymentState.classList.remove("hidden");
    }

    async function loadDeploymentReadiness() {
      try {
        const readiness = await api("/api/v1/channels/whatsapp/meta/deployment/readiness");
        renderDeploymentReadiness(readiness);
        return readiness;
      } catch (error) {
        if (deploymentTitle) deploymentTitle.textContent = "Staging técnico pendiente";
        if (deploymentCopy) deploymentCopy.textContent = "No fue posible verificar la infraestructura de staging.";
        deploymentState?.classList.remove("hidden");
        return null;
      }
    }

    function renderActivationGate(certification, deployment, tenantStatus) {
      if (!activationGate || !activationGateTitle || !activationGateCopy) return;
      const configuredDisabled = tenantStatus?.status === "CONFIGURED_DISABLED"
        && tenantStatus?.configured === true
        && tenantStatus?.enabled === false;
      const configuredEnabled = tenantStatus?.status === "CONFIGURED_ENABLED"
        && tenantStatus?.configured === true
        && tenantStatus?.enabled === true;
      const certified = certification?.ready === true && certification?.alreadyCertified === true;
      const stagingReady = deployment?.state === "READY_FOR_TENANT_STAGING"
        && deployment?.readyForTenantStaging === true;
      const activationReady = configuredDisabled && certified && stagingReady;

      activationButton?.classList.toggle("hidden", !activationReady);
      if (activationButton) activationButton.disabled = false;
      if (activationStatus) activationStatus.textContent = "";

      if (configuredEnabled) {
        activationGateTitle.textContent = "WhatsApp activado";
        activationGateCopy.textContent = "Este negocio está habilitado para Meta. La entrega real continúa sujeta a las compuertas globales del despliegue.";
      } else if (activationReady) {
        activationGateTitle.textContent = "Activación disponible con autorización manual";
        activationGateCopy.textContent = "Las validaciones técnicas están completas. Activar habilita este negocio para Meta; la entrega real sigue sujeta a las compuertas globales del despliegue.";
      } else {
        const missing = [];
        if (!configuredDisabled) missing.push("configuración guardada y desactivada");
        if (!certified) missing.push("certificación técnica");
        if (!stagingReady) missing.push("staging seguro");
        activationGateTitle.textContent = "Activación bloqueada";
        activationGateCopy.textContent = missing.length
          ? "Falta completar: " + missing.join(" y ") + "."
          : "La activación real todavía no está autorizada.";
      }
      activationGate.classList.remove("hidden");
    }

    async function loadPreparedDiagnostics(tenantStatus = null) {
      let status = tenantStatus;
      if (!status) {
        try {
          status = await api("/api/v1/channels/whatsapp/meta/config");
        } catch (error) {
          status = null;
        }
      }
      const [certification, deployment] = await Promise.all([
        loadCertificationReadiness(),
        loadDeploymentReadiness()
      ]);
      renderActivationGate(certification, deployment, status);
    }

    async function restoreConfiguredState() {
      try {
        const status = await api("/api/v1/channels/whatsapp/meta/config");
        if (onboardingInteractionStarted) return;
        const configuredDisabled = status?.status === "CONFIGURED_DISABLED"
          && status?.configured === true
          && status?.enabled === false;
        const configuredEnabled = status?.status === "CONFIGURED_ENABLED"
          && status?.configured === true
          && status?.enabled === true;
        if (!configuredDisabled && !configuredEnabled) return;

        preparedState?.classList.toggle("hidden", configuredEnabled);
        await loadPreparedDiagnostics(status);
      } catch (error) {
        // Fail closed: do not infer a prepared state when tenant status cannot be verified.
      }
    }

    function resetPinSetup() {
      selectedPhoneFinalization = null;
      if (pinInput) {
        pinInput.value = "";
        pinInput.disabled = false;
      }
      if (finalizePhoneButton) finalizePhoneButton.disabled = true;
      if (pinStatus) pinStatus.textContent = "";
      pinSetup?.classList.add("hidden");
      preparedState?.classList.add("hidden");
      certificationState?.classList.add("hidden");
      deploymentState?.classList.add("hidden");
      activationGate?.classList.add("hidden");
      activationButton?.classList.add("hidden");
      if (activationButton) activationButton.disabled = false;
      if (activationStatus) activationStatus.textContent = "";
      if (certificationTitle) certificationTitle.textContent = "";
      if (certificationCopy) certificationCopy.textContent = "";
      if (deploymentTitle) deploymentTitle.textContent = "";
      if (deploymentCopy) deploymentCopy.textContent = "";
      if (activationGateTitle) activationGateTitle.textContent = "";
      if (activationGateCopy) activationGateCopy.textContent = "";
    }

    activationButton?.addEventListener("click", async () => {
      const accepted = window.confirm(
        "Vas a habilitar WhatsApp para este negocio. La entrega real seguirá sujeta a las compuertas globales del despliegue. ¿Confirmas?"
      );
      if (!accepted) return;

      activationButton.disabled = true;
      if (activationStatus) activationStatus.textContent = "Activando WhatsApp…";
      try {
        const status = await api("/api/v1/channels/whatsapp/meta/config/activate", {
          method: "POST"
        });
        if (status?.status !== "CONFIGURED_ENABLED"
            || status?.configured !== true
            || status?.enabled !== true) {
          throw new Error("Meta activation response mismatch");
        }
        activationGateTitle.textContent = "WhatsApp activado";
        activationGateCopy.textContent = "Este negocio quedó habilitado para Meta. La entrega real continúa sujeta a las compuertas globales del despliegue.";
        activationButton.classList.add("hidden");
        if (activationStatus) activationStatus.textContent = "Activación confirmada.";
      } catch (error) {
        if (activationStatus) {
          activationStatus.textContent = "No fue posible activar WhatsApp. Revisa las validaciones e intenta nuevamente.";
        }
        activationButton.disabled = false;
      }
    });

    wabaCandidates?.addEventListener("meta-waba-selected", () => {
      selectedPhoneDiscovery = null;
      selectedPhoneValidation = null;
      selectedPhoneFinalization = null;
      resetPinSetup();
      renderPhoneCandidates(null, phoneCandidates);
      phoneConfirm?.classList.add("hidden");
      if (phoneConfirmStatus) phoneConfirmStatus.textContent = "";
      if (wabaConfirmStatus) wabaConfirmStatus.textContent = "";
      wabaConfirm?.classList.remove("hidden");
      if (wabaConfirmButton) wabaConfirmButton.disabled = false;
    });

    phoneCandidates?.addEventListener("meta-phone-selected", () => {
      selectedPhoneValidation = null;
      selectedPhoneFinalization = null;
      resetPinSetup();
      if (phoneConfirmStatus) phoneConfirmStatus.textContent = "";
      phoneConfirm?.classList.remove("hidden");
      if (phoneConfirmButton) phoneConfirmButton.disabled = false;
    });

    phoneConfirmButton?.addEventListener("click", async () => {
      const selectedWabaCard = wabaCandidates?.querySelector('.meta-whatsapp-waba-card[data-selected="true"]');
      const selectedPhoneCard = phoneCandidates?.querySelector('.meta-whatsapp-phone-card[data-selected="true"]');
      const wabaId = selectedWabaCard?.dataset.wabaId;
      const phoneNumberId = selectedPhoneCard?.dataset.phoneNumberId;
      if (!wabaId || !phoneNumberId) return;

      phoneConfirmButton.disabled = true;
      if (phoneConfirmStatus) phoneConfirmStatus.textContent = "Validando número con Meta…";
      try {
        const validation = await api("/api/v1/channels/whatsapp/meta/embedded-signup/waba/phone-number/validate", {
          method: "POST",
          body: JSON.stringify({ wabaId, phoneNumberId })
        });
        if (validation?.state !== "PHONE_NUMBER_VALIDATED" || String(validation?.phoneNumberId || "") !== phoneNumberId) {
          throw new Error("Meta phone validation response mismatch");
        }
        selectedPhoneValidation = validation;
        selectedPhoneFinalization = null;
        if (phoneConfirmStatus) phoneConfirmStatus.textContent = "Número validado por Meta.";
        if (pinInput) pinInput.disabled = false;
        if (pinStatus) pinStatus.textContent = "";
        pinSetup?.classList.remove("hidden");
        pinInput?.focus();
      } catch (error) {
        selectedPhoneValidation = null;
        selectedPhoneFinalization = null;
        resetPinSetup();
        if (phoneConfirmStatus) {
          phoneConfirmStatus.textContent = "No fue posible validar este número. Intenta nuevamente.";
        }
      } finally {
        phoneConfirmButton.disabled = false;
      }
    });

    pinInput?.addEventListener("input", () => {
      const digitsOnly = pinInput.value.replace(/\D/g, "").slice(0, 6);
      if (pinInput.value !== digitsOnly) pinInput.value = digitsOnly;
      const valid = /^[0-9]{6}$/.test(digitsOnly);
      const canFinalize = valid && selectedPhoneFinalization === null;
      if (finalizePhoneButton) finalizePhoneButton.disabled = !canFinalize;
      if (pinStatus) {
        pinStatus.textContent = selectedPhoneFinalization
          ? "Configuración ya finalizada."
          : valid
            ? "PIN listo para finalizar la configuración."
          : digitsOnly.length
            ? "El PIN debe tener exactamente 6 dígitos."
            : "";
      }
    });

    finalizePhoneButton?.addEventListener("click", async () => {
      const selectedWabaCard = wabaCandidates?.querySelector('.meta-whatsapp-waba-card[data-selected="true"]');
      const selectedPhoneCard = phoneCandidates?.querySelector('.meta-whatsapp-phone-card[data-selected="true"]');
      const wabaId = selectedWabaCard?.dataset.wabaId;
      const phoneNumberId = selectedPhoneCard?.dataset.phoneNumberId;
      const pin = pinInput?.value || "";

      if (!wabaId || !phoneNumberId || !/^[0-9]{6}$/.test(pin)) return;
      if (selectedPhoneValidation?.state !== "PHONE_NUMBER_VALIDATED"
          || String(selectedPhoneValidation?.phoneNumberId || "") !== phoneNumberId) {
        if (pinStatus) pinStatus.textContent = "Vuelve a validar el número antes de finalizar.";
        return;
      }

      finalizePhoneButton.disabled = true;
      if (pinStatus) pinStatus.textContent = "Finalizando configuración de WhatsApp…";
      try {
        const finalization = await api("/api/v1/channels/whatsapp/meta/embedded-signup/waba/phone-number/finalize", {
          method: "POST",
          body: JSON.stringify({ wabaId, phoneNumberId, pin })
        });
        if (finalization?.state !== "PHONE_NUMBER_REGISTERED_AND_STAGED"
            || finalization?.enabled !== false
            || String(finalization?.phoneNumberId || "") !== phoneNumberId
            || String(finalization?.wabaId || "") !== wabaId) {
          throw new Error("Meta phone finalization response mismatch");
        }
        selectedPhoneFinalization = finalization;
        if (pinInput) {
          pinInput.value = "";
          pinInput.disabled = true;
        }
        if (pinStatus) pinStatus.textContent = "PIN eliminado del formulario.";
        preparedState?.classList.remove("hidden");
        await loadPreparedDiagnostics();
      } catch (error) {
        selectedPhoneFinalization = null;
        if (pinStatus) pinStatus.textContent = "No fue posible finalizar la configuración. Revisa el PIN e intenta nuevamente.";
      } finally {
        const retryAllowed = selectedPhoneFinalization === null
          && /^[0-9]{6}$/.test(pinInput?.value || "");
        finalizePhoneButton.disabled = !retryAllowed;
      }
    });

    wabaConfirmButton?.addEventListener("click", async () => {
      const selectedCard = wabaCandidates?.querySelector('.meta-whatsapp-waba-card[data-selected="true"]');
      const wabaId = selectedCard?.dataset.wabaId;
      if (!wabaId) return;

      wabaConfirmButton.disabled = true;
      if (wabaConfirmStatus) wabaConfirmStatus.textContent = "Consultando números de WhatsApp Business…";
      try {
        selectedPhoneDiscovery = await api("/api/v1/channels/whatsapp/meta/embedded-signup/waba/phone-numbers", {
          method: "POST",
          body: JSON.stringify({ wabaId })
        });
        selectedPhoneValidation = null;
        selectedPhoneFinalization = null;
        resetPinSetup();
        phoneConfirm?.classList.add("hidden");
        if (phoneConfirmStatus) phoneConfirmStatus.textContent = "";
        const phoneCount = renderPhoneCandidates(selectedPhoneDiscovery, phoneCandidates);
        if (wabaConfirmStatus) {
          wabaConfirmStatus.textContent = "Cuenta confirmada. Meta devolvió " + phoneCount + " número" + (phoneCount === 1 ? "" : "s") + ".";
        }
      } catch (error) {
        selectedPhoneDiscovery = null;
        selectedPhoneValidation = null;
        selectedPhoneFinalization = null;
        resetPinSetup();
        renderPhoneCandidates(null, phoneCandidates);
        phoneConfirm?.classList.add("hidden");
        if (phoneConfirmStatus) phoneConfirmStatus.textContent = "";
        if (wabaConfirmStatus) {
          wabaConfirmStatus.textContent = "No fue posible consultar los números de esta cuenta. Intenta nuevamente.";
        }
      } finally {
        wabaConfirmButton.disabled = false;
      }
    });

    button?.addEventListener("click", async () => {
      onboardingInteractionStarted = true;
      if (button.dataset.sdkReady === "true") {
        if (!window.FB?.login) {
          button.dataset.sdkReady = "false";
          button.textContent = "Conectar WhatsApp";
          message.textContent = "Meta necesita prepararse nuevamente. Intenta otra vez.";
          message.classList.remove("hidden");
          return;
        }

        button.disabled = true;
        renderWabaCandidates(null, wabaCandidates);
        selectedPhoneDiscovery = null;
        selectedPhoneValidation = null;
        selectedPhoneFinalization = null;
        resetPinSetup();
        renderPhoneCandidates(null, phoneCandidates);
        phoneConfirm?.classList.add("hidden");
        if (phoneConfirmStatus) phoneConfirmStatus.textContent = "";
        wabaConfirm?.classList.add("hidden");
        if (wabaConfirmStatus) wabaConfirmStatus.textContent = "";
        message.textContent = "Abriendo autorización segura de Meta…";
        message.classList.remove("hidden");
        window.FB.login(async response => {
          const code = response?.authResponse?.code;
          if (!code) {
            button.disabled = false;
            renderWabaCandidates(null, wabaCandidates);
            message.textContent = "La autorización no se completó. Puedes intentarlo nuevamente.";
            return;
          }

          try {
            const handoff = await api("/api/v1/channels/whatsapp/meta/embedded-signup/authorization-code", {
              method: "POST",
              body: JSON.stringify({ code })
            });
            if (!handoff?.accepted) {
              renderWabaCandidates(null, wabaCandidates);
              message.textContent = "El servidor no pudo aceptar la autorización.";
            } else {
              const candidateCount = renderWabaCandidates(handoff, wabaCandidates);
              message.textContent = candidateCount
                ? `Autorización completada. Encontramos ${candidateCount} cuenta${candidateCount === 1 ? "" : "s"} de WhatsApp Business.`
                : "Autorización completada, pero Meta no devolvió cuentas de WhatsApp Business disponibles.";
            }
          } catch (error) {
            renderWabaCandidates(null, wabaCandidates);
            message.textContent = "No fue posible completar la autorización con Meta. Intenta nuevamente.";
          } finally {
            button.disabled = false;
          }
        }, {
          config_id: bootstrap.configId,
          auth_type: "rerequest",
          response_type: "code",
          override_default_response_type: true,
          extras: {
            setup: {}
          }
        });
        return;
      }

      button.disabled = true;
      message.textContent = "Preparando conexión segura con Meta…";
      message.classList.remove("hidden");
      try {
        await loadFacebookSdk(bootstrap);
        button.textContent = "Continuar con Meta";
        button.dataset.sdkReady = "true";
        message.textContent = "SDK de Meta preparado. Continúa para autorizar tu WhatsApp Business.";
      } catch (error) {
        button.textContent = "Conectar WhatsApp";
        button.dataset.sdkReady = "false";
        message.textContent = "No fue posible preparar Meta. Intenta nuevamente.";
      } finally {
        button.disabled = false;
      }
    });

    queueMicrotask(restoreConfiguredState);
  }

  async function loadBootstrap() {
    if (requested || dashboard.classList.contains("hidden")) return;
    if (!sessionStorage.getItem("helvoca_access_token")) return;
    requested = true;
    try {
      const bootstrap = await api("/api/v1/channels/whatsapp/meta/embedded-signup/bootstrap");
      renderConnectButton(bootstrap);
    } catch (error) {
      if (error?.status === 401) return;
      // Fail closed: no button is rendered when bootstrap cannot be verified.
    }
  }

  new MutationObserver(loadBootstrap).observe(dashboard, {
    attributes: true,
    attributeFilter: ["class"]
  });
  queueMicrotask(loadBootstrap);
})();
