const { test, expect } = require('@playwright/test');
const fs = require('node:fs');

const json = body => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

test('React Home motion layer includes reduced-motion protection', async () => {
  const css = fs.readFileSync('frontend/src/pages/Home/HomePage.module.css', 'utf8');
  expect(css).toContain('@media (prefers-reduced-motion: reduce)');
  expect(css).toContain('@keyframes homeWave');
  expect(css).toContain('@keyframes breathe');
});

test('React Home stays usable when the user requests reduced motion', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'motion-e2e'));

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
    businessName: 'Negocio Movimiento',
    timezone: 'America/Santiago',
    localNow: '2026-10-02T18:00:00-03:00',
    callsToday: 1,
    callDurationSecondsToday: 60,
    bookingsToday: 1,
    newCustomersToday: 0,
    openRequests: 0,
    unansweredQuestions: 0,
    callFailuresToday: 0,
    estimatedCallCostTodayUsd: 0.1,
    recentCalls: [],
    recentRequests: [],
    unanswered: []
  })));
  await page.route('**/api/v1/commercial/analytics**', route => route.fulfill(json({
    days: 7,
    timezone: 'America/Santiago',
    primaryCurrency: 'CLP',
    totalRevenue: 0,
    paidOrders: 0,
    unitsSold: 0,
    averageTicket: 0,
    revenueChangePercent: null,
    currencyTotals: [],
    salesOverTime: [],
    topProducts: [],
    channels: [],
    peakWeekday: null,
    peakHour: null,
    recepVozOrders: 0,
    recepVozRevenue: 0,
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

  await page.goto('/app');

  await expect(page.getByRole('heading', { level: 1, name: 'Inicio' })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Qué está pasando hoy' })).toBeVisible();
  expect(await page.evaluate(() =>
    document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1
  )).toBe(true);
});
