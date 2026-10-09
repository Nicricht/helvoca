const { test, expect } = require('@playwright/test');
const { createHash } = require('node:crypto');
const { mkdirSync, writeFileSync } = require('node:fs');
const path = require('node:path');

const rawDir = path.join(process.cwd(), '.inventory-coverage-raw');

// Production and normal browser builds are not instrumented.
if (process.env.VITE_COVERAGE === 'true') {
  test.afterEach(async ({ page }, testInfo) => {
    const counters = page.isClosed()
      ? null
      : await page.evaluate(() => globalThis.__coverage__ ?? null).catch(() => null);
    // Keep raw source counters outside Playwright's managed test-results folder.
    mkdirSync(rawDir, { recursive: true });
    const fingerprint = createHash('sha256')
      .update([testInfo.file, testInfo.title, testInfo.repeatEachIndex,
        testInfo.retry, testInfo.workerIndex].join('|'))
      .digest('hex').slice(0, 20);
    if (counters && Object.keys(counters).length > 0) {
      writeFileSync(path.join(rawDir, fingerprint + '.coverage.json'),
        JSON.stringify(counters));
    } else {
      // Redirected / closed pages are tracked rather than silently disappearing.
      writeFileSync(path.join(rawDir, fingerprint + '.unavailable.json'),
        JSON.stringify({ test: testInfo.title, reason: 'No instrumented page at teardown' }));
    }
  });
}

module.exports = { test, expect };
