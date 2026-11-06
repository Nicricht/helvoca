import assert from "node:assert/strict";
import { createServer } from "node:http";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, before, test } from "node:test";

const root = mkdtempSync(join(tmpdir(), "rv-static-preview-"));
mkdirSync(join(root, "app", "assets"), { recursive: true });
writeFileSync(join(root, "app", "index.html"), "<html><body><main>FICTITIOUS DEMO APP</main></body></html>");
writeFileSync(join(root, "app", "assets", "example.js"), "console.log('fixture');");
writeFileSync(join(root, "frontend-foundation.css"), "html{color:white}");
process.env.PREVIEW_PUBLIC_ROOT = root;
const { handle } = await import("./server.mjs");
let server;
let origin;
const auth = { Authorization: "Bearer recepvoz-preview-fixture" };

before(async () => {
  server = createServer((req, res) => void handle(req, res));
  await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
  origin = `http://127.0.0.1:${server.address().port}`;
});
after(async () => {
  await new Promise(resolve => server.close(resolve));
  rmSync(root, { recursive: true, force: true });
});

test("health is explicitly read-only and no-store", async () => {
  const response = await fetch(origin + "/healthz");
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { status: "UP", mode: "READ_ONLY_FIXTURE" });
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.equal(response.headers.get("x-content-type-options"), "nosniff");
  assert.match(response.headers.get("content-security-policy"), /connect-src 'self'/);
});

test("root redirects only to isolated preview; preview issues an example session", async () => {
  const redirect = await fetch(origin + "/", { redirect: "manual" });
  assert.equal(redirect.status, 302);
  assert.equal(redirect.headers.get("location"), "/preview");
  const page = await fetch(origin + "/preview?sector=veterinary");
  const html = await page.text();
  assert.match(html, /recepvoz-preview-fixture/);
  assert.match(html, /\/app\/settings/);
  assert.match(page.headers.get("set-cookie"), /rv_preview_sector=veterinary; HttpOnly; SameSite=Lax/);
  const invalid = await fetch(origin + "/preview?sector=nonexistent");
  assert.match(invalid.headers.get("set-cookie"), /rv_preview_sector=restaurant/);
});

test("fake API refuses unauthenticated requests and unknown endpoints", async () => {
  const denied = await fetch(origin + "/api/v1/auth/me");
  assert.equal(denied.status, 401);
  const expired = await fetch(origin + "/api/v1/auth/me", { headers: { Authorization: "Bearer real-token" } });
  assert.equal(expired.status, 401);
  const known = await fetch(origin + "/api/v1/auth/me", { headers: auth });
  assert.equal(known.status, 200);
  const identity = await known.json();
  assert.deepEqual(identity.roles, ["BUSINESS_ADMIN"]);
  assert.equal(identity.email.endsWith(".invalid"), true);
  const unknown = await fetch(origin + "/api/v1/payments", { headers: auth });
  assert.equal(unknown.status, 404);
});

test("all four sector scenarios remain fictional and map to actual next-step codes", async () => {
  const cases = {
    restaurant: ["ADD_SERVICE", "restaurant"],
    veterinary: ["CONFIGURE_HOURS", "veterinary"],
    hardware_store: ["CONFIGURE_BUSINESS", "hardware_store"],
    clinic: ["READY", "clinic"]
  };
  for (const [sector, [step, preset]] of Object.entries(cases)) {
    const headers = { ...auth, Cookie: `rv_preview_sector=${sector}` };
    const status = await fetch(origin + "/api/v1/onboarding/status", { headers });
    assert.equal(status.status, 200);
    assert.equal((await status.json()).nextStep, step);
    const profile = await fetch(origin + "/api/v1/business/profile", { headers });
    assert.equal((await profile.json()).presetKey, preset);
    const business = await fetch(origin + "/api/v1/business", { headers });
    assert.match((await business.json()).name, /demostración/);
  }
});

test("every mutating method is denied, with or without fake session", async () => {
  for (const method of ["POST", "PUT", "PATCH", "DELETE"]) {
    for (const headers of [auth, {}]) {
      const response = await fetch(origin + "/api/v1/business/profile", { method, headers });
      assert.equal(response.status, 405, method);
      assert.match((await response.json()).message, /solo lectura/);
    }
  }
  const other = await fetch(origin + "/preview", { method: "POST" });
  assert.equal(other.status, 405);
});

test("served frontend is visibly fictitious, static assets render, and paths stay isolated", async () => {
  const page = await fetch(origin + "/app/settings");
  assert.equal(page.status, 200);
  const html = await page.text();
  assert.match(html, /FICTITIOUS DEMO APP/);
  assert.match(html, /DATOS FICTICIOS/);
  assert.match(html, /SIN LLAMADAS, WHATSAPP NI PAGOS REALES/);
  const asset = await fetch(origin + "/app/assets/example.js");
  assert.equal(asset.status, 200);
  assert.match(asset.headers.get("content-type"), /text\/javascript/);
  assert.match(await asset.text(), /fixture/);
  const head = await fetch(origin + "/app/assets/example.js", { method: "HEAD" });
  assert.equal(head.status, 200);
  assert.equal((await head.text()).length, 0);
  assert.equal((await fetch(origin + "/app/assets/nope.js")).status, 404);
  assert.equal((await fetch(origin + "/app/%2e%2e/%2e%2e/server.mjs")).status, 404);
  assert.equal((await fetch(origin + "/service-worker.js")).status, 404);
  assert.equal((await fetch(origin + "/secret")).status, 404);
});
