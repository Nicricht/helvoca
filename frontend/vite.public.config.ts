import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  root: fileURLToPath(new URL("./public-booking/", import.meta.url)),
  base: "/reservar/",
  plugins: [react()],
  build: {
    outDir: fileURLToPath(new URL("../src/main/resources/static/reservar/", import.meta.url)),
    emptyOutDir: true,
    sourcemap: false
  }
});
