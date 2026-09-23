const { test, expect } = require('@playwright/test');

const json = body => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

test('confirmed WhatsApp booking is visible as WhatsApp and Confirmada', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));

  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;

    if (path === '/api/v1/auth/me') {
      return route.fulfill(json({ email: 'admin@demo.cl', roles: ['BUSINESS_ADMIN'] }));
    }
    if (path === '/api/v1/business') {
      return route.fulfill(json({
        name: 'Negocio E2E',
        timezone: 'America/Santiago',
        language: 'es',
        humanTransferPhone: null
      }));
    }
    if (path === '/api/v1/onboarding/status') {
      return route.fulfill(json({
        businessProfileConfigured: true,
        servicesConfigured: true,
        scheduleConfigured: true,
        knowledgeConfigured: true,
        humanTransferConfigured: false,
        phoneConfigured: true,
        readyForCalls: true,
        nextStep: 'OPTIONAL_HUMAN_TRANSFER'
      }));
    }
    if (path === '/api/v1/services') {
      return route.fulfill(json([
        { id: 'svc1', name: 'Peluquería', durationMinutes: 30, price: 25000, active: true }
      ]));
    }
    if (path === '/api/v1/business/hours') {
      return route.fulfill(json([
        { dayOfWeek: 4, openTime: '09:00:00', closeTime: '18:00:00' }
      ]));
    }
    if (path === '/api/v1/knowledge') {
      return route.fulfill(json([]));
    }
    if (path === '/api/v1/ai-agent') {
      return route.fulfill(json({
        configured: true,
        name: 'Helvoca',
        language: 'es',
        active: true,
        capabilities: []
      }));
    }
    if (path === '/api/v1/phone-numbers') {
      return route.fulfill(json([
        { id: 'phone1', provider: 'TWILIO', phoneNumber: '+56911111111', active: true }
      ]));
    }
    if (path === '/api/v1/phone-numbers/provisioning/status') {
      return route.fulfill(json({
        enabled: false,
        configured: false,
        purchaseAvailable: false,
        provider: 'TWILIO',
        message: 'No disponible en E2E'
      }));
    }
    if (path === '/api/v1/billing/status') {
      return route.fulfill(json({
        billingEnabled: true,
        checkoutConfigured: true,
        currentPlanCode: 'PRO',
        currentPlanName: 'Pro',
        subscriptionStatus: 'ACTIVE',
        awaitingProviderVerification: false
      }));
    }
    if (path === '/api/v1/subscription') {
      return route.fulfill(json({
        plan: 'PRO',
        status: 'ACTIVE',
        serviceAllowed: true,
        includedMinutes: 500,
        usedMinutes: 23,
        overageMinutes: 0,
        billingProviderConnected: true,
        legacyFallback: false
      }));
    }
    if (path === '/api/v1/public/pricing') {
      return route.fulfill(json([]));
    }
    if (path === '/api/v1/bookings') {
      return route.fulfill(json([
        {
          id: 'wa-confirmed',
          customerId: 'cust-wa',
          serviceId: 'svc1',
          startAt: '2026-09-24T14:30:00Z',
          endAt: '2026-09-24T15:00:00Z',
          status: 'CONFIRMED',
          source: 'AI_WHATSAPP'
        }
      ]));
    }
    if (path === '/api/v1/customers') {
      return route.fulfill(json([
        {
          id: 'cust-wa',
          name: 'Cliente WhatsApp',
          phone: '+56922222222',
          email: 'cliente@example.cl',
          createdAt: '2026-09-23T04:49:00Z'
        }
      ]));
    }
    if (path === '/api/v1/commercial/orders' || path === '/api/v1/audit' || path === '/api/v1/messaging/conversations') {
      return route.fulfill(json([]));
    }
    if (path === '/api/v1/operations/dashboard') {
      return route.fulfill(json({
        businessName: 'Negocio E2E',
        timezone: 'America/Santiago',
        localNow: '2026-09-23T01:50:00-03:00',
        callsToday: 0,
        callDurationSecondsToday: 0,
        bookingsToday: 1,
        newCustomersToday: 1,
        openRequests: 0,
        unansweredQuestions: 0,
        callFailuresToday: 0,
        estimatedCallCostTodayUsd: 0,
        recentCalls: [],
        recentRequests: [],
        unanswered: []
      }));
    }

    return route.fulfill(json(request.method() === 'GET' ? [] : {}));
  });

  await page.goto('/');

  const row = page.locator('#homeBookingsList .home-business-table [data-home-booking-id="wa-confirmed"]');
  await expect(row).toBeVisible();
  await expect(row).toContainText('Cliente WhatsApp');
  await expect(row).toContainText('Peluquería');
  await expect(row).toContainText('WhatsApp');
  await expect(row).toContainText('Confirmada');
});
