const base = require('@playwright/test');
const { createHash } = require('node:crypto');
const { mkdirSync, writeFileSync } = require('node:fs');
const path = require('node:path');
const { createCoverageMap } = require('istanbul-lib-coverage');

const rawDir = path.join(process.cwd(), '.inventory-coverage-raw');
const archiveKey = '__rv_inventory_coverage_previous_documents__';

// One fixture invocation per browser test: collect ALL navigated documents,
// not just the final document after a test's last page.goto().
const test = base.test.extend({
  page: async ({ page }, use, testInfo) => {
    if (process.env.VITE_COVERAGE === 'true') {
      await page.addInitScript(key => {
        window.addEventListener('pagehide', () => {
          const current = globalThis.__coverage__;
          if (!current || Object.keys(current).length === 0) return;
          try {
            const prior = JSON.parse(sessionStorage.getItem(key) || '{"documents":0,"files":{}}');
            for (const [file, source] of Object.entries(current)) {
              const saved = prior.files[file];
              if (!saved) {
                prior.files[file] = source;
                continue;
              }
              for (const [id, count] of Object.entries(source.s)) {
                saved.s[id] = (saved.s[id] || 0) + count;
              }
              for (const [id, count] of Object.entries(source.f)) {
                saved.f[id] = (saved.f[id] || 0) + count;
              }
              for (const [id, branches] of Object.entries(source.b)) {
                if (!saved.b[id]) saved.b[id] = Array(branches.length).fill(0);
                branches.forEach((count, index) => {
                  saved.b[id][index] = (saved.b[id][index] || 0) + count;
                });
              }
            }
            prior.documents += 1;
            sessionStorage.setItem(key, JSON.stringify(prior));
          } catch {
            // Do not silently certify partial coverage after a storage error.
            try { sessionStorage.setItem(key, '{"archiveFailed":true}'); } catch {}
          }
        }, { capture: true });
      }, archiveKey);
    }

    await use(page);

    if (process.env.VITE_COVERAGE !== 'true') return;
    mkdirSync(rawDir, { recursive: true });
    const fingerprint = createHash('sha256')
      .update([testInfo.file, testInfo.title, testInfo.repeatEachIndex,
        testInfo.retry, testInfo.workerIndex].join('|'))
      .digest('hex').slice(0, 20);
    const snapshots = page.isClosed()
      ? null
      : await page.evaluate(key => {
        let previous = null;
        try {
          previous = JSON.parse(sessionStorage.getItem(key) || 'null');
        } catch {
          previous = { archiveFailed: true };
        }
        return {
          previous,
          current: globalThis.__coverage__ ?? null
        };
      }, archiveKey).catch(() => null);

    const archiveFailed = Boolean(snapshots?.previous?.archiveFailed);
    const map = createCoverageMap(snapshots?.previous?.files ?? {});
    if (snapshots?.current) map.merge(snapshots.current);
    const collected = map.toJSON();
    const available = !archiveFailed && Object.keys(collected).length > 0;
    writeFileSync(path.join(rawDir, fingerprint + '.record.json'),
      JSON.stringify({
        suite: path.basename(testInfo.file),
        test: testInfo.title,
        instrumented: available,
        archivedDocuments: snapshots?.previous?.documents ?? 0,
        archiveFailed
      }));
    if (available) {
      writeFileSync(path.join(rawDir, fingerprint + '.coverage.json'),
        JSON.stringify(collected));
    } else {
      writeFileSync(path.join(rawDir, fingerprint + '.unavailable.json'),
        JSON.stringify({ test: testInfo.title,
          reason: archiveFailed ? 'Earlier navigation source coverage could not be archived'
            : 'No instrumented page at teardown' }));
    }
  }
});

module.exports = { test, expect: base.expect };
