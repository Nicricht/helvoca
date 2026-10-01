const { test, expect } = require('@playwright/test');

const json = body => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

test('platform admin sees the Demo Center without any countdown and can create a profile', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'platform-token'));

  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    userId: 'platform-user',
    email: 'platform@recepvoz.cl',
    roles: ['PLATFORM_ADMIN'],
    businessId: ''
  })));
  await page.route('**/api/v1/platform/demos/readiness', route => route.fulfill(json({
    runtimeConfigured: true,
    runtimeBusinessId: '99999999-8888-7777-6666-555555555555',
    runtime: { state: 'READY', detail: 'Live Demo Runtime' },
    voiceNumber: { state: 'READY', detail: '+56911112222' },
    voiceAi: { state: 'READY', detail: 'Demo AI' },
    businessData: { state: 'NOT_CONFIGURED', detail: 'No approved demo profile has been prepared into the runtime yet.' },
    operations: { state: 'READY', detail: 'DEMO operations are isolated from PILOT/CUSTOMER tenants.' },
    whatsapp: { state: 'NOT_CONFIGURED', detail: 'No certified demo WhatsApp channel is configured.' },
    payment: { state: 'SANDBOX_ONLY', detail: 'Merchant payment LIVE remains disabled.' },
    externalEffects: { state: 'DISARMED', detail: 'Outbound external effects are not armed.' }
  })));
  await page.route('**/api/v1/platform/demo-sessions/current', route => route.fulfill({
    status: 204,
    body: ''
  }));
  await page.route('**/api/v1/platform/economics', route => route.fulfill(json({
    estimatedCommercialValueClp: 0,
    estimatedPlatformCostUsd: 0,
    businesses: [],
    providers: []
  })));

  await page.route('**/api/v1/platform/demo-sessions/*/timeline', route => route.fulfill(json({
    sessionId: 'dddddddd-1111-2222-3333-444444444444',
    state: 'ACTIVE',
    callCount: 1,
    refreshedAt: '2026-10-01T06:01:00Z',
    items: [
      {
        kind: 'CALL_STARTED',
        at: '2026-10-01T06:00:10Z',
        title: 'Llamada recibida',
        detail: 'Entrante desde ••••1111 hacia el número DEMO.',
        status: 'IN_PROGRESS',
        callId: 'cccccccc-1111-2222-3333-444444444444',
        entityId: 'cccccccc-1111-2222-3333-444444444444'
      },
      {
        kind: 'TRANSCRIPT',
        at: '2026-10-01T06:00:15Z',
        title: 'Cliente',
        detail: 'Quiero dos sakes',
        status: 'USER',
        callId: 'cccccccc-1111-2222-3333-444444444444',
        entityId: 'bbbbbbbb-1111-2222-3333-444444444444'
      },
      {
        kind: 'OPERATION',
        at: '2026-10-01T06:00:25Z',
        title: 'ORDER',
        detail: 'Total registrado: 9980 CLP.',
        status: 'CONFIRMED',
        callId: 'cccccccc-1111-2222-3333-444444444444',
        entityId: 'aaaaaaaa-1111-2222-3333-444444444444'
      }
    ]
  })));

  let submitted = null;
  const profiles = [{
    id: '11111111-2222-3333-4444-555555555555',
    displayName: 'Sushi Demo',
    businessName: 'Sushi Demo',
    timezone: 'America/Santiago',
    language: 'es',
    catalog: {},
    hours: {},
    knowledge: {},
    greeting: 'Hola',
    instructions: null,
    capabilities: ['ORDER'],
    presenterNotes: null,
    sourceMetadata: { source: 'template' },
    createdAt: '2026-10-01T04:00:00Z',
    updatedAt: '2026-10-01T04:00:00Z'
  }];
  let preparedProfileId = null;
  await page.route('**/api/v1/platform/demos/*/prepare', async route => {
    preparedProfileId = route.request().url().split('/').at(-2);
    return route.fulfill(json({
      id: 'dddddddd-1111-2222-3333-444444444444',
      correlationId: 'eeeeeeee-1111-2222-3333-444444444444',
      demoProfileId: preparedProfileId,
      runtimeBusinessId: '99999999-8888-7777-6666-555555555555',
      state: 'READY',
      configurationRevision: '2026-10-01T04:00:00Z',
      stagedAt: '2026-10-01T06:00:00Z',
      startedAt: null,
      finishedAt: null,
      failureReason: null,
      readiness: {
        runtimeConfigured: true,
        runtimeBusinessId: '99999999-8888-7777-6666-555555555555',
        runtime: { state: 'READY', detail: 'Live Demo Runtime' },
        voiceNumber: { state: 'READY', detail: '+56911112222' },
        voiceAi: { state: 'READY', detail: 'Demo AI · gemini' },
        businessData: { state: 'READY', detail: 'Approved profile staged with session evidence.' },
        operations: { state: 'READY', detail: 'DEMO operations are isolated.' },
        whatsapp: { state: 'NOT_CONFIGURED', detail: 'Not armed.' },
        payment: { state: 'SANDBOX_ONLY', detail: 'Merchant payment LIVE remains disabled.' },
        externalEffects: { state: 'DISARMED', detail: 'Outbound external effects are not armed.' }
      },
      createdAt: '2026-10-01T06:00:00Z',
      updatedAt: '2026-10-01T06:00:00Z'
    }));
  });

  await page.route('**/api/v1/platform/demos', async route => {
    if (route.request().method() === 'POST') {
      submitted = route.request().postDataJSON();
      const created = {
        id: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
        ...submitted,
        createdAt: '2026-10-01T05:00:00Z',
        updatedAt: '2026-10-01T05:00:00Z'
      };
      profiles.unshift(created);
      return route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(created)
      });
    }
    return route.fulfill(json(profiles));
  });

  await page.goto('/platform.html');

  await expect(page.getByRole('heading', { name: 'Centro de Demos' })).toBeVisible();
  await expect(page.locator('#platformDemoReadiness')).toContainText('Runtime demo');
  await expect(page.locator('#platformDemoReadiness')).toContainText('READY');
  await expect(page.locator('#platformDemoReadiness')).toContainText('SANDBOX_ONLY');
  await expect(page.locator('#platformDemoReadiness')).toContainText('DISARMED');
  await expect(page.locator('body')).not.toContainText('3:00');
  await expect(page.locator('body')).not.toContainText('3 minutos');
  await expect(page.locator('#platformDemoProfiles')).toContainText('Sushi Demo');

  await page.getByRole('button', { name: 'Preparar demo' }).first().click();
  await expect.poll(() => preparedProfileId).toBe('11111111-2222-3333-4444-555555555555');
  await expect(page.locator('#platformDemoSession')).toContainText('READY');
  await expect(page.locator('#platformDemoSession')).toContainText('99999999-8888-7777-6666-555555555555');
  await expect(page.locator('#platformDemoReadiness')).toContainText('Approved profile staged with session evidence.');
  await expect(page.locator('#platformDemoTimeline')).toContainText('Llamada recibida');
  await expect(page.locator('#platformDemoTimeline')).toContainText('Cliente');
  await expect(page.locator('#platformDemoTimeline')).toContainText('Quiero dos sakes');
  await expect(page.locator('#platformDemoTimeline')).toContainText('ORDER');
  await expect(page.locator('#platformDemoTimeline')).toContainText('CONFIRMED');
  await expect(page.locator('#platformDemoTimelineMeta')).toContainText('1 llamada');
  await expect(page.locator('body')).not.toContainText('3:00');
  await expect(page.locator('body')).not.toContainText('3 minutos');

  await page.getByRole('button', { name: /Crear nueva demo/i }).click();
  await page.locator('input[name="demoDisplayName"]').fill('Sushi Akira');
  await page.locator('input[name="demoBusinessName"]').fill('Sushi Akira');
  await page.locator('textarea[name="demoGreeting"]').fill('Hola, soy la asistente de Sushi Akira.');
  await page.getByRole('button', { name: /Guardar demo/i }).click();

  await expect.poll(() => submitted).not.toBeNull();
  expect(submitted.displayName).toBe('Sushi Akira');
  expect(submitted.businessName).toBe('Sushi Akira');
  expect(submitted.timezone).toBe('America/Santiago');
  expect(submitted.language).toBe('es');
  expect(submitted.capabilities).toEqual([]);
  await expect(page.locator('#platformDemoProfiles')).toContainText('Sushi Akira');
});
