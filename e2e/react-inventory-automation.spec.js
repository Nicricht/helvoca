const { test, expect } = require('./inventory-coverage-fixture');

// Read/write fixtures are entirely local to Playwright request interception.
// No real messaging provider, customer record or database is contacted.
const json = (data, status = 200) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(data)
});

async function bootAutomation(page, options = {}) {
  await page.addInitScript(() =>
    sessionStorage.setItem('helvoca_access_token', 'inventory-automation-e2e'));

  const itemId = 'automation-item-1';
  const variantId = 'automation-variant-1';
  const requests = { acknowledge: 0, cancel: 0, adjust: [], adjustVariant: [] };
  let alert = {
    id: 'alert-1', catalogItemId: itemId,
    variantId: options.variantAlert ? variantId : null,
    type: 'LOW_STOCK', subjectName: 'Taladro demo', sku: 'TAL-1',
    available: 4, reorderThreshold: 5, acknowledged: false
  };
  let waiting = [{
    id: 'wait-1', catalogItemId: itemId,
    preferredChannel: 'WHATSAPP', contact: '+56911112222',
    status: 'ACTIVE'
  }];
  let stock = [{
    id: 'stock-1', catalogItemId: itemId, sku: 'TAL-1',
    trackingEnabled: true, onHand: 5, reserved: 1,
    available: 4, reorderThreshold: 5, lowStock: true
  }];
  let variants = [{
    id: variantId, catalogItemId: itemId, name: 'Taladro azul',
    optionValuesJson: '{"color":"Azul"}', sku: 'TAL-AZ',
    trackingEnabled: true, active: true,
    onHand: 3, reserved: 0, available: 3, reorderThreshold: 4, lowStock: true
  }];

  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: 'automation@demo.cl',
    roles: options.operator ? ['OPERATOR'] : ['BUSINESS_ADMIN'],
    ...(Array.isArray(options.permissions) ? { permissions: options.permissions } : {})
  })));
  await page.route('**/api/v1/business', route => route.fulfill(json({
    id: 'business-automation-demo', name: 'Ferretería QA'
  })));
  await page.route('**/api/v1/catalog', route => route.fulfill(json([{
    id: itemId, kind: 'PRODUCT', name: 'Taladro demo',
    description: 'Inventario simulado', price: 19900,
    currency: 'CLP', active: true
  }])));
  await page.route('**/api/v1/inventory', route => route.fulfill(json(stock)));
  await page.route('**/api/v1/inventory/alerts', route => route.fulfill(json([alert])));
  await page.route('**/api/v1/inventory/restock-subscriptions', route => route.fulfill(json(waiting)));
  await page.route('**/api/v1/inventory/restock-subscriptions/notifications', route => route.fulfill(json([{
    id: 'notification-1', preferredChannel: 'EMAIL',
    subjectName: 'Taladro demo', contact: 'cliente@example.test',
    available: 4, status: 'READY'
  }])));

  await page.route('**/api/v1/inventory/alerts/alert-1/acknowledge', route => {
    requests.acknowledge += 1;
    if (options.failAcknowledge) {
      return route.fulfill(json({ message: 'alert update unavailable' }, 503));
    }
    alert = { ...alert, acknowledged: true };
    return route.fulfill(json(alert));
  });
  await page.route('**/api/v1/inventory/restock-subscriptions/wait-1/cancel', route => {
    requests.cancel += 1;
    if (options.failCancel) {
      return route.fulfill(json({ message: 'subscription unavailable' }, 503));
    }
    waiting = [];
    return route.fulfill(json({ id: 'wait-1', status: 'CANCELLED' }));
  });
  await page.route('**/api/v1/inventory/' + itemId + '/adjustments', route => {
    const body = route.request().postDataJSON();
    requests.adjust.push(body);
    stock = stock.map(s => ({
      ...s, onHand: s.onHand + body.delta,
      available: s.available + body.delta, lowStock: false
    }));
    return route.fulfill(json(stock[0]));
  });
  await page.route('**/api/v1/inventory/' + itemId + '/variants', route => route.fulfill(json(variants)));
  await page.route('**/api/v1/inventory/' + itemId + '/variants/' + variantId + '/adjustments', route => {
    const body = route.request().postDataJSON();
    requests.adjustVariant.push(body);
    variants = variants.map(v => ({
      ...v, onHand: v.onHand + body.delta, available: v.available + body.delta,
      lowStock: false
    }));
    return route.fulfill(json(variants[0]));
  });
  return { itemId, variantId, requests };
}

test.describe('Inventory automation interaction contracts', () => {
  test('admin marks a low-stock alert attended with one authoritative POST and refreshed state', async ({ page }) => {
    const { requests } = await bootAutomation(page);
    await page.goto('/app/inventory');
    const alert = page.getByTestId('inventory-alert-alert-1');
    await expect(alert).toContainText('Pendiente');
    await expect(page.getByLabel('Resumen de reposición')).toContainText('1 alertas');
    await alert.getByRole('button', { name: 'Marcar atendida' }).click();
    await expect.poll(() => requests.acknowledge).toBe(1);
    await expect(alert).toContainText('Atendida');
    await expect(alert.getByRole('button', { name: 'Marcar atendida' })).toHaveCount(0);
    await expect(page.getByLabel('Resumen de reposición')).toContainText('0 alertas');
  });

  test('admin cancels a restock wait without triggering any external notification', async ({ page }) => {
    const { requests } = await bootAutomation(page);
    await page.goto('/app/inventory');
    const waiting = page.getByTestId('restock-subscription-wait-1');
    await expect(waiting).toContainText('WhatsApp');
    await waiting.getByRole('button', { name: 'Cancelar espera' }).click();
    await expect.poll(() => requests.cancel).toBe(1);
    await expect(waiting).toHaveCount(0);
    await expect(page.getByLabel('Resumen de reposición')).toContainText('0 esperando reposición');
    await expect(page.getByTestId('restock-notification-notification-1')).toContainText('Email');
    expect(requests.adjust).toHaveLength(0);
    expect(requests.adjustVariant).toHaveLength(0);
  });

  test('restock alert opens the base adjustment and sends exactly one explicit delta', async ({ page }) => {
    const { requests } = await bootAutomation(page);
    await page.goto('/app/inventory');
    await page.getByTestId('inventory-alert-alert-1')
      .getByRole('button', { name: 'Reponer stock' }).click();
    const dialog = page.getByRole('dialog', { name: 'Ajustar stock · Taladro demo' });
    await expect(dialog).toBeVisible();
    expect(requests.adjust).toHaveLength(0);
    await dialog.getByLabel('Ajuste').fill('3');
    await dialog.getByLabel('Nota').fill('Reposición controlada');
    await dialog.getByRole('button', { name: 'Aplicar ajuste' }).click();
    await expect.poll(() => requests.adjust.length).toBe(1);
    expect(requests.adjust[0]).toEqual({
      delta: 3, referenceType: 'MANUAL', referenceId: null,
      note: 'Reposición controlada'
    });
    await expect(dialog).toHaveCount(0);
    await expect(page.getByTestId('inventory-available')).toContainText('7');
  });


  test('an orphaned restock alert cannot initiate a stock mutation for a missing catalog item', async ({ page }) => {
    const { requests } = await bootAutomation(page);
    await page.route('**/api/v1/inventory/alerts', route => route.fulfill(json([{
      id: 'alert-1', catalogItemId: 'deleted-product',
      variantId: null, type: 'LOW_STOCK', subjectName: 'Producto eliminado',
      sku: 'STALE-1', available: 0, reorderThreshold: 2, acknowledged: false
    }])));
    await page.goto('/app/inventory');
    const alert = page.getByTestId('inventory-alert-alert-1');
    await expect(alert).toContainText('Producto eliminado');
    // The orphaned alert has no valid catalog row, so the UI must not offer
    // the mutation action at all. An absent action is stronger than a no-op click.
    await expect(alert.getByRole('button', { name: 'Reponer stock' })).toHaveCount(0);
    await expect(page.getByRole('dialog')).toHaveCount(0);
    expect(requests.adjust).toHaveLength(0);
    expect(requests.adjustVariant).toHaveLength(0);
  });

  test('an adjustment without optional note sends null rather than an invented explanation', async ({ page }) => {
    const { requests } = await bootAutomation(page);
    await page.goto('/app/inventory');
    await page.getByTestId('inventory-alert-alert-1').getByRole('button', { name: 'Reponer stock' }).click();
    const dialog = page.getByRole('dialog', { name: 'Ajustar stock · Taladro demo' });
    await dialog.getByLabel('Ajuste').fill('1');
    await dialog.getByLabel('Nota').fill('');
    await dialog.getByRole('button', { name: 'Aplicar ajuste' }).click();
    await expect.poll(() => requests.adjust.length).toBe(1);
    expect(requests.adjust[0]).toEqual({
      delta: 1, referenceType: 'MANUAL', referenceId: null, note: null
    });
    await expect(dialog).toHaveCount(0);
  });

  test('variant-linked restock opens the correct variant adjustment without changing base stock', async ({ page }) => {
    const { requests } = await bootAutomation(page, { variantAlert: true });
    await page.goto('/app/inventory');
    await page.getByTestId('inventory-alert-alert-1')
      .getByRole('button', { name: 'Reponer stock' }).click();
    const dialog = page.getByRole('dialog', { name: 'Variantes · Taladro demo' });
    await expect(dialog.getByRole('heading', { name: 'Ajustar · Taladro azul' })).toBeVisible();
    await dialog.getByLabel('Ajuste de variante').fill('2');
    await dialog.getByLabel('Nota de ajuste').fill('Reposición variante');
    await dialog.getByRole('button', { name: 'Aplicar ajuste de variante' }).click();
    await expect.poll(() => requests.adjustVariant.length).toBe(1);
    expect(requests.adjustVariant[0]).toEqual({
      delta: 2, note: 'Reposición variante'
    });
    expect(requests.adjust).toHaveLength(0);
    await expect(dialog.getByTestId('inventory-variant-automation-variant-1')).toContainText('Disponible: 5');
  });

  test('failed acknowledge and cancel display errors and do not invent success', async ({ page }) => {
    const { requests } = await bootAutomation(page, { failAcknowledge: true, failCancel: true });
    await page.goto('/app/inventory');
    const alert = page.getByTestId('inventory-alert-alert-1');
    await alert.getByRole('button', { name: 'Marcar atendida' }).click();
    await expect.poll(() => requests.acknowledge).toBe(1);
    await expect(page.getByRole('alert')).toContainText('El servidor no pudo guardar');
    await expect(alert).toContainText('Pendiente');
    await expect(page.getByLabel('Resumen de reposición')).toContainText('1 alertas');

    const waiting = page.getByTestId('restock-subscription-wait-1');
    await waiting.getByRole('button', { name: 'Cancelar espera' }).click();
    await expect.poll(() => requests.cancel).toBe(1);
    await expect(page.getByRole('alert')).toContainText('El servidor no pudo guardar');
    await expect(waiting).toContainText('Esperando');
    await expect(page.getByLabel('Resumen de reposición')).toContainText('1 esperando reposición');
  });

  test('operator can inspect alerts, waitlist and queued notices but cannot mutate them', async ({ page }) => {
    const { requests } = await bootAutomation(page, { operator: true });
    await page.goto('/app/inventory');
    await expect(page.getByTestId('inventory-alert-alert-1')).toContainText('Pendiente');
    await expect(page.getByTestId('restock-subscription-wait-1')).toContainText('WhatsApp');
    await expect(page.getByTestId('restock-notification-notification-1')).toContainText('Email');
    await expect(page.getByRole('button', { name: 'Marcar atendida' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Reponer stock' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Cancelar espera' })).toHaveCount(0);
    expect(requests.acknowledge).toBe(0);
    expect(requests.cancel).toBe(0);
  });

  test('explicit empty permission claims prevent admin automation mutations despite legacy role', async ({ page }) => {
    const { requests } = await bootAutomation(page, { permissions: [] });
    await page.goto('/app/inventory');
    await expect(page.getByTestId('inventory-alert-alert-1')).toContainText('Pendiente');
    await expect(page.getByTestId('restock-subscription-wait-1')).toContainText('WhatsApp');
    await expect(page.getByRole('button', { name: 'Marcar atendida' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Reponer stock' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Cancelar espera' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Ajustar stock' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Nuevo producto' })).toHaveCount(0);
    expect(requests.acknowledge).toBe(0);
    expect(requests.cancel).toBe(0);
    expect(requests.adjust).toHaveLength(0);
  });

  test('explicit inventory-only claim permits stock management without catalog creation', async ({ page }) => {
    const { requests } = await bootAutomation(page, { permissions: ['INVENTORY_MANAGE'] });
    await page.goto('/app/inventory');
    await expect(page.getByRole('button', { name: 'Nuevo producto' })).toHaveCount(0);
    await expect(page.getByTestId('inventory-alert-alert-1')
      .getByRole('button', { name: 'Marcar atendida' })).toBeVisible();
    await expect(page.getByTestId('inventory-alert-alert-1')
      .getByRole('button', { name: 'Reponer stock' })).toBeVisible();
    await expect(page.getByTestId('restock-subscription-wait-1')
      .getByRole('button', { name: 'Cancelar espera' })).toBeVisible();
    expect(requests.acknowledge).toBe(0);
    expect(requests.cancel).toBe(0);
  });



  test('stale variant restock alert does not open adjustment or issue inventory mutations', async ({ page }) => {
    const { requests } = await bootAutomation(page, { variantAlert: true });
    await page.route('**/api/v1/inventory/automation-item-1/variants', route => route.fulfill(json([{
      id: 'automation-variant-1', catalogItemId: 'automation-item-1',
      name: 'Taladro azul', optionValuesJson: '{"color":"Azul"}', sku: 'TAL-AZ',
      trackingEnabled: true, active: false, onHand: 3,
      reserved: 0, available: 3, reorderThreshold: 4, lowStock: true
    }])));
    await page.goto('/app/inventory');
    await page.getByTestId('inventory-alert-alert-1').getByRole('button', { name: 'Reponer stock' }).click();
    const dialog = page.getByRole('dialog', { name: 'Variantes · Taladro demo' });
    await expect(dialog.getByTestId('inventory-variant-automation-variant-1')).toContainText('Inactiva');
    await expect(dialog.getByRole('heading', { name: 'Ajustar · Taladro azul' })).toHaveCount(0);
    await expect(page.getByRole('alert')).toContainText('ya no está disponible');
    expect(requests.adjust).toHaveLength(0);
    expect(requests.adjustVariant).toHaveLength(0);
  });



  test('restock on unconfigured stock explains why it cannot adjust without sending mutations', async ({ page }) => {
    const { requests } = await bootAutomation(page);
    await page.route('**/api/v1/inventory', route => route.fulfill(json([])));
    await page.goto('/app/inventory');
    await expect(page.getByTestId('inventory-row-automation-item-1')).toContainText('Sin configurar');
    const alert = page.getByTestId('inventory-alert-alert-1');
    await alert.getByRole('button', { name: 'Reponer stock' }).click();
    await expect(page.getByRole('alert')).toContainText('Configura el stock');
    await expect(page.getByRole('dialog')).toHaveCount(0);
    expect(requests.adjust).toHaveLength(0);
    expect(requests.adjustVariant).toHaveLength(0);
    expect(requests.acknowledge).toBe(0);
    expect(requests.cancel).toBe(0);
  });

  test('a synchronous double activation of alert acknowledgement submits no duplicate', async ({ page }) => {
    const { requests } = await bootAutomation(page);
    await page.goto('/app/inventory');
    const alert = page.getByTestId('inventory-alert-alert-1');
    await alert.getByRole('button', { name: 'Marcar atendida' }).evaluate(button => {
      button.click();
      button.click();
    });
    await expect(alert).toContainText('Atendida');
    await expect.poll(() => requests.acknowledge).toBe(1);
    expect(requests.cancel).toBe(0);
    expect(requests.adjust).toHaveLength(0);
    expect(requests.adjustVariant).toHaveLength(0);
  });

});
