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
      mkdirSync(inventoryDir, { recursive: true });
      mkdirSync(agendaDir, { recursive: true });
      copyFileSync(`${appDir}index.html`, `${inventoryDir}index.html`);
      copyFileSync(`${appDir}index.html`, `${agendaDir}index.html`);
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
