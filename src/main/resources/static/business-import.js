(() => {
    const TOKEN_KEY = "helvoca_access_token";
    const token = sessionStorage.getItem(TOKEN_KEY) || "";
    const $ = selector => document.querySelector(selector);

    const state = {
        files: [],
        preview: null,
        business: null,
        roles: []
    };

    const loading = $("#importLoading");
    const app = $("#importApp");
    const authRequired = $("#importAuthRequired");
    const message = $("#importMessage");
    const filesInput = $("#importFiles");
    const dropzone = $("#importDropzone");
    const previewBtn = $("#importPreviewBtn");
    const applyBtn = $("#importApplyBtn");
    const rows = $("#importProductRows");

    function escapeHtml(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }

    function showMessage(text, kind = "error") {
        message.textContent = text;
        message.classList.remove("hidden", "error", "success");
        message.classList.add(kind);
    }

    function clearMessage() {
        message.textContent = "";
        message.classList.add("hidden");
        message.classList.remove("error", "success");
    }

    async function api(path, options = {}) {
        const headers = new Headers(options.headers || {});
        if (token) headers.set("Authorization", `Bearer ${token}`);
        if (options.body && !(options.body instanceof FormData) && !headers.has("Content-Type")) {
            headers.set("Content-Type", "application/json");
        }
        const response = await fetch(path, { ...options, headers });
        let payload = null;
        const contentType = response.headers.get("content-type") || "";
        if (contentType.includes("application/json")) {
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
    }

    function formatBytes(bytes) {
        if (bytes < 1024) return `${bytes} B`;
        if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
        return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    }

    function typeLabel(file) {
        const name = file.name.toLowerCase();
        if (/\.(xlsx?|csv|tsv)$/.test(name)) return "Planilla";
        if (name.endsWith(".pdf")) return "PDF";
        if (file.type.startsWith("image/")) return "Imagen";
        return "Archivo";
    }

    function setFiles(list) {
        const accepted = [...list].filter(file => file.size > 0);
        state.files = accepted.slice(0, 12);
        renderFiles();
        previewBtn.disabled = state.files.length === 0;
        $("#importPreviewSection").classList.add("hidden");
        $("#importResultSection").classList.add("hidden");
        state.preview = null;
    }

    function renderFiles() {
        const target = $("#importFileList");
        target.innerHTML = state.files.map((file, index) => `
            <div class="import-file-chip">
                <div>
                    <strong>${escapeHtml(file.name)}</strong>
                    <span>${escapeHtml(typeLabel(file))} · ${escapeHtml(formatBytes(file.size))}</span>
                </div>
                <button type="button" data-remove-file="${index}" aria-label="Quitar ${escapeHtml(file.name)}">×</button>
            </div>`).join("");

        target.querySelectorAll("[data-remove-file]").forEach(button => {
            button.addEventListener("click", () => {
                state.files.splice(Number(button.dataset.removeFile), 1);
                renderFiles();
                previewBtn.disabled = state.files.length === 0;
            });
        });
    }

    function sourceKindLabel(kind) {
        return {
            PRODUCTS: "Productos",
            SALES: "Ventas históricas",
            RECEIPTS: "Boletas/documentos",
            CUSTOMERS: "Clientes",
            MIXED: "Datos mixtos",
            UNKNOWN: "Sin clasificar"
        }[kind] || kind || "Sin clasificar";
    }

    function renderSources() {
        const target = $("#importSourceSummary");
        const sources = state.preview?.sources || [];
        target.innerHTML = sources.map(source => {
            const ok = source.recognized;
            const rowsText = source.rowCount ? `${source.rowCount} filas` : source.method === "AI" ? "analizado con IA" : "sin filas";
            return `
                <div class="import-source-card ${ok ? "ok" : "attention"}">
                    <div>
                        <strong>${escapeHtml(source.name)}</strong>
                        <span>${escapeHtml(sourceKindLabel(source.kind))} · ${escapeHtml(rowsText)}</span>
                    </div>
                    <b>${escapeHtml(source.method === "SPREADSHEET" ? "Automático" : source.method === "AI" ? "IA" : "Revisar")}</b>
                </div>`;
        }).join("");
    }

    function renderWarnings() {
        const target = $("#importWarnings");
        const warnings = state.preview?.warnings || [];
        target.classList.toggle("hidden", warnings.length === 0);
        target.innerHTML = warnings.map(value => `<div><span>!</span><p>${escapeHtml(value)}</p></div>`).join("");
    }

    function moneyValue(value) {
        if (value === null || value === undefined || value === "") return "";
        return String(value);
    }

    function stockValue(value) {
        if (value === null || value === undefined || value === "") return "";
        return String(value);
    }

    function renderProducts() {
        const products = state.preview?.products || [];
        $("#importDetectedCount").textContent = `${products.length} producto${products.length === 1 ? "" : "s"}`;
        $("#importNoProducts").classList.toggle("hidden", products.length > 0);
        rows.innerHTML = products.map((product, index) => {
            const confidence = Math.round(Number(product.confidence || 0) * 100);
            return `
                <tr data-import-row="${index}">
                    <td data-label="Importar"><input class="import-row-check" type="checkbox" checked aria-label="Importar ${escapeHtml(product.name)}"></td>
                    <td data-label="Producto">
                        <input class="import-cell import-name" maxlength="150" value="${escapeHtml(product.name)}">
                        <input class="import-cell import-description" maxlength="500" placeholder="Descripción opcional" value="${escapeHtml(product.description || "")}">
                    </td>
                    <td data-label="Categoría"><input class="import-cell import-category" maxlength="120" value="${escapeHtml(product.category || "")}" placeholder="Sin categoría"></td>
                    <td data-label="SKU"><input class="import-cell import-sku" maxlength="80" value="${escapeHtml(product.sku || "")}" placeholder="Opcional"></td>
                    <td data-label="Precio"><input class="import-cell import-price" type="number" min="0" step="0.01" value="${escapeHtml(moneyValue(product.price))}" placeholder="Sin dato"></td>
                    <td data-label="Stock"><input class="import-cell import-stock" type="number" min="0" step="1" value="${escapeHtml(stockValue(product.onHand))}" placeholder="Sin dato"></td>
                    <td data-label="Origen">
                        <div class="import-origin"><strong>${escapeHtml(product.sourceName || "archivo")}</strong><span>${confidence}% confianza</span></div>
                    </td>
                </tr>`;
        }).join("");

        rows.querySelectorAll(".import-row-check").forEach(input => input.addEventListener("change", updateSelected));
        rows.querySelectorAll(".import-cell").forEach(input => input.addEventListener("input", updateSelected));
        updateSelected();
    }

    function selectedRows() {
        return [...rows.querySelectorAll("[data-import-row]")].filter(row => row.querySelector(".import-row-check")?.checked);
    }

    function updateSelected() {
        const selected = selectedRows();
        $("#importSelectedText").textContent = `${selected.length} seleccionado${selected.length === 1 ? "" : "s"}`;
        applyBtn.disabled = selected.length === 0;
    }

    async function preview() {
        clearMessage();
        const businessName = $("#importBusinessName").value.trim();
        if (!businessName) {
            showMessage("Escribe el nombre del negocio antes de analizar.");
            $("#importBusinessName").focus();
            return;
        }
        if (!state.files.length) {
            showMessage("Selecciona al menos un archivo.");
            return;
        }

        previewBtn.disabled = true;
        previewBtn.textContent = "Analizando…";
        const form = new FormData();
        form.append("businessName", businessName);
        state.files.forEach(file => form.append("files", file, file.name));

        try {
            state.preview = await api("/api/v1/onboarding/import/preview", {
                method: "POST",
                body: form
            });
            renderSources();
            renderWarnings();
            renderProducts();
            $("#importPreviewSection").classList.remove("hidden");
            $("#importResultSection").classList.add("hidden");
            $("#importPreviewSection").scrollIntoView({ behavior: "smooth", block: "start" });
        } catch (error) {
            showMessage(`No pude crear el borrador: ${error.message}`);
        } finally {
            previewBtn.disabled = state.files.length === 0;
            previewBtn.textContent = "Analizar y crear borrador";
        }
    }

    function parseOptionalNumber(value) {
        const text = String(value ?? "").trim();
        if (!text) return null;
        const number = Number(text);
        return Number.isFinite(number) && number >= 0 ? number : NaN;
    }

    function buildPayload() {
        const products = [];
        for (const row of selectedRows()) {
            const name = row.querySelector(".import-name").value.trim();
            const price = parseOptionalNumber(row.querySelector(".import-price").value);
            const stock = parseOptionalNumber(row.querySelector(".import-stock").value);
            if (!name) throw new Error("Todos los productos seleccionados necesitan nombre.");
            if (Number.isNaN(price)) throw new Error(`Precio inválido en ${name}.`);
            if (Number.isNaN(stock) || (stock !== null && !Number.isInteger(stock))) {
                throw new Error(`El stock de ${name} debe ser un entero igual o mayor que cero.`);
            }
            const index = Number(row.dataset.importRow);
            const original = state.preview.products[index];
            products.push({
                name,
                description: row.querySelector(".import-description").value.trim() || null,
                price,
                currency: original.currency || "CLP",
                sku: row.querySelector(".import-sku").value.trim() || null,
                onHand: stock,
                category: row.querySelector(".import-category").value.trim() || null,
                sourceName: original.sourceName || null
            });
        }
        return { products };
    }

    async function applyImport() {
        clearMessage();
        let payload;
        try {
            payload = buildPayload();
        } catch (error) {
            showMessage(error.message);
            return;
        }
        if (!payload.products.length) {
            showMessage("Selecciona al menos un producto.");
            return;
        }

        applyBtn.disabled = true;
        applyBtn.textContent = "Importando…";
        try {
            const result = await api("/api/v1/onboarding/import/apply", {
                method: "POST",
                body: JSON.stringify(payload)
            });
            $("#importCreatedCount").textContent = result.created ?? 0;
            $("#importUpdatedCount").textContent = result.updated ?? 0;
            $("#importInventoryCount").textContent = result.inventoryConfigured ?? 0;
            $("#importResultSection").classList.remove("hidden");
            $("#importResultSection").scrollIntoView({ behavior: "smooth", block: "center" });
            showMessage("Importación aplicada correctamente.", "success");
        } catch (error) {
            showMessage(`No pude aplicar la importación: ${error.message}`);
        } finally {
            applyBtn.disabled = false;
            applyBtn.textContent = "Importar al negocio";
            updateSelected();
        }
    }

    async function load() {
        if (!token) {
            showAuthRequired();
            return;
        }
        try {
            const [me, business] = await Promise.all([
                api("/api/v1/auth/me"),
                api("/api/v1/business")
            ]);
            state.roles = Array.isArray(me?.roles) ? me.roles.map(String) : [];
            state.business = business || {};
            if (!state.roles.includes("BUSINESS_ADMIN")) {
                loading.classList.add("hidden");
                app.classList.remove("hidden");
                showMessage("Necesitas rol de administrador para importar datos del negocio.");
                previewBtn.disabled = true;
                return;
            }
            const businessName = String(state.business?.name || "").trim();
            $("#importBusinessName").value = businessName;
            $("#importBrand").textContent = (businessName || "RECEPVOZ").toUpperCase();
            $("#importRoleBadge").textContent = "Administrador";
            $("#importRoleBadge").className = "badge online";
            document.title = `${businessName || "RecepVoz"} · Importar negocio`;
            loading.classList.add("hidden");
            app.classList.remove("hidden");
        } catch (error) {
            if (error.status === 401) return;
            loading.classList.add("hidden");
            app.classList.remove("hidden");
            showMessage(`No pude preparar el importador: ${error.message}`);
        }
    }

    filesInput.addEventListener("change", () => setFiles(filesInput.files));
    ["dragenter", "dragover"].forEach(name => dropzone.addEventListener(name, event => {
        event.preventDefault();
        dropzone.classList.add("dragging");
    }));
    ["dragleave", "drop"].forEach(name => dropzone.addEventListener(name, event => {
        event.preventDefault();
        dropzone.classList.remove("dragging");
    }));
    dropzone.addEventListener("drop", event => setFiles(event.dataTransfer.files));

    previewBtn.addEventListener("click", preview);
    applyBtn.addEventListener("click", applyImport);
    $("#importSelectAllBtn").addEventListener("click", () => {
        const checks = [...rows.querySelectorAll(".import-row-check")];
        const shouldSelect = checks.some(input => !input.checked);
        checks.forEach(input => { input.checked = shouldSelect; });
        updateSelected();
    });
    $("#importAnotherBtn").addEventListener("click", () => {
        state.preview = null;
        state.files = [];
        filesInput.value = "";
        renderFiles();
        $("#importPreviewSection").classList.add("hidden");
        $("#importResultSection").classList.add("hidden");
        previewBtn.disabled = true;
        clearMessage();
        window.scrollTo({ top: 0, behavior: "smooth" });
    });
    $("#importLogoutBtn").addEventListener("click", () => {
        sessionStorage.removeItem(TOKEN_KEY);
        location.href = "/";
    });

    load();
})();