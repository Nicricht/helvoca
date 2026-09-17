const { test, expect } = require('@playwright/test');

const json = body => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });

test('settings is a dedicated authenticated workspace', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await page.route('**/api/v1/business', route => route.fulfill(json({ name: 'Negocio E2E', timezone: 'America/Santiago', language: 'es', humanTransferPhone: '+56900000000' })));
  await page.route('**/api/v1/ai-agent', route => {
    if (route.request().method() === 'PUT') return route.fulfill(json({ configured: true }));
    return route.fulfill(json({ configured: true, name: 'Helvoca', voice: 'alloy', greeting: 'Hola', instructions: '', active: true, capabilities: ['LIST_SERVICES'] }));
  });
  await page.route('**/api/v1/services', route => route.fulfill(json([{ id: 'svc1', name: 'Peluquería', durationMinutes: 30, price: 25000, description: '', active: true }])));
  await page.route('**/api/v1/business/hours', route => route.fulfill(json([{ dayOfWeek: 1, openTime: '09:00:00', closeTime: '18:00:00' }])));
  await page.route('**/api/v1/knowledge?activeOnly=false', route => route.fulfill(json([{ id: 'k1', title: 'Estacionamiento', category: 'Info', content: 'Sí', active: true }])));
  await page.route('**/api/v1/phone-numbers', route => route.fulfill(json([{ id: 'p1', phoneNumber: '+56911111111', active: true, provider: 'TWILIO' }])));
  await page.route('**/api/v1/billing/status', route => route.fulfill(json({ currentPlanName: 'Pro', currentPlanCode: 'PRO' })));
  await page.route('**/api/v1/subscription', route => route.fulfill(json({ plan: 'PRO', status: 'ACTIVE', includedMinutes: 500, usedMinutes: 28, serviceAllowed: true })));
  await page.route('**/api/v1/onboarding/setup', route => route.fulfill(json({ ok: true })));

  await page.goto('/settings.html');

  await expect(page.getByRole('heading', { level: 1, name: 'Configuración' })).toBeVisible();
  await expect(page.locator('#settingsBusinessName')).toHaveValue('Negocio E2E');
  await expect(page.locator('#settingsServices')).toContainText('Peluquería');
  await expect(page.locator('#settingsHours')).toContainText('Lunes');
  await expect(page.locator('#settingsPhones')).toContainText('+56911111111');
  await expect(page.locator('#settingsPlan')).toContainText('Pro');
  await expect(page.locator('.nav-config')).toHaveClass(/active/);
});
