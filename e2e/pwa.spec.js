const { test, expect } = require('@playwright/test');

test('PWA manifest is linked and service worker controls the app scope', async ({ page }) => {
  await page.goto('/');

  const manifestHref = await page.locator('link[rel="manifest"]').getAttribute('href');
  expect(manifestHref).toBe('/manifest.webmanifest');

  const manifestResponse = await page.request.get('/manifest.webmanifest');
  expect(manifestResponse.ok()).toBe(true);

  const manifest = await manifestResponse.json();
  expect(manifest.name).toBe('RecepVoz');
  expect(manifest.start_url).toBe('/');
  expect(manifest.scope).toBe('/');
  expect(manifest.display).toBe('standalone');
  expect(manifest.icons).toEqual(expect.arrayContaining([
    expect.objectContaining({ src: '/recepvoz-icon-192.png', sizes: '192x192', type: 'image/png' }),
    expect.objectContaining({ src: '/recepvoz-icon-512.png', sizes: '512x512', type: 'image/png' })
  ]));

  await expect.poll(async () => page.evaluate(async () => {
    if (!('serviceWorker' in navigator)) {
      return null;
    }

    const registration = await navigator.serviceWorker.ready;
    return new URL(registration.scope).pathname;
  })).toBe('/');

  await expect.poll(() => page.evaluate(() => Boolean(navigator.serviceWorker.controller)))
    .toBe(true);

  await page.route('**/api/v1/pwa-route-probe', route => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ intercepted: true })
    });
  });

  const probe = await page.evaluate(async () => {
    const response = await fetch('/api/v1/pwa-route-probe');
    return response.json();
  });
  expect(probe).toEqual({ intercepted: true });
});
