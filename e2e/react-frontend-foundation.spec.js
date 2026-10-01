const fs = require('fs');
const path = require('path');
const { test, expect } = require('@playwright/test');

const ROOT = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(ROOT, relativePath), 'utf8');
}

test.describe('React frontend foundation', () => {
  test('publishes the dedicated React + TypeScript + Vite application contract', async () => {
    const packagePath = path.join(ROOT, 'frontend', 'package.json');
    const tsconfigPath = path.join(ROOT, 'frontend', 'tsconfig.json');
    const vitePath = path.join(ROOT, 'frontend', 'vite.config.ts');

    expect(fs.existsSync(packagePath)).toBeTruthy();
    expect(fs.existsSync(tsconfigPath)).toBeTruthy();
    expect(fs.existsSync(vitePath)).toBeTruthy();

    const pkg = JSON.parse(fs.readFileSync(packagePath, 'utf8'));
    expect(pkg.dependencies.react).toBeTruthy();
    expect(pkg.dependencies['react-router-dom']).toBeTruthy();
    expect(pkg.dependencies['@tanstack/react-query']).toBeTruthy();
    expect(pkg.devDependencies.typescript).toBeTruthy();
    expect(pkg.devDependencies.vite).toBeTruthy();
  });

  test('centralizes authenticated API handling instead of duplicating fetch per page', async () => {
    const client = read('frontend/src/api/client.ts');

    expect(client).toContain('helvoca_access_token');
    expect(client).toContain('Authorization');
    expect(client).toContain('Bearer');
    expect(client).toContain('response.status === 401');
  });

  test('keeps the React bundle inside the existing Spring deployment', async () => {
    const dockerfile = read('Dockerfile');
    const security = read('src/main/java/cl/helvoca/security/SecurityConfig.java');

    expect(dockerfile).toContain('npm run frontend:build');
    expect(dockerfile).toContain('/app/frontend-dist');
    expect(security).toContain('"/app/**"');
  });

  test('loads the authenticated React shell from the built application', async ({ page }) => {
    await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));

    await page.goto('/app/index.html');

    await expect(page.locator('[data-react-app="recepvoz"]')).toBeVisible();
    await expect(page.getByRole('heading', { level: 1, name: 'Plan y consumo' })).toBeVisible();
    await expect(page).toHaveURL(/\/app\/plan$/);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
  });

  test('redirects an unauthenticated React visit back to the existing auth surface', async ({ page }) => {
    await page.goto('/app/index.html');
    await expect(page).toHaveURL('http://127.0.0.1:4173/');
  });
});
