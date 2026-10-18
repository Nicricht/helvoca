const { test, expect } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

const json = (body, status = 200) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function boot(page, options = {}) {
  const roles = options.roles || ['BUSINESS_ADMIN'];
  const permissions = options.permissions || [
    'BUSINESS_READ',
    'BUSINESS_CONFIGURE',
    'CATALOG_READ',
    'CATALOG_MANAGE',
    'INVENTORY_READ',
    'INVENTORY_MANAGE'
  ];

  await page.addInitScript(() => {
    sessionStorage.setItem('helvoca_access_token', 'business-import-react-e2e');
  });

  let previewCalls = 0;
  let applyCalls = 0;
  let applyPayload = null;
  let savedProfile = null;
  let savedHours = null;

  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const pathname = url.pathname;

    if (pathname === '/api/v1/auth/me' && request.method() === 'GET') {
      return route.fulfill(json({
        email: 'admin@demo.cl',
        roles,
        permissions
      }));
    }

    if (pathname === '/api/v1/subscription' && request.method() === 'GET') {
      return route.fulfill(json({
        plan: 'PRO',
        publicPlanCode: 'PRO',
        planName: 'Profesional',
        status: 'ACTIVE',
        includedMinutes: 500,
        usedMinutes: 42
      }));
    }

    if (pathname === '/api/v1/business' && request.method() === 'GET') {
      return route.fulfill(json({
        name: 'Veterinaria Norte',
        timezone: 'America/Santiago',
        language: 'es'
      }));
    }

    if (pathname === '/api/v1/business/profile' && request.method() === 'GET') {
      return route.fulfill(json(options.currentProfile || {}));
    }
    if (pathname === '/api/v1/business/profile' && request.method() === 'PUT') {
      savedProfile = request.postDataJSON();
      return route.fulfill(json(savedProfile));
    }
    if (pathname === '/api/v1/business/hours' && request.method() === 'GET') {
      return route.fulfill(json(options.existingHours || []));
    }
    if (pathname === '/api/v1/business/hours' && request.method() === 'PUT') {
      savedHours = request.postDataJSON();
      return route.fulfill(json(savedHours.hours));
    }

    if (pathname === '/api/v1/onboarding/import/preview' && request.method() === 'POST') {
      previewCalls += 1;
      return route.fulfill(json({
        businessName: 'Veterinaria Norte',
        products: [
          {
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
          },
          {
            name: 'Alimento premium',
            description: null,
            price: 15990,
            currency: 'CLP',
            sku: 'ALI-01',
            onHand: null,
            category: 'Alimentos',
            kind: 'PRODUCT',
            durationMinutes: null,
            confidence: 0.91,
            sourceName: 'tarifario.csv'
          }
        ],
        sources: [{
          name: 'tarifario.csv',
          kind: 'MIXED',
          rowCount: 2,
          method: 'SPREADSHEET',
          recognized: true,
          warnings: []
        }],
        warnings: [],
        aiUsed: false,
        businessFacts: options.businessFacts || null
      }));
    }

    if (pathname === '/api/v1/onboarding/import/apply' && request.method() === 'POST') {
      applyCalls += 1;
      applyPayload = request.postDataJSON();
      return route.fulfill(json({
        created: 2,
        updated: 0,
        inventoryConfigured: 1,
        items: [
          {
            catalogItemId: '11111111-1111-1111-1111-111111111111',
            name: 'Consulta veterinaria',
            action: 'CREATED',
            inventoryConfigured: false
          },
          {
            catalogItemId: '22222222-2222-2222-2222-222222222222',
            name: 'Alimento premium',
            action: 'CREATED',
            inventoryConfigured: true
          }
        ]
      }));
    }

    return route.fulfill(json({}));
  });

  return {
    previewCalls: () => previewCalls,
    applyCalls: () => applyCalls,
    applyPayload: () => applyPayload,
    savedProfile: () => savedProfile,
    savedHours: () => savedHours
  };
}

test.describe('React business import migration', () => {

  test('Aurora document fields are suggestions until independently confirmed, preserving unrelated profile facts', async ({ page }) => {
    const calls = await boot(page, {
      currentProfile: { presetKey: "medical", publicEmail: "actual@example.test", defaultCurrency: "CLP",
        sellsServices: true, usesReservations: true },
      businessFacts: {
        publicPhone: null, publicEmail: null, websiteUrl: null, addressLine: null,
        commune: "Providencia", city: "Santiago", region: null, countryCode: null,
        publicDescription: "Centro médico ambulatorio", sourceName: "aurora.pdf",
        hours: [{ dayOfWeek: 1, openTime: "08:00", closeTime: "20:00" }]
      }
    });
    await page.goto('/app/settings/import');
    await page.getByLabel('Archivos del negocio').setInputFiles({
      name: 'aurora.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4\n%test')
    });
    await page.getByRole('button', { name: 'Analizar y crear borrador' }).click();
    await expect(page.getByTestId('business-import-facts')).toBeVisible();
    await expect(page.getByLabel('Comuna')).toHaveValue('Providencia');
    await expect(page.getByLabel('Teléfono público')).toHaveValue('');
    expect(calls.savedProfile()).toBeNull();
    expect(calls.savedHours()).toBeNull();
    await page.getByLabel('Comuna').fill('Providencia Norte');
    await page.getByRole('button', { name: 'Confirmar y guardar perfil' }).click();
    await expect(page.getByRole('button', { name: 'Perfil guardado' })).toBeVisible();
    expect(calls.savedProfile().commune).toBe('Providencia Norte');
    expect(calls.savedProfile().publicEmail).toBe('actual@example.test');
    expect(calls.savedProfile().presetKey).toBe('medical');
    expect(calls.savedProfile().publicPhone).toBeNull();
    expect(calls.savedProfile().usesReservations).toBe(true);
    expect(calls.savedHours()).toBeNull();
    await page.getByRole('button', { name: 'Confirmar y guardar horarios' }).click();
    await expect(page.getByRole('button', { name: 'Horarios guardados' })).toBeVisible();
    expect(calls.savedHours().hours).toEqual([{ dayOfWeek: 1, openTime: '08:00', closeTime: '20:00' }]);
    expect(calls.applyCalls()).toBe(0);
  });

  test('file-import schedule never overwrites an existing weekly calendar', async ({ page }) => {
    const calls = await boot(page, {
      existingHours: [{ dayOfWeek: 2, openTime: '10:00', closeTime: '19:00' }],
      businessFacts: { publicPhone: null, publicEmail: null, websiteUrl: null, addressLine: null,
        commune: 'Providencia', city: null, region: null, countryCode: null, publicDescription: null,
        sourceName: 'aurora.pdf', hours: [{ dayOfWeek: 1, openTime: '08:00', closeTime: '20:00' }] }
    });
    await page.goto('/app/settings/import');
    await page.getByLabel('Archivos del negocio').setInputFiles({
      name: 'aurora.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4\n%test')
    });
    await page.getByRole('button', { name: 'Analizar y crear borrador' }).click();
    await page.getByRole('button', { name: 'Confirmar y guardar horarios' }).click();
    await expect(page.getByText(/Ya existen horarios configurados/)).toBeVisible();
    expect(calls.savedHours()).toBeNull();
  });


  test('admin previews, reviews and explicitly applies without inventing service inventory', async ({ page }) => {
    const calls = await boot(page);

    await page.goto('/app/settings/import');

    await expect(page.getByRole('heading', { level: 1, name: 'Importar negocio' })).toBeVisible();
    await expect(page.getByText(/vista previa.*revisión.*aplicar/i)).toBeVisible();

    await page.getByLabel('Archivos del negocio').setInputFiles({
      name: 'tarifario.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from('Tipo;Nombre;Precio;Duración;SKU\nServicio;Consulta veterinaria;20000;45 min;\nProducto;Alimento premium;15990;;ALI-01')
    });

    expect(calls.previewCalls()).toBe(0);
    expect(calls.applyCalls()).toBe(0);

    await page.getByRole('button', { name: 'Analizar y crear borrador' }).click();

    await expect.poll(calls.previewCalls).toBe(1);
    expect(calls.applyCalls()).toBe(0);

    const serviceRow = page.getByTestId('business-import-row-0');
    await expect(serviceRow.getByLabel('Tipo')).toHaveValue('SERVICE');
    await expect(serviceRow.getByLabel('Duración')).toHaveValue('45');
    await expect(serviceRow.getByLabel('SKU')).toBeDisabled();
    await expect(serviceRow.getByLabel('Stock')).toBeDisabled();

    const productRow = page.getByTestId('business-import-row-1');
    await expect(productRow.getByLabel('Stock')).toHaveValue('');

    expect(calls.applyCalls()).toBe(0);
    await page.getByRole('button', { name: 'Importar al negocio' }).click();
    await expect.poll(calls.applyCalls).toBe(1);

    const payload = calls.applyPayload();
    expect(payload.products).toHaveLength(2);
    expect(payload.products[0]).toMatchObject({
      name: 'Consulta veterinaria',
      kind: 'SERVICE',
      durationMinutes: 45,
      sku: null,
      onHand: null
    });
    expect(payload.products[1]).toMatchObject({
      name: 'Alimento premium',
      kind: 'PRODUCT',
      sku: 'ALI-01',
      onHand: null
    });

    await expect(page.getByRole('heading', { name: 'Importación aplicada' })).toBeVisible();
    await expect(page.getByTestId('business-import-created')).toHaveText('2');
    await expect(page.getByTestId('business-import-updated')).toHaveText('0');
    await expect(page.getByTestId('business-import-inventory')).toHaveText('1');
  });

  test('non-admin cannot use the import surface', async ({ page }) => {
    let previewCalls = 0;
    await boot(page, {
      roles: ['OPERATOR'],
      permissions: ['BUSINESS_READ', 'CATALOG_READ', 'INVENTORY_READ']
    });

    await page.route('**/api/v1/onboarding/import/preview', route => {
      previewCalls += 1;
      return route.fulfill(json({}));
    });

    await page.goto('/app/settings/import');

    await expect(page).toHaveURL(/\/app\/settings\/?$/);
    expect(previewCalls).toBe(0);
  });

  test('legacy URL is compatibility-only and does not load retired page assets', async ({ page }) => {
    await boot(page);
    const requested = [];
    page.on('request', request => requested.push(new URL(request.url()).pathname));

    await page.goto('/business-import.html');

    await expect(page).toHaveURL(/\/app\/settings\/import\/?$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Importar negocio' })).toBeVisible();
    expect(requested).not.toContain('/business-import.js');
    expect(requested).not.toContain('/business-import.css');
  });

  test('Settings and Inventory point directly to the canonical React importer', async () => {
    const root = path.resolve(__dirname, '..');
    const settings = fs.readFileSync(path.join(root, 'frontend/src/pages/Settings/SettingsPage.tsx'), 'utf8');
    const inventory = fs.readFileSync(path.join(root, 'frontend/src/pages/Inventory/InventoryPage.tsx'), 'utf8');

    expect(settings).not.toContain('/business-import.html');
    expect(inventory).not.toContain('/business-import.html');
    expect(settings).toContain('/app/settings/import');
    expect(inventory).toContain('/app/settings/import');
  });
});
