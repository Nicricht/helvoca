import { test, expect } from '@playwright/test';

const businessId = '11111111-1111-1111-1111-111111111111';
const token = 'test.jwt.token';

const subscription = {
  businessId,
  plan: 'EMPRENDE',
  status: 'ACTIVE',
  serviceAllowed: true,
  includedVoiceMinutes: 100,
  usedVoiceMinutes: 12,
  remainingVoiceMinutes: 88,
  voiceOverageMinutes: 0,
  voiceOverageRateClp: 149,
  estimatedVoiceOverageClp: 0,
  maxConcurrentCalls: 1,
  whatsappEnabled: false
};

const billing = {
  businessId,
  provider: 'MERCADOPAGO',
  providerConfigured: true,
  providerReference: 'pre-test-123',
  providerStatus: 'authorized',
  providerLiveMode: false,
  subscriptionStatus: 'ACTIVE',
  activePlan: 'EMPRENDE',
  pendingPlan: null,
  amountClp: 24990,
  nextPaymentDate: '2026-09-30'
};

const pricing = [
  {
    code: 'EMPRENDE',
    displayName: 'Emprende',
    monthlyPriceClp: 24990,
    includedVoiceMinutes: 100,
    overageRateClp: 149,
    maxConcurrentCalls: 1,
    features: ['Voz', 'Agenda']
  },
  {
    code: 'NEGOCIO',
    displayName: 'Negocio',
    monthlyPriceClp: 39990,
    includedVoiceMinutes: 250,
    overageRateClp: 129,
    maxConcurrentCalls: 2,
    features: ['Voz', 'Agenda', 'WhatsApp piloto']
  }
];

test.beforeEach(async ({ page }) => {
  await page.addInitScript(({ businessId, token }) => {
    localStorage.setItem('businessId', businessId);
    localStorage.setItem('token', token);
    window.__openedCheckoutUrls = [];
    window.open = (url) => {
      window.__openedCheckoutUrls.push(url);
      return null;
    };
  }, { businessId, token });

  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    const method = route.request().method();

    if (path === '/api/v1/subscription') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(subscription) });
      return;
    }
    if (path === '/api/v1/billing/status') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(billing) });
      return;
    }
    if (path === '/api/v1/public/pricing') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(pricing) });
      return;
    }
    if (path === '/api/v1/billing/checkout' && method === 'POST') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          provider: 'MERCADOPAGO',
          plan: 'NEGOCIO',
          providerReference: 'pre-e2e-1',
          providerStatus: 'pending',
          checkoutUrl: 'https://checkout.example.test/pre-e2e-1'
        })
      });
      return;
    }
    if (path === '/api/v1/ai-agent/voices') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
      return;
    }
    await route.fulfill({ status: 404, contentType: 'application/json', body: '{}' });
  });
});

test('commercial dashboard shows confirmed and pending state without starting checkout on load', async ({ page }) => {
  await page.goto('/');

  const card = page.locator('#commercialStatusCard');
  await expect(card).toBeVisible();
  await expect(card.locator('#commercialPlan')).toHaveText('Emprende');
  await expect(card.locator('#commercialPlanStatus')).toHaveText('Activo');
  await expect(card.locator('#commercialUsage')).toContainText('12 / 100 min');
  await expect(card.locator('#commercialWhatsApp')).toContainText('No habilitado');
  await expect(card.locator('#commercialBillingProvider')).toContainText('MercadoPago');
  await expect(card.locator('#commercialBillingReference')).toContainText('pre-test-123');
  await expect(card.locator('#commercialBillingNextPayment')).toContainText('30-09-2026');
  await expect.poll(() => page.evaluate(() => window.__openedCheckoutUrls)).toEqual([]);
});

test('plan checkout starts only after explicit confirmation and does not activate the plan locally', async ({ page }) => {
  let checkoutPayload = null;
  await page.unroute('**/api/v1/**');
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    const method = route.request().method();

    if (path === '/api/v1/subscription') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(subscription) });
      return;
    }
    if (path === '/api/v1/billing/status') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(billing) });
      return;
    }
    if (path === '/api/v1/public/pricing') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(pricing) });
      return;
    }
    if (path === '/api/v1/billing/checkout' && method === 'POST') {
      checkoutPayload = route.request().postDataJSON();
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          provider: 'MERCADOPAGO',
          plan: 'NEGOCIO',
          providerReference: 'pre-e2e-1',
          providerStatus: 'pending',
          checkoutUrl: 'https://checkout.example.test/pre-e2e-1'
        })
      });
      return;
    }
    if (path === '/api/v1/ai-agent/voices') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
      return;
    }
    await route.fulfill({ status: 404, contentType: 'application/json', body: '{}' });
  });

  await page.goto('/');
  const card = page.locator('#commercialStatusCard');
  await expect(card.locator('#commercialPlan')).toHaveText('Emprende');

  await card.locator('#commercialPlanSelect').selectOption('NEGOCIO');
  await card.getByRole('button', { name: 'Cambiar plan' }).click();
  await expect(page.locator('#planConfirmDialog')).toBeVisible();
  await expect.poll(() => checkoutPayload).toBeNull();
  await expect.poll(() => page.evaluate(() => window.__openedCheckoutUrls)).toEqual([]);

  await page.locator('#confirmPlanChange').click();
  await expect.poll(() => checkoutPayload).toEqual({ plan: 'NEGOCIO' });
  await expect(card.locator('#commercialPlan')).toHaveText('Emprende');
  await expect(card).toContainText('Plan pendiente: Negocio');
  await expect(card.locator('#commercialPlanMessage')).toContainText('Checkout creado para Negocio');
  await expect.poll(() => page.evaluate(() => window.__openedCheckoutUrls)).toEqual([
    'https://checkout.example.test/pre-e2e-1'
  ]);
});

test('sales landing exposes pricing and signup paths', async ({ page }) => {
  await page.goto('/sales.html');
  await expect(page).toHaveTitle(/Helvoca/);
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Que una llamada o un WhatsApp sin responder');
  await expect(page.getByRole('link', { name: 'Probar con mi negocio' })).toHaveAttribute('href', '/');
  await expect(page.getByRole('link', { name: 'Planes desde $24.990' })).toHaveAttribute('href', '/pricing.html');
});
