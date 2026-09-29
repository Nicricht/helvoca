(() => {
  const CACHE_KEY = "recepvoz_appearance_theme";
  const DEFAULT_THEME = "cyan";
  const THEMES = Object.freeze({
    cyan: {
      label: "Cyan Voice",
      description: "Voz, tecnología y energía limpia.",
      swatches: ["#22c7d6", "#43d7e3"]
    },
    blue: {
      label: "Electric Blue",
      description: "Corporativo, confiable y directo.",
      swatches: ["#4f8cff", "#72a4ff"]
    },
    emerald: {
      label: "Emerald",
      description: "Operación activa y cercana.",
      swatches: ["#35d399", "#5de0ae"]
    },
    violet: {
      label: "Violet AI",
      description: "IA, creatividad y sofisticación.",
      swatches: ["#806bff", "#927fff"]
    },
    amber: {
      label: "Amber",
      description: "Cálido, humano y diferenciador.",
      swatches: ["#f59e42", "#ffb45f"]
    }
  });

  let authoritativeTheme = DEFAULT_THEME;
  let canManage = false;
  let saveTheme = null;
  let saving = false;

  function normalize(theme) {
    const value = String(theme || "").trim().toLowerCase();
    return Object.prototype.hasOwnProperty.call(THEMES, value) ? value : DEFAULT_THEME;
  }

  function apply(theme, { cache = true } = {}) {
    const normalized = normalize(theme);
    document.documentElement.dataset.rvAccentTheme = normalized;
    if (cache) sessionStorage.setItem(CACHE_KEY, normalized);
    updateThemeButtons(normalized);
    document.dispatchEvent(new CustomEvent("recepvoz:appearance-changed", {
      detail: { theme: normalized }
    }));
    return normalized;
  }

  function clearCache() {
    sessionStorage.removeItem(CACHE_KEY);
    authoritativeTheme = DEFAULT_THEME;
    document.documentElement.dataset.rvAccentTheme = DEFAULT_THEME;
    updateThemeButtons(DEFAULT_THEME);
  }

  function setMessage(text, kind = "") {
    const target = document.querySelector("#appearanceMessage");
    if (!target) return;
    target.textContent = text || "";
    target.classList.toggle("hidden", !text);
    target.classList.remove("error", "success");
    if (kind) target.classList.add(kind);
  }

  function setBusy(value) {
    saving = Boolean(value);
    document.querySelectorAll("[data-appearance-theme]").forEach(button => {
      button.disabled = saving || !canManage;
      button.setAttribute("aria-busy", saving ? "true" : "false");
    });
  }

  function updateThemeButtons(theme = document.documentElement.dataset.rvAccentTheme || DEFAULT_THEME) {
    document.querySelectorAll("[data-appearance-theme]").forEach(button => {
      const selected = button.dataset.appearanceTheme === theme;
      button.setAttribute("aria-pressed", String(selected));
      button.classList.toggle("selected", selected);
    });
    const readonly = document.querySelector("#appearanceReadonly");
    if (readonly) readonly.classList.toggle("hidden", canManage);
  }

  function renderSettings() {
    const grid = document.querySelector("#appearanceThemeGrid");
    if (!grid || grid.dataset.appearanceMounted) {
      updateThemeButtons();
      return;
    }
    grid.dataset.appearanceMounted = "true";

    Object.entries(THEMES).forEach(([theme, meta]) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "appearance-theme-card";
      button.dataset.appearanceTheme = theme;
      button.setAttribute("aria-pressed", "false");
      button.setAttribute("aria-label", meta.label);
      button.disabled = true;

      const preview = document.createElement("span");
      preview.className = "appearance-theme-preview";
      preview.setAttribute("aria-hidden", "true");
      preview.style.setProperty("--appearance-preview-primary", meta.swatches[0]);
      preview.style.setProperty("--appearance-preview-secondary", meta.swatches[1]);
      preview.innerHTML =
        '<span class="appearance-preview-sidebar"></span>' +
        '<span class="appearance-preview-main"><i></i><i></i><i></i></span>' +
        '<span class="appearance-preview-action"></span>';

      const copy = document.createElement("span");
      copy.className = "appearance-theme-copy";
      const strong = document.createElement("strong");
      strong.textContent = meta.label;
      const small = document.createElement("small");
      small.textContent = meta.description;
      copy.append(strong, small);

      const check = document.createElement("span");
      check.className = "appearance-theme-check";
      check.setAttribute("aria-hidden", "true");
      check.textContent = "✓";

      button.append(preview, copy, check);
      button.addEventListener("click", () => selectTheme(theme));
      grid.appendChild(button);
    });

    updateThemeButtons();
    setBusy(false);
  }

  async function selectTheme(theme) {
    if (!canManage || saving || typeof saveTheme !== "function") return;
    const selected = normalize(theme);
    const previous = authoritativeTheme;
    if (selected === previous) {
      apply(previous);
      setMessage("Este color ya está activo.", "success");
      return;
    }

    setMessage("");
    apply(selected);
    setBusy(true);
    try {
      const response = await saveTheme(selected);
      authoritativeTheme = normalize(response?.theme || selected);
      apply(authoritativeTheme);
      setMessage("Apariencia guardada para tu negocio.", "success");
    } catch (error) {
      apply(previous);
      setMessage(error?.message || "No se pudo guardar la apariencia.", "error");
    } finally {
      setBusy(false);
    }
  }

  function configure({ theme, canManage: allowed, save } = {}) {
    authoritativeTheme = normalize(theme);
    canManage = Boolean(allowed);
    saveTheme = typeof save === "function" ? save : null;
    apply(authoritativeTheme);
    renderSettings();
    setBusy(false);
    updateThemeButtons(authoritativeTheme);
  }

  function syncFromBusiness(business) {
    authoritativeTheme = normalize(business?.appearanceTheme);
    return apply(authoritativeTheme);
  }

  const hasAuthenticatedSession = Boolean(sessionStorage.getItem("helvoca_access_token"));
  const cached = hasAuthenticatedSession ? sessionStorage.getItem(CACHE_KEY) : null;
  if (!hasAuthenticatedSession) sessionStorage.removeItem(CACHE_KEY);
  apply(cached || DEFAULT_THEME, { cache: false });

  window.RecepVozAppearance = Object.freeze({
    themes: THEMES,
    normalize,
    apply,
    configure,
    syncFromBusiness,
    clearCache
  });

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", renderSettings, { once: true });
  } else {
    renderSettings();
  }
})();
