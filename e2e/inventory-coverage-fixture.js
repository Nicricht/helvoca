const base = require('@playwright/test');
const { createHash } = require('node:crypto');
const { mkdirSync, writeFileSync } = require('node:fs');
const path = require('node:path');

const rawDir = path.join(process.cwd(), '.inventory-coverage-raw');

// Extend the page fixture, not a module-global afterEach hook. A shared module
// is cached across spec files, so a top-level afterEach can silently collect
// just the first suite. This fixture runs for EVERY test that uses page.
const test = base.test.extend({
  page: async ({ page }, use, testInfo) => {
    await use(page);
    if (process.env.VITE_COVERAGE !== 'true') return;
    mkdirSync(rawDir, { recursive: true });
    const fingerprint = createHash('sha256')
      .update([testInfo.file, testInfo.title, testInfo.repeatEachIndex,
        testInfo.retry, testInfo.workerIndex].join('|'))
      .digest('hex').slice(0, 20);
    const counters = page.isClosed()
      ? null
      : await page.evaluate(() => globalThis.__coverage__ ?? null).catch(() => null);
    const available = Boolean(counters && Object.keys(counters).length);
    writeFileSync(path.join(rawDir, fingerprint + '.record.json'),
      JSON.stringify({
        suite: path.basename(testInfo.file),
        test: testInfo.title,
        instrumented: available
      }));
    if (available) {
      writeFileSync(path.join(rawDir, fingerprint + '.coverage.json'),
        JSON.stringify(counters));
    } else {
      writeFileSync(path.join(rawDir, fingerprint + '.unavailable.json'),
        JSON.stringify({ test: testInfo.title, reason: 'No instrumented page at teardown' }));
    }
  }
});
module.exports = { test, expect: base.expect };
