(() => {
  const TOKEN_KEY="helvoca_access_token";
  const token=sessionStorage.getItem(TOKEN_KEY);
  if(!token){location.replace("/");return;}
  const $=(s,r=document)=>r.querySelector(s), $$=(s,r=document)=>[...r.querySelectorAll(s)];
  const DAYS=[[1,"Lunes"],[2,"Martes"],[3,"Miércoles"],[4,"Jueves"],[5,"Viernes"],[6,"Sábado"],[7,"Domingo"]];
  let dirty=false, currentPhones=[];

  async function api(path,options={}){
    const headers=new Headers(options.headers||{});headers.set("Authorization",`Bearer ${token}`);
    if(options.body&&!headers.has("Content-Type"))headers.set("Content-Type","application/json");
    const res=await fetch(path,{...options,headers});let payload=null;const type=res.headers.get("content-type")||"";
    if(type.includes("application/json")){try{payload=await res.json();}catch(_){}}else if(res.status!==204){try{payload=await res.text();}catch(_){}}
    if(!res.ok){if(res.status===401){sessionStorage.removeItem(TOKEN_KEY);location.replace("/");}throw new Error(payload?.message||payload?.detail||payload?.error||(typeof payload==="string"&&payload)||`HTTP ${res.status}`);}
    return payload;
  }
  function esc(v){return String(v??"").replace(/[&<>'"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));}
  function message(text,kind="error"){const el=$("#settingsMessage");el.textContent=text;el.classList.remove("hidden","error","success");el.classList.add(kind);clearTimeout(message.timer);message.timer=setTimeout(()=>el.classList.add("hidden"),3500);}
  function markDirty(){dirty=true;$("#settingsSaveBar").classList.remove("hidden");}
  function clean(){dirty=false;$("#settingsSaveBar").classList.add("hidden");}
  function dayOptions(selected){return DAYS.map(([v,l])=>`<option value="${v}" ${Number(selected)===v?"selected":""}>${l}</option>`).join("");}

  function addService(item={}){
    const root=$("#settingsServices"), row=document.createElement("div");row.className="settings-row service";if(item.id)row.dataset.id=item.id;
    row.innerHTML=`<label>Servicio<input data-field="name" value="${esc(item.name||"")}"></label><label>Duración<input data-field="durationMinutes" type="number" min="1" value="${Number(item.durationMinutes||30)}"></label><label>Precio<input data-field="price" type="number" min="0" step="0.01" value="${item.price??""}"></label><label>Descripción<input data-field="description" value="${esc(item.description||"")}"></label><button class="remove" type="button">×</button>`;
    $(".remove",row).addEventListener("click",()=>{if($$(".settings-row.service",root).length>1){row.remove();markDirty();}});root.appendChild(row);
  }
  function addKnowledge(item={}){
    const row=document.createElement("div");row.className="settings-row knowledge";if(item.id)row.dataset.id=item.id;
    row.innerHTML=`<label>Título<input data-field="title" value="${esc(item.title||"")}"></label><label>Categoría<input data-field="category" value="${esc(item.category||"")}"></label><label>Respuesta<input data-field="content" value="${esc(item.content||"")}"></label><button class="remove" type="button">×</button>`;
    $(".remove",row).addEventListener("click",()=>{row.remove();markDirty();});$("#settingsKnowledge").appendChild(row);
  }
  function addHour(item={dayOfWeek:1,openTime:"09:00",closeTime:"18:00"}){
    const row=document.createElement("div");row.className="settings-row hour";
    row.innerHTML=`<label>Día<select data-field="dayOfWeek">${dayOptions(item.dayOfWeek)}</select></label><label>Abre<input data-field="openTime" type="time" value="${String(item.openTime||"09:00").slice(0,5)}"></label><label>Cierra<input data-field="closeTime" type="time" value="${String(item.closeTime||"18:00").slice(0,5)}"></label><button class="remove" type="button">×</button>`;
    $(".remove",row).addEventListener("click",()=>{row.remove();markDirty();});$("#settingsHours").appendChild(row);
  }
  function renderPhones(items=[]){
    currentPhones=items;const root=$("#settingsPhones");root.innerHTML="";
    if(!items.length){root.innerHTML='<div class="empty">Aún no hay números conectados.</div>';return;}
    items.forEach(phone=>{const row=document.createElement("div");row.className="settings-row phone";row.innerHTML=`<div><strong>${esc(phone.phoneNumber)}</strong><small>${esc(phone.provider||"Proveedor")}</small></div><span class="pill ${phone.active?"":"bad"}">${phone.active?"Activo":"Inactivo"}</span><button type="button" class="ghost" data-phone-toggle="${esc(phone.id)}">${phone.active?"Desactivar":"Activar"}</button>`;root.appendChild(row);});
    $$("[data-phone-toggle]",root).forEach(button=>button.addEventListener("click",async()=>{const phone=currentPhones.find(p=>String(p.id)===button.dataset.phoneToggle);if(!phone)return;button.disabled=true;try{await api(`/api/v1/phone-numbers/${encodeURIComponent(phone.id)}/active`,{method:"PATCH",body:JSON.stringify({active:!phone.active})});await loadPhones();message("Número actualizado.","success");}catch(e){message(e.message);button.disabled=false;}}));
  }
  function renderPlan(billing={},subscription={}){
    const used=Number(subscription.usedMinutes||0),included=Number(subscription.includedMinutes||0);
    $("#settingsPlan").innerHTML=`<div class="settings-plan-card"><span>Plan</span><strong>${esc(billing.currentPlanName||billing.currentPlanCode||subscription.plan||"Sin plan")}</strong></div><div class="settings-plan-card"><span>Estado</span><strong>${esc(subscription.status||"—")}</strong></div><div class="settings-plan-card"><span>Minutos</span><strong>${used} / ${included}</strong></div>`;
  }
  function renderAgent(agent={},business={}){
    $("#settingsAgentName").value=agent.name||"Helvoca";$("#settingsAgentVoice").value=agent.voice||"";
    $("#settingsAgentGreeting").value=agent.greeting||`Hola, gracias por llamar a ${business.name||"nuestro negocio"}. ¿En qué puedo ayudarte?`;
    $("#settingsAgentInstructions").value=agent.instructions||"";$("#settingsAgentActive").checked=agent.active!==false;
    const enabled=new Set(agent.capabilities||[]);$$('#settingsCapabilities input[type=checkbox]').forEach(i=>i.checked=enabled.has(i.value));
  }
  function collectServices(){return $(".settings-row.service",$("#settingsServices")).map(row=>({id:row.dataset.id||null,name:$('[data-field="name"]',row).value.trim(),description:$('[data-field="description"]',row).value.trim()||null,durationMinutes:Number($('[data-field="durationMinutes"]',row).value),price:$('[data-field="price"]',row).value===""?null:Number($('[data-field="price"]',row).value)})).filter(x=>x.name);}
  function collectHours(){return $$(".settings-row.hour",$("#settingsHours")).map(row=>({dayOfWeek:Number($('[data-field="dayOfWeek"]',row).value),openTime:$('[data-field="openTime"]',row).value,closeTime:$('[data-field="closeTime"]',row).value}));}
  function collectKnowledge(){return $$(".settings-row.knowledge",$("#settingsKnowledge")).map(row=>({id:row.dataset.id||null,title:$('[data-field="title"]',row).value.trim(),category:$('[data-field="category"]',row).value.trim()||null,content:$('[data-field="content"]',row).value.trim()})).filter(x=>x.title&&x.content);}
  function collectAgent(){return{name:$("#settingsAgentName").value.trim()||"Helvoca",language:$("#settingsLanguage").value.trim()||"es",voice:$("#settingsAgentVoice").value.trim()||null,greeting:$("#settingsAgentGreeting").value.trim(),instructions:$("#settingsAgentInstructions").value.trim()||null,active:$("#settingsAgentActive").checked,capabilities:$$('#settingsCapabilities input[type=checkbox]:checked').map(i=>i.value)};}

  async function loadPhones(){try{renderPhones(await api("/api/v1/phone-numbers"));}catch(e){$("#settingsPhones").innerHTML='<div class="empty">No pude cargar los teléfonos.</div>';}}
  async function load(){
    $("#settingsLoadState").textContent="Cargando";
    const [business,agent,services,hours,knowledge,phones,billing,subscription]=await Promise.allSettled([
      api("/api/v1/business"),api("/api/v1/ai-agent"),api("/api/v1/services"),api("/api/v1/business/hours"),api("/api/v1/knowledge?activeOnly=false"),api("/api/v1/phone-numbers"),api("/api/v1/billing/status"),api("/api/v1/subscription")
    ]);
    const b=business.status==="fulfilled"?business.value:{};
    $("#settingsBusinessName").value=b.name||"";$("#settingsTimezone").value=b.timezone||Intl.DateTimeFormat().resolvedOptions().timeZone||"America/Santiago";$("#settingsLanguage").value=b.language||"es";$("#settingsHumanTransferPhone").value=b.humanTransferPhone||"";
    renderAgent(agent.status==="fulfilled"?agent.value:{},b);
    $("#settingsServices").innerHTML="";const svc=services.status==="fulfilled"&&Array.isArray(services.value)?services.value.filter(x=>x.active!==false):[];(svc.length?svc:[{}]).forEach(addService);
    $("#settingsHours").innerHTML="";const hs=hours.status==="fulfilled"&&Array.isArray(hours.value)?hours.value:[];(hs.length?hs:[{dayOfWeek:1,openTime:"09:00",closeTime:"18:00"}]).forEach(addHour);
    $("#settingsKnowledge").innerHTML="";const kn=knowledge.status==="fulfilled"&&Array.isArray(knowledge.value)?knowledge.value.filter(x=>x.active!==false):[];kn.forEach(addKnowledge);
    renderPhones(phones.status==="fulfilled"&&Array.isArray(phones.value)?phones.value:[]);
    renderPlan(billing.status==="fulfilled"?billing.value:{},subscription.status==="fulfilled"?subscription.value:{});
    $("#settingsLoadState").textContent="Actualizado";clean();
  }
  async function save(){
    const services=collectServices(),hours=collectHours(),greeting=$("#settingsAgentGreeting").value.trim();
    if(!services.length){message("Añade al menos un servicio.");return;}if(!hours.length){message("Añade al menos un horario.");return;}if(!greeting){message("Define el saludo inicial.");return;}
    const button=$("#settingsSaveBtn");button.disabled=true;
    try{
      await api("/api/v1/onboarding/setup",{method:"PUT",body:JSON.stringify({businessName:$("#settingsBusinessName").value.trim(),timezone:$("#settingsTimezone").value.trim(),language:$("#settingsLanguage").value.trim(),humanTransferPhone:$("#settingsHumanTransferPhone").value.trim()||null,services,hours,knowledge:collectKnowledge()})});
      await api("/api/v1/ai-agent",{method:"PUT",body:JSON.stringify(collectAgent())});
      clean();message("Configuración guardada.","success");
    }catch(e){message(e.message||"No pude guardar la configuración.");}finally{button.disabled=false;}
  }

  $$(".settings-tab").forEach(button=>button.addEventListener("click",()=>{$$(".settings-tab").forEach(x=>x.classList.toggle("active",x===button));$$("[data-settings-panel]").forEach(panel=>panel.classList.toggle("hidden",panel.dataset.settingsPanel!==button.dataset.settingsTab));}));
  $("#settingsAddService").addEventListener("click",()=>{addService();markDirty();});
  $("#settingsAddHour").addEventListener("click",()=>{addHour();markDirty();});
  $("#settingsAddKnowledge").addEventListener("click",()=>{addKnowledge();markDirty();});
  $("#settingsSaveBtn").addEventListener("click",save);
  $("#refreshSettingsBtn").addEventListener("click",load);
  $(".settings-content").addEventListener("input",markDirty);
  $(".settings-content").addEventListener("change",markDirty);
  $("#settingsPhoneForm").addEventListener("submit",async event=>{event.preventDefault();const form=event.currentTarget,data=Object.fromEntries(new FormData(form).entries());form.querySelector("button").disabled=true;try{await api("/api/v1/phone-numbers",{method:"POST",body:JSON.stringify({phoneNumber:String(data.phoneNumber||"").trim(),externalId:String(data.externalId||"").trim()||null,active:true})});form.reset();await loadPhones();message("Número conectado.","success");}catch(e){message(e.message||"No pude conectar el número.");}finally{form.querySelector("button").disabled=false;}});
  load();
})();