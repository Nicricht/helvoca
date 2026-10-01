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
  let liveSession = null;
  await page.route('**/api/v1/platform/demo-sessions/current', route => {
    if (!liveSession) return route.fulfill({ status: 204, body: '' });
    return route.fulfill(json(liveSession));
  });
  await page.route('**/api/v1/platform/demo-sessions/*/timeline', route => {
    const events = liveSession ? [{
      at: '2026-10-01T06:30:00Z',
      type: 'SESSION',
      entityId: liveSession.id,
      status: liveSession.state,
      detail: 'Persisted demo session'
    }] : [];
    if (liveSession?.state === 'ACTIVE' || liveSession?.state === 'FINISHED') {
      events.push({
        at: '2026-10-01T06:31:00Z',
        type: 'CALL',
        entityId: 'call-demo-1',
        status: liveSession.state === 'FINISHED' ? 'COMPLETED' : 'IN_PROGRESS',
        detail: 'Llamada recibida'
      }, {
        at: '2026-10-01T06:31:10Z',
        type: 'TRANSCRIPT',
        entityId: 'turn-demo-1',
        status: 'USER',
        detail: 'Quiero dos sakes'
      }, {
        at: '2026-10-01T06:31:15Z',
        type: 'TRANSCRIPT',
        entityId: 'turn-demo-2',
        status: 'ASSISTANT',
        detail: 'Confirmo dos sakes.'
      }, {
        at: '2026-10-01T06:31:20Z',
        type: 'OPERATION',
        entityId: 'operation-demo-1',
        status: 'CONFIRMED',
        detail: 'ORDER: CONFIRMED'
      });
    }
    return route.fulfill(json({
      sessionId: liveSession?.id || null,
      runtimeBusinessId: '99999999-8888-7777-6666-555555555555',
      sessionStatus: liveSession?.state || 'READY',
      events,
      proofOfValue: {
        state: liveSession?.state === 'FINISHED' ? 'RECORDED_VALUE' : 'REVIEW_REQUIRED',
        calls: liveSession?.state === 'ACTIVE' || liveSession?.state === 'FINISHED' ? 1 : 0,
        conversations: 0,
        operations: liveSession?.state === 'ACTIVE' || liveSession?.state === 'FINISHED' ? 1 : 0,
        facts: liveSession?.state === 'FINISHED' ? ['ORDER: CONFIRMED'] : [],
        followUps: liveSession?.state === 'FINISHED' ? [] : ['No persisted terminal outcome evidence yet.']
      }
    }));
  });
  await page.route('**/api/v1/platform/demo-sessions/*/start', route => {
    liveSession = { ...liveSession, state: 'ACTIVE', startedAt: '2026-10-01T06:31:00Z' };
    return route.fulfill(json(liveSession));
  });
  await page.route('**/api/v1/platform/demo-sessions/*/finish', route => {
    liveSession = { ...liveSession, state: 'FINISHED', finishedAt: '2026-10-01T06:33:00Z' };
    return route.fulfill(json(liveSession));
  });
  await page.route('**/api/v1/platform/demo-sessions/*/abort', route => {
    liveSession = { ...liveSession, state: 'ABORTED', finishedAt: '2026-10-01T06:33:00Z' };
    return route.fulfill(json(liveSession));
  });
  await page.route('**/api/v1/platform/demo-sessions/*/convert-to-pilot', async route => {
    expect(route.request().postDataJSON()).toEqual({
      adminName: 'Ana Pérez',
      adminEmail: 'ana@negocio.cl'
    });
    liveSession = { ...liveSession, convertedPilotBusinessId: 'aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb' };
    return route.fulfill(json({
      sessionId: liveSession.id,
      pilotBusinessId: liveSession.convertedPilotBusinessId,
      pilotBusinessName: 'Sushi Demo',
      mode: 'PILOT',
      invitationStatus: 'PENDING',
      invitePath: '/invite.html?businessId=pilot&token=one-time',
      onboardingPath: '/',
      idempotentReplay: false
    }));
  });
  await page.route('**/api/v1/platform/economics', route => route.fulfill(json({
    estimatedCommercialValueClp: 0,
    estimatedPlatformCostUsd: 0,
    businesses: [],
    providers: []
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
    liveSession = {
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
      operator: 'platform@recepvoz.cl',
      externalEffectsState: 'DISARMED',
      paymentState: 'SANDBOX_ONLY',
      convertedPilotBusinessId: null,
      updatedAt: '2026-10-01T06:00:00Z'
    };
    return route.fulfill(json(liveSession));
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
  await expect(page.locator('#platformDemoSession')).toContainText('DISARMED');
  await expect(page.locator('#platformDemoSession')).toContainText('SANDBOX_ONLY');

  await expect(page.locator('#platformDemoSessionState')).toContainText('Esperando llamada');
  await expect(page.getByText('Controles manuales de respaldo')).toBeVisible();

  liveSession = { ...liveSession, state: 'ACTIVE', startedAt: '2026-10-01T06:31:00Z' };
  await expect(page.locator('#platformDemoSession')).toContainText('ACTIVE', { timeout: 7000 });
  await expect(page.locator('#platformDemoSessionState')).toContainText('Llamada detectada');
  await expect(page.locator('#platformDemoTimeline')).toContainText('TRANSCRIPT · USER');
  await expect(page.locator('#platformDemoTimeline')).toContainText('Quiero dos sakes');
  await expect(page.locator('#platformDemoTimeline')).toContainText('TRANSCRIPT · ASSISTANT');
  await expect(page.locator('#platformDemoTimeline')).toContainText('ORDER: CONFIRMED');

  liveSession = { ...liveSession, state: 'FINISHED', finishedAt: '2026-10-01T06:33:00Z' };
  await expect(page.locator('#platformDemoSession')).toContainText('FINISHED', { timeout: 7000 });
  await expect(page.locator('#platformDemoSessionState')).toContainText('se cerró automáticamente');
  await expect(page.locator('#platformDemoProof')).toContainText('RECORDED_VALUE');
  await expect(page.locator('#platformDemoProof')).toContainText('ORDER: CONFIRMED');

  await page.locator('input[name="pilotAdminName"]').fill('Ana Pérez');
  await page.locator('input[name="pilotAdminEmail"]').fill('ana@negocio.cl');
  await page.getByRole('button', { name: 'Crear PILOT e invitación' }).click();
  await expect(page.locator('#platformDemoSessionMessage')).toContainText('PILOT creado');
  await expect(page.locator('#platformDemoSession')).toContainText('aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb');
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
