import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, Link2, Phone, Search, ShieldCheck, Trash2 } from "lucide-react";
import { useMemo, useRef, useState } from "react";
import {
  activateMetaWhatsApp,
  connectExistingPhoneNumber,
  deactivateMetaWhatsApp,
  detachPhoneNumber,
  discoverMetaPhoneNumbers,
  exchangeMetaAuthorizationCode,
  finalizeMetaPhoneNumber,
  getMetaCertificationReadiness,
  getMetaDeploymentReadiness,
  getMetaWhatsAppBootstrap,
  getMetaWhatsAppStatus,
  getPhoneProvisioningStatus,
  provisionPhoneNumber,
  searchAvailablePhoneNumbers,
  validateMetaPhoneNumber,
  type AvailablePhoneNumber,
  type MetaPhoneCandidate,
  type MetaWabaCandidate,
  type PhoneNumber
} from "../../features/settings/api";
import styles from "./SettingsPage.module.css";

declare global {
  interface Window {
    FB?: {
      init: (options: { appId: string; xfbml: boolean; version: string }) => void;
      login: (
        callback: (response: { authResponse?: { code?: string } }) => void,
        options: Record<string, unknown>
      ) => void;
    };
    fbAsyncInit?: () => void;
  }
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}

function loadFacebookSdk(appId: string, graphApiVersion: string) {
  if (window.FB?.init) {
    window.FB.init({ appId, xfbml: false, version: graphApiVersion });
    return Promise.resolve();
  }

  return new Promise<void>((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>("#facebook-jssdk");
    const timeout = window.setTimeout(() => reject(new Error("Meta SDK timed out")), 10000);

    window.fbAsyncInit = () => {
      window.clearTimeout(timeout);
      if (!window.FB?.init) {
        reject(new Error("Meta SDK unavailable"));
        return;
      }
      window.FB.init({ appId, xfbml: false, version: graphApiVersion });
      resolve();
    };

    if (existing) return;

    const script = document.createElement("script");
    script.id = "facebook-jssdk";
    script.async = true;
    script.defer = true;
    script.crossOrigin = "anonymous";
    script.src = "https://connect.facebook.net/en_US/sdk.js";
    script.onerror = () => {
      window.clearTimeout(timeout);
      reject(new Error("Meta SDK failed to load"));
    };
    document.head.appendChild(script);
  });
}

export function ChannelsSettingsPanel({
  phones,
  canRead,
  canManage
}: {
  phones: PhoneNumber[];
  canRead: boolean;
  canManage: boolean;
}) {
  const queryClient = useQueryClient();
  const [country, setCountry] = useState("CL");
  const [areaCode, setAreaCode] = useState("");
  const [available, setAvailable] = useState<AvailablePhoneNumber[]>([]);
  const [existingPhone, setExistingPhone] = useState("");
  const [phoneMessage, setPhoneMessage] = useState("");

  const [metaMessage, setMetaMessage] = useState("");
  const [sdkReady, setSdkReady] = useState(false);
  const [wabas, setWabas] = useState<MetaWabaCandidate[]>([]);
  const [selectedWaba, setSelectedWaba] = useState("");
  const [metaPhones, setMetaPhones] = useState<MetaPhoneCandidate[]>([]);
  const [selectedMetaPhone, setSelectedMetaPhone] = useState("");
  const [validatedMetaPhone, setValidatedMetaPhone] = useState(false);
  const [pin, setPin] = useState("");
  const metaActionLock = useRef(false);

  const provisioningStatus = useQuery({
    queryKey: ["settings", "phone-provisioning-status"],
    queryFn: getPhoneProvisioningStatus,
    enabled: canRead,
    retry: false,
    refetchOnWindowFocus: false
  });

  const metaStatus = useQuery({
    queryKey: ["settings", "meta-whatsapp-status"],
    queryFn: getMetaWhatsAppStatus,
    enabled: canRead,
    retry: false,
    refetchOnWindowFocus: false
  });

  const metaBootstrap = useQuery({
    queryKey: ["settings", "meta-whatsapp-bootstrap"],
    queryFn: getMetaWhatsAppBootstrap,
    enabled: canRead,
    retry: false,
    refetchOnWindowFocus: false
  });

  const shouldCheckMetaReadiness = canRead
    && metaStatus.data?.configured === true
    && metaStatus.data?.enabled === false;

  const certification = useQuery({
    queryKey: ["settings", "meta-whatsapp-certification-readiness"],
    queryFn: getMetaCertificationReadiness,
    enabled: shouldCheckMetaReadiness,
    retry: false,
    refetchOnWindowFocus: false
  });

  const deployment = useQuery({
    queryKey: ["settings", "meta-whatsapp-deployment-readiness"],
    queryFn: getMetaDeploymentReadiness,
    enabled: shouldCheckMetaReadiness,
    retry: false,
    refetchOnWindowFocus: false
  });

  const activationReady = metaStatus.data?.configured === true
    && metaStatus.data?.enabled === false
    && certification.data?.ready === true
    && deployment.data?.readyForTenantStaging === true;

  async function refreshChannels() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["settings", "phone-numbers"] }),
      queryClient.invalidateQueries({ queryKey: ["settings", "phone-provisioning-status"] }),
      queryClient.invalidateQueries({ queryKey: ["settings", "meta-whatsapp-status"] }),
      queryClient.invalidateQueries({ queryKey: ["settings", "meta-whatsapp-certification-readiness"] }),
      queryClient.invalidateQueries({ queryKey: ["settings", "meta-whatsapp-deployment-readiness"] })
    ]);
  }

  const searchPhones = useMutation({
    mutationFn: () => searchAvailablePhoneNumbers(country, areaCode),
    onSuccess: numbers => {
      setAvailable(numbers);
      setPhoneMessage(numbers.length ? "" : "No encontramos números con esos filtros.");
    },
    onError: error => setPhoneMessage(errorMessage(error, "No pudimos buscar números disponibles."))
  });

  const provisionPhone = useMutation({
    mutationFn: provisionPhoneNumber,
    onSuccess: async result => {
      setAvailable([]);
      setPhoneMessage((result.phoneNumber || "El número") + " quedó conectado.");
      await refreshChannels();
    },
    onError: error => setPhoneMessage(errorMessage(error, "No pudimos aprovisionar ese número."))
  });

  const connectExisting = useMutation({
    mutationFn: connectExistingPhoneNumber,
    onSuccess: async result => {
      setExistingPhone("");
      setPhoneMessage((result.phoneNumber || "El número") + " quedó conectado.");
      await refreshChannels();
    },
    onError: error => setPhoneMessage(errorMessage(error, "No pudimos conectar ese número."))
  });

  const detach = useMutation({
    mutationFn: detachPhoneNumber,
    onSuccess: async () => {
      setPhoneMessage("Número desvinculado.");
      await refreshChannels();
    },
    onError: error => setPhoneMessage(errorMessage(error, "No pudimos desvincular el número."))
  });

  const activateMeta = useMutation({
    mutationFn: activateMetaWhatsApp,
    onSuccess: async () => {
      setMetaMessage("WhatsApp quedó activado para este negocio.");
      await refreshChannels();
    },
    onError: error => setMetaMessage(errorMessage(error, "No pudimos activar WhatsApp."))
  });

  const deactivateMeta = useMutation({
    mutationFn: deactivateMetaWhatsApp,
    onSuccess: async () => {
      setMetaMessage("WhatsApp quedó desactivado. La configuración se conserva.");
      await refreshChannels();
    },
    onError: error => setMetaMessage(errorMessage(error, "No pudimos desactivar WhatsApp."))
  });

  const connectedPhones = useMemo(() => phones ?? [], [phones]);

  function doProvision(item: AvailablePhoneNumber) {
    if (!canManage || provisionPhone.isPending) return;
    const accepted = window.confirm(
      "Vas a aprovisionar " + item.phoneNumber + " con el proveedor. Puede generar cargos. ¿Confirmas?"
    );
    if (accepted) provisionPhone.mutate(item.phoneNumber);
  }

  function doConnectExisting() {
    const value = existingPhone.trim();
    if (!/^\+[1-9][0-9]{7,14}$/.test(value)) {
      setPhoneMessage("Usa formato internacional E.164, por ejemplo +56912345678.");
      return;
    }
    connectExisting.mutate(value);
  }

  function doDetach(phone: PhoneNumber) {
    if (!phone.id || detach.isPending) return;
    if (!window.confirm("¿Desvincular " + (phone.phoneNumber || "este número") + "?")) return;
    detach.mutate(phone.id);
  }

  async function beginMetaSignup() {
    if (!canManage || metaActionLock.current) return;
    const bootstrap = metaBootstrap.data;
    if (!bootstrap?.available || !bootstrap.appId || !bootstrap.configId || !bootstrap.graphApiVersion) {
      setMetaMessage("La conexión guiada con Meta no está disponible en este entorno.");
      return;
    }

    metaActionLock.current = true;
    setMetaMessage("");
    try {
      if (!sdkReady) {
        await loadFacebookSdk(bootstrap.appId, bootstrap.graphApiVersion);
        setSdkReady(true);
        setMetaMessage("Meta está preparado. Continúa para autorizar tu WhatsApp Business.");
        return;
      }

      if (!window.FB?.login) throw new Error("Meta SDK unavailable");
      setMetaMessage("Abriendo autorización segura de Meta…");

      window.FB.login(async response => {
        const code = response?.authResponse?.code;
        if (!code) {
          setMetaMessage("La autorización no se completó.");
          metaActionLock.current = false;
          return;
        }
        try {
          const handoff = await exchangeMetaAuthorizationCode(code);
          if (!handoff.accepted) throw new Error("Meta authorization rejected");
          setWabas(handoff.wabas ?? []);
          setSelectedWaba("");
          setMetaPhones([]);
          setSelectedMetaPhone("");
          setValidatedMetaPhone(false);
          setPin("");
          setMetaMessage(
            (handoff.wabas?.length ?? 0) > 0
              ? "Autorización completada. Selecciona la cuenta de WhatsApp Business."
              : "Meta no devolvió cuentas de WhatsApp Business disponibles."
          );
        } catch (error) {
          setMetaMessage(errorMessage(error, "No pudimos completar la autorización con Meta."));
        } finally {
          metaActionLock.current = false;
        }
      }, {
        config_id: bootstrap.configId,
        auth_type: "rerequest",
        response_type: "code",
        override_default_response_type: true,
        extras: { setup: {} }
      });
      return;
    } catch (error) {
      setSdkReady(false);
      setMetaMessage(errorMessage(error, "No pudimos preparar la conexión con Meta."));
    } finally {
      if (!window.FB?.login || !sdkReady) metaActionLock.current = false;
    }
  }

  async function loadMetaPhones() {
    if (!selectedWaba || metaActionLock.current) return;
    metaActionLock.current = true;
    setMetaMessage("Consultando números de WhatsApp Business…");
    try {
      const result = await discoverMetaPhoneNumbers(selectedWaba);
      setMetaPhones(result.phoneNumbers ?? []);
      setSelectedMetaPhone("");
      setValidatedMetaPhone(false);
      setPin("");
      setMetaMessage(
        (result.phoneNumbers?.length ?? 0) > 0
          ? "Selecciona el número que quieres conectar."
          : "Esta cuenta no devolvió números disponibles."
      );
    } catch (error) {
      setMetaMessage(errorMessage(error, "No pudimos consultar los números de esta cuenta."));
    } finally {
      metaActionLock.current = false;
    }
  }

  async function validateMetaPhone() {
    if (!selectedWaba || !selectedMetaPhone || metaActionLock.current) return;
    metaActionLock.current = true;
    setMetaMessage("Validando número con Meta…");
    try {
      const result = await validateMetaPhoneNumber(selectedWaba, selectedMetaPhone);
      if (result.state !== "PHONE_NUMBER_VALIDATED") throw new Error("Meta phone validation failed");
      setValidatedMetaPhone(true);
      setPin("");
      setMetaMessage("Número validado. Ingresa un PIN de seis dígitos para finalizar.");
    } catch (error) {
      setValidatedMetaPhone(false);
      setMetaMessage(errorMessage(error, "No pudimos validar ese número."));
    } finally {
      metaActionLock.current = false;
    }
  }

  async function finalizeMetaPhone() {
    if (!selectedWaba || !selectedMetaPhone || !validatedMetaPhone || !/^[0-9]{6}$/.test(pin)) {
      setMetaMessage("El PIN debe tener exactamente seis dígitos.");
      return;
    }
    if (metaActionLock.current) return;
    metaActionLock.current = true;
    setMetaMessage("Finalizando configuración de WhatsApp…");
    const pinForRequest = pin;
    setPin("");
    try {
      const result = await finalizeMetaPhoneNumber(selectedWaba, selectedMetaPhone, pinForRequest);
      if (result.state !== "PHONE_NUMBER_REGISTERED_AND_STAGED" || result.enabled !== false) {
        throw new Error("Meta finalization response mismatch");
      }
      setWabas([]);
      setMetaPhones([]);
      setSelectedWaba("");
      setSelectedMetaPhone("");
      setValidatedMetaPhone(false);
      setMetaMessage("WhatsApp quedó preparado y desactivado hasta completar las validaciones de activación.");
      await refreshChannels();
    } catch (error) {
      setMetaMessage(errorMessage(error, "No pudimos finalizar la configuración de WhatsApp."));
    } finally {
      metaActionLock.current = false;
    }
  }

  function doActivateMeta() {
    if (!activationReady || activateMeta.isPending) return;
    if (!window.confirm("¿Activar WhatsApp para este negocio?")) return;
    activateMeta.mutate();
  }

  function doDeactivateMeta() {
    if (deactivateMeta.isPending) return;
    if (!window.confirm("¿Desactivar WhatsApp para este negocio? La configuración se conservará.")) return;
    deactivateMeta.mutate();
  }

  return (
    <div className={styles.channelWorkspace}>
      <section className={styles.subPanel} aria-label="Telefonía">
        <div className={styles.subPanelHeading}>
          <div>
            <h3>Telefonía</h3>
            <span>Conecta un número existente o busca uno nuevo. Ninguna compra ocurre al abrir esta pantalla.</span>
          </div>
          <Phone size={18} aria-hidden="true" />
        </div>

        <div className={styles.compactList}>
          {connectedPhones.length === 0 && (
            <div className={styles.mutedState}>No hay números conectados.</div>
          )}
          {connectedPhones.map(phone => (
            <article className={styles.compactRow} key={phone.id || phone.phoneNumber}>
              <div>
                <strong>{phone.phoneNumber || "Número"}</strong>
                <span>{phone.provider || "Proveedor"} · {phone.active ? "Activo" : "Inactivo"}</span>
              </div>
              <div className={styles.rowActions}>
                <span className={phone.whatsappEnabled ? styles.goodPill : styles.mutedPill}>
                  {phone.whatsappEnabled ? "WhatsApp" : "Voz"}
                </span>
                {canManage && phone.id && (
                  <button
                    className={styles.iconDangerStatic}
                    type="button"
                    aria-label={"Desvincular " + (phone.phoneNumber || "número")}
                    onClick={() => doDetach(phone)}
                  >
                    <Trash2 size={16} aria-hidden="true" />
                  </button>
                )}
              </div>
            </article>
          ))}
        </div>

        {canManage && (
          <>
            <div className={styles.channelActionGrid}>
              <div className={styles.channelActionCard}>
                <strong>Conectar número existente</strong>
                <span>Usa formato internacional E.164.</span>
                <input
                  aria-label="Número existente"
                  placeholder="+56912345678"
                  value={existingPhone}
                  onChange={event => setExistingPhone(event.target.value)}
                />
                <button
                  className="button secondary"
                  type="button"
                  disabled={connectExisting.isPending}
                  onClick={doConnectExisting}
                >
                  <Link2 size={15} aria-hidden="true" />
                  {connectExisting.isPending ? "Conectando…" : "Conectar número"}
                </button>
              </div>

              <div className={styles.channelActionCard}>
                <strong>Buscar número nuevo</strong>
                <span>
                  {provisioningStatus.data?.purchaseAvailable
                    ? "La búsqueda es gratuita. Comprar requiere confirmación explícita."
                    : provisioningStatus.data?.message || "El aprovisionamiento no está disponible."}
                </span>
                <div className={styles.phoneSearchFields}>
                  <input
                    aria-label="País ISO para búsqueda"
                    maxLength={2}
                    value={country}
                    onChange={event => setCountry(event.target.value.toUpperCase())}
                  />
                  <input
                    aria-label="Código de área para búsqueda"
                    placeholder="2"
                    value={areaCode}
                    onChange={event => setAreaCode(event.target.value)}
                  />
                </div>
                <button
                  className="button secondary"
                  type="button"
                  disabled={!provisioningStatus.data?.purchaseAvailable || searchPhones.isPending}
                  onClick={() => searchPhones.mutate()}
                >
                  <Search size={15} aria-hidden="true" />
                  {searchPhones.isPending ? "Buscando…" : "Buscar números"}
                </button>
              </div>
            </div>

            {available.length > 0 && (
              <div className={styles.compactList}>
                {available.map(item => (
                  <article className={styles.compactRow} key={item.phoneNumber}>
                    <div>
                      <strong>{item.phoneNumber}</strong>
                      <span>{[item.locality, item.region, item.isoCountry].filter(Boolean).join(" · ") || "Número disponible"}</span>
                    </div>
                    <button
                      className="button primary"
                      type="button"
                      disabled={provisionPhone.isPending}
                      onClick={() => doProvision(item)}
                    >
                      Aprovisionar
                    </button>
                  </article>
                ))}
              </div>
            )}
          </>
        )}

        {phoneMessage && <div className={styles.inlineMessage} role="status">{phoneMessage}</div>}
      </section>

      <section className={styles.subPanel} aria-label="WhatsApp Business">
        <div className={styles.subPanelHeading}>
          <div>
            <h3>WhatsApp Business</h3>
            <span>La conexión y activación requieren acciones explícitas. Las credenciales nunca se muestran.</span>
          </div>
          <ShieldCheck size={18} aria-hidden="true" />
        </div>

        {!canRead && (
          <div className={styles.mutedState}>Tu rol no puede consultar la configuración de WhatsApp.</div>
        )}

        {canRead && (
          <>
            <div className={styles.channelStatusCard}>
              <div>
                <strong>{metaStatus.data?.phoneNumber || "WhatsApp Business"}</strong>
                <span>
                  {metaStatus.isPending
                    ? "Comprobando estado…"
                    : metaStatus.data?.configured
                      ? metaStatus.data.enabled ? "Configurado y activo" : "Configurado, pendiente de activación"
                      : "No configurado"}
                </span>
              </div>
              <span className={metaStatus.data?.enabled ? styles.goodPill : styles.mutedPill}>
                {metaStatus.data?.enabled ? "Activo" : "Inactivo"}
              </span>
            </div>

            {canManage && !metaStatus.data?.configured && (
              <div className={styles.metaFlow}>
                <button
                  className="button secondary"
                  type="button"
                  onClick={beginMetaSignup}
                >
                  {sdkReady ? "Continuar con Meta" : "Conectar WhatsApp"}
                </button>

                {wabas.length > 0 && (
                  <div className={styles.choiceGrid}>
                    {wabas.map(waba => (
                      <button
                        type="button"
                        className={selectedWaba === waba.id ? styles.choiceCardActive : styles.choiceCard}
                        key={waba.id}
                        onClick={() => {
                          setSelectedWaba(waba.id);
                          setMetaPhones([]);
                          setSelectedMetaPhone("");
                          setValidatedMetaPhone(false);
                        }}
                      >
                        <strong>{waba.name || "Cuenta de WhatsApp Business"}</strong>
                        <span>{waba.id}</span>
                      </button>
                    ))}
                    {selectedWaba && (
                      <button className="button secondary" type="button" onClick={loadMetaPhones}>
                        Continuar con esta cuenta
                      </button>
                    )}
                  </div>
                )}

                {metaPhones.length > 0 && (
                  <div className={styles.choiceGrid}>
                    {metaPhones.map(phone => (
                      <button
                        type="button"
                        className={selectedMetaPhone === phone.id ? styles.choiceCardActive : styles.choiceCard}
                        key={phone.id}
                        onClick={() => {
                          setSelectedMetaPhone(phone.id);
                          setValidatedMetaPhone(false);
                          setPin("");
                        }}
                      >
                        <strong>{phone.displayPhoneNumber || "Número de WhatsApp"}</strong>
                        <span>{phone.verifiedName || phone.id}</span>
                      </button>
                    ))}
                    {selectedMetaPhone && !validatedMetaPhone && (
                      <button className="button secondary" type="button" onClick={validateMetaPhone}>
                        Validar número
                      </button>
                    )}
                  </div>
                )}

                {validatedMetaPhone && (
                  <div className={styles.pinBox}>
                    <label className={styles.field}>
                      <span>PIN de registro de Meta</span>
                      <input
                        aria-label="PIN de registro de Meta"
                        type="password"
                        inputMode="numeric"
                        autoComplete="off"
                        maxLength={6}
                        value={pin}
                        onChange={event => setPin(event.target.value.replace(/\D/g, "").slice(0, 6))}
                      />
                    </label>
                    <button
                      className="button primary"
                      type="button"
                      disabled={!/^[0-9]{6}$/.test(pin)}
                      onClick={finalizeMetaPhone}
                    >
                      Finalizar conexión
                    </button>
                  </div>
                )}
              </div>
            )}

            {canManage && metaStatus.data?.configured && metaStatus.data.enabled === false && (
              <div className={styles.activationBox}>
                <div>
                  <strong>{activationReady ? "Activación disponible" : "Activación todavía bloqueada"}</strong>
                  <span>
                    {activationReady
                      ? "Preflight y staging están listos."
                      : "RecepVoz no activará WhatsApp hasta que las validaciones técnicas estén listas."}
                  </span>
                </div>
                <button
                  className="button primary"
                  type="button"
                  disabled={!activationReady || activateMeta.isPending}
                  onClick={doActivateMeta}
                >
                  <CheckCircle2 size={15} aria-hidden="true" />
                  Activar WhatsApp
                </button>
              </div>
            )}

            {canManage && metaStatus.data?.enabled === true && (
              <button
                className="button secondary"
                type="button"
                disabled={deactivateMeta.isPending}
                onClick={doDeactivateMeta}
              >
                Desactivar WhatsApp
              </button>
            )}
          </>
        )}

        {metaMessage && <div className={styles.inlineMessage} role="status">{metaMessage}</div>}
      </section>
    </div>
  );
}
