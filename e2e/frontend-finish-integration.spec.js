const { test, expect } = require('@playwright/test');

const customerSurfaces = [
  '/',
  '/settings.html',
  '/inventory.html',
  '/conversations.html',
  '/account.html',
  '/simulator.html'
];

const coreLinks = [
  'href="/"',
  'href="/#bookings"',
  'href="/#customers"',
  'href="/conversations.html"',
  'href="/inventory.html"',
  'href="/settings.html"'
];

test('integrated customer surfaces share navigation and canonical foundation authority', async ({ request }) => {
  for (const path of customerSurfaces) {
    const response = await request.get(path);
    expect(response.ok(), path).toBe(true);
    const html = await response.text();

    for (const link of coreLinks) {
      expect(html, path + ' should expose ' + link).toContain(link);
    }
    expect(html, path + ' must not expose internal operations').not.toContain('href="/operations.html"');

    const stylesheets = [...html.matchAll(/<link[^>]+rel="stylesheet"[^>]+href="([^"?]+)(?:\?[^"]*)?"/g)]
      .map(match => match[1]);
    expect(stylesheets, path + ' should load canonical Foundation last').not.toHaveLength(0);
    expect(stylesheets.at(-1), path + ' should load canonical Foundation last').toBe('/frontend-foundation.css');
  }
});

test('integrated dashboard exposes conversations without routing calls to internal operations', async ({ request }) => {
  const [indexResponse, dashboardJsResponse] = await Promise.all([
    request.get('/'),
    request.get('/commercial-status.js')
  ]);
  expect(indexResponse.ok()).toBe(true);
  expect(dashboardJsResponse.ok()).toBe(true);

  const index = await indexResponse.text();
  const dashboardJs = await dashboardJsResponse.text();
  expect(index).toContain('href="/conversations.html"');
  expect(dashboardJs).toContain('<a href="/conversations.html">Conversaciones</a>');
  expect(dashboardJs).not.toContain('/operations.html');
});

test('billing account remains informational and does not introduce payment mutations', async ({ request }) => {
  const response = await request.get('/account.js');
  expect(response.ok()).toBe(true);
  const source = await response.text();

  expect(source).toContain('/api/v1/subscription');
  expect(source).toContain('/api/v1/usage/summary');
  expect(source).not.toMatch(/method\s*:\s*['"](?:POST|PUT|PATCH|DELETE)['"]/);
  expect(source).not.toMatch(/checkout|create[_-]?payment|payment[_-]?intent/i);
});
