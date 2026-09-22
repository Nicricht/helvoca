(() => {
  if (!document.body.classList.contains("settings-page") || typeof api !== "function") return;

  const ENDPOINT = "/api/v1/business/schedule-exceptions";
  let mounted = false;
  let canManage = false;
  let exceptions = [];

  const style = document.createElement("style");
  style.textContent = `
    #scheduleExceptionsPanel {
      margin-top: 18px;
      padding-top: 16px;
      border-top: 1px solid rgba(255,255,255,.07);
    }
    #scheduleExceptionsPanel .schedule-exception-head {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 12px;
      margin-bottom: 10px;
    }
    #scheduleExceptionsPanel .schedule-exception-head h3 {
      margin: 0;
      font-size: 14px;
    }
    #scheduleExceptionsPanel .schedule-exception-head p {
      margin: 4px 0 0;
      color: var(--muted);
      font-size: 11px;
      line-height: 1.45;
    }
    #scheduleExceptionForm {
      display: grid;
      grid-template-columns: minmax(150px, .9fr) minmax(150px, .9fr) minmax(120px, .7fr) minmax(120px, .7fr);
      gap: 8px;
      align-items: end;
      margin: 10px 0;
    }
    #scheduleExceptionForm .schedule-exception-reason {
      grid-column: 1 / -2;
    }
    #scheduleExceptionForm .schedule-exception-actions {
      display: flex;
      gap: 6px;
      justify-content: flex-end;
    }
    #scheduleExceptionForm label {
      min-width: 0;
      font-size: 11px;
      color: var(--muted);
    }
    #scheduleExceptionForm input,
    #scheduleExceptionForm select {
      width: 100%;
      min-height: 36px;
    }
    #scheduleExceptionMessage {
      margin: 8px 0;
      font-size: 11px;
      line-height: 1.45;
    }
    #scheduleExceptionMessage.error { color: var(--danger); }
    #scheduleExceptionMessage.success { color: var(--success); }
    #scheduleExceptionsList {
      display: grid;
      gap: 7px;
      margin-top: 10px;
    }
    .schedule-exception-row {
      display: grid;
      grid-template-columns: minmax(130px, .85fr) minmax(180px, 1.2fr) minmax(140px, 1fr) auto;
      gap: 10px;
      align-items: center;
      padding: 9px 10px;
      border: 1px solid rgba(255,255,255,.07);
      border-radius: 10px;
      background: rgba(255,255,255,.02);
    }
    .schedule-exception-row strong { font-size: 12px; }
    .schedule-exception-row span {
      color: var(--muted);
      font-size: 11px;
      line-height: 1.4;
    }
    .schedule-exception-row .schedule-exception-row-actions {
      display: flex;
      gap: 5px;
      justify-content: flex-end;
    }
    #scheduleExceptionsEmpty {
      padding: 12px 0;
      color: var(--muted);
      font-size: 11px;
    }
    @media (max-width: 760px) {
      #scheduleExceptionForm { grid-template-columns: 1fr 1fr; }
      #scheduleExceptionForm .schedule-exception-reason { grid-column: 1 / -1; }
      #scheduleExceptionForm .schedule-exception-actions { grid-column: 1 / -1; justify-content: stretch; }
      #scheduleExceptionForm .schedule-exception-actions .button { flex: 1; }
      .schedule-exception-row { grid-template-columns: 1fr; }
      .schedule-exception-row .schedule-exception-row-actions { justify-content: flex-start; }
    }
  `;
  document.head.appendChild(style);

  function formatDate(value) {
    if (!value) return "";
    try {
      return new Intl.DateTimeFormat("es-CL", { dateStyle: "medium" })
        .format(new Date(value + "T12:00:00"));
    } catch (_) {
      return value;
    }
  }

  function shortTime(value) {
    return value ? String(value).slice(0, 5) : "";
  }

  function message(text, kind = "") {
    const node = document.querySelector("#scheduleExceptionMessage");
    if (!node) return;
    node.textContent = text || "";
    node.classList.remove("error", "success");
    if (kind) node.classList.add(kind);
  }

  function syncMode() {
    const form = document.querySelector("#scheduleExceptionForm");
    if (!form) return;
    const closed = form.elements.kind.value === "closed";
    for (const name of ["openTime", "closeTime"]) {
      const field = form.elements[name];
      field.disabled = closed || !canManage;
      field.closest("label")?.classList.toggle("hidden", closed);
      if (closed) field.value = "";
    }
  }

  function resetForm() {
    const form = document.querySelector("#scheduleExceptionForm");
    if (!form) return;
    form.reset();
    form.elements.kind.value = "closed";
    syncMode();
    form.querySelector('[type="submit"]').textContent = "Guardar día";
    form.querySelector('[data-action="cancel-edit"]').classList.add("hidden");
    message("");
  }

  function editException(item) {
    const form = document.querySelector("#scheduleExceptionForm");
    if (!form || !canManage) return;
    form.elements.date.value = item.exceptionDate || "";
    form.elements.kind.value = item.closed ? "closed" : "special";
    form.elements.openTime.value = shortTime(item.openTime);
    form.elements.closeTime.value = shortTime(item.closeTime);
    form.elements.reason.value = item.reason || "";
    syncMode();
    form.querySelector('[type="submit"]').textContent = "Guardar cambios";
    form.querySelector('[data-action="cancel-edit"]').classList.remove("hidden");
    form.elements.date.focus();
  }

  function render() {
    const list = document.querySelector("#scheduleExceptionsList");
    const empty = document.querySelector("#scheduleExceptionsEmpty");
    if (!list || !empty) return;
    list.replaceChildren();

    if (!exceptions.length) {
      empty.classList.remove("hidden");
      return;
    }
    empty.classList.add("hidden");

    exceptions.forEach(item => {
      const row = document.createElement("div");
      row.className = "schedule-exception-row";
      row.dataset.exceptionDate = item.exceptionDate;

      const date = document.createElement("strong");
      date.textContent = formatDate(item.exceptionDate);

      const kind = document.createElement("span");
      kind.textContent = item.closed
        ? "Cerrado todo el día"
        : `Horario especial · ${shortTime(item.openTime)}–${shortTime(item.closeTime)}`;

      const reason = document.createElement("span");
      reason.textContent = item.reason || "Sin motivo indicado";

      const actions = document.createElement("div");
      actions.className = "schedule-exception-row-actions";
      if (canManage) {
        const edit = document.createElement("button");
        edit.type = "button";
        edit.className = "button small ghost";
        edit.textContent = "Editar";
        edit.addEventListener("click", () => editException(item));

        const remove = document.createElement("button");
        remove.type = "button";
        remove.className = "button small ghost";
        remove.textContent = "Eliminar";
        remove.addEventListener("click", async () => {
          if (!window.confirm(`Eliminar la excepción del ${formatDate(item.exceptionDate)}?`)) return;
          remove.disabled = true;
          try {
            await api(`${ENDPOINT}/${encodeURIComponent(item.exceptionDate)}`, { method: "DELETE" });
            exceptions = exceptions.filter(value => value.exceptionDate !== item.exceptionDate);
            render();
            if (document.querySelector("#scheduleExceptionForm")?.elements.date.value === item.exceptionDate) resetForm();
            message("Día especial eliminado.", "success");
          } catch (error) {
            message(error?.message || "No se pudo eliminar el día especial.", "error");
            remove.disabled = false;
          }
        });

        actions.append(edit, remove);
      }

      row.append(date, kind, reason, actions);
      list.appendChild(row);
    });
  }

  async function load() {
    if (!sessionStorage.getItem("helvoca_access_token")) return;
    try {
      const [me, data] = await Promise.all([
        api("/api/v1/auth/me"),
        api(ENDPOINT)
      ]);
      canManage = Array.isArray(me?.roles) && me.roles.map(String).includes("BUSINESS_ADMIN");
      exceptions = Array.isArray(data) ? data : [];

      const form = document.querySelector("#scheduleExceptionForm");
      if (form) form.classList.toggle("hidden", !canManage);
      const readonly = document.querySelector("#scheduleExceptionsReadonly");
      if (readonly) readonly.classList.toggle("hidden", canManage);

      syncMode();
      render();
    } catch (error) {
      message(error?.message || "No se pudieron cargar los días especiales.", "error");
    }
  }

  function mount() {
    if (mounted) return;
    const panel = document.querySelector("#configHoursPanel");
    if (!panel) return;
    mounted = true;

    const section = document.createElement("section");
    section.id = "scheduleExceptionsPanel";
    section.innerHTML = `
      <div class="schedule-exception-head">
        <div>
          <h3>Días especiales</h3>
          <p>Cierra una fecha concreta o reemplaza el horario normal solo para ese día.</p>
        </div>
      </div>
      <div id="scheduleExceptionsReadonly" class="hidden muted-text">Puedes revisar estos días, pero solo un administrador del negocio puede modificarlos.</div>
      <form id="scheduleExceptionForm">
        <label>Fecha<input name="date" type="date" required></label>
        <label>Tipo<select name="kind"><option value="closed">Cerrado</option><option value="special">Horario especial</option></select></label>
        <label class="hidden">Apertura<input name="openTime" type="time"></label>
        <label class="hidden">Cierre<input name="closeTime" type="time"></label>
        <label class="schedule-exception-reason">Motivo <span class="optional">opcional</span><input name="reason" maxlength="200" placeholder="Ej. feriado o evento especial"></label>
        <div class="schedule-exception-actions">
          <button class="button ghost hidden" data-action="cancel-edit" type="button">Cancelar</button>
          <button class="button primary" type="submit">Guardar día</button>
        </div>
      </form>
      <div id="scheduleExceptionMessage"></div>
      <div id="scheduleExceptionsEmpty" class="hidden">No hay días especiales configurados.</div>
      <div id="scheduleExceptionsList"></div>
    `;
    panel.appendChild(section);

    const form = section.querySelector("#scheduleExceptionForm");
    form.elements.kind.addEventListener("change", syncMode);
    form.querySelector('[data-action="cancel-edit"]').addEventListener("click", resetForm);
    form.addEventListener("submit", async event => {
      event.preventDefault();
      if (!canManage) return;

      const date = form.elements.date.value;
      const closed = form.elements.kind.value === "closed";
      const openTime = form.elements.openTime.value;
      const closeTime = form.elements.closeTime.value;
      if (!date) {
        message("Selecciona una fecha.", "error");
        return;
      }
      if (!closed && (!openTime || !closeTime || openTime >= closeTime)) {
        message("Define un horario especial válido.", "error");
        return;
      }

      const submit = form.querySelector('[type="submit"]');
      submit.disabled = true;
      try {
        const saved = await api(`${ENDPOINT}/${encodeURIComponent(date)}`, {
          method: "PUT",
          body: JSON.stringify({
            closed,
            openTime: closed ? null : openTime,
            closeTime: closed ? null : closeTime,
            reason: form.elements.reason.value.trim() || null
          })
        });
        const index = exceptions.findIndex(item => item.exceptionDate === saved.exceptionDate);
        if (index >= 0) exceptions[index] = saved;
        else exceptions.push(saved);
        exceptions.sort((a, b) => String(a.exceptionDate).localeCompare(String(b.exceptionDate)));
        render();
        resetForm();
        message("Día especial guardado.", "success");
      } catch (error) {
        message(error?.message || "No se pudo guardar el día especial.", "error");
      } finally {
        submit.disabled = false;
      }
    });

    syncMode();
    load();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", mount, { once: true });
  } else {
    mount();
  }
})();