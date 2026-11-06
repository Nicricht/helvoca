import { createReadStream } from "node:fs";
import { stat } from "node:fs/promises";
import { createServer } from "node:http";
import { extname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = resolve(process.env.PREVIEW_PUBLIC_ROOT ?? "public");
const PORT = Number(process.env.PORT || 8080);
const DEMO_TOKEN = "recepvoz-preview-fixture";
const SECTORS = Object.freeze({
  restaurant: { label: "Pizzería demostración", nextStep: "ADD_SERVICE", presetKey: "restaurant" },
  veterinary: { label: "Veterinaria demostración", nextStep: "CONFIGURE_HOURS", presetKey: "veterinary" },
  hardware_store: { label: "Ferretería demostración", nextStep: "CONFIGURE_BUSINESS", presetKey: "hardware_store" },
  clinic: { label: "Clínica demostración", nextStep: "READY", presetKey: "clinic" }
});
const STATIC_TYPES = Object.freeze({
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".svg": "image/svg+xml",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".ico": "image/x-icon",
  ".webp": "image/webp",
  ".woff2": "font/woff2"
});
const SECURITY = Object.freeze({
  "Cache-Control": "no-store",
  "X-Content-Type-Options": "nosniff",
  "Referrer-Policy": "no-referrer",
  "X-Frame-Options": "DENY",
  "Content-Security-Policy": "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"
});

function respond(res, status, payload, headers = {}) {
  res.writeHead(status, { ...SECURITY, ...headers });
  res.end(payload);
}

function json(res, status, value) {
  respond(res, status, JSON.stringify(value), { "Content-Type": "application/json; charset=utf-8" });
}

function context(req) {
  const cookie = String(req.headers.cookie ?? "").split(";")
    .map(value => value.trim())
    .find(value => value.startsWith("rv_preview_sector="));
  const value = cookie?.split("=")[1] ?? "restaurant";
  return Object.hasOwn(SECTORS, value) ? SECTORS[value] : SECTORS.restaurant;
}

function fixture(req, pathname) {
  const selected = context(req);
  if (pathname === "/api/v1/auth/me") return { email: "demo@recepvoz.invalid", roles: ["BUSINESS_ADMIN"], permissions: ["BUSINESS_READ", "BUSINESS_CONFIGURE"] };
  if (pathname === "/api/v1/business") return { name: selected.label, timezone: "America/Santiago", language: "es", humanTransferPhone: null };
  if (pathname === "/api/v1/business/profile") return { presetKey: selected.presetKey, countryCode: "CL", defaultCurrency: "CLP", usesReservations: selected.presetKey !== "hardware_store", sellsServices: selected.presetKey !== "hardware_store", sellsProducts: selected.presetKey !== "clinic" };
  if (pathname === "/api/v1/onboarding/status") return {
    businessProfileConfigured: selected.nextStep !== "CONFIGURE_BUSINESS",
    servicesConfigured: selected.nextStep !== "ADD_SERVICE",
    scheduleConfigured: selected.nextStep !== "CONFIGURE_HOURS",
    knowledgeConfigured: true,
    humanTransferConfigured: false,
    phoneConfigured: selected.nextStep === "READY",
    readyForCalls: selected.nextStep === "READY",
    nextStep: selected.nextStep
  };
  if (pathname === "/api/v1/services") return [{ id: "example-service", name: "Servicio de ejemplo", description: "Dato ficticio", durationMinutes: 30, price: null, active: true }];
  if (pathname === "/api/v1/knowledge") return [{ id: "example-faq", title: "Atención", category: "General", content: "Horario sujeto a confirmación del negocio.", active: true }];
  if (pathname === "/api/v1/business/hours") return [{ dayOfWeek: 1, openTime: "09:00", closeTime: "18:00" }];
  if (pathname === "/api/v1/ai-agent") return { name: "Recepcionista DEMO", language: "es", greeting: "Hola, esta es una demostración.", instructions: "No realizar acciones externas.", active: true, capabilities: [] };
  if (pathname === "/api/v1/ai-agent/voices") return [];
  if (pathname === "/api/v1/phone-numbers") return [];
  if (pathname === "/api/v1/billing/subscription" || pathname === "/api/v1/subscriptions/current") return { planName: "Demostración sin consumo", includedMinutes: 0, usedMinutes: 0 };
  return undefined;
}

function previewPage(sector) {
  return `<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>RecepVoz · Vista previa aislada</title></head><body><p>Abriendo vista previa con datos ficticios…</p><script>sessionStorage.setItem("helvoca_access_token", "${DEMO_TOKEN}");location.replace("/app/settings");</script></body></html>`;
}

export async function handle(req, res) {
  const method = req.method ?? "GET";
  if (method !== "GET" && method !== "HEAD") {
    json(res, 405, { message: "Vista previa de solo lectura; no se permiten cambios." });
    return;
  }

  let url;
  try { url = new URL(req.url ?? "/", "http://demo.invalid"); }
  catch { json(res, 400, { message: "Ruta no válida" }); return; }
  const pathname = url.pathname;
  if (pathname === "/healthz") { json(res, 200, { status: "UP", mode: "READ_ONLY_FIXTURE" }); return; }
  if (pathname === "/") { respond(res, 302, "", { Location: "/preview" }); return; }
  if (pathname === "/preview") {
    const rawSector = url.searchParams.get("sector") ?? "restaurant";
    const sector = Object.hasOwn(SECTORS, rawSector) ? rawSector : "restaurant";
    respond(res, 200, previewPage(sector), {
      "Content-Type": "text/html; charset=utf-8",
      "Set-Cookie": `rv_preview_sector=${sector}; HttpOnly; SameSite=Lax; Path=/`
    });
    return;
  }

  if (pathname.startsWith("/api/")) {
    if (req.headers.authorization !== `Bearer ${DEMO_TOKEN}`) {
      json(res, 401, { message: "Esta vista previa requiere su sesión ficticia." });
      return;
    }
    const data = fixture(req, pathname);
    if (data === undefined) json(res, 404, { message: "Endpoint no simulado en esta vista previa." });
    else json(res, 200, data);
    return;
  }

  if (pathname === "/service-worker.js") { json(res, 404, { message: "Sin PWA en vista previa." }); return; }
  if (!pathname.startsWith("/app/") && pathname !== "/frontend-foundation.css") {
    json(res, 404, { message: "Ruta no disponible" });
    return;
  }

  const decoded = (() => { try { return decodeURIComponent(pathname); } catch { return ""; } })();
  if (!decoded || decoded.includes("\\") || decoded.split("/").includes("..") || decoded.includes("\0")) {
    json(res, 400, { message: "Ruta no válida" }); return;
  }
  let filename = resolve(ROOT, "." + decoded);
  if (!relative(ROOT, filename) || relative(ROOT, filename).startsWith("..")) {
    json(res, 400, { message: "Ruta no válida" }); return;
  }

  let info;
  try { info = await stat(filename); } catch { info = null; }
  if (!info?.isFile()) {
    if (!decoded.startsWith("/app/assets/") && !extname(decoded)) {
      filename = join(ROOT, "app", "index.html");
      try { info = await stat(filename); } catch { info = null; }
    }
    if (!info?.isFile()) { json(res, 404, { message: "Archivo no encontrado" }); return; }
  }

  const html = filename.endsWith(".html");
  if (html) {
    const { readFile } = await import("node:fs/promises");
    const raw = await readFile(filename, "utf8");
    const banner = '<div role="note" aria-label="Vista previa de datos ficticios" style="position:fixed;bottom:0;left:0;right:0;z-index:2147483647;background:#092e41;color:white;font:600 13px system-ui;padding:9px;text-align:center">DEMO AISLADA · DATOS FICTICIOS · SIN LLAMADAS, WHATSAPP NI PAGOS REALES</div>';
    respond(res, 200, raw.replace("</body>", banner + "</body>"), { "Content-Type": STATIC_TYPES[".html"] });
    return;
  }
  res.writeHead(200, { ...SECURITY, "Content-Type": STATIC_TYPES[extname(filename)] ?? "application/octet-stream" });
  if (method === "HEAD") res.end();
  else createReadStream(filename).pipe(res);
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  createServer((req, res) => void handle(req, res).catch(() => json(res, 500, { message: "Error interno de vista previa" })))
    .listen(PORT, "0.0.0.0", () => console.log(`Read-only RecepVoz preview listening on ${PORT}`));
}
