const { test, expect } = require('@playwright/test');

const json = body => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

test('business import reviews services with duration and never sends inventory fields', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'import-token'));

  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: 'admin@demo.cl',
    roles: ['BUSINESS_ADMIN']
  })));
  await page.route('**/api/v1/business', route => route.fulfill(json({
    name: 'Veterinaria Norte',
    timezone: 'America/Santiago',
    language: 'es'
  })));

  await page.route('**/api/v1/onboarding/import/preview', route => route.fulfill(json({
    businessName: 'Veterinaria Norte',
    products: [{
      name: 'Consulta veterinaria',
      description: 'Evaluación general',
      price: 20000,
      currency: 'CLP',
      sku: null,
      onHand: null,
      category: 'Consulta',
      kind: 'SERVICE',
      durationMinutes: 45,
      confidence: 0.96,
      sourceName: 'tarifario.csv'
    }],
    sources: [{
      name: 'tarifario.csv',
      kind: 'SERVICES',
      rowCount: 1,
      method: 'SPREADSHEET',
      recognized: true,
      warnings: []
    }],
    warnings: [],
    aiUsed: false
  })));

  let applied = null;
  await page.route('**/api/v1/onboarding/import/apply', async route => {
    applied = route.request().postDataJSON();
    await route.fulfill(json({
      created: 1,
      updated: 0,
      inventoryConfigured: 0,
      items: [{
        catalogItemId: '11111111-1111-1111-1111-111111111111',
        name: 'Consulta veterinaria',
        action: 'CREATED',
        inventoryConfigured: false
      }]
    }));
  });

  await page.goto('/business-import.html');
  await page.locator('#importFiles').setInputFiles({
    name: 'tarifario.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from('Servicio;Precio;Duración\nConsulta veterinaria;$20.000;45 min')
  });

  await page.getByRole('button', { name: 'Analizar y crear borrador' }).click();

  const row = page.locator('[data-import-row="0"]');
  await expect(row.locator('.import-kind')).toHaveValue('SERVICE');
  await expect(row.locator('.import-duration')).toHaveValue('45');
  await expect(row.locator('.import-sku')).toBeDisabled();
  await expect(row.locator('.import-stock')).toBeDisabled();
  await expect(page.locator('#importDetectedCount')).toContainText('1 elemento');

  await page.getByRole('button', { name: 'Importar al negocio' }).click();

  await expect.poll(() => applied).not.toBeNull();
  expect(applied.products).toHaveLength(1);
  expect(applied.products[0]).toMatchObject({
    name: 'Consulta veterinaria',
    kind: 'SERVICE',
    durationMinutes: 45,
    sku: null,
    onHand: null
  });
});
