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
  let facebookSdkPromise = null;

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
    button?.addEventListener("click", async () => {
      if (button.dataset.sdkReady === "true") {
        if (!window.FB?.login) {
          button.dataset.sdkReady = "false";
          button.textContent = "Conectar WhatsApp";
          message.textContent = "Meta necesita prepararse nuevamente. Intenta otra vez.";
          message.classList.remove("hidden");
          return;
        }

        button.disabled = true;
        message.textContent = "Abriendo autorización segura de Meta…";
        message.classList.remove("hidden");
        window.FB.login(async response => {
          const code = response?.authResponse?.code;
          if (!code) {
            button.disabled = false;
            message.textContent = "La autorización no se completó. Puedes intentarlo nuevamente.";
            return;
          }

          try {
            const handoff = await api("/api/v1/channels/whatsapp/meta/embedded-signup/authorization-code", {
              method: "POST",
              body: JSON.stringify({ code })
            });
            message.textContent = handoff?.accepted
              ? "Autorización recibida de forma segura por el servidor. Falta el intercambio con Meta."
              : "El servidor no pudo aceptar la autorización.";
          } catch (error) {
            message.textContent = "No fue posible entregar la autorización al servidor. Intenta nuevamente.";
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
