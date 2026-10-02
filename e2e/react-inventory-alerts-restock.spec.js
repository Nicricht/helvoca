const { test, expect } = require('@playwright/test');

const json = body => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function bootAutomationInventory(page, roles = ['BUSINESS_ADMIN']) {
  await page.addInitScript(
    token => sessionStorage.setItem('helvoca_access_token', token),
    'inventory-react-automation'
  );

  const productId = '77777777-7777-7777-7777-777777777777';
  const alertId = '88888888-8888-8888-8888-888888888888';
  const subscriptionId = '99999999-9999-9999-9999-999999999999';
  const notificationId = 'aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb';

  let inventory = [{
    id: 'stock-alert-product',
    catalogItemId: productId,
    sku: 'SHA-01',
    trackingEnabled: true,
    onHand: 0,
    reserved: 0,
    available: 0,
    reorderThreshold: 2,
    lowStock: true
  }];

  let alerts = [{
    id: alertId,
    catalogItemId: productId,
    variantId: null,
    type: 'OUT_OF_STOCK',
    subjectName: 'Shampoo profesional',
    sku: 'SHA-01',
    available: 0,
    reorderThreshold: 2,
    acknowledged: false,
    acknowledgedAt: null,
    createdAt: '2026-10-02T15:00:00Z'
  }];

  let subscriptions = [{
    id: subscriptionId,
    customerId: 'bbbbbbbb-1111-2222-3333-cccccccccccc',
    catalogItemId: productId,
    variantId: null,
    preferredChannel: 'WHATSAPP',
    contact: '+56911112222',
    consentGranted: true,
    consentGrantedAt: '2026-10-02T14:00:00Z',
    consentSource: 'VOICE',
    status: 'ACTIVE',
    notifiedAt: null,
    cancelledAt: null,
    createdAt: '2026-10-02T14:00:00Z'
  }];

  let notifications = [];

  const requests = {
    acknowledge: 0,
    adjust: [],
    cancel: 0
  };

  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: roles.includes('BUSINESS_ADMIN') ? 'admin@demo.cl' : 'operator@demo.cl',
    roles
  })));

  await page.route('**/api/v1/business', route => route.fulfill(json({
    id: '11111111-1111-1111-1111-111111111111',
    name: 'Barbería Norte'
  })));

  await page.route('**/api/v1/catalog', route => route.fulfill(json([{
    id: productId,
    kind: 'PRODUCT',
    name: 'Shampoo profesional',
    description: 'Línea hidratante',
    price: 8990,
    currency: 'CLP',
    active: true
  }])));

  await page.route('**/api/v1/inventory', route => {
    if (!route.request().url().endsWith('/api/v1/inventory')) return route.continue();
    return route.fulfill(json(inventory));
  });

  await page.route(`**/api/v1/inventory/alerts/${alertId}/acknowledge`, async route => {
    requests.acknowledge += 1;
    alerts = alerts.map(alert => ({
      ...alert,
      acknowledged: true,
      acknowledgedAt: '2026-10-02T15:10:00Z'
    }));
    return route.fulfill(json(alerts[0]));
  });

  await page.route('**/api/v1/inventory/alerts', route => route.fulfill(json(alerts)));

  await page.route(`**/api/v1/inventory/restock-subscriptions/${subscriptionId}/cancel`, async route => {
    requests.cancel += 1;
    subscriptions = subscriptions.map(subscription => ({
      ...subscription,
      status: 'CANCELLED',
      cancelledAt: '2026-10-02T15:12:00Z'
    }));
    return route.fulfill(json(subscriptions[0]));
  });

  await page.route('**/api/v1/inventory/restock-subscriptions/notifications', route => {
    return route.fulfill(json(notifications));
  });

  await page.route('**/api/v1/inventory/restock-subscriptions', route => {
    return route.fulfill(json(subscriptions.filter(subscription => subscription.status === 'ACTIVE')));
  });

  await page.route(`**/api/v1/inventory/${productId}/adjustments`, async route => {
    const body = route.request().postDataJSON();
    requests.adjust.push(body);
    inventory = [{
      ...inventory[0],
      onHand: inventory[0].onHand + body.delta,
      available: inventory[0].available + body.delta,
      lowStock: false
    }];
    subscriptions = [];
    notifications = [{
      id: notificationId,
      subscriptionId,
      customerId: 'bbbbbbbb-1111-2222-3333-cccccccccccc',
      catalogItemId: productId,
      variantId: null,
      preferredChannel: 'WHATSAPP',
      contact: '+56911112222',
      subjectName: 'Shampoo profesional',
      sku: 'SHA-01',
      available: inventory[0].available,
      status: 'PENDING',
      idempotencyKey: `inventory-restock:${subscriptionId}`,
      createdAt: '2026-10-02T15:20:00Z'
    }];
    return route.fulfill(json(inventory[0]));
  });

  return { productId, alertId, subscriptionId, notificationId, requests };
}

test.describe('React Inventory alerts and restock', () => {
  test('admin handles an alert and restocks from the automation workspace', async ({ page }) => {
    const { requests, notificationId } = await bootAutomationInventory(page);
    await page.goto('/app/inventory');

    const automation = page.getByRole('region', { name: 'Alertas y reposición' });
    await expect(automation).toBeVisible();

    const alert = automation.getByTestId('inventory-alert-88888888-8888-8888-8888-888888888888');
    await expect(alert).toContainText('Agotado');
    await expect(alert).toContainText('Shampoo profesional');
    await expect(alert).toContainText('Disponible: 0');

    const waiting = automation.getByTestId('restock-subscription-99999999-9999-9999-9999-999999999999');
    await expect(waiting).toContainText('+56911112222');
    await expect(waiting).toContainText('WhatsApp');

    await alert.getByRole('button', { name: 'Marcar atendida' }).click();
    await expect.poll(() => requests.acknowledge).toBe(1);
    await expect(alert).toContainText('Atendida');

    await alert.getByRole('button', { name: 'Reponer stock' }).click();
    const adjustDialog = page.getByRole('dialog', { name: 'Ajustar stock · Shampoo profesional' });
    await adjustDialog.getByLabel('Ajuste').fill('5');
    await adjustDialog.getByLabel('Nota').fill('Reposición por alerta');
    await adjustDialog.getByRole('button', { name: 'Aplicar ajuste' }).click();

    await expect.poll(() => requests.adjust.length).toBe(1);
    expect(requests.adjust[0]).toEqual({
      delta: 5,
      referenceType: 'MANUAL',
      referenceId: null,
      note: 'Reposición por alerta'
    });

    await expect(page.getByTestId('inventory-available')).toContainText('5');
    await expect(automation.getByText('0 esperando reposición')).toBeVisible();
    await expect(automation.getByText('1 aviso listo')).toBeVisible();

    const notification = automation.getByTestId(`restock-notification-${notificationId}`);
    await expect(notification).toContainText('Pendiente de envío');
    await expect(notification).toContainText('+56911112222');
    await expect(notification).toContainText('Disponible: 5');
  });

  test('operator can inspect alerts and restock queues without admin actions', async ({ page }) => {
    await bootAutomationInventory(page, ['OPERATOR']);
    await page.goto('/app/inventory');

    const automation = page.getByRole('region', { name: 'Alertas y reposición' });
    await expect(automation.getByText('Shampoo profesional')).toBeVisible();
    await expect(automation.getByText('+56911112222')).toBeVisible();

    await expect(automation.getByRole('button', { name: 'Marcar atendida' })).toHaveCount(0);
    await expect(automation.getByRole('button', { name: 'Reponer stock' })).toHaveCount(0);
    await expect(automation.getByRole('button', { name: 'Cancelar espera' })).toHaveCount(0);
  });
});
