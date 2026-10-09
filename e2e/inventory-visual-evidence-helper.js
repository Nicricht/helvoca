const fs = require('node:fs');
const path = require('node:path');
const { expect } = require('@playwright/test');

const INVENTORY_VIEWPORTS = [
  { width: 1536, height: 950 },
  { width: 1440, height: 900 },
  { width: 1366, height: 768 },
  { width: 1280, height: 720 },
  { width: 768, height: 1024 },
  { width: 390, height: 844 }
];

/**
 * CI-owned, deterministic, exact-head visual evidence. Fixtures intercept API
 * requests; this helper never writes customer records or calls paid services.
 * Save the screenshot before asserting overflow so failures retain evidence.
 */
async function captureInventoryVisual(page, state) {
  if (!/^[a-z0-9-]+$/.test(state)) {
    throw new Error('Invalid Inventory evidence state');
  }
  const viewport = page.viewportSize();
  const width = viewport.width;
  const height = viewport.height;
  await page.evaluate(() => document.fonts.ready);
  const head = (process.env.VISUAL_EVIDENCE_SHA || 'local').slice(0, 12);
  const destination = path.resolve('test-results', 'visual-evidence', head);
  fs.mkdirSync(destination, { recursive: true });
  await page.screenshot({
    path: path.join(destination, 'inventory-' + state + '-' + width + 'x' + height + '.png'),
    fullPage: false,
    animations: 'disabled'
  });
  const layout = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth
  }));
  expect(layout.scrollWidth, state + ' at ' + width + 'px must not widen the page')
    .toBeLessThanOrEqual(layout.clientWidth + 1);
}

module.exports = { INVENTORY_VIEWPORTS, captureInventoryVisual };
