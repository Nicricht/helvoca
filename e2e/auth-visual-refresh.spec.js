const { test, expect } = require('@playwright/test');

test.describe('public authentication visual refresh', () => {
  test('presents the product before asking the visitor to register', async ({ page }) => {
    await page.goto('/');

    await expect(page.locator('#authView')).toBeVisible();
    await expect(page.getByRole('heading', { level: 1, name: 'No pierdas otra llamada.' })).toBeVisible();
    await expect(page.locator('.rv-hero-emphasis')).toHaveText('de tu negocio');
    await expect(page.locator('.rv-brand-mark .rv-brand-logo')).toBeVisible();
    await expect(page.locator('.rv-auth-visual img.rv-phone-device')).toHaveAttribute('src', '/recepvoz-phone-hero.svg');
    await expect(page.locator('.rv-benefit-row')).toContainText('Atiende llamadas 24/7');
    await expect(page.locator('.rv-benefit-row')).toContainText('Agenda citas');
    await expect(page.locator('.rv-benefit-row')).toContainText('WhatsApp Business');
    await expect(page.locator('.rv-auth-feature-strip')).toBeVisible();
    await expect(page.locator('.rv-auth-feature svg.rv-auth-feature-art')).toHaveCount(4);
    for (const feature of ['calls', 'calendar', 'whatsapp', 'analytics']) {
      await expect(page.locator(`.rv-auth-feature[data-feature="${feature}"] .rv-auth-feature-art`)).toBeVisible();
    }
    await expect(page.locator('#registerForm')).toBeVisible();
  });

  test('keeps the public entry surface contained on desktop, tablet and mobile', async ({ page }) => {
    for (const viewport of [
      { width: 1440, height: 900 },
      { width: 768, height: 900 },
      { width: 390, height: 844 }
    ]) {
      await page.setViewportSize(viewport);
      await page.goto('/');
      await expect(page.locator('#registerForm')).toBeVisible();
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1)).toBe(true);
    }
  });

  test('keeps hero words intact at common desktop widths', async ({ page }) => {
    for (const viewport of [
      { width: 1536, height: 950 },
      { width: 1366, height: 768 },
      { width: 1180, height: 820 }
    ]) {
      await page.setViewportSize(viewport);
      await page.goto('/');

      const fragmentedWords = await page.locator('.rv-auth-copy').evaluate((root) => {
        const targets = ['pierdas', 'llamada', 'negocio'];
        const textNodes = [];
        const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
        while (walker.nextNode()) textNodes.push(walker.currentNode);

        return targets.filter((target) => {
          for (const node of textNodes) {
            const source = node.textContent || '';
            const index = source.toLowerCase().indexOf(target);
            if (index < 0) continue;
            const range = document.createRange();
            range.setStart(node, index);
            range.setEnd(node, index + target.length);
            return range.getClientRects().length > 1;
          }
          return true;
        });
      });

      expect(fragmentedWords, `hero words must not split at ${viewport.width}px`).toEqual([]);
    }
  });

  test('keeps the 1280px desktop composition inside its cards', async ({ page }) => {
    for (const viewport of [
      { width: 1280, height: 720 },
      { width: 1278, height: 690 }
    ]) {
      await page.setViewportSize(viewport);
      await page.goto('/');

      const metrics = await page.evaluate(() => {
        const box = (selector) => {
          const rect = document.querySelector(selector).getBoundingClientRect();
          return { left: rect.left, right: rect.right, top: rect.top, bottom: rect.bottom, width: rect.width, height: rect.height };
        };
        return {
          viewportWidth: document.documentElement.clientWidth,
          scrollWidth: document.documentElement.scrollWidth,
          topbar: box('.topbar'),
          hero: box('.rv-auth-hero'),
          copy: box('.rv-auth-copy'),
          phone: box('.rv-phone-stage'),
          authCard: box('.rv-auth-card')
        };
      });

      expect(metrics.scrollWidth).toBeLessThanOrEqual(metrics.viewportWidth + 1);
      expect(metrics.hero.left).toBeGreaterThanOrEqual(12);
      expect(metrics.copy.left - metrics.hero.left).toBeGreaterThanOrEqual(28);
      expect(metrics.hero.right).toBeLessThanOrEqual(metrics.authCard.left - 12);
      expect(metrics.copy.right).toBeLessThanOrEqual(metrics.phone.left + 18);
      expect(metrics.authCard.right).toBeLessThanOrEqual(metrics.viewportWidth - 12);
      expect(metrics.hero.top - metrics.topbar.bottom).toBeLessThanOrEqual(48);
    }
  });

  test('gives the phone a dominant visual stage on desktop', async ({ page }) => {
    await page.setViewportSize({ width: 1536, height: 950 });
    await page.goto('/');

    const heroBox = await page.locator('.rv-auth-hero').boundingBox();
    const phoneBox = await page.locator('.rv-phone-device').boundingBox();

    expect(heroBox).not.toBeNull();
    expect(phoneBox).not.toBeNull();
    expect(phoneBox.width).toBeGreaterThan(320);
    expect(phoneBox.height).toBeGreaterThan(500);
    expect(phoneBox.width / heroBox.width).toBeGreaterThan(0.34);
  });

  test('renders coded conversation layers around the phone', async ({ page }) => {
    await page.setViewportSize({ width: 1536, height: 950 });
    await page.goto('/');

    await expect(page.locator('.rv-phone-stage')).toBeVisible();
    await expect(page.locator('.rv-phone-device')).toHaveAttribute('src', '/recepvoz-phone-hero.svg');
    await expect(page.locator('.rv-phone-bubble')).toHaveCount(3);
    await expect(page.locator('.rv-phone-wave i')).toHaveCount(9);
    await expect(page.locator('.rv-phone-status')).toContainText('Recepcionista disponible');
  });

  test('serves the new visual assets to unauthenticated visitors', async ({ request }) => {
    const css = await request.get('/auth-visual-refresh.css');
    const motionCss = await request.get('/landing-motion.css');
    const phone = await request.get('/recepvoz-phone-hero.svg');

    expect(css.status()).toBe(200);
    expect(motionCss.status()).toBe(200);
    expect(phone.status()).toBe(200);
    expect(css.headers()['content-type']).toContain('text/css');
    expect(motionCss.headers()['content-type']).toContain('text/css');
    expect(phone.headers()['content-type']).toContain('image/svg+xml');
  });

  test('keeps the public surface visually alive without relying on user interaction', async ({ page }) => {
    await page.goto('/');

    const motion = await page.evaluate(() => {
      const hero = getComputedStyle(document.querySelector('.rv-phone-device')).animationName;
      const feature = getComputedStyle(document.querySelector('.rv-auth-feature-art')).animationName;
      const glow = getComputedStyle(document.querySelector('.rv-auth-hero')).getPropertyValue('--rv-motion-ready').trim();
      return { hero, feature, glow };
    });

    expect(motion.hero).not.toBe('none');
    expect(motion.feature).not.toBe('none');
    expect(motion.glow).toBe('1');
  });

  test('motion remains nonessential when reduced motion is requested', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.goto('/');

    const motion = await page.evaluate(() => ({
      hero: getComputedStyle(document.querySelector('.rv-phone-device')).animationName,
      feature: getComputedStyle(document.querySelector('.rv-auth-feature-art')).animationName,
      cardTransition: getComputedStyle(document.querySelector('.rv-auth-feature')).transitionDuration
    }));
    expect(motion.hero).toBe('none');
    expect(motion.feature).toBe('none');
    expect(parseFloat(motion.cardTransition)).toBeLessThanOrEqual(0.01);
  });
});
