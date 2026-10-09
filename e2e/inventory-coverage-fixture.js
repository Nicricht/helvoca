const { test, expect } = require('@playwright/test');
const { createHash } = require('node:crypto');
const { mkdirSync, writeFileSync } = require('node:fs');
const path = require('node:path');

const rawDir = path.join(process.cwd(), 'test-results', 'inventory-coverage-raw');

// Production and normal browser builds are not instrumented.
if (process.env.VITE_COVERAGE === 'true') {
  test.afterEach(async ({ page }, testInfo) => {
    if (page.isClosed()) return;
    const counters = await page.evaluate(() => globalThis.__coverage__ ?? null)
      .catch(() => null);
    // A redirected browser test can finish on the uninstrumented login page.
    // The report still fails if any affected source is absent overall.
    if (!counters || Object.keys(counters).length === 0) return;
    mkdirSync(rawDir, { recursive: true });
    const fingerprint = createHash('sha256')
      .update([testInfo.file, testInfo.title, testInfo.repeatEachIndex,
        testInfo.retry, testInfo.workerIndex].join('|'))
      .digest('hex').slice(0, 20);
    writeFileSync(path.join(rawDir, fingerprint + '.coverage.json'),
      JSON.stringify(counters));
  });
}

module.exports = { test, expect };
