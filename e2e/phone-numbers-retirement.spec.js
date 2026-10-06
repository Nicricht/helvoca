const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');

const json = (body, status = 200) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function bootChannels(page) {
  await page.addInitScript(() => {
    sessionStorage.setItem('helvoca_access_token', 'phone-retirement-e2e');
  });

  const writes = [];

  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const p = url.pathname;
    const method = request.method();

    if (method !== 'GET') {
      writes.push({ method, path: p });
      if (method === 'DELETE' && p === '/api/v1/phone-numbers/phone-1') {
        return route.fulfill({ status: 204, body: '' });
      }
      return route.fulfill(json({}));
    }

    if (p === '/api/v1/auth/me') {
      return route.fulfill(json({
        email: 'admin@demo.cl',
        roles: ['BUSINESS_ADMIN'],
        permissions: [
          'BUSINESS_READ', 'BUSINESS_CONFIGURE',
          'CHANNELS_READ', 'CHANNELS_MANAGE'
        ]
      }));
    }
    if (p === '/api/v1/subscription') {
      return route.fulfill(json({
        plan: 'PRO',
        publicPlanCode: 'PRO',
        planName: 'Profesional',
        status: 'ACTIVE',
        includedMinutes: 500,
        usedMinutes: 50
      }));
    }
    if (p === '/api/v1/business') {
      return route.fulfill(json({
        name: 'Negocio demo',
        timezone: 'America/Santiago',
        language: 'es'
      }));
    }
    if (p === '/api/v1/business/profile') {
      return route.fulfill(json({ defaultCurrency: 'CLP' }));
    }
    if (p === '/api/v1/onboarding/status') {
      return route.fulfill(json({ readyForCalls: true }));
    }
    if (p === '/api/v1/services') return route.fulfill(json([]));
    if (p === '/api/v1/business/hours') return route.fulfill(json([]));
    if (p === '/api/v1/knowledge') return route.fulfill(json([]));
    if (p === '/api/v1/ai-agent/voices') return route.fulfill(json([]));
    if (p === '/api/v1/ai-agent') {
      return route.fulfill(json({
        configured: true,
        name: 'Helvoca',
        language: 'es',
        voice: '',
        greeting: '',
        instructions: '',
        active: true,
        capabilities: []
      }));
    }
    if (p === '/api/v1/phone-numbers') {
      return route.fulfill(json([{
        id: 'phone-1',
        provider: 'TWILIO',
        phoneNumber: '+56220001111',
        active: true,
        whatsappEnabled: false
      }]));
    }
    if (p === '/api/v1/phone-numbers/provisioning/status') {
      return route.fulfill(json({
        enabled: true,
        configured: true,
        purchaseAvailable: false,
        provider: 'TWILIO',
        message: 'No disponible en E2E'
      }));
    }
    if (p === '/api/v1/channels/whatsapp/meta/config') {
      return route.fulfill(json({
        status: 'NOT_CONFIGURED',
        configured: false,
        enabled: false
      }));
    }
    if (p === '/api/v1/channels/whatsapp/meta/embedded-signup/bootstrap') {
      return route.fulfill(json({ enabled: true, available: false }));
    }

    return route.fulfill(json({}));
  });

  return { writes };
}

test.describe('legacy phone numbers retirement', () => {
  test('legacy document is compatibility-only and contains no direct phone mutation code', async () => {
    const root = path.resolve(__dirname, '..');
    const html = fs.readFileSync(path.join(root, 'src/main/resources/static/phone-numbers.html'), 'utf8');

    expect(html).toContain('url=/app/settings?section=channels');
    expect(html).toContain("window.location.replace('/app/settings?section=channels')");
    expect(html).not.toContain('/api/v1/phone-numbers');
    expect(html).not.toContain('method: "DELETE"');
  });

  test('legacy URL redirects into canonical React Channels without mutating', async ({ page }) => {
    const state = await bootChannels(page);

    await page.goto('/phone-numbers.html');

    await expect(page).toHaveURL(/\/app\/settings\?section=channels$/);
    await expect(page.getByRole('heading', { name: 'Canales' })).toBeVisible();
    await expect(page.getByText('+56220001111')).toBeVisible();
    expect(state.writes).toEqual([]);
  });

  test('detach confirmation explains that the provider number is not released', async ({ page }) => {
    await bootChannels(page);
    await page.goto('/app/settings?section=channels');

    let confirmation = '';
    page.once('dialog', async dialog => {
      confirmation = dialog.message();
      await dialog.dismiss();
    });

    await page.getByRole('button', { name: 'Desvincular +56220001111' }).click();

    expect(confirmation).toMatch(/Twilio|proveedor/i);
    expect(confirmation).toMatch(/no .*liber|no .*elimin|seguirá existiendo/i);
  });

  test('detach is explicit and sends exactly one DELETE after confirmation', async ({ page }) => {
    const state = await bootChannels(page);
    await page.goto('/app/settings?section=channels');

    expect(state.writes).toEqual([]);

    page.once('dialog', dialog => dialog.accept());
    await page.getByRole('button', { name: 'Desvincular +56220001111' }).click();

    await expect.poll(() => state.writes).toEqual([
      { method: 'DELETE', path: '/api/v1/phone-numbers/phone-1' }
    ]);
    await expect(page.getByText('Número desvinculado.', { exact: true })).toBeVisible();
  });
});
