const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const staticDir = path.join(root, 'src/main/resources/static');

const retiredAssets = [
  'app.js',
  'commercial-status.js',
  'business-activation-guide.js',
  'ux-simplification.js',
  'first-user-ux-v2.js',
  'phone-provisioning.js',
  'voice-selector.js',
  'auth-visual-refresh.css',
  'landing-motion.css',
  'dashboard-motion.css',
  'dashboard-finish.css',
  'first-user-ux-v2.css',
  'commercial-ui-v3.css',
  'recepvoz-auth-hero.svg',
  'recepvoz-icon.svg'
];

const preservedAssets = [
  'styles.css',
  'frontend-foundation.css',
  'recepvoz-phone-hero.svg',
  'recepvoz-icon-192.png',
  'recepvoz-icon-512.png',
  'manifest.webmanifest',
  'service-worker.js'
];

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

test.describe('legacy root asset cleanup', () => {
  test('approved retired runtime assets are physically absent', async () => {
    for (const asset of retiredAssets) {
      expect(
        fs.existsSync(path.join(staticDir, asset)),
        asset + ' should be retired after the React public-entry cutover'
      ).toBe(false);
    }
  });

  test('required shared, legal and PWA assets remain present', async () => {
    for (const asset of preservedAssets) {
      expect(
        fs.existsSync(path.join(staticDir, asset)),
        asset + ' is still an active shared/PWA asset'
      ).toBe(true);
    }

    for (const documentName of ['privacy.html', 'terms.html', 'data-deletion.html', 'index.html']) {
      expect(fs.existsSync(path.join(staticDir, documentName))).toBe(true);
    }
  });

  test('Spring Security no longer exposes retired assets explicitly', async () => {
    const security = read('src/main/java/cl/helvoca/security/SecurityConfig.java');

    for (const asset of retiredAssets) {
      expect(security).not.toContain('/' + asset);
    }

    expect(security).toContain('"/styles.css"');
    expect(security).toContain('"/frontend-foundation.css"');
    expect(security).toContain('"/manifest.webmanifest"');
    expect(security).toContain('"/service-worker.js"');
  });

  test('CI no longer syntax-checks retired root JavaScript', async () => {
    const workflow = read('.github/workflows/ci.yml');

    for (const asset of retiredAssets.filter(name => name.endsWith('.js'))) {
      expect(workflow).not.toContain('node --check src/main/resources/static/' + asset);
    }
  });

  test('current HTML documents never load a retired JS or CSS asset', async () => {
    const htmlFiles = fs.readdirSync(staticDir).filter(name => name.endsWith('.html'));

    for (const documentName of htmlFiles) {
      const html = fs.readFileSync(path.join(staticDir, documentName), 'utf8');
      for (const asset of retiredAssets.filter(name => name.endsWith('.js') || name.endsWith('.css'))) {
        expect(html, documentName + ' must not load ' + asset).not.toContain('/' + asset);
      }
    }
  });

  test('canonical React auth remains the replacement for the retired root implementation', async ({ page }) => {
    await page.goto('/');

    await expect(page).toHaveURL(/\/app\/auth\/?$/);
    await expect(page.locator('[data-auth-page="recepvoz"]')).toBeVisible();
    await expect(page.getByRole('heading', { level: 1, name: 'No pierdas otra llamada.' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Planes' })).toHaveAttribute('href', '/app/pricing');
  });
});
