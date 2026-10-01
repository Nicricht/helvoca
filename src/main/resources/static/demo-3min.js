(() => {
  'use strict';

  const PRESETS = {
    sushi: {
      label: 'Sushi',
      icon: '🍣',
      title: 'Sushi · pedidos y cambios',
      business: 'Sushi Demo',
      summary: 'Cliente consulta una promo, cambia el pedido y hace una pregunta que el sistema no debe inventar.',
      actionCount: '1 pedido',
      actionLabel: 'preparado para confirmar',
      outcome: 'En tres escenas mostraste catálogo, pedido, modificación y límite seguro.',
      steps: [
        {
          headline: 'Cliente pregunta',
          customer: 'Hola, quiero algo para dos personas. ¿Qué promo tienen?',
          assistant: 'Tengo una promo de 40 piezas para dos personas por $24.990. Puedes pedirla para retiro o delivery.',
          proofTitle: 'Responde con datos del negocio',
          proofCopy: 'No improvisa una carta. Usa productos, precios y reglas configuradas.',
          proofBadge: 'Catálogo consultado',
          presenter: '“Primero responde usando la información real del negocio.”'
        },
        {
          headline: 'Cliente cambia el pedido',
          customer: 'Perfecto. Agrégame una bebida y mejor retiro a las 21:30.',
          assistant: 'Listo. Mantengo la promo, agrego una bebida y dejo el retiro solicitado para las 21:30.',
          proofTitle: 'Actualiza en vez de empezar de cero',
          proofCopy: 'La conversación conserva contexto y transforma el cambio en una acción clara.',
          proofBadge: 'Pedido actualizado',
          presenter: '“Si el cliente cambia de idea, RecepVoz mantiene el contexto.”'
        },
        {
          headline: 'Pregunta que no debe inventar',
          customer: '¿El salmón que usan llegó hoy en la mañana?',
          assistant: 'Ese dato no está confirmado en la información del negocio. Puedo dejar la consulta pendiente para que el equipo la responda.',
          proofTitle: 'Sabe cuándo frenar',
          proofCopy: 'Cuando el dato no existe, no fabrica una respuesta comercial.',
          proofBadge: 'Pregunta derivada',
          presenter: '“Y si no sabe algo, prefiero esto a que invente.”'
        }
      ]
    },
    pizzeria: {
      label: 'Pizzería',
      icon: '🍕',
      title: 'Pizzería · pedido y modificación',
      business: 'Pizzería Demo',
      summary: 'Cliente arma un pedido, cambia ingredientes y pregunta algo no confirmado.',
      actionCount: '1 pedido',
      actionLabel: 'actualizado sin duplicar',
      outcome: 'En tres escenas mostraste menú, pedido, modificación y límite seguro.',
      steps: [
        {
          headline: 'Cliente arma un pedido',
          customer: 'Quiero dos pizzas familiares. Una pepperoni y una napolitana.',
          assistant: 'Perfecto. Puedo preparar dos familiares: pepperoni y napolitana. ¿Retiro o delivery?',
          proofTitle: 'Convierte consulta en intención de compra',
          proofCopy: 'Usa el menú configurado y lleva la conversación hacia una acción concreta.',
          proofBadge: 'Pedido iniciado',
          presenter: '“No es solo un chat: intenta llevar la consulta al siguiente paso.”'
        },
        {
          headline: 'Cliente modifica',
          customer: 'La napolitana mejor mitad vegetariana y agrega una bebida.',
          assistant: 'Entendido. Actualizo la segunda pizza a mitad napolitana y mitad vegetariana, y agrego una bebida.',
          proofTitle: 'Corrige el mismo pedido',
          proofCopy: 'La modificación se aplica sobre el contexto existente en lugar de duplicar.',
          proofBadge: 'Pedido modificado',
          presenter: '“Los cambios del cliente no obligan a empezar de nuevo.”'
        },
        {
          headline: 'Pregunta desconocida',
          customer: '¿La masa fermentó exactamente 48 horas?',
          assistant: 'No tengo ese dato confirmado. Puedo dejar la pregunta para que el equipo la responda.',
          proofTitle: 'No inventa detalles',
          proofCopy: 'Los datos no configurados se reconocen como desconocidos.',
          proofBadge: 'Consulta pendiente',
          presenter: '“Aquí ves algo importante: sabe decir que no tiene el dato.”'
        }
      ]
    },
    carniceria: {
      label: 'Carnicería',
      icon: '🥩',
      title: 'Carnicería · cotización y cambio',
      business: 'Carnicería Demo',
      summary: 'Cliente arma una compra para un asado, cambia cantidades y consulta un dato no disponible.',
      actionCount: '1 cotización',
      actionLabel: 'lista para revisar',
      outcome: 'En tres escenas mostraste catálogo, cálculo estructurado, cambio y límite seguro.',
      steps: [
        {
          headline: 'Cliente pide ayuda',
          customer: 'Necesito carne para un asado de 8 personas. Quiero entraña y sobrecostilla.',
          assistant: 'Puedo armarte una cotización con los cortes disponibles. Por ejemplo, 2 kg de entraña y 3 kg de sobrecostilla.',
          proofTitle: 'Usa productos y cantidades',
          proofCopy: 'Convierte una consulta abierta en una compra/cotización estructurada.',
          proofBadge: 'Cotización iniciada',
          presenter: '“RecepVoz puede pasar de una pregunta a una solicitud concreta.”'
        },
        {
          headline: 'Cliente cambia cantidades',
          customer: 'Cambia un kilo de entraña por costillar.',
          assistant: 'Listo. Mantengo la cotización y reemplazo 1 kg de entraña por costillar.',
          proofTitle: 'Conserva el contexto',
          proofCopy: 'El cambio actualiza la intención existente sin crear otra solicitud paralela.',
          proofBadge: 'Cotización actualizada',
          presenter: '“Los cambios quedan sobre la misma operación.”'
        },
        {
          headline: 'Dato no confirmado',
          customer: '¿De qué predio exacto viene esa carne?',
          assistant: 'Ese origen no está registrado en la información disponible. Puedo dejar la consulta al equipo.',
          proofTitle: 'No inventa trazabilidad',
          proofCopy: 'Si el dato no está cargado, lo deriva en vez de afirmarlo.',
          proofBadge: 'Pregunta derivada',
          presenter: '“Lo que no está respaldado, no se inventa.”'
        }
      ]
    },
    peluqueria: {
      label: 'Peluquería',
      icon: '💇',
      title: 'Peluquería · reserva y reprogramación',
      business: 'Peluquería Demo',
      summary: 'Cliente busca hora, reserva y después cambia el horario.',
      actionCount: '1 reserva',
      actionLabel: 'creada y reprogramada',
      outcome: 'En tres escenas mostraste disponibilidad, reserva, reprogramación y límite seguro.',
      steps: [
        {
          headline: 'Cliente busca una hora',
          customer: 'Quiero corte y barba mañana después de las 17:00.',
          assistant: 'Tengo disponibilidad a las 17:30 y 18:30. ¿Cuál te sirve?',
          proofTitle: 'Consulta disponibilidad',
          proofCopy: 'La respuesta puede apoyarse en horarios y servicios configurados.',
          proofBadge: 'Disponibilidad revisada',
          presenter: '“Puede responder la consulta y avanzar directamente a una reserva.”'
        },
        {
          headline: 'Cliente elige y cambia',
          customer: '18:30. Aunque mejor déjamela para el viernes a esa hora.',
          assistant: 'Perfecto. La dejo para el viernes a las 18:30 y mantengo corte + barba.',
          proofTitle: 'Reprograma sin duplicar',
          proofCopy: 'La intención se corrige manteniendo servicio y contexto.',
          proofBadge: 'Reserva reprogramada',
          presenter: '“También entiende cambios sin obligar al equipo a reconstruir todo.”'
        },
        {
          headline: 'Pregunta no configurada',
          customer: '¿Qué peluquero me va a dejar mejor según mi cara?',
          assistant: 'No tengo información suficiente para prometer ese resultado. Puedo dejarte con el equipo para una recomendación personal.',
          proofTitle: 'Evita promesas inventadas',
          proofCopy: 'La recomendación subjetiva se deriva cuando no existe una regla aprobada.',
          proofBadge: 'Derivación humana',
          presenter: '“Cuando necesita criterio humano, no finge certeza.”'
        }
      ]
    },
    veterinaria: {
      label: 'Veterinaria',
      icon: '🐾',
      title: 'Veterinaria · agenda y derivación segura',
      business: 'Veterinaria Demo',
      summary: 'Cliente agenda un control, cambia horario y luego plantea una consulta clínica que debe derivarse.',
      actionCount: '1 reserva',
      actionLabel: 'administrativa, no clínica',
      outcome: 'En tres escenas mostraste agenda administrativa, reprogramación y una frontera clínica clara.',
      steps: [
        {
          headline: 'Cliente pide hora',
          customer: 'Necesito hora para el control de mi perro esta semana.',
          assistant: 'Puedo ayudarte con la agenda. Tengo bloques disponibles el jueves y viernes. ¿Qué día prefieres?',
          proofTitle: 'Automatiza lo administrativo',
          proofCopy: 'Agenda, horarios y datos del negocio sí son parte natural de la atención.',
          proofBadge: 'Agenda consultada',
          presenter: '“Aquí le quita trabajo administrativo al equipo.”'
        },
        {
          headline: 'Cliente cambia horario',
          customer: 'Viernes, pero después de las 18:00.',
          assistant: 'Perfecto. Te muestro las horas disponibles del viernes después de las 18:00.',
          proofTitle: 'Mantiene contexto',
          proofCopy: 'La conversación conserva la intención y ajusta el criterio de horario.',
          proofBadge: 'Disponibilidad ajustada',
          presenter: '“El cliente puede conversar normalmente, no llenar un formulario rígido.”'
        },
        {
          headline: 'Pregunta clínica',
          customer: '¿Qué medicamento le doy si está muy decaído?',
          assistant: 'No voy a indicar medicamentos ni hacer un diagnóstico. Para una situación clínica, corresponde hablar con el equipo veterinario; si parece urgente, busca atención veterinaria inmediata.',
          proofTitle: 'Frontera clínica explícita',
          proofCopy: 'La demo de salud se limita a funciones administrativas y deriva decisiones clínicas.',
          proofBadge: 'Derivación segura',
          presenter: '“En salud, prefiero mostrarte dónde se detiene la automatización.”'
        }
      ]
    },
    clinica: {
      label: 'Clínica',
      icon: '🏥',
      title: 'Clínica · agenda e información administrativa',
      business: 'Clínica Demo',
      summary: 'Cliente consulta especialidad, coordina una hora y hace una pregunta médica que se deriva.',
      actionCount: '1 reserva',
      actionLabel: 'administrativa',
      outcome: 'En tres escenas mostraste orientación administrativa, agenda y límite médico.',
      steps: [
        {
          headline: 'Paciente consulta',
          customer: 'Necesito una hora con dermatología. ¿Atienden en la tarde?',
          assistant: 'Sí puedo ayudarte con información de agenda y disponibilidad de dermatología en los horarios configurados.',
          proofTitle: 'Resuelve la parte administrativa',
          proofCopy: 'Especialidades, ubicación, horarios y agenda pueden atenderse sin convertir la IA en médico.',
          proofBadge: 'Agenda administrativa',
          presenter: '“La automatización aquí es administrativa, no médica.”'
        },
        {
          headline: 'Paciente cambia la hora',
          customer: 'Que sea después de las 17:00, por favor.',
          assistant: 'Entendido. Te muestro únicamente los bloques disponibles después de las 17:00.',
          proofTitle: 'Filtra y mantiene contexto',
          proofCopy: 'La conversación ajusta disponibilidad según la preferencia del paciente.',
          proofBadge: 'Horario ajustado',
          presenter: '“Coordina la hora sin hacer pasar al paciente por varios pasos.”'
        },
        {
          headline: 'Pregunta médica',
          customer: '¿Qué medicamento debería tomar antes de ir?',
          assistant: 'No puedo indicar medicamentos ni tratamientos. Esa decisión debe responderla un profesional de salud o el equipo de la clínica.',
          proofTitle: 'No da tratamiento',
          proofCopy: 'Las decisiones clínicas quedan fuera del alcance de esta demo.',
          proofBadge: 'Derivación profesional',
          presenter: '“Esto es justamente lo que quieres que haga ante una pregunta médica.”'
        }
      ]
    },
    taller: {
      label: 'Taller',
      icon: '🔧',
      title: 'Taller · solicitud y cotización',
      business: 'Taller Demo',
      summary: 'Cliente describe un problema, coordina revisión y pregunta algo que requiere inspección real.',
      actionCount: '1 solicitud',
      actionLabel: 'lista para el taller',
      outcome: 'En tres escenas mostraste captura de problema, coordinación y límite técnico.',
      steps: [
        {
          headline: 'Cliente describe el problema',
          customer: 'Mi auto hace un ruido al frenar. ¿Puedo llevarlo mañana?',
          assistant: 'Puedo registrar la solicitud y revisar los horarios disponibles para recepción mañana.',
          proofTitle: 'Convierte el contacto en trabajo',
          proofCopy: 'Captura el motivo y lo lleva a una solicitud o reserva para el taller.',
          proofBadge: 'Solicitud registrada',
          presenter: '“No necesita diagnosticar para capturar bien la oportunidad.”'
        },
        {
          headline: 'Cliente agrega contexto',
          customer: 'Es un Mazda 3 y prefiero dejarlo a primera hora.',
          assistant: 'Perfecto. Mantengo la solicitud, agrego el modelo y priorizo los horarios de primera hora.',
          proofTitle: 'Enriquece la misma solicitud',
          proofCopy: 'Los nuevos datos quedan asociados al mismo caso.',
          proofBadge: 'Solicitud actualizada',
          presenter: '“Cada mensaje mejora la información que recibe el negocio.”'
        },
        {
          headline: 'Diagnóstico imposible',
          customer: '¿Puedes asegurarme que son las pastillas de freno?',
          assistant: 'No puedo asegurarlo sin una revisión del vehículo. Puedo dejar registrada la sospecha y coordinar la inspección.',
          proofTitle: 'No inventa un diagnóstico',
          proofCopy: 'Lo que requiere inspección física se deja para el profesional.',
          proofBadge: 'Revisión humana',
          presenter: '“No promete algo que todavía nadie verificó.”'
        }
      ]
    }
  };

  const $ = selector => document.querySelector(selector);
  let presetKey = 'sushi';
  let stepIndex = 0;
  let timerId = null;
  let secondsLeft = 180;

  function formatTime(seconds) {
    const safe = Math.max(0, seconds);
    const minutes = Math.floor(safe / 60);
    const remainder = safe % 60;
    return minutes + ':' + String(remainder).padStart(2, '0');
  }

  function setTimer(seconds) {
    secondsLeft = seconds;
    $('#demoTimer').textContent = formatTime(secondsLeft);
  }

  function stopTimer() {
    if (timerId) window.clearInterval(timerId);
    timerId = null;
  }

  function startTimer() {
    stopTimer();
    setTimer(180);
    timerId = window.setInterval(() => {
      secondsLeft -= 1;
      $('#demoTimer').textContent = formatTime(secondsLeft);
      if (secondsLeft <= 0) stopTimer();
    }, 1000);
  }

  function selectedPreset() {
    return PRESETS[presetKey];
  }

  function selectPreset(key) {
    if (!PRESETS[key]) return;
    presetKey = key;
    const preset = selectedPreset();

    document.querySelectorAll('[data-preset]').forEach(button => {
      button.setAttribute('aria-pressed', button.dataset.preset === key ? 'true' : 'false');
    });

    $('#presetIcon').textContent = preset.icon;
    $('#presetTitle').textContent = preset.title;
    $('#presetSummary').textContent = preset.summary;
  }

  function renderStep() {
    const preset = selectedPreset();
    const step = preset.steps[stepIndex];

    $('#stagePresetLabel').textContent = preset.label;
    $('#stageBusiness').textContent = preset.business;
    $('#stageHeadline').textContent = step.headline;
    $('#customerBubble').textContent = step.customer;
    $('#assistantBubble').textContent = step.assistant;
    $('#proofTitle').textContent = step.proofTitle;
    $('#proofCopy').textContent = step.proofCopy;
    $('#proofBadge').textContent = step.proofBadge;
    $('#presenterLine').textContent = step.presenter;
    $('#stepCounter').textContent = (stepIndex + 1) + ' de 3';
    $('#progressBar').style.width = (((stepIndex + 1) / 3) * 100) + '%';
    $('#nextDemoBtn').textContent = stepIndex === 2 ? 'Ver resultado' : 'Siguiente';
    $('#demoStage').dataset.demoStep = String(stepIndex + 1);
    $('#demoStage').setAttribute('data-demo-step', String(stepIndex + 1));
  }

  function startDemo() {
    stepIndex = 0;
    $('#demoIntro').classList.add('hidden');
    $('#demoOutcome').classList.add('hidden');
    $('#demoStage').classList.remove('hidden');
    startTimer();
    renderStep();
    window.scrollTo({ top: 0, behavior: 'instant' });
  }

  function showOutcome() {
    const preset = selectedPreset();
    stopTimer();
    $('#demoStage').classList.add('hidden');
    $('#demoOutcome').classList.remove('hidden');
    $('#outcomeLead').textContent = preset.outcome;
    $('#outcomeAction').textContent = preset.actionCount;
    $('#outcomeActionLabel').textContent = preset.actionLabel;
    window.scrollTo({ top: 0, behavior: 'instant' });
  }

  function nextStep() {
    if (stepIndex < 2) {
      stepIndex += 1;
      renderStep();
      return;
    }
    showOutcome();
  }

  function resetDemo() {
    stopTimer();
    setTimer(180);
    stepIndex = 0;
    $('#demoStage').classList.add('hidden');
    $('#demoOutcome').classList.add('hidden');
    $('#demoIntro').classList.remove('hidden');
    window.scrollTo({ top: 0, behavior: 'instant' });
  }

  document.querySelectorAll('[data-preset]').forEach(button => {
    button.addEventListener('click', () => selectPreset(button.dataset.preset));
  });

  $('#startDemoBtn').addEventListener('click', startDemo);
  $('#nextDemoBtn').addEventListener('click', nextStep);
  $('#resetDemoBtn').addEventListener('click', resetDemo);
  $('#restartOutcomeBtn').addEventListener('click', resetDemo);

  document.addEventListener('keydown', event => {
    if (event.key === 'ArrowRight' || event.key === ' ') {
      if (!$('#demoStage').classList.contains('hidden')) {
        event.preventDefault();
        nextStep();
      }
    }
    if (event.key.toLowerCase() === 'r') resetDemo();
  });

  const requested = new URLSearchParams(window.location.search).get('rubro');
  selectPreset(PRESETS[requested] ? requested : 'sushi');
  setTimer(180);
})();
