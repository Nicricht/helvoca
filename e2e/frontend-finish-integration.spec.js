const { test, expect } = require('@playwright/test');
const fs = require('node:fs');

const STATIC = 'src/main/resources/static';
const customerPages = [
  'index.html',
  'settings.html',
  'inventory.html',
  'conversations.html',
  'simulator.html',
  'account.html'
];

function read(name) {
  return fs.readFileSync(`${STATIC}/${name}`, 'utf8');
}

function navEntries(html) {
  const nav = html.match(/<nav[^>]+aria-label="Navegación principal"[^>]*>([\s\S]*?)<\/nav>/i);
  if (!nav) return [];
  return [...nav[1].matchAll(/<a\s+([^>]*?)>([\s\S]*?)<\/a>/gi)].map(match => {
    const attrs = match[1];
    const href = attrs.match(/href="([^"]+)"/i)?.[1] || '';
    const text = match[2].replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim();
    return { href, text };
  });
}

test('customer navigation stays coherent and never exposes internal operations', async () => {
  const expected = [
    { href: '/', text: 'Inicio' },
    { href: '/#bookings', text: 'Reservas' },
    { href: '/#customers', text: 'Clientes' },
    { href: '/inventory.html', text: 'Inventario' },
    { href: '/conversations.html', text: 'Recepcionista IA' },
    { href: '/settings.html', text: 'Configuración' }
  ];

  for (const page of customerPages) {
    const html = read(page);
    expect(navEntries(html), `${page} primary navigation`).toEqual(expected);
    expect(html, `${page} must not expose internal operations`).not.toContain('href="/operations.html"');
  }
});

test('canonical foundation remains dark, solid and readable', async () => {
  const css = read('frontend-foundation.css');

  expect(css).toContain('--rv-bg-canvas: #070a10');
  expect(css).toContain('--rv-surface-1: #0d131d');
  expect(css).toContain('--rv-text-primary: #f4f7fb');
  expect(css).toContain('--rv-accent: #806bff');
  expect(css).not.toMatch(/(?:linear|radial)-gradient/i);
  expect(css).toContain('.app-nav a,.inventory-nav a,.topbar nav a,.account-nav a,.rv-nav a{font-size:14px}');
});

test('receptionist area keeps history and safe simulation as local actions', async () => {
  const conversations = read('conversations.html');
  const simulator = read('simulator.html');

  expect(conversations).toContain('>Conversaciones</h1>');
  expect(conversations).toContain('href="/simulator.html">Probar recepcionista</a>');
  expect(simulator).toContain('href="/conversations.html">Ver historial</a>');
  expect(simulator).toMatch(/No crea datos comerciales reales ni realiza llamadas telefónicas/i);
  expect(simulator).toMatch(/Tampoco envía WhatsApp real/i);
});
