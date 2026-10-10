const { test, expect } = require('@playwright/test');
const json = body => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });

async function mockSetup(page, roles = ['BUSINESS_ADMIN']) {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'express-e2e'));
  const mutations = [];
  await page.route('**/api/v1/**', route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    if (request.method() !== 'GET') mutations.push({ path, method: request.method() });
    if (path === '/api/v1/auth/me') return route.fulfill(json({ email: 'admin@demo.cl', roles, permissions: ['BUSINESS_READ', 'BUSINESS_CONFIGURE'] }));
    if (path === '/api/v1/business') return route.fulfill(json({ name: 'Restaurante La Plaza', language: 'es', timezone: 'America/Santiago' }));
    if (path === '/api/v1/business/profile') return route.fulfill(json({ presetKey: 'restaurant', countryCode: 'CL', defaultCurrency: 'CLP' }));
    if (path === '/api/v1/onboarding/import/ai-quota') return route.fulfill(json({
      status: 'DISABLED', planCode: 'BASIC', limit: 0, used: 0, remaining: 0,
      masterEnabled: false, currentPeriodStart: null, currentPeriodEnd: null
    }));
    if (path === '/api/v1/onboarding/status') return route.fulfill(json({
      businessProfileConfigured: true, servicesConfigured: true,
      knowledgeConfigured: false, scheduleConfigured: true,
      phoneConfigured: false, readyForCalls: false
    }));
    if (path === '/api/v1/services') return route.fulfill(json([{ id: 's1', name: 'Almuerzo', durationMinutes: 45, active: true }]));
    if (path === '/api/v1/knowledge') return route.fulfill(json([]));
    if (path === '/api/v1/business/hours') return route.fulfill(json([{ dayOfWeek: 1, openTime: '09:00', closeTime: '18:00' }]));
    if (path === '/api/v1/ai-agent') return route.fulfill(json({ name: 'Sofía', voice: 'marin', greeting: 'Hola', active: true, capabilities: [] }));
    if (path === '/api/v1/ai-agent/voices') return route.fulfill(json([]));
    if (path === '/api/v1/phone-numbers') return route.fulfill(json([]));
    return route.fulfill(json({}));
  });
  return mutations;
}

test.describe('Settings Express user journey', () => {
  test('puts Express above manual editing while preserving every advanced tab', async ({ page }) => {
    const writes = await mockSetup(page);
    await page.goto('/app/settings');
    const express = page.getByTestId('settings-express');
    await expect(express.getByRole('heading', { name: 'Tu negocio, preparado con menos trabajo.' })).toBeVisible();
    await expect(express.getByRole('link', { name: /Empezar/ })).toHaveAttribute('href', '/app/settings/import');
    await expect(express.getByText('Teléfono por verificar')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Editar manualmente' })).toBeVisible();
    const tabs = page.getByRole('tablist', { name: 'Secciones de configuración' });
    await expect(tabs.getByRole('tab')).toHaveCount(8);
    await express.getByRole('button', { name: /Editar manualmente/ }).click();
    await expect(page.getByRole('tab', { name: 'Negocio' })).toBeVisible();
    expect(writes).toEqual([]);
  });

  test('operator can view configuration but never gets a paid import action', async ({ page }) => {
    const writes = await mockSetup(page, ['OPERATOR']);
    await page.goto('/app/settings');
    await expect(page.getByTestId('settings-express').getByText('Solo administración')).toBeVisible();
    await expect(page.getByTestId('settings-express').getByRole('link', { name: /Empezar/ })).toHaveCount(0);
    expect(writes).toEqual([]);
  });

  test('import steps show quota refusal without submitting any apply mutation', async ({ page }) => {
    const writes = await mockSetup(page);
    await page.route('**/api/v1/onboarding/import/preview', route => route.fulfill(json({
      businessName: 'Restaurante La Plaza', aiUsed: false, products: [], sources: [
        { name: 'carta.jpg', kind: 'UNKNOWN', rowCount: 0, method: 'AI_BUDGET_EXCEEDED', recognized: false,
          warnings: ['Cupo agotado'] }
      ], warnings: ['Este negocio no tiene cupo disponible de importaciones pagadas con IA.']
    })));
    await page.goto('/app/settings/import');
    await expect(page.getByRole('list', { name: 'Progreso de Configuración Express' }).locator('[aria-current="step"]')).toContainText('Importar');
    await page.getByLabel('Archivos del negocio').setInputFiles({
      name: 'carta.jpg', mimeType: 'image/jpeg', buffer: Buffer.from('mock-photo')
    });
    await page.getByRole('button', { name: 'Analizar y crear borrador' }).click();
    await expect(page.getByText('Cupo de IA agotado')).toBeVisible();
    await expect(page.getByRole('list', { name: 'Progreso de Configuración Express' }).locator('[aria-current="step"]')).toContainText('Revisar');
    expect(writes.filter(x => x.path === '/api/v1/onboarding/import/apply')).toHaveLength(0);
  });

  test('after approved import it guides preparation without claiming a live phone was tested', async ({ page }) => {
    await mockSetup(page);
    await page.route('**/api/v1/onboarding/import/preview', route => route.fulfill(json({
      businessName: 'Restaurante La Plaza', aiUsed: false,
      products: [{
        name: 'Almuerzo', price: 9000, currency: 'CLP', kind: 'PRODUCT',
        confidence: 0.98, sourceName: 'carta.csv'
      }],
      sources: [{ name: 'carta.csv', kind: 'PRODUCTS', method: 'SPREADSHEET', rowCount: 1,
        recognized: true, warnings: [] }], warnings: []
    })));
    let applied = 0;
    await page.route('**/api/v1/onboarding/import/apply', route => {
      applied++;
      return route.fulfill(json({ created: 1, updated: 0, inventoryConfigured: 0, items: [] }));
    });
    await page.goto('/app/settings/import');
    await page.getByLabel('Archivos del negocio').setInputFiles({
      name: 'carta.csv', mimeType: 'text/csv', buffer: Buffer.from('producto,precio\nAlmuerzo,9000\n')
    });
    await page.getByRole('button', { name: 'Analizar y crear borrador' }).click();
    await expect(page.getByText('Sin IA pagada')).toBeVisible();
    expect(applied).toBe(0);
    await page.getByRole('button', { name: 'Importar al negocio' }).click();
    await expect.poll(() => applied).toBe(1);
    await expect(page.getByRole('list', { name: 'Progreso de Configuración Express' }).locator('[aria-current="step"]')).toContainText('Preparar');
    await expect(page.getByText(/Aún quedan requisitos de atención/)).toBeVisible();
    await expect(page.getByRole('link', { name: /Preparar recepcionista/ })).toHaveAttribute('href', '/app/settings?section=receptionist');
  });

  for (const [width, height] of [
    [1536, 950], [1440, 900], [1366, 768],
    [1280, 720], [768, 1024], [390, 844]
  ]) {
    test(`Express remains usable without horizontal page overflow at ${width}x${height}`, async ({ page }, testInfo) => {
      await page.setViewportSize({ width, height });
      const writes = await mockSetup(page);
      await page.goto('/app/settings');
      const express = page.getByTestId('settings-express');
      await expect(express.getByRole('heading', { name: 'Tu negocio, preparado con menos trabajo.' })).toBeVisible();
      await expect(express.getByRole('link', { name: /Empezar/ })).toBeVisible();
      await expect(page.getByRole('tablist', { name: 'Secciones de configuración' }).getByRole('tab')).toHaveCount(8);
      const overflow = await page.evaluate(() =>
        document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflow, `horizontal page overflow at ${width}x${height}`).toBeLessThanOrEqual(1);
      await page.screenshot({ path: testInfo.outputPath(`settings-express-${width}x${height}.png`), fullPage: true });
      expect(writes).toEqual([]);
    });
  }

  test('Express disables motion on reduced-motion preference without blocking keyboard navigation', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    const writes = await mockSetup(page);
    await page.goto('/app/settings');
    const express = page.getByTestId('settings-express');
    const start = express.getByRole('link', { name: /Empezar/ });
    await expect(start).toBeVisible();
    const transitionDuration = await start.evaluate(node => getComputedStyle(node).transitionDuration);
    // Chromium reports 0.00001s for reduced-motion transitions when the global
    // accessibility reset takes precedence. Prove the duration is effectively zero.
    expect(transitionDuration.split(',').every(value => Number.parseFloat(value) <= 0.00001)).toBe(true);
    await start.focus();
    await expect(start).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/\/app\/settings\/import/);
    expect(writes).toEqual([]);
  });
});

test('paid import quota is shown read-only without blocking free spreadsheets', async ({ page }) => {
  const writes = await mockSetup(page);
  await page.goto('/app/settings/import');
  const quota = page.getByTestId('ai-import-quota');
  await expect(quota).toContainText('Análisis pagado desactivado por seguridad.');
  await expect(quota).toContainText('Excel y CSV siguen disponibles');
  await expect(page.getByLabel('Archivos del negocio')).toBeVisible();
  expect(writes).toEqual([]);
});

test('exhausted paid quota has specific accessible copy', async ({ page }) => {
  await mockSetup(page);
  await page.route('**/api/v1/onboarding/import/ai-quota', route => route.fulfill(json({
    status: 'LIMIT_REACHED', planCode: 'PRO', limit: 2, used: 2, remaining: 0,
    masterEnabled: true, currentPeriodStart: null, currentPeriodEnd: null
  })));
  await page.goto('/app/settings/import');
  await expect(page.getByTestId('ai-import-quota')).toContainText('Agotaste el cupo de IA de este período.');
});
