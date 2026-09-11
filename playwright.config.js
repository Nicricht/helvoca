const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './e2e',
  workers: 1,
  retries: 0,
  reporter: 'line',
  use: {
    baseURL: 'http://127.0.0.1:4173',
    browserName: 'chromium',
    headless: true
  },
  webServer: {
    command: 'python3 -m http.server 4173 --bind 127.0.0.1 --directory src/main/resources/static',
    port: 4173,
    reuseExistingServer: false,
    timeout: 15000
  }
});
