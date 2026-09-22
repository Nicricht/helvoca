const { test, expect } = require('@playwright/test');

const json = body => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

test('Embedded Signup renders WABA candidates after secure authorization', async ({ page }) => {
  const authorizationBodies = [];
  const phoneDiscoveryBodies = [];
  const phoneValidationBodies = [];
  const phoneFinalizeBodies = [];
  let certificationReadinessRequests = 0;
  let certificationAlreadyCertified = false;
  let deploymentReadinessRequests = 0;
  let tenantConfigRequests = 0;
  let activationRequests = 0;
  let deactivationRequests = 0;
  let authRoles = ['BUSINESS_ADMIN'];
  let tenantConfigStatus = {
    status: 'NOT_CONFIGURED',
    configured: false,
    enabled: false,
    provider: null,
    phoneRecordId: null,
    phoneNumber: null,
    phone_number_id: null,
    waba_id: null,
    credentialReferenceConfigured: false,
    certifiedAt: null
  };
  const unexpectedEmbeddedSignupRequests = [];

  page.on('request', request => {
    const pathname = new URL(request.url()).pathname;
    const embeddedSignupPrefix = '/api/v1/channels/whatsapp/meta/embedded-signup/';
    if (pathname === '/api/v1/channels/whatsapp/meta/config/activate') {
      activationRequests += 1;
    }
    if (pathname === '/api/v1/channels/whatsapp/meta/config/deactivate') {
      deactivationRequests += 1;
    }
    const allowed = new Set([
      `${embeddedSignupPrefix}bootstrap`,
      `${embeddedSignupPrefix}authorization-code`,
      `${embeddedSignupPrefix}waba/phone-numbers`,
      `${embeddedSignupPrefix}waba/phone-number/validate`,
      `${embeddedSignupPrefix}waba/phone-number/finalize`
    ]);
    if (pathname.startsWith(embeddedSignupPrefix) && !allowed.has(pathname)) {
      unexpectedEmbeddedSignupRequests.push(pathname);
    }
  });

  await page.addInitScript(() => {
    sessionStorage.setItem('helvoca_access_token', 'e2e-token');
    window.FB = {
      init() {},
      login(callback) {
        callback({ authResponse: { code: 'meta-code-e2e' } });
      }
    };
  });

  await page.route('**/api/v1/channels/whatsapp/meta/embedded-signup/bootstrap', route =>
    route.fulfill(json({
      enabled: true,
      available: true,
      appId: '123456789',
      configId: '987654321',
      graphApiVersion: 'v99.0'
    }))
  );

  await page.route('**/api/v1/channels/whatsapp/meta/embedded-signup/waba/phone-numbers', async route => {
    phoneDiscoveryBodies.push(route.request().postDataJSON());
    await route.fulfill(json({
      state: 'PHONE_NUMBERS_DISCOVERED',
      appSubscribed: true,
      phoneNumbers: [
        {
          id: '12025550123',
          displayPhoneNumber: '+56 9 1111 2222',
          verifiedName: '<img src=x onerror=alert(2)>',
          qualityRating: 'GREEN',
          codeVerificationStatus: 'VERIFIED'
        },
        {
          id: '12025550124',
          displayPhoneNumber: '+56 9 3333 4444',
          verifiedName: 'RecepVoz Sucursal',
          qualityRating: 'YELLOW',
          codeVerificationStatus: 'NOT_VERIFIED'
        }
      ],
      afterCursor: null
    }));
  });

  await page.route('**/api/v1/channels/whatsapp/meta/embedded-signup/waba/phone-number/validate', async route => {
    const body = route.request().postDataJSON();
    phoneValidationBodies.push(body);
    await route.fulfill(json({
      state: 'PHONE_NUMBER_VALIDATED',
      phoneNumberId: body.phoneNumberId,
      displayPhoneNumber: '+56 9 3333 4444',
      verifiedName: 'RecepVoz Sucursal',
      qualityRating: 'YELLOW',
      codeVerificationStatus: 'NOT_VERIFIED'
    }));
  });

  await page.route('**/api/v1/channels/whatsapp/meta/embedded-signup/waba/phone-number/finalize', async route => {
    const body = route.request().postDataJSON();
    phoneFinalizeBodies.push(body);
    tenantConfigStatus = {
      status: 'CONFIGURED_DISABLED',
      configured: true,
      enabled: false,
      provider: 'META_WHATSAPP_CLOUD',
      phoneRecordId: '11111111-1111-4111-8111-111111111111',
      phoneNumber: '+56 9 3333 4444',
      phone_number_id: body.phoneNumberId,
      waba_id: body.wabaId,
      credentialReferenceConfigured: true,
      certifiedAt: null
    };
    await route.fulfill(json({
      state: 'PHONE_NUMBER_REGISTERED_AND_STAGED',
      phoneRecordId: '11111111-1111-4111-8111-111111111111',
      provider: 'META_WHATSAPP_CLOUD',
      phoneNumberId: body.phoneNumberId,
      wabaId: body.wabaId,
      credentialRef: 'EMBEDDED_SIGNUP_SYSTEM_USER',
      enabled: false
    }));
  });

  await page.route('**/api/v1/channels/whatsapp/meta/config', async route => {
    tenantConfigRequests += 1;
    await route.fulfill(json(tenantConfigStatus));
  });

  await page.route('**/api/v1/channels/whatsapp/meta/config/activate', async route => {
    tenantConfigStatus = {
      ...tenantConfigStatus,
      status: 'CONFIGURED_ENABLED',
      configured: true,
      enabled: true
    };
    await route.fulfill(json(tenantConfigStatus));
  });

  await page.route('**/api/v1/channels/whatsapp/meta/config/deactivate', async route => {
    tenantConfigStatus = {
      ...tenantConfigStatus,
      status: 'CONFIGURED_DISABLED',
      configured: true,
      enabled: false
    };
    await route.fulfill(json(tenantConfigStatus));
  });

  await page.route('**/api/v1/channels/whatsapp/meta/certification/readiness', async route => {
    certificationReadinessRequests += 1;
    await route.fulfill(json({
      state: certificationAlreadyCertified ? 'CERTIFIED' : 'READY_FOR_PILOT_CERTIFICATION',
      ready: true,
      alreadyCertified: certificationAlreadyCertified,
      blockers: []
    }));
  });

  await page.route('**/api/v1/channels/whatsapp/meta/deployment/readiness', async route => {
    deploymentReadinessRequests += 1;
    await route.fulfill(json({
      state: 'READY_FOR_TENANT_STAGING',
      readyForTenantStaging: true,
      webhookValidationEnabled: true,
      appSecretConfigured: true,
      verifyTokenConfigured: true,
      globalMetaEnabled: false,
      outboundDeliveryEnabled: false,
      outboundProvider: 'NONE',
      jobsEnabled: false,
      blockers: []
    }));
  });

  await page.route('**/api/v1/channels/whatsapp/meta/embedded-signup/authorization-code', async route => {
    authorizationBodies.push(route.request().postDataJSON());
    await route.fulfill(json({
      state: 'AUTHORIZATION_CODE_EXCHANGED_AND_VALIDATED',
      accepted: true,
      retained: false,
      exchangePending: false,
      wabas: [
        {
          id: '1906385232743451',
          name: 'RecepVoz Peluquería',
          currency: 'CLP',
          timezoneId: 'America/Santiago',
          messageTemplateNamespace: 'namespace-one',
          systemUserAssigned: true
        },
        {
          id: '1906385232743452',
          name: '<img src=x onerror=alert(1)>',
          currency: 'USD',
          timezoneId: 'America/New_York',
          messageTemplateNamespace: 'namespace-two',
          systemUserAssigned: false
        }
      ],
      wabaAfterCursor: 'next-page'
    }));
  });

  await page.route('**/api/v1/auth/me', route =>
    route.fulfill(json({ email: 'admin@demo.cl', roles: authRoles }))
  );
  await page.route('**/api/v1/phone-numbers/provisioning/status', route =>
    route.fulfill(json({
      enabled: false,
      configured: false,
      purchaseAvailable: false,
      provider: 'TWILIO',
      message: 'No disponible en E2E'
    }))
  );
  await page.route('**/api/v1/phone-numbers', route => route.fulfill(json([])));
  await page.route('**/api/v1/billing/status', route => route.fulfill(json({})));
  await page.route('**/api/v1/subscription', route => route.fulfill(json({})));
  await page.route('**/api/v1/business/profile', route => route.fulfill(json({})));
  await page.route(/\/api\/v1\/business$/, route => route.fulfill(json({
    name: 'Negocio E2E',
    timezone: 'America/Santiago',
    language: 'es'
  })));
  await page.route('**/api/v1/onboarding/status', route => route.fulfill(json({
    businessProfileConfigured: true,
    servicesConfigured: true,
    scheduleConfigured: true,
    knowledgeConfigured: true,
    humanTransferConfigured: true,
    phoneConfigured: true,
    readyForCalls: true,
    nextStep: 'READY'
  })));
  await page.route('**/api/v1/services', route => route.fulfill(json([])));
  await page.route('**/api/v1/business/hours', route => route.fulfill(json([])));
  await page.route('**/api/v1/knowledge?activeOnly=false', route => route.fulfill(json([])));
  await page.route(/\/api\/v1\/ai-agent(?:\/voices)?$/, route => route.fulfill(json([])));
  await page.route('**/api/v1/public/pricing', route => route.fulfill(json([])));
  await page.route('**/api/v1/operations/dashboard', route => route.fulfill(json({})));
  await page.route('**/api/v1/messaging/conversations', route => route.fulfill(json([])));

  await page.goto('/settings.html');

  await page.getByRole('button', { name: '📞 Canales', exact: true }).click();

  const connect = page.getByRole('button', { name: 'Conectar WhatsApp', exact: true });
  await expect(connect).toBeVisible();
  await connect.click();

  const continueButton = page.getByRole('button', { name: 'Continuar con Meta', exact: true });
  await expect(continueButton).toBeVisible();
  await continueButton.click();

  await expect.poll(() => authorizationBodies).toEqual([{ code: 'meta-code-e2e' }]);

  await expect(page.locator('#metaWhatsAppConnectMessage'))
    .toHaveText('Autorización completada. Encontramos 2 cuentas de WhatsApp Business.');

  const candidates = page.locator('#metaWhatsAppWabaCandidates');
  await expect(candidates).toBeVisible();
  await expect(candidates.locator('.meta-whatsapp-waba-card')).toHaveCount(2);
  await expect(candidates).toContainText('RecepVoz Peluquería');
  await expect(candidates).toContainText('ID 1906385232743451 · CLP · America/Santiago');
  await expect(candidates).toContainText('Acceso técnico de RecepVoz listo');
  await expect(candidates).toContainText('<img src=x onerror=alert(1)>');
  await expect(candidates.locator('img')).toHaveCount(0);
  await expect(candidates).toContainText('Acceso técnico pendiente de asignación');
  await expect(candidates).toContainText('Meta indica que existen más cuentas. Esta vista muestra la primera página.');

  const cards = candidates.locator('.meta-whatsapp-waba-card');
  const firstCard = cards.nth(0);
  const secondCard = cards.nth(1);

  await firstCard.getByRole('button', { name: 'Seleccionar', exact: true }).click();
  await expect(firstCard).toHaveClass(/selected/);
  await expect(firstCard).toHaveAttribute('data-selected', 'true');
  await expect(firstCard.getByRole('button', { name: 'Seleccionado', exact: true }))
    .toHaveAttribute('aria-pressed', 'true');
  await expect(secondCard).not.toHaveClass(/selected/);
  await expect(secondCard).toHaveAttribute('data-selected', 'false');

  await secondCard.getByRole('button', { name: 'Seleccionar', exact: true }).click();
  await expect(secondCard).toHaveClass(/selected/);
  await expect(secondCard).toHaveAttribute('data-selected', 'true');
  await expect(secondCard.getByRole('button', { name: 'Seleccionado', exact: true }))
    .toHaveAttribute('aria-pressed', 'true');
  await expect(firstCard).not.toHaveClass(/selected/);
  await expect(firstCard).toHaveAttribute('data-selected', 'false');
  await expect(firstCard.getByRole('button', { name: 'Seleccionar', exact: true }))
    .toHaveAttribute('aria-pressed', 'false');

  await expect.poll(() => phoneDiscoveryBodies).toEqual([]);
  await expect.poll(() => unexpectedEmbeddedSignupRequests).toEqual([]);

  const confirmButton = page.getByRole('button', { name: 'Continuar con esta cuenta', exact: true });
  await expect(confirmButton).toBeVisible();
  await confirmButton.click();

  await expect.poll(() => phoneDiscoveryBodies).toEqual([{ wabaId: '1906385232743452' }]);
  await expect(page.locator('#metaWhatsAppWabaConfirmStatus'))
    .toHaveText('Cuenta confirmada. Meta devolvió 2 números.');

  const phoneCandidates = page.locator('#metaWhatsAppPhoneCandidates');
  await expect(phoneCandidates).toBeVisible();
  await expect(phoneCandidates.locator('.meta-whatsapp-phone-card')).toHaveCount(2);
  await expect(phoneCandidates).toContainText('+56 9 1111 2222');
  await expect(phoneCandidates).toContainText('<img src=x onerror=alert(2)>');
  await expect(phoneCandidates).toContainText('ID 12025550123 · Calidad GREEN · Verificación VERIFIED');
  await expect(phoneCandidates).toContainText('+56 9 3333 4444');
  await expect(phoneCandidates).toContainText('ID 12025550124 · Calidad YELLOW · Verificación NOT_VERIFIED');
  await expect(phoneCandidates.locator('img')).toHaveCount(0);

  const phoneCards = phoneCandidates.locator('.meta-whatsapp-phone-card');
  const firstPhoneCard = phoneCards.nth(0);
  const secondPhoneCard = phoneCards.nth(1);

  await firstPhoneCard.getByRole('button', { name: 'Seleccionar', exact: true }).click();
  await expect(firstPhoneCard).toHaveClass(/selected/);
  await expect(firstPhoneCard).toHaveAttribute('data-selected', 'true');
  await expect(firstPhoneCard.getByRole('button', { name: 'Seleccionado', exact: true }))
    .toHaveAttribute('aria-pressed', 'true');
  await expect(secondPhoneCard).not.toHaveClass(/selected/);
  await expect(secondPhoneCard).toHaveAttribute('data-selected', 'false');

  await secondPhoneCard.getByRole('button', { name: 'Seleccionar', exact: true }).click();
  await expect(secondPhoneCard).toHaveClass(/selected/);
  await expect(secondPhoneCard).toHaveAttribute('data-selected', 'true');
  await expect(secondPhoneCard.getByRole('button', { name: 'Seleccionado', exact: true }))
    .toHaveAttribute('aria-pressed', 'true');
  await expect(firstPhoneCard).not.toHaveClass(/selected/);
  await expect(firstPhoneCard).toHaveAttribute('data-selected', 'false');
  await expect(firstPhoneCard.getByRole('button', { name: 'Seleccionar', exact: true }))
    .toHaveAttribute('aria-pressed', 'false');

  await expect.poll(() => phoneValidationBodies).toEqual([]);

  const pinSetup = page.locator('#metaWhatsAppPinSetup');
  const pinInput = page.getByLabel('PIN de Meta', { exact: true });
  const preparedState = page.locator('#metaWhatsAppPreparedState');
  const certificationState = page.locator('#metaWhatsAppCertificationState');
  const deploymentState = page.locator('#metaWhatsAppDeploymentState');
  const activationGate = page.locator('#metaWhatsAppActivationGate');
  await expect(pinSetup).toHaveClass(/hidden/);
  await expect(preparedState).toHaveClass(/hidden/);
  await expect(certificationState).toHaveClass(/hidden/);
  await expect(deploymentState).toHaveClass(/hidden/);
  await expect(activationGate).toHaveClass(/hidden/);
  await expect.poll(() => tenantConfigRequests).toBe(1);
  await expect.poll(() => certificationReadinessRequests).toBe(0);
  await expect.poll(() => deploymentReadinessRequests).toBe(0);

  const phoneConfirmButton = page.getByRole('button', { name: 'Continuar con este número', exact: true });
  await expect(phoneConfirmButton).toBeVisible();
  await phoneConfirmButton.click();

  await expect.poll(() => phoneValidationBodies).toEqual([{
    wabaId: '1906385232743452',
    phoneNumberId: '12025550124'
  }]);
  await expect(page.locator('#metaWhatsAppPhoneConfirmStatus'))
    .toHaveText('Número validado por Meta.');

  await expect(pinSetup).toBeVisible();
  await expect(pinInput).toBeFocused();
  await expect(pinInput).toHaveAttribute('type', 'password');
  await expect(pinInput).toHaveAttribute('inputmode', 'numeric');
  await expect(pinInput).toHaveAttribute('maxlength', '6');
  await expect(pinInput).toHaveAttribute('autocomplete', 'off');

  await pinInput.fill('12ab34');
  await expect(pinInput).toHaveValue('1234');
  await expect(page.locator('#metaWhatsAppPinStatus'))
    .toHaveText('El PIN debe tener exactamente 6 dígitos.');

  const finalizePhoneButton = page.getByRole('button', { name: 'Finalizar configuración', exact: true });
  await expect(finalizePhoneButton).toBeDisabled();
  await expect.poll(() => phoneFinalizeBodies).toEqual([]);

  await pinInput.fill('123456');
  await expect(pinInput).toHaveValue('123456');
  await expect(page.locator('#metaWhatsAppPinStatus'))
    .toHaveText('PIN listo para finalizar la configuración.');
  await expect(finalizePhoneButton).toBeEnabled();

  await finalizePhoneButton.click();

  await expect.poll(() => phoneFinalizeBodies).toEqual([{
    wabaId: '1906385232743452',
    phoneNumberId: '12025550124',
    pin: '123456'
  }]);
  await expect(page.locator('#metaWhatsAppPinStatus'))
    .toHaveText('PIN eliminado del formulario.');
  await expect(preparedState).toBeVisible();
  await expect(preparedState).toContainText('WhatsApp preparado');
  await expect(preparedState)
    .toContainText('Configuración guardada y desactivada. Todavía no se ha activado el tráfico real.');
  await expect.poll(() => certificationReadinessRequests).toBe(1);
  await expect(certificationState).toBeVisible();
  await expect(certificationState).toContainText('Listo para certificación piloto');
  await expect(certificationState)
    .toContainText('No hay bloqueos técnicos pendientes para iniciar la certificación.');
  await expect.poll(() => deploymentReadinessRequests).toBe(1);
  await expect(deploymentState).toBeVisible();
  await expect(deploymentState).toContainText('Infraestructura lista para staging');
  await expect(deploymentState)
    .toContainText('Las compuertas de tráfico real siguen apagadas, como exige el staging seguro.');
  await expect(activationGate).toBeVisible();
  await expect(activationGate).toContainText('Activación disponible con autorización manual');
  await expect(activationGate)
    .toContainText('Activar arma este negocio para el piloto');
  await expect(page.locator('#metaWhatsAppActivateBtn')).toBeVisible();
  await expect(pinInput).toHaveValue('');
  await expect(pinInput).toBeDisabled();
  await expect(finalizePhoneButton).toBeDisabled();

  await expect.poll(() => phoneValidationBodies).toEqual([{
    wabaId: '1906385232743452',
    phoneNumberId: '12025550124'
  }]);
  await expect.poll(() => phoneFinalizeBodies).toEqual([{
    wabaId: '1906385232743452',
    phoneNumberId: '12025550124',
    pin: '123456'
  }]);
  await expect.poll(() => phoneDiscoveryBodies).toEqual([{ wabaId: '1906385232743452' }]);
  await expect.poll(() => unexpectedEmbeddedSignupRequests).toEqual([]);
  await expect.poll(() => activationRequests).toBe(0);

  certificationAlreadyCertified = true;
  await page.reload();
  await page.getByRole('button', { name: '📞 Canales', exact: true }).click();

  const restoredPreparedState = page.locator('#metaWhatsAppPreparedState');
  const restoredCertificationState = page.locator('#metaWhatsAppCertificationState');
  const restoredDeploymentState = page.locator('#metaWhatsAppDeploymentState');
  const restoredActivationGate = page.locator('#metaWhatsAppActivationGate');
  await expect.poll(() => tenantConfigRequests).toBe(3);
  await expect(restoredPreparedState).toBeVisible();
  await expect(restoredPreparedState).toContainText('WhatsApp preparado');
  await expect.poll(() => certificationReadinessRequests).toBe(2);
  await expect(restoredCertificationState).toBeVisible();
  await expect(restoredCertificationState).toContainText('WhatsApp certificado');
  await expect.poll(() => deploymentReadinessRequests).toBe(2);
  await expect(restoredDeploymentState).toBeVisible();
  await expect(restoredDeploymentState).toContainText('Infraestructura lista para staging');
  await expect(restoredActivationGate).toBeVisible();
  await expect(restoredActivationGate)
    .toContainText('Activación disponible con autorización manual');
  await expect(restoredActivationGate)
    .toContainText('Activar habilita este negocio para Meta');
  const activateButton = page.locator('#metaWhatsAppActivateBtn');
  await expect(activateButton).toBeVisible();
  await expect.poll(() => phoneFinalizeBodies).toHaveLength(1);
  await expect.poll(() => activationRequests).toBe(0);

  page.once('dialog', dialog => dialog.dismiss());
  await activateButton.click();
  await expect.poll(() => activationRequests).toBe(0);
  await expect(activateButton).toBeEnabled();

  page.once('dialog', dialog => dialog.accept());
  await activateButton.click();
  await expect.poll(() => activationRequests).toBe(1);
  await expect(restoredActivationGate).toContainText('WhatsApp activado');
  await expect(restoredActivationGate).toContainText('Activación confirmada.');
  await expect(restoredPreparedState).toHaveClass(/hidden/);
  await expect(restoredCertificationState).not.toBeVisible();
  await expect(restoredDeploymentState).not.toBeVisible();
  await expect(activateButton).toHaveClass(/hidden/);
  await expect(page.locator('#metaWhatsAppDeactivateBtn')).toBeVisible();

  authRoles = ['OPERATOR'];
  await page.reload();
  await page.getByRole('button', { name: '📞 Canales', exact: true }).click();

  const enabledActivationGate = page.locator('#metaWhatsAppActivationGate');
  const enabledActivateButton = page.locator('#metaWhatsAppActivateBtn');
  const deactivateButton = page.locator('#metaWhatsAppDeactivateBtn');
  await expect.poll(() => tenantConfigRequests).toBe(4);
  await expect(page.locator('#metaWhatsAppPreparedState')).toHaveClass(/hidden/);
  await expect(page.locator('#metaWhatsAppCertificationState')).not.toBeVisible();
  await expect(page.locator('#metaWhatsAppDeploymentState')).not.toBeVisible();
  await expect.poll(() => certificationReadinessRequests).toBe(2);
  await expect.poll(() => deploymentReadinessRequests).toBe(2);
  await expect(enabledActivationGate).toBeVisible();
  await expect(enabledActivationGate).toContainText('WhatsApp activado');
  await expect(enabledActivationGate)
    .toContainText('Este negocio está habilitado para Meta.');
  await expect(enabledActivateButton).toHaveClass(/hidden/);
  await expect(deactivateButton).toHaveClass(/hidden/);
  await expect(page.locator('#metaWhatsAppConnectBtn')).toHaveClass(/hidden/);
  await deactivateButton.evaluate(element => element.classList.remove('hidden'));
  await deactivateButton.click();
  await deactivateButton.click();
  await expect.poll(() => activationRequests).toBe(1);
  await expect.poll(() => deactivationRequests).toBe(0);

  authRoles = ['BUSINESS_ADMIN'];
  await page.reload();
  await page.getByRole('button', { name: '📞 Canales', exact: true }).click();
  await expect.poll(() => tenantConfigRequests).toBe(5);
  await expect(deactivateButton).toBeVisible();

  page.once('dialog', dialog => dialog.dismiss());
  await deactivateButton.click();
  await expect.poll(() => deactivationRequests).toBe(0);

  page.once('dialog', dialog => dialog.accept());
  await deactivateButton.click();
  await expect.poll(() => deactivationRequests).toBe(1);
  await expect(page.locator('#metaWhatsAppPreparedState')).toBeVisible();
  await expect(enabledActivationGate)
    .toContainText('Activación disponible con autorización manual');
  await expect(page.locator('#metaWhatsAppActivationStatus'))
    .toHaveText('Desactivación confirmada.');
  await expect(deactivateButton).toHaveClass(/hidden/);
  await expect(enabledActivateButton).toBeVisible();
  await expect.poll(() => activationRequests).toBe(1);

  authRoles = ['OPERATOR'];
  await page.reload();
  await page.getByRole('button', { name: '📞 Canales', exact: true }).click();
  await expect.poll(() => tenantConfigRequests).toBe(6);
  await expect(page.locator('#metaWhatsAppActivateBtn')).toHaveClass(/hidden/);
  await expect(page.locator('#metaWhatsAppDeactivateBtn')).toHaveClass(/hidden/);
  await expect(page.locator('#metaWhatsAppConnectBtn')).toHaveClass(/hidden/);
  await expect.poll(() => activationRequests).toBe(1);
  await expect.poll(() => deactivationRequests).toBe(1);
});
