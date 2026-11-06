const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tools/demo-preview",
  testMatch: "**/live-browser.spec.cjs",
  timeout: 60_000,
  expect: { timeout: 20_000 },
  fullyParallel: false,
  retries: 1,
  workers: 1,
  reporter: [["line"], ["html", { open: "never", outputFolder: "playwright-preview-report" }]],
  use: {
    baseURL: "https://recepvoz-onboarding-preview-demo-isolated.up.railway.app",
    browserName: "chromium",
    headless: true,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    actionTimeout: 15_000
  }
});
