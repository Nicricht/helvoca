(() => {
    const TOKEN_KEY = "helvoca_access_token";
    const token = sessionStorage.getItem(TOKEN_KEY) || "";

    const $ = (selector, root = document) => root.querySelector(selector);
    const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

    const loading = $("#inventoryLoading");
    const app = $("#inventoryApp");
    const authRequired = $("#inventoryAuthRequired");
    const rows = $("#inventoryRows");
    const empty = $("#inventoryEmpty");
    const message = $("#inventoryMessage");
    const search = $("#inventorySearch");
    const filter = $("#inventoryFilter");
    const roleBadge = $("#inventoryRoleBadge");

    const configDialog = $("#inventoryConfigDialog");
    const configForm = $("#inventoryConfigForm");
    const configMessage = $("#inventoryConfigMessage");
    const adjustDialog = $("#inventoryAdjustDialog");
    const adjustForm = $("#inventoryAdjustForm");
    const adjustMessage = $("#inventoryAdjustMessage");
    const historyDialog = $("#inventoryHistoryDialog");
    const variantsDialog = $("#inventoryVariantsDialog");
    const variantEditDialog = $("#inventoryVariantEditDialog");
    const variantForm = $("#inventoryVariantForm");
    const variantMessage = $("#inventoryVariantMessage");

    const state = {
        roles: [],
        canManage: false,
        business: null,
        catalog: [],
        inventory: [],
        products: [],
        currentVariantProductId: null,
        variants: []
    };

    const movementLabels = {
        CONFIGURE: "Configuración",
        ADJUSTMENT: "Ajuste",
        RESERVATION: "Reserva",
        RELEASE: "Liberación",
        CONSUMPTION: "Consumo"
    };

    function escapeHtml(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }

    function showMessage(target, text, kind = "error") {
        target.textContent = text;
        target.classList.remove("hidden", "error", "success");
        target.classList.add(kind);
    }

    function clearMessage(target) {
        target.textContent = "";
        target.classList.add("hidden");
        target.classList.remove("error", "success");
    }

    async function api(path, options = {}) {
        const headers = new Headers(options.headers || {});
        if (options.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
        if (token) headers.set("Authorization", `Bearer ${token}`);
        const response = await fetch(path, { ...options, headers });
        let payload = null;
        const type = response.headers.get("content-type") || "";
        if (type.includes("application/json")) {
            try { payload = await response.json(); } catch (_) { payload = null; }
        } else if (response.status !== 204) {
            try { payload = await response.text(); } catch (_) { payload = null; }
        }
        if (!response.ok) {
            if (response.status === 401) {
                sessionStorage.removeItem(TOKEN_KEY);
                showAuthRequired();
            }
            const text = payload?.message || payload?.detail || payload?.error ||
                (typeof payload === "string" ? payload : "") || `Error HTTP ${response.status}`;
            const error = new Error(text);
            error.status = response.status;
            throw error;
        }
        return payload;
    }

    function showAuthRequired() {
        loading.classList.add("hidden");
        app.classList.add("hidden");
        authRequired.classList.remove("hidden");
        roleBadge.textContent = "Sin sesión";
        roleBadge.className = "badge muted";
    }

    function mergeProducts() {
        const byId = new Map(state.inventory.map(item => [String(item.catalogItemId), item]));
        state.products = state.catalog
            .filter(item => item && item.kind === "PRODUCT" && item.active !== false)
            .map(item => {
                const stock = byId.get(String(item.id));
                return {
                    id: item.id,
                    name: item.name,
                    description: item.description || "",
                    price: item.price,
                    currency: item.currency || "CLP",
                    configured: Boolean(stock),
                    sku: stock?.sku || "",
                    trackingEnabled: Boolean(stock?.trackingEnabled),
                    onHand: stock?.onHand ?? null,
                    reserved: stock?.reserved ?? null,
                    available: stock?.available ?? null,
                    reorderThreshold: stock?.reorderThreshold ?? null,
                    lowStock: Boolean(stock?.lowStock)
                };
            });
    }

    function renderSummary() {
        const tracked = state.products.filter(item => item.configured && item.trackingEnabled);
        const onHand = tracked.reduce((sum, item) => sum + Number(item.onHand || 0), 0);
        const reserved = tracked.reduce((sum, item) => sum + Number(item.reserved || 0), 0);
        const low = tracked.filter(item => item.lowStock).length;

        $("#inventoryProductsCount").textContent = state.products.length;
        $("#inventoryConfiguredCount").textContent = `${tracked.length} con seguimiento`;
        $("#inventoryOnHandTotal").textContent = onHand;
        $("#inventoryReservedTotal").textContent = reserved;
        $("#inventoryLowStockCount").textContent = low;
        $("#inventoryLowStockKpi").classList.toggle("alert", low > 0);
    }

    function productMatches(item) {
        const query = search.value.trim().toLowerCase();
        if (query && !`${item.name} ${item.sku}`.toLowerCase().includes(query)) return false;
        switch (filter.value) {
            case "TRACKED": return item.configured && item.trackingEnabled;
            case "LOW": return item.configured && item.trackingEnabled && item.lowStock;
            case "UNCONFIGURED": return !item.configured || !item.trackingEnabled;
            default: return true;
        }
    }

    function stateBadge(item) {
        if (!item.configured || !item.trackingEnabled) {
            return '<span class="inventory-state off">Sin seguimiento</span>';
        }
        if (item.lowStock) return '<span class="inventory-state low">Stock bajo</span>';
        return '<span class="inventory-state ok">Disponible</span>';
    }

    function numberCell(value, extra = "") {
        if (value === null || value === undefined) return '<span class="inventory-sku missing">—</span>';
        return `<span class="inventory-number ${extra}">${Number(value)}</span>`;
    }

    function actionButtons(item) {
        const variants = `<button class="button ghost inventory-variants-btn" type="button" data-id="${item.id}">Variantes</button>`;
        const history = item.configured
            ? `<button class="button ghost inventory-history-btn" type="button" data-id="${item.id}">Historial</button>`
            : "";
        if (!state.canManage) return variants + history;
        const configureLabel = item.configured ? "Editar" : "Configurar";
        const configure = `<button class="button ghost inventory-config-btn" type="button" data-id="${item.id}">${configureLabel}</button>`;
        const adjust = item.configured && item.trackingEnabled
            ? `<button class="button secondary inventory-adjust-btn" type="button" data-id="${item.id}">Ajustar</button>`
            : "";
        return variants + configure + adjust + history;
    }

    function renderRows() {
        const visible = state.products.filter(productMatches);
        rows.innerHTML = visible.map(item => `
            <tr data-inventory-product-id="${escapeHtml(item.id)}">
                <td class="inventory-product">
                    <strong>${escapeHtml(item.name)}</strong>
                    <small>${escapeHtml(item.description || "Producto del catálogo")}</small>
                </td>
                <td><span class="inventory-sku ${item.sku ? "" : "missing"}">${escapeHtml(item.sku || "Sin SKU")}</span></td>
                <td>${numberCell(item.onHand)}</td>
                <td>${numberCell(item.reserved, "reserved")}</td>
                <td>${numberCell(item.available, "available")}</td>
                <td>${numberCell(item.reorderThreshold)}</td>
                <td>${stateBadge(item)}</td>
                <td><div class="inventory-row-actions">${actionButtons(item)}</div></td>
            </tr>
        `).join("");
        empty.classList.toggle("hidden", visible.length > 0);
        bindRowActions();
    }

    function bindRowActions() {
        $$(".inventory-variants-btn", rows).forEach(button => {
            button.addEventListener("click", () => openVariants(button.dataset.id));
        });
        $$(".inventory-config-btn", rows).forEach(button => {
            button.addEventListener("click", () => openConfig(button.dataset.id));
        });
        $$(".inventory-adjust-btn", rows).forEach(button => {
            button.addEventListener("click", () => openAdjust(button.dataset.id));
        });
        $$(".inventory-history-btn", rows).forEach(button => {
            button.addEventListener("click", () => openHistory(button.dataset.id));
        });
    }

    function render() {
        renderSummary();
        renderRows();
    }

    function product(id) {
        return state.products.find(item => String(item.id) === String(id));
    }

    function openConfig(id) {
        const item = product(id);
        if (!item || !state.canManage) return;
        clearMessage(configMessage);
        $("#inventoryConfigTitle").textContent = item.name;
        configForm.elements.catalogItemId.value = item.id;
        configForm.elements.sku.value = item.sku || "";
        configForm.elements.onHand.value = item.onHand ?? 0;
        configForm.elements.reorderThreshold.value = item.reorderThreshold ?? 0;
        configForm.elements.trackingEnabled.checked = item.configured ? item.trackingEnabled : true;
        configForm.elements.note.value = "";
        configDialog.showModal();
    }

    function openAdjust(id) {
        const item = product(id);
        if (!item || !state.canManage || !item.trackingEnabled) return;
        clearMessage(adjustMessage);
        $("#inventoryAdjustTitle").textContent = item.name;
        $("#inventoryAdjustAvailable").textContent = item.available ?? 0;
        adjustForm.elements.catalogItemId.value = item.id;
        adjustForm.elements.variantId.value = "";
        adjustForm.elements.delta.value = "";
        adjustForm.elements.note.value = "";
        adjustDialog.showModal();
        setTimeout(() => adjustForm.elements.delta.focus(), 0);
    }

    function optionText(json) {
        if (!json) return "";
        try {
            const value = typeof json === "string" ? JSON.parse(json) : json;
            return Object.entries(value || {})
                .map(([key, item]) => `${key}=${item}`)
                .join(", ");
        } catch (_) {
            return "";
        }
    }

    function optionJson(text) {
        const out = {};
        String(text || "").split(",").forEach(part => {
            const [rawKey, ...rawValue] = part.split("=");
            const key = String(rawKey || "").trim();
            const value = rawValue.join("=").trim();
            if (key && value) out[key] = value;
        });
        return JSON.stringify(out);
    }

    function variantState(variant) {
        if (!variant.active) return '<span class="inventory-state off">Inactiva</span>';
        if (!variant.trackingEnabled) return '<span class="inventory-state off">Sin seguimiento</span>';
        if (variant.lowStock) return '<span class="inventory-state low">Stock bajo</span>';
        return '<span class="inventory-state ok">Disponible</span>';
    }

    async function openVariants(productId) {
        const item = product(productId);
        if (!item) return;
        state.currentVariantProductId = item.id;
        state.variants = [];
        $("#inventoryVariantsTitle").textContent = `Variantes · ${item.name}`;
        $("#inventoryVariantsList").innerHTML = "";
        $("#inventoryVariantsEmpty").classList.add("hidden");
        $("#inventoryVariantsLoading").classList.remove("hidden");
        $("#inventoryAddVariantBtn").classList.toggle("hidden", !state.canManage);
        if (!variantsDialog.open) variantsDialog.showModal();
        try {
            const values = await api(
                `/api/v1/inventory/${encodeURIComponent(item.id)}/variants`);
            state.variants = Array.isArray(values) ? values : [];
            renderVariants();
        } catch (error) {
            $("#inventoryVariantsLoading").classList.add("hidden");
            $("#inventoryVariantsList").innerHTML =
                `<div class="message error">${escapeHtml(error.message)}</div>`;
        }
    }

    function renderVariants() {
        $("#inventoryVariantsLoading").classList.add("hidden");
        const active = state.variants;
        $("#inventoryVariantsEmpty").classList.toggle("hidden", active.length > 0);
        $("#inventoryVariantsList").innerHTML = active.map(variant => {
            const actions = [
                state.canManage
                    ? `<button class="button ghost variant-edit-btn" data-id="${variant.id}" type="button">Editar</button>`
                    : "",
                state.canManage && variant.active && variant.trackingEnabled
                    ? `<button class="button secondary variant-adjust-btn" data-id="${variant.id}" type="button">Ajustar</button>`
                    : "",
                `<button class="button ghost variant-history-btn" data-id="${variant.id}" type="button">Historial</button>`
            ].join("");
            return `
                <article class="inventory-variant-card ${variant.active ? "" : "inactive"}"
                         data-inventory-variant-id="${escapeHtml(variant.id)}">
                    <div class="inventory-variant-name">
                        <strong>${escapeHtml(variant.name)}</strong>
                        <small class="inventory-sku">${escapeHtml(variant.sku)}</small>
                        <small class="inventory-variant-options">${escapeHtml(optionText(variant.optionValuesJson) || "Sin opciones")}</small>
                    </div>
                    <div>${variantState(variant)}</div>
                    <div class="inventory-variant-metric"><span>Físico</span><strong>${Number(variant.onHand)}</strong></div>
                    <div class="inventory-variant-metric"><span>Reservado</span><strong>${Number(variant.reserved)}</strong></div>
                    <div class="inventory-variant-metric"><span>Disponible</span><strong>${Number(variant.available)}</strong></div>
                    <div class="inventory-variant-actions">${actions}</div>
                </article>`;
        }).join("");

        $$(".variant-edit-btn", $("#inventoryVariantsList")).forEach(button => {
            button.addEventListener("click", () => openVariantForm(button.dataset.id));
        });
        $$(".variant-adjust-btn", $("#inventoryVariantsList")).forEach(button => {
            button.addEventListener("click", () => openVariantAdjust(button.dataset.id));
        });
        $$(".variant-history-btn", $("#inventoryVariantsList")).forEach(button => {
            button.addEventListener("click", () => openVariantHistory(button.dataset.id));
        });
    }

    function findVariant(id) {
        return state.variants.find(value => String(value.id) === String(id));
    }

    function openVariantForm(variantId = null) {
        if (!state.canManage || !state.currentVariantProductId) return;
        const variant = variantId ? findVariant(variantId) : null;
        clearMessage(variantMessage);
        $("#inventoryVariantFormTitle").textContent =
            variant ? `Editar · ${variant.name}` : "Nueva variante";
        variantForm.elements.catalogItemId.value = state.currentVariantProductId;
        variantForm.elements.variantId.value = variant?.id || "";
        variantForm.elements.name.value = variant?.name || "";
        variantForm.elements.sku.value = variant?.sku || "";
        variantForm.elements.onHand.value = variant?.onHand ?? 0;
        variantForm.elements.reorderThreshold.value = variant?.reorderThreshold ?? 0;
        variantForm.elements.options.value = optionText(variant?.optionValuesJson);
        variantForm.elements.trackingEnabled.checked =
            variant ? Boolean(variant.trackingEnabled) : true;
        variantForm.elements.active.checked = variant ? Boolean(variant.active) : true;
        variantForm.elements.note.value = "";
        variantEditDialog.showModal();
    }

    async function saveVariant(event) {
        event.preventDefault();
        if (!state.canManage) return;
        clearMessage(variantMessage);
        const productId = variantForm.elements.catalogItemId.value;
        const variantId = variantForm.elements.variantId.value;
        const button = $("#inventoryVariantSave");
        button.disabled = true;
        try {
            const payload = {
                name: variantForm.elements.name.value.trim(),
                optionValuesJson: optionJson(variantForm.elements.options.value),
                sku: variantForm.elements.sku.value.trim(),
                trackingEnabled: variantForm.elements.trackingEnabled.checked,
                onHand: Number(variantForm.elements.onHand.value || 0),
                reorderThreshold: Number(variantForm.elements.reorderThreshold.value || 0),
                active: variantForm.elements.active.checked,
                note: variantForm.elements.note.value.trim() || null
            };
            const path = variantId
                ? `/api/v1/inventory/${encodeURIComponent(productId)}/variants/${encodeURIComponent(variantId)}`
                : `/api/v1/inventory/${encodeURIComponent(productId)}/variants`;
            await api(path, {
                method: variantId ? "PUT" : "POST",
                body: JSON.stringify(payload)
            });
            variantEditDialog.close();
            await openVariants(productId);
        } catch (error) {
            showMessage(variantMessage, error.message);
        } finally {
            button.disabled = false;
        }
    }

    function openVariantAdjust(variantId) {
        const variant = findVariant(variantId);
        if (!variant || !state.canManage || !variant.trackingEnabled) return;
        clearMessage(adjustMessage);
        $("#inventoryAdjustTitle").textContent = variant.name;
        $("#inventoryAdjustAvailable").textContent = variant.available ?? 0;
        adjustForm.elements.catalogItemId.value = state.currentVariantProductId;
        adjustForm.elements.variantId.value = variant.id;
        adjustForm.elements.delta.value = "";
        adjustForm.elements.note.value = "";
        adjustDialog.showModal();
        setTimeout(() => adjustForm.elements.delta.focus(), 0);
    }

    async function openVariantHistory(variantId) {
        const variant = findVariant(variantId);
        if (!variant || !state.currentVariantProductId) return;
        const productId = state.currentVariantProductId;
        $("#inventoryHistoryTitle").textContent = `Movimientos · ${variant.name}`;
        $("#inventoryHistoryList").innerHTML = "";
        $("#inventoryHistoryEmpty").classList.add("hidden");
        $("#inventoryHistoryLoading").classList.remove("hidden");
        historyDialog.showModal();
        try {
            const movements = await api(
                `/api/v1/inventory/${encodeURIComponent(productId)}/variants/${encodeURIComponent(variant.id)}/movements`);
            renderMovementHistory(movements);
        } catch (error) {
            $("#inventoryHistoryLoading").classList.add("hidden");
            $("#inventoryHistoryList").innerHTML =
                `<div class="message error">${escapeHtml(error.message)}</div>`;
        }
    }

    function formatMovementDelta(value) {
        const n = Number(value || 0);
        return n > 0 ? `+${n}` : String(n);
    }

    async function openHistory(id) {
        const item = product(id);
        if (!item?.configured) return;
        $("#inventoryHistoryTitle").textContent = `Movimientos · ${item.name}`;
        $("#inventoryHistoryList").innerHTML = "";
        $("#inventoryHistoryEmpty").classList.add("hidden");
        $("#inventoryHistoryLoading").classList.remove("hidden");
        historyDialog.showModal();
        try {
            const movements = await api(`/api/v1/inventory/${encodeURIComponent(id)}/movements`);
            renderMovementHistory(movements);
        } catch (error) {
            $("#inventoryHistoryLoading").classList.add("hidden");
            $("#inventoryHistoryList").innerHTML = `<div class="message error">${escapeHtml(error.message)}</div>`;
        }
    }

    function renderMovementHistory(movements) {
        $("#inventoryHistoryLoading").classList.add("hidden");
        if (!Array.isArray(movements) || movements.length === 0) {
            $("#inventoryHistoryEmpty").classList.remove("hidden");
            return;
        }
        $("#inventoryHistoryList").innerHTML = movements.map(movement => {
            const when = movement.createdAt
                ? new Intl.DateTimeFormat(
                        "es-CL", { dateStyle:"short", timeStyle:"short" })
                        .format(new Date(movement.createdAt))
                : "";
            const title = movement.note || movementLabels[movement.type] || movement.type;
            return `
                <article class="inventory-history-item">
                    <div class="inventory-history-type">${escapeHtml(movementLabels[movement.type] || movement.type)}</div>
                    <div class="inventory-history-main">
                        <strong>${escapeHtml(title)}</strong>
                        <small>${escapeHtml(when)} · físico ${formatMovementDelta(movement.quantityDelta)} · reservado ${formatMovementDelta(movement.reservedDelta)}</small>
                    </div>
                    <div class="inventory-history-after">
                        Después
                        <strong>${Number(movement.onHandAfter)} / ${Number(movement.reservedAfter)} res.</strong>
                    </div>
                </article>`;
        }).join("");
    }

    async function saveConfig(event) {
        event.preventDefault();
        clearMessage(configMessage);
        const id = configForm.elements.catalogItemId.value;
        const button = $("#inventoryConfigSave");
        button.disabled = true;
        try {
            await api(`/api/v1/inventory/${encodeURIComponent(id)}`, {
                method: "PUT",
                body: JSON.stringify({
                    sku: configForm.elements.sku.value.trim() || null,
                    trackingEnabled: configForm.elements.trackingEnabled.checked,
                    onHand: Number(configForm.elements.onHand.value || 0),
                    reorderThreshold: Number(configForm.elements.reorderThreshold.value || 0),
                    note: configForm.elements.note.value.trim() || null
                })
            });
            configDialog.close();
            await reloadInventory("Inventario actualizado.");
        } catch (error) {
            showMessage(configMessage, error.message);
        } finally {
            button.disabled = false;
        }
    }

    async function saveAdjustment(event) {
        event.preventDefault();
        clearMessage(adjustMessage);
        const id = adjustForm.elements.catalogItemId.value;
        const variantId = adjustForm.elements.variantId.value;
        const delta = Number(adjustForm.elements.delta.value || 0);
        if (!Number.isInteger(delta) || delta === 0) {
            showMessage(adjustMessage, "El ajuste debe ser un número entero distinto de cero.");
            return;
        }
        const button = $("#inventoryAdjustSave");
        button.disabled = true;
        try {
            const path = variantId
                ? `/api/v1/inventory/${encodeURIComponent(id)}/variants/${encodeURIComponent(variantId)}/adjustments`
                : `/api/v1/inventory/${encodeURIComponent(id)}/adjustments`;
            const payload = variantId
                ? { delta, note: adjustForm.elements.note.value.trim() }
                : {
                    delta,
                    referenceType: "MANUAL",
                    referenceId: null,
                    note: adjustForm.elements.note.value.trim()
                };
            await api(path, {
                method: "POST",
                body: JSON.stringify(payload)
            });
            adjustDialog.close();
            if (variantId) {
                await openVariants(id);
            } else {
                await reloadInventory("Movimiento registrado.");
            }
        } catch (error) {
            showMessage(adjustMessage, error.message);
        } finally {
            button.disabled = false;
        }
    }

    async function reloadInventory(successText = "") {
        const [catalog, inventory] = await Promise.all([
            api("/api/v1/catalog"),
            api("/api/v1/inventory")
        ]);
        state.catalog = Array.isArray(catalog) ? catalog : [];
        state.inventory = Array.isArray(inventory) ? inventory : [];
        mergeProducts();
        render();
        if (successText) {
            showMessage(message, successText, "success");
            setTimeout(() => clearMessage(message), 2600);
        }
    }

    async function load() {
        if (!token) {
            showAuthRequired();
            return;
        }
        try {
            const [me, business, catalog, inventory] = await Promise.all([
                api("/api/v1/auth/me"),
                api("/api/v1/business"),
                api("/api/v1/catalog"),
                api("/api/v1/inventory")
            ]);
            state.roles = Array.isArray(me?.roles) ? me.roles.map(String) : [];
            state.canManage = state.roles.includes("BUSINESS_ADMIN");
            state.business = business || {};
            state.catalog = Array.isArray(catalog) ? catalog : [];
            state.inventory = Array.isArray(inventory) ? inventory : [];

            const businessName = String(state.business?.name || "RecepVoz").trim() || "RecepVoz";
            $("#inventoryBrand").textContent = businessName.toUpperCase();
            document.title = `${businessName} · Inventario`;

            roleBadge.textContent = state.canManage ? "Administrador" : "Solo lectura";
            roleBadge.className = `badge ${state.canManage ? "online" : "muted"}`;

            mergeProducts();
            render();
            loading.classList.add("hidden");
            app.classList.remove("hidden");
        } catch (error) {
            if (error.status === 401) return;
            loading.classList.add("hidden");
            app.classList.remove("hidden");
            showMessage(message, `No pude cargar el inventario: ${error.message}`);
        }
    }

    search.addEventListener("input", renderRows);
    filter.addEventListener("change", renderRows);
    $("#inventoryRefreshBtn").addEventListener("click", async event => {
        event.currentTarget.disabled = true;
        clearMessage(message);
        try { await reloadInventory("Datos actualizados."); }
        catch (error) { showMessage(message, error.message); }
        finally { event.currentTarget.disabled = false; }
    });

    configForm.addEventListener("submit", saveConfig);
    adjustForm.addEventListener("submit", saveAdjustment);
    variantForm.addEventListener("submit", saveVariant);
    $("#inventoryAddVariantBtn").addEventListener("click", () => openVariantForm());

    $$("[data-close-dialog]").forEach(button => {
        button.addEventListener("click", () => button.closest("dialog")?.close());
    });
    [configDialog, adjustDialog, historyDialog, variantsDialog, variantEditDialog].forEach(dialog => {
        dialog.addEventListener("click", event => {
            if (event.target === dialog) dialog.close();
        });
    });

    $("#inventoryLogoutBtn").addEventListener("click", () => {
        sessionStorage.removeItem(TOKEN_KEY);
        location.replace("/");
    });

    load();
})();
