const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './e2e-system',
  timeout: 60000,
  expect: { timeout: 10000 },
  workers: 1,
  retries: 0,
  reporter: 'line',
  outputDir: 'test-results/system-e2e',
  use: {
    baseURL: process.env.SYSTEM_E2E_BASE_URL || 'http://127.0.0.1:8080',
    browserName: 'chromium',
    headless: true,
    timezoneId: 'America/Santiago',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  }
});
