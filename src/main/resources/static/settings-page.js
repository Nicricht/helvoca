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
    @media (max-width: 520px) {
      #metaWhatsAppConnect .meta-whatsapp-row {
        align-items: stretch;
        flex-direction: column;
      }
    }
  `;
  document.head.appendChild(style);

  let requested = false;

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
    `;
    panel.appendChild(section);

    const button = section.querySelector("#metaWhatsAppConnectBtn");
    const message = section.querySelector("#metaWhatsAppConnectMessage");
    button?.addEventListener("click", () => {
      message.textContent = "Meta está preparado. El inicio de sesión seguro se habilitará en el siguiente paso.";
      message.classList.remove("hidden");
    });
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
