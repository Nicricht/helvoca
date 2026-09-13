const root = document.querySelector('#plans');

const clp = value => new Intl.NumberFormat('es-CL', {
  style: 'currency', currency: 'CLP', maximumFractionDigits: 0
}).format(value);

function card(plan) {
  const enterprise = plan.customPricing;
  const overage = enterprise
    ? 'Excedentes según cotización'
    : `${clp(plan.overagePerMinuteClp)} por minuto adicional`;
  const price = enterprise ? `Desde ${clp(plan.monthlyPriceClp)}` : clp(plan.monthlyPriceClp);
  return `
    <article class="plan ${plan.recommended ? 'recommended' : ''}">
      ${plan.recommended ? '<div class="recommended-tag">RECOMENDADO</div>' : ''}
      <h2>${plan.name}</h2>
      <div class="price">${price}<small>/mes</small></div>
      <ul>
        <li><strong>${plan.includedMinutes}</strong> minutos incluidos</li>
        <li><strong>${plan.maxConcurrentCalls}</strong> llamada${plan.maxConcurrentCalls === 1 ? '' : 's'} simultánea${plan.maxConcurrentCalls === 1 ? '' : 's'}</li>
        <li>Atención IA 24/7</li>
        <li>Preguntas, reservas y reagendamiento</li>
        <li>Transferencia a humano</li>
        <li>${overage}</li>
      </ul>
      <a class="plan-cta" href="/">${enterprise ? 'Cotizar' : 'Empezar'}</a>
    </article>`;
}

fetch('/api/v1/public/pricing')
  .then(response => {
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return response.json();
  })
  .then(plans => { root.innerHTML = plans.map(card).join(''); })
  .catch(() => {
    root.innerHTML = '<div class="error">No pudimos cargar los planes. Intenta nuevamente.</div>';
  });
