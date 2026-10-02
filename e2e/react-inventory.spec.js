const { test, expect } = require('@playwright/test');

const json = body => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function bootInventory(page, options = {}) {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', options.token || 'inventory-react-e2e'));

  await page.route('**/api/v1/auth/me', route => {
    if (options.expired) {
      return route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'expired' })
      });
    }
    return route.fulfill(json({
      email: 'owner@negocio.cl',
      roles: options.roles || ['BUSINESS_OWNER']
    }));
  });

  await page.route('**/api/v1/business', route => route.fulfill(json({
    id: '11111111-1111-1111-1111-111111111111',
    name: 'Taller Norte'
  })));

  await page.route('**/api/v1/catalog', route => {
    if (options.catalogError) {
      return route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'catalog unavailable' })
      });
    }
    return route.fulfill(json([
      {
        id: 'prod-1',
        kind: 'PRODUCT',
        name: 'Taladro percutor',
        description: 'Taladro 18V',
        price: 54990,
        currency: 'CLP',
        active: true
      },
      {
        id: 'prod-2',
        kind: 'PRODUCT',
        name: 'Broca metal 8 mm',
        description: 'Broca HSS',
        price: 4990,
        currency: 'CLP',
        active: true
      },
      {
        id: 'svc-1',
        kind: 'SERVICE',
        name: 'Instalación',
        active: true
      }
    ]));
  });

  await page.route('**/api/v1/inventory', route => route.fulfill(json([
    {
      id: 'stock-1',
      catalogItemId: 'prod-1',
      sku: 'TAL-18V',
      trackingEnabled: true,
      onHand: 8,
      reserved: 3,
      available: 5,
      reorderThreshold: 4,
      lowStock: false
    },
    {
      id: 'stock-2',
      catalogItemId: 'prod-2',
      sku: 'BRO-8MM',
      trackingEnabled: true,
      onHand: 2,
      reserved: 1,
      available: 1,
      reorderThreshold: 2,
      lowStock: true
    }
  ])));

  await page.route('**/api/v1/inventory/alerts', route => route.fulfill(json([
    {
      id: 'alert-1',
      catalogItemId: 'prod-2',
      type: 'LOW_STOCK',
      acknowledged: false
    }
  ])));

  await page.route('**/api/v1/inventory/restock-subscriptions', route => route.fulfill(json([])));
  await page.route('**/api/v1/inventory/restock-subscriptions/notifications', route => route.fulfill(json([])));
}

test.describe('React Inventory migration', () => {
  test('renders authoritative stock without confusing physical, reserved and available', async ({ page }) => {
    await bootInventory(page);
    await page.goto('/app/inventory');

    await expect(page.getByRole('heading', { level: 1, name: 'Inventario' })).toBeVisible();
    await expect(page.getByTestId('inventory-products')).toContainText('2');
    await expect(page.getByTestId('inventory-available')).toContainText('6');
    await expect(page.getByTestId('inventory-reserved')).toContainText('4');
    await expect(page.getByTestId('inventory-low-stock')).toContainText('1');

    const taladro = page.getByTestId('inventory-row-prod-1');
    await expect(taladro).toContainText('Taladro percutor');
    await expect(taladro).toContainText('5');
    await expect(taladro).toContainText('3');
    await expect(taladro).toContainText('8');

    const broca = page.getByTestId('inventory-row-prod-2');
    await expect(broca).toContainText('Stock bajo');
  });

  test('keeps search, filtering and sorting local to the inventory workspace', async ({ page }) => {
    await bootInventory(page);
    await page.goto('/app/inventory');

    await page.getByRole('searchbox', { name: 'Buscar productos' }).fill('Broca');
    await expect(page.getByTestId('inventory-row-prod-2')).toBeVisible();
    await expect(page.getByTestId('inventory-row-prod-1')).toHaveCount(0);

    await page.getByRole('searchbox', { name: 'Buscar productos' }).fill('');
    await page.getByLabel('Estado').selectOption('LOW');
    await expect(page.getByTestId('inventory-row-prod-2')).toBeVisible();
    await expect(page.getByTestId('inventory-row-prod-1')).toHaveCount(0);
  });

  test('stays useful when catalog data fails instead of inventing stock data', async ({ page }) => {
    await bootInventory(page, { catalogError: true });
    await page.goto('/app/inventory');

    await expect(page.getByRole('alert')).toContainText('No pudimos cargar');
    await expect(page.getByTestId('inventory-available')).not.toContainText('6');
    await expect(page.getByRole('button', { name: /reintentar/i })).toBeVisible();
  });

  test('does not expose manage actions to a restricted role', async ({ page }) => {
    await bootInventory(page, { roles: ['OPERATOR'] });
    await page.goto('/app/inventory');

    await expect(page.getByRole('heading', { level: 1, name: 'Inventario' })).toBeVisible();
    await expect(page.getByRole('button', { name: /nuevo producto/i })).toHaveCount(0);
    await expect(page.getByRole('button', { name: /ajustar stock/i })).toHaveCount(0);
  });

  test('redirects to auth on expired session', async ({ page }) => {
    await bootInventory(page, { expired: true });
    await page.goto('/app/inventory');

    await expect(page).toHaveURL('http://127.0.0.1:4173/');
  });

  test('stays contained at desktop tablet and mobile widths', async ({ page }) => {
    await bootInventory(page);

    for (const viewport of [
      { width: 1440, height: 900 },
      { width: 768, height: 1024 },
      { width: 390, height: 844 }
    ]) {
      await page.setViewportSize(viewport);
      await page.goto('/app/inventory');
      await expect(page.getByRole('heading', { level: 1, name: 'Inventario' })).toBeVisible();
      expect(await page.evaluate(() =>
        document.documentElement.scrollWidth <= document.documentElement.clientWidth
      )).toBe(true);
    }
  });
});
