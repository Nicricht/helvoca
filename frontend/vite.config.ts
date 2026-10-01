import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  base: "/app/",
  plugins: [react()],
  build: {
    outDir: "../src/main/resources/static/app",
    emptyOutDir: true,
    sourcemap: false
  }
});
