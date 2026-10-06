import { copyFileSync, mkdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

function directReactRoutes() {
  return {
    name: "recepvoz-direct-react-routes",
    closeBundle() {
      const appDir = fileURLToPath(new URL("../src/main/resources/static/app/", import.meta.url));
      const inventoryDir = fileURLToPath(new URL("../src/main/resources/static/app/inventory/", import.meta.url));
      const agendaDir = fileURLToPath(new URL("../src/main/resources/static/app/agenda/", import.meta.url));
      const ordersDir = fileURLToPath(new URL("../src/main/resources/static/app/orders/", import.meta.url));
      const settingsDir = fileURLToPath(new URL("../src/main/resources/static/app/settings/", import.meta.url));
      const planDir = fileURLToPath(new URL("../src/main/resources/static/app/plan/", import.meta.url));
      const simulatorDir = fileURLToPath(new URL("../src/main/resources/static/app/simulator/", import.meta.url));
      const internalOperationsDir = fileURLToPath(new URL("../src/main/resources/static/app/internal/operations/", import.meta.url));
      mkdirSync(inventoryDir, { recursive: true });
      mkdirSync(agendaDir, { recursive: true });
      mkdirSync(ordersDir, { recursive: true });
      mkdirSync(settingsDir, { recursive: true });
      mkdirSync(planDir, { recursive: true });
      mkdirSync(simulatorDir, { recursive: true });
      mkdirSync(internalOperationsDir, { recursive: true });
      copyFileSync(`${appDir}index.html`, `${inventoryDir}index.html`);
      copyFileSync(`${appDir}index.html`, `${agendaDir}index.html`);
      copyFileSync(`${appDir}index.html`, `${ordersDir}index.html`);
      copyFileSync(`${appDir}index.html`, `${settingsDir}index.html`);
      copyFileSync(`${appDir}index.html`, `${planDir}index.html`);
      copyFileSync(`${appDir}index.html`, `${simulatorDir}index.html`);
      copyFileSync(`${appDir}index.html`, `${internalOperationsDir}index.html`);
    }
  };
}

export default defineConfig({
  base: "/app/",
  plugins: [react(), directReactRoutes()],
  build: {
    outDir: "../src/main/resources/static/app",
    emptyOutDir: true,
    sourcemap: false
  }
});
