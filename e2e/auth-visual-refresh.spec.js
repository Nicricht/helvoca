const { test, expect } = require('@playwright/test');

test.describe('public authentication visual refresh', () => {
  test('presents the product before asking the visitor to register', async ({ page }) => {
    await page.goto('/');

    await expect(page.locator('#authView')).toBeVisible();
    await expect(page.getByRole('heading', { level: 1, name: 'No pierdas otra llamada.' })).toBeVisible();
    await expect(page.locator('.rv-hero-emphasis')).toHaveText('de tu negocio');
    await expect(page.locator('.rv-auth-visual img')).toHaveAttribute('src', '/recepvoz-auth-hero.svg');
    await expect(page.locator('.rv-benefit-row')).toContainText('Atiende llamadas 24/7');
    await expect(page.locator('.rv-benefit-row')).toContainText('Agenda citas');
    await expect(page.locator('.rv-benefit-row')).toContainText('WhatsApp Business');
    await expect(page.locator('.rv-auth-feature-strip')).toBeVisible();
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

  test('serves the new visual assets to unauthenticated visitors', async ({ request }) => {
    const css = await request.get('/auth-visual-refresh.css');
    const hero = await request.get('/recepvoz-auth-hero.svg');

    expect(css.status()).toBe(200);
    expect(hero.status()).toBe(200);
    expect(css.headers()['content-type']).toContain('text/css');
    expect(hero.headers()['content-type']).toContain('image/svg+xml');
  });

  test('motion remains nonessential when reduced motion is requested', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.goto('/');

    const animationName = await page.locator('.rv-auth-visual').evaluate(element => getComputedStyle(element).animationName);
    expect(animationName).toBe('none');
  });
});
