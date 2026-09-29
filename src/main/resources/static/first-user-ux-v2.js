(() => {
  const dashboard = document.querySelector("#dashboardView");
  const statusGrid = document.querySelector("#statusGrid");
  const onboarding = document.querySelector("#firstUserOnboarding");
  const progressText = document.querySelector("#firstUserProgressText");
  const progressBar = document.querySelector("#firstUserProgressBar");
  const progressMeter = onboarding?.querySelector('[role="progressbar"]');
  const stepsHost = document.querySelector("#firstUserSteps");
  const tryButton = document.querySelector("#tryRecepVozBtn");
  const nextAction = document.querySelector("#firstUserNextAction");
  const homeWorkspace = document.querySelector("#homeBusinessWorkspace");
  const primaryNav = document.querySelector("#primaryNav");

  if (!dashboard || !statusGrid || !primaryNav) return;

  const stepDefinitions = [
    {
      key: "businessProfileConfigured",
      label: "Tu negocio",
      detail: "Nombre y datos básicos",
      section: "business",
      actionLabel: "Continuar con Negocio"
    },
    {
      key: "servicesConfigured",
      label: "Servicios",
      detail: "Qué ofreces y cuánto dura",
      section: "services",
      actionLabel: "Continuar con Servicios"
    },
    {
      key: "scheduleConfigured",
      label: "Horarios",
      detail: "Cuándo puede reservar la gente",
      section: "hours",
      actionLabel: "Continuar con Horarios"
    },
    {
      key: "phoneConfigured",
      label: "Recepcionista",
      detail: "Voz y canal de atención",
      section: "receptionist",
      actionLabel: "Continuar con Recepcionista"
    }
  ];

  function statusFor(key) {
    return statusGrid.querySelector(`.status-card[data-key="${key}"]`)?.classList.contains("done") === true;
  }

  function renderSteps() {
    if (!onboarding || !stepsHost || !progressText || !progressBar) return;

    if (dashboard.classList.contains("hidden")) {
      onboarding.classList.add("hidden");
      document.body.classList.remove("first-user-onboarding-active");
      tryButton?.classList.add("hidden");
      return;
    }

    const states = stepDefinitions.map(step => ({ ...step, complete: statusFor(step.key) }));
    const completed = states.filter(step => step.complete).length;
    const ready = completed === states.length;
    const nextIndex = states.findIndex(step => !step.complete);

    progressText.textContent = `${completed} de ${states.length} pasos completados`;
    progressBar.style.width = `${Math.round((completed / states.length) * 100)}%`;
    progressBar.setAttribute("aria-valuenow", String(completed));
    progressMeter?.setAttribute("aria-valuenow", String(completed));

    states.forEach((step, index) => {
      const node = stepsHost.querySelector(`[data-step-key="${step.key}"]`);
      if (!node) return;
      const isNext = !step.complete && index === nextIndex;
      node.classList.toggle("complete", step.complete);
      node.classList.toggle("next", isNext);
      node.setAttribute("data-step-state", step.complete ? "complete" : (isNext ? "current" : "upcoming"));
      if (isNext) node.setAttribute("aria-current", "step");
      else node.removeAttribute("aria-current");
      const number = node.querySelector(".first-user-step-number");
      if (number) number.textContent = step.complete ? "✓" : String(index + 1);
    });

    if (nextAction && nextIndex >= 0) {
      const nextStep = states[nextIndex];
      nextAction.textContent = nextStep.actionLabel;
      nextAction.href = `/settings.html?section=${nextStep.section}`;
    }

    onboarding.classList.toggle("hidden", ready);
    document.body.classList.toggle("first-user-onboarding-active", !ready);
    tryButton?.classList.toggle("hidden", !ready);

    primaryNav.querySelectorAll("[data-home-nav]").forEach(link => {
      link.setAttribute("aria-disabled", ready ? "false" : "true");
      link.classList.toggle("disabled", !ready);
    });
  }

  function setPrimaryActive(name) {
    primaryNav.querySelectorAll("a").forEach(link => {
      const isHome = name === "home" && link.classList.contains("nav-home");
      const isSection = link.dataset.homeNav === name;
      link.classList.toggle("active", isHome || isSection);
    });
  }

  function activateHomeTab(name) {
    if (!homeWorkspace || homeWorkspace.classList.contains("hidden")) {
      if (!dashboard.classList.contains("hidden")) window.location.assign("/settings.html");
      return;
    }

    const target = homeWorkspace.querySelector(`[data-home-tab="${name}"]`);
    if (!target) return;
    target.click();
    setPrimaryActive(name);
    homeWorkspace.scrollIntoView({ behavior: "smooth", block: "start" });
    history.replaceState(null, "", `/#${name}`);
  }

  primaryNav.querySelectorAll("[data-home-nav]").forEach(link => {
    link.addEventListener("click", event => {
      event.preventDefault();
      activateHomeTab(link.dataset.homeNav);
    });
  });

  primaryNav.querySelector(".nav-home")?.addEventListener("click", () => {
    setPrimaryActive("home");
    history.replaceState(null, "", "/");
  });

  const statusObserver = new MutationObserver(() => {
    renderSteps();
  });
  statusObserver.observe(statusGrid, {
    attributes: true,
    subtree: true,
    attributeFilter: ["class"]
  });
  statusObserver.observe(dashboard, {
    attributes: true,
    attributeFilter: ["class"]
  });


  queueMicrotask(() => {
    renderSteps();
    const hash = window.location.hash.replace("#", "");
    if (hash === "bookings" || hash === "customers") activateHomeTab(hash);
  });
})();
