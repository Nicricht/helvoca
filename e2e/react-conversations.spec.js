const { test, expect } = require('@playwright/test');
const { readFileSync, existsSync } = require('node:fs');
const path = require('node:path');

const repo = (...parts) => path.join(process.cwd(), ...parts);

test.describe('Embedded Conversations / Recepcionista IA architecture', () => {
  test('keeps legacy conversations reachable only as fallback during migration', async ({ page }) => {
    await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'conversation-fallback-e2e'));
    await page.route('**/api/v1/calls?**', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ content: [], number: 0, size: 100, totalElements: 0, totalPages: 0 })
    }));
    await page.route('**/api/v1/messaging/conversations', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: '[]'
    }));

    const response = await page.goto('/conversations.html');

    expect(response).not.toBeNull();
    expect(response.status()).toBe(200);
    await expect(page.getByRole('heading', { level: 1, name: 'Conversaciones' })).toBeVisible();
  });

  test('does not expose Conversations as an independent React navigation destination', async ({ page }) => {
    await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'conversation-nav-e2e'));

    await page.route('**/api/v1/auth/me', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ email: 'owner@demo.cl', roles: ['BUSINESS_OWNER'] })
    }));
    await page.route('**/api/v1/business', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ id: '11111111-1111-1111-1111-111111111111', name: 'Demo' })
    }));
    await page.route('**/api/v1/catalog', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: '[]'
    }));
    await page.route('**/api/v1/inventory', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: '[]'
    }));
    await page.route('**/api/v1/inventory/alerts', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: '[]'
    }));
    await page.route('**/api/v1/inventory/restock-subscriptions', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: '[]'
    }));
    await page.route('**/api/v1/inventory/restock-subscriptions/notifications', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: '[]'
    }));

    await page.goto('/app/inventory');

    await expect(page.getByRole('navigation', { name: 'Navegación principal' }))
      .not.toContainText('Recepcionista IA');
  });

  test('ships conversations as an embeddable feature, not a standalone page', () => {
    const featureRoot = repo('frontend', 'src', 'features', 'conversations');
    const expected = [
      'api.ts',
      'presentation.ts',
      'useCustomerConversations.ts',
      'ConversationPanel.tsx',
      'ConversationPanel.module.css',
      'index.ts'
    ];

    for (const filename of expected) {
      expect(existsSync(path.join(featureRoot, filename)), filename).toBe(true);
    }

    expect(existsSync(repo('frontend', 'src', 'pages', 'Conversations'))).toBe(false);

    const app = readFileSync(repo('frontend', 'src', 'app', 'App.tsx'), 'utf8');
    expect(app).not.toMatch(/path=["']\/conversations/);
    expect(app).not.toMatch(/ConversationsPage/);
  });

  test('read API never sends businessId or implicit write methods', () => {
    const api = readFileSync(
      repo('frontend', 'src', 'features', 'conversations', 'api.ts'),
      'utf8'
    );

    expect(api).not.toMatch(/businessId/i);
    expect(api).not.toMatch(/method:\s*["'](?:POST|PUT|PATCH|DELETE)["']/i);
    expect(api).toContain('/api/v1/calls');
    expect(api).toContain('/api/v1/messaging/conversations');
  });

  test('human presentation layer hides provider/action codes from the reservation UI', () => {
    const presentation = readFileSync(
      repo('frontend', 'src', 'features', 'conversations', 'presentation.ts'),
      'utf8'
    );

    expect(presentation).toContain('Reserva creada');
    expect(presentation).toContain('Consultó disponibilidad');
    expect(presentation).toContain('Derivación a una persona');
    expect(presentation).toContain('Pregunta pendiente para revisar');
  });

  test('embedded panel answers the three operational questions without write controls', () => {
    const panel = readFileSync(
      repo('frontend', 'src', 'features', 'conversations', 'ConversationPanel.tsx'),
      'utf8'
    );

    expect(panel).toContain('Qué preguntó el cliente');
    expect(panel).toContain('Qué hizo RecepVoz');
    expect(panel).toContain('Necesitas actuar');
    expect(panel).toContain('conversation-transcript');
    expect(panel).not.toMatch(/>\s*(Enviar|Llamar|Transferir|Marcar)\s*</i);
  });

  test('embedded feature keeps Voice and WhatsApp independently recoverable', () => {
    const hook = readFileSync(
      repo('frontend', 'src', 'features', 'conversations', 'useCustomerConversations.ts'),
      'utf8'
    );

    expect(hook).toContain('calls');
    expect(hook).toContain('whatsapp');
    expect(hook).toContain('partial');
    expect(hook).toContain('customerId');
  });
});
