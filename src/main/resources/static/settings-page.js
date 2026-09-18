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
    if (title) title.textContent = "Cómo trabaja Helvoca";
    if (welcome) welcome.textContent = "Ajusta tu negocio, recepcionista, servicios, horarios, conocimiento, canales y plan.";
  }

  new MutationObserver(apply).observe(dashboard, { attributes: true, attributeFilter: ["class"] });
  new MutationObserver(() => advanced.classList.remove("hidden"))
    .observe(advanced, { attributes: true, attributeFilter: ["class"] });
  queueMicrotask(apply);
})();