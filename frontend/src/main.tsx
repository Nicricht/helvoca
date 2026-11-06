import React from "react";
import ReactDOM from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router-dom";
import { App } from "./app/App";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
      refetchOnWindowFocus: false
    }
  }
});

const root = document.getElementById("root");
if (!root) throw new Error("Missing React root element.");

ReactDOM.createRoot(root).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter basename="/app">
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>
);


if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("/service-worker.js", { scope: "/" }).catch(() => {
      // PWA support is progressive; the app remains usable if registration fails.
    });
  });
}

/**
 * Browser-only source coverage diagnostic. Vite removes this entire branch from
 * normal builds because VITE_IMPORT_COVERAGE is unset. This does not expose a
 * write action on the production site, and E2E calls only local mocked APIs.
 */
if (import.meta.env.VITE_IMPORT_COVERAGE === "true") {
  void import("./features/businessImport/setupApply").then(({ applyReviewedSetup }) => {
    (window as Window & { __recepvozImportCoverageApply?: typeof applyReviewedSetup })
      .__recepvozImportCoverageApply = applyReviewedSetup;
  });
}
