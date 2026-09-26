(() => {
  const dashboard = document.querySelector('#dashboardView');
  const activation = document.querySelector('#pilotActivationCard');
  if (!dashboard || typeof api !== 'function') return;

  const style = document.createElement('style');
  style.textContent = `
    #teamInvitationsCard { margin:18px 0; padding:16px 18px; }
    .team-invite-head { display:flex; justify-content:space-between; gap:14px; align-items:flex-start; }
    .team-invite-head h2 { margin:3px 0 4px; font-size:18px; }
    .team-invite-head p { margin:0; color:var(--muted); font-size:12px; line-height:1.45; }
    .team-invite-form { display:grid; grid-template-columns:1fr 1.4fr .8fr auto; gap:8px; margin-top:13px; align-items:end; }
    .team-invite-form label { display:grid; gap:5px; font-size:10px; color:var(--muted); }
    .team-invite-form input,.team-invite-form select { width:100%; box-sizing:border-box; }
    .team-invite-link { display:grid; grid-template-columns:1fr auto; gap:7px; margin-top:10px; }
    .team-invite-link input { width:100%; box-sizing:border-box; font-size:11px; }
    .team-invite-list { display:grid; gap:7px; margin-top:12px; }
    .team-invite-row { display:grid; grid-template-columns:minmax(0,1fr) auto auto; gap:9px; align-items:center; padding:9px 10px; border:1px solid var(--border); border-radius:10px; }
    .team-invite-row strong { display:block; font-size:11px; }
    .team-invite-row small { display:block; margin-top:2px; color:var(--muted); font-size:9px; }
    .team-invite-status { font-size:9px; font-weight:850; }
    .team-invite-status.pending { color:#f4a636; }
    .team-invite-status.accepted { color:#37cd91; }
    #teamInviteMessage { margin-top:9px; font-size:10px; }
    #teamInviteMessage.error { color:#f28d8d; }
    #teamInviteMessage.success { color:#37cd91; }
    @media(max-width:760px){ .team-invite-form{grid-template-columns:1fr;} .team-invite-row{grid-template-columns:1fr auto;} .team-invite-row button{grid-column:1/-1;} }
  `;
  document.head.appendChild(style);

  const card = document.createElement('section');
  card.id = 'teamInvitationsCard';
  card.className = 'card hidden';
  card.innerHTML = `
    <div class="team-invite-head">
      <div>
        <div class="eyebrow">Equipo</div>
        <h2>Invitar personas</h2>
        <p>Genera un enlace de un solo uso. La persona crea su propia contraseña; tú nunca la conoces.</p>
      </div>
    </div>
    <form id="teamInviteForm" class="team-invite-form">
      <label>Nombre<input name="name" maxlength="150" required placeholder="Camila Soto"></label>
      <label>Email<input name="email" type="email" maxlength="180" required placeholder="camila@negocio.cl"></label>
      <label>Rol<select name="role"><option value="OPERATOR">Operador</option><option value="BUSINESS_ADMIN">Administrador</option></select></label>
      <button class="button small primary" type="submit">Generar invitación</button>
    </form>
    <div id="teamInviteLink" class="team-invite-link hidden">
      <input id="teamInviteUrl" readonly aria-label="Enlace de invitación">
      <button id="teamInviteCopy" class="button small secondary" type="button">Copiar enlace</button>
    </div>
    <div id="teamInviteMessage" class="hidden"></div>
    <div id="teamInviteList" class="team-invite-list"></div>
  `;

  (activation || document.querySelector('#nextStepBanner'))?.insertAdjacentElement('afterend', card);

  const form = card.querySelector('#teamInviteForm');
  const list = card.querySelector('#teamInviteList');
  const linkWrap = card.querySelector('#teamInviteLink');
  const linkInput = card.querySelector('#teamInviteUrl');
  const copy = card.querySelector('#teamInviteCopy');
  const message = card.querySelector('#teamInviteMessage');
  let loading = false;

  function setMessage(text, kind='') {
    message.textContent = text || '';
    message.className = text ? kind : 'hidden';
  }

  function statusLabel(status) {
    return {
      PENDING:'Pendiente',
      ACCEPTED:'Aceptada',
      EXPIRED:'Expirada',
      REVOKED:'Revocada'
    }[status] || status || '';
  }

  function render(items) {
    list.replaceChildren();
    if (!Array.isArray(items) || !items.length) {
      const empty = document.createElement('span');
      empty.className = 'muted-text';
      empty.textContent = 'Aún no hay invitaciones.';
      list.appendChild(empty);
      return;
    }
    items.slice(0, 12).forEach(item => {
      const row = document.createElement('div');
      row.className = 'team-invite-row';

      const info = document.createElement('div');
      const name = document.createElement('strong');
      name.textContent = item.name || item.email;
      const detail = document.createElement('small');
      detail.textContent = `${item.email} · ${item.role === 'BUSINESS_ADMIN' ? 'Administrador' : 'Operador'}`;
      info.append(name, detail);

      const status = document.createElement('span');
      status.className = `team-invite-status ${String(item.status || '').toLowerCase()}`;
      status.textContent = statusLabel(item.status);

      row.append(info, status);

      if (item.status === 'PENDING') {
        const revoke = document.createElement('button');
        revoke.type = 'button';
        revoke.className = 'button small ghost';
        revoke.textContent = 'Revocar';
        revoke.addEventListener('click', async () => {
          revoke.disabled = true;
          try {
            await api(`/api/v1/admin/invitations/${encodeURIComponent(item.id)}`, { method:'DELETE' });
            setMessage('Invitación revocada.', 'success');
            await load();
          } catch (error) {
            setMessage(error.message || 'No fue posible revocar la invitación.', 'error');
            revoke.disabled = false;
          }
        });
        row.appendChild(revoke);
      }

      list.appendChild(row);
    });
  }

  async function load() {
    if (loading || dashboard.classList.contains('hidden') || !sessionStorage.getItem('helvoca_access_token')) return;
    loading = true;
    try {
      const me = await api('/api/v1/auth/me');
      const admin = Array.isArray(me?.roles) && me.roles.includes('BUSINESS_ADMIN');
      if (!admin) {
        card.classList.add('hidden');
        return;
      }
      card.classList.remove('hidden');
      render(await api('/api/v1/admin/invitations'));
    } catch (error) {
      if (error.status === 401 || error.status === 403) card.classList.add('hidden');
    } finally {
      loading = false;
    }
  }

  form.addEventListener('submit', async event => {
    event.preventDefault();
    setMessage('');
    const button = form.querySelector('button[type="submit"]');
    button.disabled = true;
    try {
      const f = new FormData(form);
      const result = await api('/api/v1/admin/invitations', {
        method:'POST',
        body:JSON.stringify({
          name:String(f.get('name') || '').trim(),
          email:String(f.get('email') || '').trim(),
          role:String(f.get('role') || 'OPERATOR')
        })
      });
      const url = new URL(result.invitePath, location.origin).href;
      linkInput.value = url;
      linkWrap.classList.remove('hidden');
      setMessage('Invitación creada. Comparte este enlace con la persona invitada.', 'success');
      form.reset();
      render(await api('/api/v1/admin/invitations'));
    } catch (error) {
      setMessage(error.message || 'No fue posible crear la invitación.', 'error');
    } finally {
      button.disabled = false;
    }
  });

  copy.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(linkInput.value);
      setMessage('Enlace copiado.', 'success');
    } catch (_) {
      linkInput.select();
      setMessage('Seleccioné el enlace para que puedas copiarlo.', 'success');
    }
  });

  new MutationObserver(() => {
    if (!dashboard.classList.contains('hidden')) load();
  }).observe(dashboard, {attributes:true, attributeFilter:['class']});

  document.querySelector('#refreshBtn')?.addEventListener('click', load);
  load();
})();
