const { test, expect } = require('@playwright/test');

const json = body => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

test('platform admin sees the Demo Center without any countdown and can create a profile', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'platform-token'));

  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    userId: 'platform-user',
    email: 'platform@recepvoz.cl',
    roles: ['PLATFORM_ADMIN'],
    businessId: ''
  })));
  await page.route('**/api/v1/platform/economics', route => route.fulfill(json({
    estimatedCommercialValueClp: 0,
    estimatedPlatformCostUsd: 0,
    businesses: [],
    providers: []
  })));

  let submitted = null;
  const profiles = [{
    id: '11111111-2222-3333-4444-555555555555',
    displayName: 'Sushi Demo',
    businessName: 'Sushi Demo',
    timezone: 'America/Santiago',
    language: 'es',
    catalog: {},
    hours: {},
    knowledge: {},
    greeting: 'Hola',
    instructions: null,
    capabilities: ['ORDER'],
    presenterNotes: null,
    sourceMetadata: { source: 'template' },
    createdAt: '2026-10-01T04:00:00Z',
    updatedAt: '2026-10-01T04:00:00Z'
  }];
  await page.route('**/api/v1/platform/demos', async route => {
    if (route.request().method() === 'POST') {
      submitted = route.request().postDataJSON();
      const created = {
        id: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
        ...submitted,
        createdAt: '2026-10-01T05:00:00Z',
        updatedAt: '2026-10-01T05:00:00Z'
      };
      profiles.unshift(created);
      return route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(created)
      });
    }
    return route.fulfill(json(profiles));
  });

  await page.goto('/platform.html');

  await expect(page.getByRole('heading', { name: 'Centro de Demos' })).toBeVisible();
  await expect(page.locator('body')).not.toContainText('3:00');
  await expect(page.locator('body')).not.toContainText('3 minutos');
  await expect(page.locator('#platformDemoProfiles')).toContainText('Sushi Demo');

  await page.getByRole('button', { name: /Crear nueva demo/i }).click();
  await page.locator('input[name="demoDisplayName"]').fill('Sushi Akira');
  await page.locator('input[name="demoBusinessName"]').fill('Sushi Akira');
  await page.locator('textarea[name="demoGreeting"]').fill('Hola, soy la asistente de Sushi Akira.');
  await page.getByRole('button', { name: /Guardar demo/i }).click();

  await expect.poll(() => submitted).not.toBeNull();
  expect(submitted.displayName).toBe('Sushi Akira');
  expect(submitted.businessName).toBe('Sushi Akira');
  expect(submitted.timezone).toBe('America/Santiago');
  expect(submitted.language).toBe('es');
  expect(submitted.capabilities).toEqual([]);
  await expect(page.locator('#platformDemoProfiles')).toContainText('Sushi Akira');
});
