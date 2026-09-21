const { test, expect } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

const staticDir = path.resolve(__dirname, '../src/main/resources/static');
const htmlFiles = fs.readdirSync(staticDir).filter(name => name.endsWith('.html')).sort();

function attributes(source) {
  return Object.fromEntries(
    [...source.matchAll(/([\w:-]+)\s*=\s*(["'])(.*?)\2/gs)].map(match => [match[1].toLowerCase(), match[3]])
  );
}

function plainText(source) {
  return source.replace(/<[^>]*>/g, ' ').replace(/&\w+;/g, ' ').replace(/\s+/g, ' ').trim();
}

test('static interaction markup has unique targets and complete controls', async () => {
  const failures = [];
  const availablePages = new Set(htmlFiles);

  for (const file of htmlFiles) {
    const html = fs.readFileSync(path.join(staticDir, file), 'utf8');
    const ids = [...html.matchAll(/\bid=(["'])(.*?)\1/gs)].map(match => match[2]);
    const duplicates = [...new Set(ids.filter((id, index) => ids.indexOf(id) !== index))];
    if (duplicates.length) failures.push(`${file}: IDs duplicados: ${duplicates.join(', ')}`);

    for (const match of html.matchAll(/<a\b([^>]*)>/gi)) {
      const href = attributes(match[1]).href || '';
      if (!href || /^(https?:|mailto:|tel:)/i.test(href)) continue;
      if (/^[a-z][a-z0-9+.-]*:/i.test(href) || href.startsWith('//')) {
        failures.push(`${file}: esquema de enlace no permitido ${href}`);
        continue;
      }
      if (href.startsWith('#')) {
        if (href.length > 1 && !ids.includes(href.slice(1))) failures.push(`${file}: fragmento inexistente ${href}`);
        continue;
      }
      const target = href.split(/[?#]/, 1)[0];
      const targetFile = target === '/'
        ? 'index.html'
        : target.startsWith('/')
          ? target.slice(1)
          : path.posix.normalize(path.posix.join(path.posix.dirname(file), target));
      if (targetFile.startsWith('../')) {
        failures.push(`${file}: ruta interna fuera del sitio ${href}`);
        continue;
      }
      if (!availablePages.has(targetFile)) failures.push(`${file}: ruta interna inexistente ${href}`);
    }

    for (const match of html.matchAll(/<button\b([^>]*)>([\s\S]*?)<\/button>/gi)) {
      const attrs = attributes(match[1]);
      const name = attrs['aria-label'] || attrs.title || plainText(match[2]);
      if (!name) failures.push(`${file}: botón sin nombre accesible`);
    }

    for (const match of html.matchAll(/<form\b([^>]*)>([\s\S]*?)<\/form>/gi)) {
      const attrs = attributes(match[1]);
      if (!attrs.id) failures.push(`${file}: formulario sin id`);
      if (!/<button\b[^>]*type=(["'])submit\1/i.test(match[2]) && !/<input\b[^>]*type=(["'])submit\1/i.test(match[2])) {
        failures.push(`${file}: formulario ${attrs.id || '(sin id)'} sin control submit`);
      }
    }

    for (const match of html.matchAll(/<select\b([^>]*)>/gi)) {
      const attrs = attributes(match[1]);
      const before = html.slice(0, match.index);
      const wrapped = before.lastIndexOf('<label') > before.lastIndexOf('</label>');
      const labelled = Boolean(attrs['aria-label'] || attrs['aria-labelledby'] || (attrs.id && new RegExp(`<label\\b[^>]*for=["']${attrs.id}["']`, 'i').test(html)));
      if (!wrapped && !labelled) failures.push(`${file}: select ${attrs.id || '(sin id)'} sin etiqueta`);
    }
  }

  expect(failures, failures.join('\n')).toEqual([]);
});

test('Meta state handlers fail closed before privileged actions', async () => {
  const settings = fs.readFileSync(path.join(staticDir, 'settings-page.js'), 'utf8');

  expect(settings).toMatch(
    /activationButton\?\.addEventListener\("click", async \(\) => \{\s*if \(!canManageMeta\) return;/
  );
  expect(settings).toMatch(
    /deactivationButton\?\.addEventListener\("click", async \(\) => \{\s*if \(!canManageMeta\) return;/
  );
});
