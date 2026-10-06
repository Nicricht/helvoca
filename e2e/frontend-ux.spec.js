const { test, expect } = require('@playwright/test');

const json = (body, status = 200) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function mockReactHome(page) {
  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: 'admin@demo.cl',
    roles: ['BUSINESS_ADMIN']
  })));
  await page.route('**/api/v1/onboarding/status', route => route.fulfill(json({
    businessProfileConfigured: true,
    servicesConfigured: true,
    scheduleConfigured: true,
    knowledgeConfigured: true,
    humanTransferConfigured: false,
    phoneConfigured: true,
    readyForCalls: true,
    nextStep: 'READY'
  })));
  await page.route('**/api/v1/operations/dashboard', route => route.fulfill(json({
    businessName: 'Negocio E2E',
    timezone: 'America/Santiago',
    localNow: '2026-10-02T18:00:00-03:00',
    callsToday: 2,
    callDurationSecondsToday: 180,
    bookingsToday: 1,
    newCustomersToday: 1,
    openRequests: 0,
    unansweredQuestions: 0,
    callFailuresToday: 0,
    estimatedCallCostTodayUsd: 0.2,
    recentCalls: [],
    recentRequests: [],
    unanswered: []
  })));
  await page.route('**/api/v1/commercial/analytics**', route => route.fulfill(json({
    days: 7,
    timezone: 'America/Santiago',
    primaryCurrency: 'CLP',
    totalRevenue: 25000,
    paidOrders: 1,
    unitsSold: 1,
    averageTicket: 25000,
    revenueChangePercent: null,
    currencyTotals: [{ currency: 'CLP', amount: 25000 }],
    salesOverTime: [],
    topProducts: [],
    channels: [],
    peakWeekday: null,
    peakHour: null,
    recepVozOrders: 1,
    recepVozRevenue: 25000,
    bookingCurrency: 'CLP',
    paidBookings: 0,
    bookingRevenue: 0,
    providerVerifiedBookingRevenue: 0,
    manualRecordedBookingRevenue: 0,
    recepVozPaidBookings: 0,
    recepVozBookingRevenue: 0,
    bookingCurrencyTotals: [],
    insights: []
  })));
}

test('auth tabs and simplified registration controls are usable', async ({ page }) => {
  await page.goto('/');

  await expect(page.locator('#registerForm')).toBeVisible();
  await expect(page.locator('#loginForm')).toBeHidden();
  await expect(page.locator('#registerForm input')).toHaveCount(3);
  await expect(page.locator('#registerForm [name="businessName"]')).toBeVisible();
  await expect(page.locator('#registerForm [name="email"]')).toBeVisible();
  await expect(page.locator('#registerForm [name="password"]')).toBeVisible();

  await page.locator('#loginTab').click();
  await expect(page.locator('#loginForm')).toBeVisible();
  await expect(page.locator('#registerForm')).toBeHidden();

  await page.locator('#registerTab').click();
  await expect(page.locator('#registerForm')).toBeVisible();
});

test('registration validates fields and enters canonical React Home', async ({ page }) => {
  let registerCalls = 0;
  let registerPayload = null;
  await page.route('**/api/v1/auth/register', async route => {
    registerCalls += 1;
    registerPayload = route.request().postDataJSON();
    await route.fulfill(json({ accessToken: 'register-e2e-token' }));
  });

  await page.goto('/');
  await page.locator('#registerForm [name="businessName"]').fill('Negocio QA');
  await page.locator('#registerForm [name="email"]').fill('qa@example.cl');
  await page.locator('#registerForm [name="password"]').fill('corta');
  await page.locator('#registerForm button[type="submit"]').click();
  expect(registerCalls).toBe(0);

  await page.locator('#registerForm [name="password"]').fill('clave-segura-123');
  await page.locator('#registerForm button[type="submit"]').click();

  await expect.poll(() => registerCalls).toBe(1);
  expect(registerPayload).toMatchObject({
    adminName: 'Negocio QA',
    businessName: 'Negocio QA',
    email: 'qa@example.cl',
    password: 'clave-segura-123',
    humanTransferPhone: null
  });
  await expect(page).toHaveURL(/\/app\/?$/);
  await expect.poll(() =>
    page.evaluate(() => sessionStorage.getItem('helvoca_access_token'))
  ).toBe('register-e2e-token');
});

test('login keeps errors visible and enters canonical React Home after valid credentials', async ({ page }) => {
  let loginCalls = 0;
  await page.route('**/api/v1/auth/login', async route => {
    loginCalls += 1;
    if (loginCalls === 1) {
      return route.fulfill(json({ message: 'Credenciales inválidas.' }, 401));
    }
    return route.fulfill(json({ accessToken: 'login-e2e-token' }));
  });

  await page.goto('/');
  await page.locator('#loginTab').click();
  await page.locator('#loginForm [name="email"]').fill('qa@example.cl');
  await page.locator('#loginForm [name="password"]').fill('incorrecta');
  await page.locator('#loginForm button[type="submit"]').click();

  await expect(page.locator('#authMessage')).toContainText('Credenciales inválidas');
  await expect(page.locator('#loginForm')).toBeVisible();
  expect(loginCalls).toBe(1);

  await page.locator('#loginForm [name="password"]').fill('clave-segura-123');
  await page.locator('#loginForm button[type="submit"]').click();

  await expect.poll(() => loginCalls).toBe(2);
  await expect(page).toHaveURL(/\/app\/?$/);
  await expect.poll(() =>
    page.evaluate(() => sessionStorage.getItem('helvoca_access_token'))
  ).toBe('login-e2e-token');
});

test('React primary navigation fits desktop tablet and mobile viewports', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReactHome(page);

  for (const viewport of [
    { width: 1440, height: 900 },
    { width: 768, height: 1024 },
    { width: 390, height: 844 }
  ]) {
    await page.setViewportSize(viewport);
    await page.goto('/app');

    const nav = page.getByRole('navigation', { name: 'Navegación principal' });
    await expect(nav.getByRole('link', { name: 'Inicio' })).toHaveAttribute('href', '/app');
    await expect(nav.getByRole('link', { name: 'Agenda' })).toHaveAttribute('href', '/app/agenda');
    await expect(nav.getByRole('link', { name: 'Operaciones' })).toHaveAttribute('href', '/app/orders');
    await expect(nav.getByRole('link', { name: 'Inventario' })).toHaveAttribute('href', '/app/inventory');
    await expect(nav.getByRole('link', { name: 'Configuración' })).toHaveAttribute('href', '/app/settings');
    await expect(nav.getByRole('link', { name: 'Simulador' })).toHaveAttribute('href', '/app/simulator');
    await expect(nav.getByRole('link', { name: 'Plan y consumo' })).toHaveAttribute('href', '/app/plan');
    expect(await page.evaluate(() =>
      document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1
    )).toBe(true);
  }
});

test('public authentication remains responsive after Home retirement', async ({ page }) => {
  for (const viewport of [
    { width: 1440, height: 900 },
    { width: 768, height: 900 },
    { width: 390, height: 844 }
  ]) {
    await page.setViewportSize(viewport);
    await page.goto('/');
    await expect(page.locator('#registerForm')).toBeVisible();
    await page.locator('#loginTab').click();
    await expect(page.locator('#loginForm')).toBeVisible();
    expect(await page.evaluate(() =>
      document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1
    )).toBe(true);
  }
});

test('public commercial layer no longer depends on retired Home assets', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByRole('heading', { level: 1, name: 'No pierdas otra llamada.' })).toBeVisible();
  const stylesheets = await page.locator('link[rel="stylesheet"]').evaluateAll(
    links => links.map(link => new URL(link.href).pathname)
  );
  expect(stylesheets).not.toContain('/home-business.css');
  expect(stylesheets).not.toContain('/commercial-ui-v3.css');
  expect(stylesheets.some(path => /^\/app\/assets\/index-.*\.css$/.test(path))).toBe(true);
  await expect(page.locator('[data-auth-page="recepvoz"]')).toBeVisible();
  await expect(page.locator('script[src*="home-business.js"]')).toHaveCount(0);
});

test('ready customer React console uses the canonical dark design system', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'commercial-ui-token'));
  await mockReactHome(page);

  await page.goto('/app');
  await expect(page.getByRole('heading', { level: 1, name: 'Inicio' })).toBeVisible();
  await expect(page.locator('[data-react-app="recepvoz"]')).toBeVisible();

  const palette = await page.evaluate(() => {
    const bodyStyle = getComputedStyle(document.body);
    return {
      background: bodyStyle.backgroundColor,
      text: bodyStyle.color,
      accent: bodyStyle.getPropertyValue('--rv-accent').trim()
    };
  });

  expect(palette.background).toBe('rgb(6, 17, 28)');
  expect(palette.text).toBe('rgb(244, 251, 255)');
  expect(palette.accent).toBe('#16d9f5');
  await expect(page.locator('#homeBusinessWorkspace')).toHaveCount(0);
});
