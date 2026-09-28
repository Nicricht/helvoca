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
    const sort = $("#inventorySort");
    const roleBadge = $("#inventoryRoleBadge");

    const productDialog = $("#inventoryProductDialog");
    const productForm = $("#inventoryProductForm");
    const productMessage = $("#inventoryProductMessage");
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
        alerts: [],
        restockSubscriptions: [],
        restockNotifications: [],
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

    const alertLabels = {
        LOW_STOCK: "Stock bajo",
        OUT_OF_STOCK: "Agotado",
        RESTOCKED: "Repuesto"
    };

    function escapeHtml(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }

    function formatMoney(value, currency = "CLP") {
        if (value === null || value === undefined || value === "") {
            return '<span class="inventory-sku missing">Sin precio</span>';
        }
        try {
            return escapeHtml(new Intl.NumberFormat("es-CL", {
                style: "currency",
                currency: currency || "CLP",
                maximumFractionDigits: (currency || "CLP") === "CLP" ? 0 : 2
            }).format(Number(value)));
        } catch (_) {
            return escapeHtml(String(value));
        }
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

    function renderAlerts() {
        const list = $("#inventoryAlertsList");
        const emptyAlerts = $("#inventoryAlertsEmpty");
        const alerts = Array.isArray(state.alerts) ? state.alerts : [];
        const pending = alerts.filter(alert => !alert.acknowledged).length;
        $("#inventoryAlertsCount").textContent =
            `${pending} pendiente${pending === 1 ? "" : "s"}`;
        $("#inventoryAlertsCount").className =
            `badge ${pending > 0 ? "online" : "muted"}`;

        emptyAlerts.classList.toggle("hidden", alerts.length > 0);
        list.innerHTML = alerts.map(alert => {
            const typeClass = alert.type === "OUT_OF_STOCK"
                ? "out"
                : alert.type === "RESTOCKED" ? "restocked" : "low";
            const icon = alert.type === "OUT_OF_STOCK"
                ? "0"
                : alert.type === "RESTOCKED" ? "↥" : "!";
            const detail = alert.type === "RESTOCKED"
                ? `Disponible nuevamente: ${Number(alert.available)}.`
                : `Disponible: ${Number(alert.available)} · mínimo: ${Number(alert.reorderThreshold)}.`;
            const sku = alert.sku ? ` · SKU ${alert.sku}` : "";
            const restockAction = state.canManage
                && !alert.acknowledged
                && (alert.type === "LOW_STOCK" || alert.type === "OUT_OF_STOCK")
                ? `<button class="button secondary inventory-alert-restock" type="button"
                           data-product-id="${escapeHtml(alert.catalogItemId)}"
                           data-variant-id="${escapeHtml(alert.variantId || "")}">Reponer stock</button>`
                : "";
            const acknowledgeAction = state.canManage && !alert.acknowledged
                ? `<button class="button ghost inventory-alert-ack" type="button" data-id="${escapeHtml(alert.id)}">Marcar atendida</button>`
                : alert.acknowledged
                    ? '<span class="inventory-sku missing">Atendida</span>'
                    : "";
            const action = restockAction + acknowledgeAction;
            return `
                <article class="inventory-alert-card ${typeClass} ${alert.acknowledged ? "acknowledged" : ""}"
                         data-inventory-alert-id="${escapeHtml(alert.id)}">
                    <div class="inventory-alert-icon">${icon}</div>
                    <div class="inventory-alert-main">
                        <strong>${escapeHtml(alertLabels[alert.type] || alert.type)} · ${escapeHtml(alert.subjectName)}</strong>
                        <small>${escapeHtml(detail + sku)}</small>
                    </div>
                    <div class="inventory-alert-action">${action}</div>
                </article>`;
        }).join("");

        list.querySelectorAll(".inventory-alert-restock").forEach(button => {
            button.addEventListener("click", async () => {
                button.disabled = true;
                try {
                    await openRestock(
                        button.dataset.productId,
                        button.dataset.variantId || null);
                } catch (error) {
                    showMessage(message, error.message);
                } finally {
                    button.disabled = false;
                }
            });
        });

        list.querySelectorAll(".inventory-alert-ack").forEach(button => {
            button.addEventListener("click", async () => {
                button.disabled = true;
                try {
                    await api(
                        `/api/v1/inventory/alerts/${encodeURIComponent(button.dataset.id)}/acknowledge`,
                        { method: "POST" });
                    await reloadAlerts();
                } catch (error) {
                    showMessage(message, error.message);
                } finally {
                    button.disabled = false;
                }
            });
        });
    }

    async function reloadAlerts() {
        const alerts = await api("/api/v1/inventory/alerts");
        state.alerts = Array.isArray(alerts) ? alerts : [];
        renderAlerts();
    }

    function productHasAlert(item, type) {
        return state.alerts.some(alert =>
            String(alert.catalogItemId) === String(item.id) && alert.type === type);
    }

    function restockProductName(subscription) {
        return product(subscription.catalogItemId)?.name || "Producto";
    }

    function formatRestockChannel(channel) {
        if (channel === "WHATSAPP") return "WhatsApp";
        if (channel === "SMS") return "SMS";
        if (channel === "EMAIL") return "Email";
        return channel || "Canal";
    }

    function renderRestockQueue() {
        const subscriptions = Array.isArray(state.restockSubscriptions)
            ? state.restockSubscriptions
            : [];
        const notifications = Array.isArray(state.restockNotifications)
            ? state.restockNotifications
            : [];
        $("#inventoryRestockCount").textContent =
            `${subscriptions.length} esperando`;
        $("#inventoryRestockCount").className =
            `badge ${subscriptions.length ? "online" : "muted"}`;
        $("#inventoryPendingNotificationCount").textContent =
            `${notifications.length} aviso${notifications.length === 1 ? "" : "s"} listo${notifications.length === 1 ? "" : "s"}`;
        $("#inventoryPendingNotificationCount").className =
            `badge ${notifications.length ? "warning" : "muted"}`;

        const list = $("#inventoryRestockList");
        const emptyRestock = $("#inventoryRestockEmpty");
        emptyRestock.classList.toggle("hidden", subscriptions.length > 0 || notifications.length > 0);

        const waiting = subscriptions.map(subscription => {
            const productName = restockProductName(subscription);
            const subject = subscription.variantId
                ? `${productName} · variante específica`
                : productName;
            const cancel = state.canManage
                ? `<button class="button ghost inventory-restock-cancel" type="button"
                           data-id="${escapeHtml(subscription.id)}">Cancelar aviso</button>`
                : "";
            return `
                <article class="inventory-restock-card" data-restock-subscription-id="${escapeHtml(subscription.id)}">
                    <div class="inventory-restock-status waiting">Esperando</div>
                    <div class="inventory-restock-main">
                        <strong>${escapeHtml(subject)}</strong>
                        <small>${escapeHtml(formatRestockChannel(subscription.preferredChannel))} · ${escapeHtml(subscription.contact)}</small>
                    </div>
                    <div class="inventory-restock-action">${cancel}</div>
                </article>`;
        });

        const queued = notifications.map(notification => `
            <article class="inventory-restock-card queued" data-restock-notification-id="${escapeHtml(notification.id)}">
                <div class="inventory-restock-status ready">Aviso listo</div>
                <div class="inventory-restock-main">
                    <strong>${escapeHtml(notification.subjectName || restockProductName(notification))}</strong>
                    <small>${escapeHtml(formatRestockChannel(notification.preferredChannel))} · ${escapeHtml(notification.contact)} · disponible: ${Number(notification.available || 0)}</small>
                </div>
                <div class="inventory-restock-action"><span class="inventory-sku missing">Pendiente de envío</span></div>
            </article>`);

        list.innerHTML = waiting.concat(queued).join("");

        list.querySelectorAll(".inventory-restock-cancel").forEach(button => {
            button.addEventListener("click", async () => {
                button.disabled = true;
                try {
                    await api(
                        `/api/v1/inventory/restock-subscriptions/${encodeURIComponent(button.dataset.id)}/cancel`,
                        { method: "POST" });
                    await reloadRestockQueue();
                    showMessage(message, "Aviso de reposición cancelado.", "success");
                    setTimeout(() => clearMessage(message), 2200);
                } catch (error) {
                    showMessage(message, error.message);
                } finally {
                    button.disabled = false;
                }
            });
        });
    }

    async function reloadRestockQueue() {
        const [subscriptions, notifications] = await Promise.all([
            api("/api/v1/inventory/restock-subscriptions"),
            api("/api/v1/inventory/restock-subscriptions/notifications")
        ]);
        state.restockSubscriptions = Array.isArray(subscriptions) ? subscriptions : [];
        state.restockNotifications = Array.isArray(notifications) ? notifications : [];
        renderRestockQueue();
    }

    function productMatches(item) {
        const query = search.value.trim().toLowerCase();
        if (query && !`${item.name} ${item.sku} ${item.description}`.toLowerCase().includes(query)) return false;
        switch (filter.value) {
            case "TRACKED": return item.configured && item.trackingEnabled;
            case "LOW": return item.configured && item.trackingEnabled
                && (item.lowStock || productHasAlert(item, "LOW_STOCK"));
            case "OUT": return item.configured && item.trackingEnabled
                && (Number(item.available) === 0 || productHasAlert(item, "OUT_OF_STOCK"));
            case "RESTOCKED": return productHasAlert(item, "RESTOCKED");
            case "UNCONFIGURED": return !item.configured || !item.trackingEnabled;
            default: return true;
        }
    }

    function attentionRank(item) {
        if (item.configured && item.trackingEnabled && Number(item.available) === 0) return 0;
        if (item.configured && item.trackingEnabled && (item.lowStock || productHasAlert(item, "LOW_STOCK"))) return 1;
        if (!item.configured || !item.trackingEnabled) return 2;
        return 3;
    }

    function sortProducts(items) {
        const next = [...items];
        switch (sort?.value) {
            case "NAME_ASC":
                return next.sort((a, b) => String(a.name || "").localeCompare(String(b.name || ""), "es"));
            case "AVAILABLE_ASC":
                return next.sort((a, b) => {
                    const av = a.available === null || a.available === undefined ? Number.POSITIVE_INFINITY : Number(a.available);
                    const bv = b.available === null || b.available === undefined ? Number.POSITIVE_INFINITY : Number(b.available);
                    return av - bv || String(a.name || "").localeCompare(String(b.name || ""), "es");
                });
            case "AVAILABLE_DESC":
                return next.sort((a, b) => {
                    const av = a.available === null || a.available === undefined ? Number.NEGATIVE_INFINITY : Number(a.available);
                    const bv = b.available === null || b.available === undefined ? Number.NEGATIVE_INFINITY : Number(b.available);
                    return bv - av || String(a.name || "").localeCompare(String(b.name || ""), "es");
                });
            default:
                return next.sort((a, b) =>
                    attentionRank(a) - attentionRank(b)
                    || String(a.name || "").localeCompare(String(b.name || ""), "es"));
        }
    }

    function stateBadge(item) {
        if (!item.configured || !item.trackingEnabled) {
            return '<span class="inventory-state off">Sin seguimiento</span>';
        }
        if (Number(item.available) === 0) return '<span class="inventory-state out">Agotado</span>';
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
        const editProduct = `<button class="button ghost inventory-product-edit-btn" type="button" data-id="${item.id}" aria-label="Editar producto ${escapeHtml(item.name)}">Producto</button>`;
        const configureLabel = item.configured ? "Editar stock" : "Configurar stock";
        const configure = `<button class="button ghost inventory-config-btn" type="button" data-id="${item.id}" aria-label="${configureLabel} de ${escapeHtml(item.name)}">${configureLabel}</button>`;
        const needsRestock = item.configured && item.trackingEnabled
            && Number(item.available) <= Number(item.reorderThreshold || 0);
        const adjust = item.configured && item.trackingEnabled
            ? `<button class="button ${needsRestock ? "primary" : "secondary"} inventory-adjust-btn" type="button" data-id="${item.id}">${needsRestock ? "Reponer" : "Ajustar"}</button>`
            : "";
        return editProduct + variants + configure + adjust + history;
    }

    function renderRows() {
        const visible = sortProducts(state.products.filter(productMatches));
        rows.innerHTML = visible.map(item => `
            <tr data-inventory-product-id="${escapeHtml(item.id)}">
                <td class="inventory-product" data-label="Producto">
                    <strong>${escapeHtml(item.name)}</strong>
                    <small>${escapeHtml(item.description || "Producto del catálogo")}</small>
                </td>
                <td data-label="SKU"><span class="inventory-sku ${item.sku ? "" : "missing"}">${escapeHtml(item.sku || "Sin SKU")}</span></td>
                <td data-label="Precio"><span class="inventory-price">${formatMoney(item.price, item.currency)}</span></td>
                <td data-label="Físico">${numberCell(item.onHand)}</td>
                <td data-label="Reservado">${numberCell(item.reserved, "reserved")}</td>
                <td data-label="Disponible">${numberCell(item.available, "available")}</td>
                <td data-label="Mínimo">${numberCell(item.reorderThreshold)}</td>
                <td data-label="Estado">${stateBadge(item)}</td>
                <td data-label="Acciones"><div class="inventory-row-actions">${actionButtons(item)}</div></td>
            </tr>
        `).join("");
        empty.classList.toggle("hidden", visible.length > 0);
        bindRowActions();
    }

    function bindRowActions() {
        $(".inventory-product-edit-btn", rows).forEach(button => {
            button.addEventListener("click", () => openProductForm(button.dataset.id));
        });
        $(".inventory-variants-btn", rows).forEach(button => {
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
        renderAlerts();
        renderRestockQueue();
        renderRows();
    }

    function product(id) {
        return state.products.find(item => String(item.id) === String(id));
    }

    function openProductForm(id = null) {
        if (!state.canManage) return;
        const item = id ? product(id) : null;
        clearMessage(productMessage);
        productForm.reset();
        productForm.elements.catalogItemId.value = item?.id || "";
        productForm.elements.name.value = item?.name || "";
        productForm.elements.description.value = item?.description || "";
        productForm.elements.price.value = item?.price ?? "";
        productForm.elements.currency.value = String(item?.currency || "CLP").toUpperCase();
        $("#inventoryProductTitle").textContent = item ? `Editar · ${item.name}` : "Nuevo producto";
        $("#inventoryProductSave").textContent = item ? "Guardar producto" : "Crear producto";
        productDialog.showModal();
        setTimeout(() => productForm.elements.name.focus(), 0);
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

    async function openRestock(productId, variantId = null) {
        if (!state.canManage) return;
        if (variantId) {
            await openVariants(productId);
            const variant = findVariant(variantId);
            if (!variant) throw new Error("La variante ya no está disponible.");
            openVariantAdjust(variantId);
            return;
        }
        openAdjust(productId);
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
            await Promise.all([reloadAlerts(), reloadRestockQueue()]);
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

    async function saveProduct(event) {
        event.preventDefault();
        if (!state.canManage) return;
        clearMessage(productMessage);

        const id = productForm.elements.catalogItemId.value;
        const name = productForm.elements.name.value.trim();
        const description = productForm.elements.description.value.trim();
        const currency = productForm.elements.currency.value.trim().toUpperCase();
        const priceText = productForm.elements.price.value.trim();
        const price = Number(priceText);

        if (!name) {
            showMessage(productMessage, "Escribe un nombre para el producto.");
            return;
        }
        if (priceText === "" || !Number.isFinite(price) || price < 0) {
            showMessage(productMessage, "El precio debe ser un número igual o mayor que cero.");
            return;
        }
        if (!/^[A-Z]{3}$/.test(currency)) {
            showMessage(productMessage, "La moneda debe tener tres letras, por ejemplo CLP.");
            return;
        }

        const button = $("#inventoryProductSave");
        button.disabled = true;
        try {
            await api(id ? `/api/v1/catalog/${encodeURIComponent(id)}` : "/api/v1/catalog", {
                method: id ? "PUT" : "POST",
                body: JSON.stringify({
                    kind: "PRODUCT",
                    name,
                    description: description || null,
                    price,
                    currency,
                    durationMinutes: null,
                    metadataJson: null,
                    active: true
                })
            });
            productDialog.close();
            await reloadInventory(id ? "Producto actualizado." : "Producto creado.");
        } catch (error) {
            showMessage(productMessage, error.message);
        } finally {
            button.disabled = false;
        }
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
                await Promise.all([reloadAlerts(), reloadRestockQueue()]);
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
        const [catalog, inventory, alerts, subscriptions, notifications] = await Promise.all([
            api("/api/v1/catalog"),
            api("/api/v1/inventory"),
            api("/api/v1/inventory/alerts"),
            api("/api/v1/inventory/restock-subscriptions"),
            api("/api/v1/inventory/restock-subscriptions/notifications")
        ]);
        state.catalog = Array.isArray(catalog) ? catalog : [];
        state.inventory = Array.isArray(inventory) ? inventory : [];
        state.alerts = Array.isArray(alerts) ? alerts : [];
        state.restockSubscriptions = Array.isArray(subscriptions) ? subscriptions : [];
        state.restockNotifications = Array.isArray(notifications) ? notifications : [];
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
            const [me, business, catalog, inventory, alerts, subscriptions, notifications] = await Promise.all([
                api("/api/v1/auth/me"),
                api("/api/v1/business"),
                api("/api/v1/catalog"),
                api("/api/v1/inventory"),
                api("/api/v1/inventory/alerts"),
                api("/api/v1/inventory/restock-subscriptions"),
                api("/api/v1/inventory/restock-subscriptions/notifications")
            ]);
            state.roles = Array.isArray(me?.roles) ? me.roles.map(String) : [];
            state.canManage = state.roles.includes("BUSINESS_ADMIN");
            state.business = business || {};
            state.catalog = Array.isArray(catalog) ? catalog : [];
            state.inventory = Array.isArray(inventory) ? inventory : [];
            state.alerts = Array.isArray(alerts) ? alerts : [];
            state.restockSubscriptions = Array.isArray(subscriptions) ? subscriptions : [];
            state.restockNotifications = Array.isArray(notifications) ? notifications : [];

            const businessName = String(state.business?.name || "RecepVoz").trim() || "RecepVoz";
            $("#inventoryBrand").textContent = businessName.toUpperCase();
            document.title = `${businessName} · Inventario`;

            roleBadge.textContent = state.canManage ? "Administrador" : "Solo lectura";
            roleBadge.className = `badge ${state.canManage ? "online" : "muted"}`;
            $("#inventoryAddProductBtn").classList.toggle("hidden", !state.canManage);

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
    sort?.addEventListener("change", renderRows);
    $("#inventoryAddProductBtn").addEventListener("click", () => openProductForm());
    $("#inventoryRefreshBtn").addEventListener("click", async event => {
        event.currentTarget.disabled = true;
        clearMessage(message);
        try { await reloadInventory("Datos actualizados."); }
        catch (error) { showMessage(message, error.message); }
        finally { event.currentTarget.disabled = false; }
    });

    productForm.addEventListener("submit", saveProduct);
    configForm.addEventListener("submit", saveConfig);
    adjustForm.addEventListener("submit", saveAdjustment);
    variantForm.addEventListener("submit", saveVariant);
    $("#inventoryAddVariantBtn").addEventListener("click", () => openVariantForm());

    $$("[data-close-dialog]").forEach(button => {
        button.addEventListener("click", () => button.closest("dialog")?.close());
    });
    [productDialog, configDialog, adjustDialog, historyDialog, variantsDialog, variantEditDialog].forEach(dialog => {
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
