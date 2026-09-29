const { test, expect } = require('@playwright/test');

function rgb(hex) {
  const value = hex.replace('#', '');
  return [0, 2, 4].map(index => parseInt(value.slice(index, index + 2), 16));
}

function relativeLuminance(hex) {
  const channels = rgb(hex).map(value => {
    const channel = value / 255;
    return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
  });
  return (0.2126 * channels[0]) + (0.7152 * channels[1]) + (0.0722 * channels[2]);
}

function contrastRatio(first, second) {
  const a = relativeLuminance(first);
  const b = relativeLuminance(second);
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
}

test('all curated themes preserve primary-action contrast and semantic status colors', async ({ page, request }) => {
  const cssResponse = await request.get('/frontend-foundation.css');
  expect(cssResponse.ok()).toBeTruthy();
  const css = await cssResponse.text();

  await page.setContent(
    '<style>' + css + '</style>' +
    '<button class="button primary">Guardar</button>' +
    '<span class="semantic"></span>'
  );

  const themes = ['cyan', 'blue', 'emerald', 'violet', 'amber'];
  let semanticBaseline = null;

  for (const theme of themes) {
    await page.evaluate(value => {
      document.documentElement.dataset.rvAccentTheme = value;
    }, theme);

    const tokens = await page.evaluate(() => {
      const style = getComputedStyle(document.documentElement);
      return {
        primary: style.getPropertyValue('--rv-brand-primary').trim(),
        strong: style.getPropertyValue('--rv-brand-strong').trim(),
        onPrimary: style.getPropertyValue('--rv-brand-contrast').trim(),
        success: style.getPropertyValue('--rv-success').trim(),
        warning: style.getPropertyValue('--rv-warning').trim(),
        danger: style.getPropertyValue('--rv-danger').trim(),
        info: style.getPropertyValue('--rv-info').trim()
      };
    });

    expect(contrastRatio(tokens.primary, tokens.onPrimary), theme + ' primary').toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(tokens.strong, tokens.onPrimary), theme + ' strong').toBeGreaterThanOrEqual(4.5);

    const semantic = {
      success: tokens.success,
      warning: tokens.warning,
      danger: tokens.danger,
      info: tokens.info
    };
    if (!semanticBaseline) semanticBaseline = semantic;
    else expect(semantic, theme).toEqual(semanticBaseline);
  }
});

test('appearance runtime uses authenticated session cache only', async ({ page, request }) => {
  const scriptResponse = await request.get('/appearance.js');
  expect(scriptResponse.ok()).toBeTruthy();
  const script = await scriptResponse.text();

  await page.goto('/terms.html');
  await page.evaluate(() => {
    sessionStorage.setItem('helvoca_access_token', 'test-token');
    sessionStorage.setItem('recepvoz_appearance_theme', 'blue');
  });
  await page.addScriptTag({ content: script });
  await expect(page.locator('html')).toHaveAttribute('data-rv-accent-theme', 'blue');

  await page.evaluate(() => {
    sessionStorage.removeItem('helvoca_access_token');
    delete window.RecepVozAppearance;
    document.documentElement.removeAttribute('data-rv-accent-theme');
  });
  await page.addScriptTag({ content: script });
  await expect(page.locator('html')).toHaveAttribute('data-rv-accent-theme', 'cyan');
  expect(await page.evaluate(() => sessionStorage.getItem('recepvoz_appearance_theme'))).toBeNull();
});
