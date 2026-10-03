import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CreditCard, ShieldCheck } from "lucide-react";
import {
  disableManagedPaymentSandbox,
  enableManagedPaymentSandbox,
  getManagedPaymentSandbox
} from "../../features/settings/api";
import styles from "./SettingsPage.module.css";

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}

export function IntegrationsSettingsPanel({
  canRead,
  canManage
}: {
  canRead: boolean;
  canManage: boolean;
}) {
  const queryClient = useQueryClient();

  const sandbox = useQuery({
    queryKey: ["settings", "managed-payment-sandbox"],
    queryFn: getManagedPaymentSandbox,
    enabled: canRead,
    retry: false,
    refetchOnWindowFocus: false
  });

  const enable = useMutation({
    mutationFn: enableManagedPaymentSandbox,
    onSuccess: status => {
      queryClient.setQueryData(["settings", "managed-payment-sandbox"], status);
    }
  });

  const disable = useMutation({
    mutationFn: disableManagedPaymentSandbox,
    onSuccess: status => {
      queryClient.setQueryData(["settings", "managed-payment-sandbox"], status);
    }
  });

  const status = sandbox.data;
  const busy = enable.isPending || disable.isPending;
  const mutationError = enable.error || disable.error;

  function activate() {
    if (!canManage || busy) return;
    if (!window.confirm("¿Activar Mercado Pago Sandbox para realizar pagos de prueba? No usa dinero real.")) return;
    enable.mutate();
  }

  function deactivate() {
    if (!canManage || busy) return;
    if (!window.confirm("¿Desactivar los pagos de prueba? La configuración administrada se conservará.")) return;
    disable.mutate();
  }

  if (!canRead) {
    return (
      <div className={styles.authorityNote}>
        <ShieldCheck size={18} aria-hidden="true" />
        <div>
          <strong>Integraciones protegidas por rol</strong>
          <span>Tu rol no puede consultar la configuración comercial del negocio.</span>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.integrationGrid}>
      <section className={styles.subPanel} aria-label="Mercado Pago Sandbox">
        <div className={styles.subPanelHeading}>
          <div>
            <h3>Pagos de prueba</h3>
            <span>
              Mercado Pago Sandbox administrado por RecepVoz. No necesitas pegar tokens ni secretos.
            </span>
          </div>
          <CreditCard size={18} aria-hidden="true" />
        </div>

        {sandbox.isPending && <div className={styles.mutedState}>Comprobando integración…</div>}

        {sandbox.isError && (
          <div className={styles.inlineError}>
            {errorMessage(sandbox.error, "No pudimos comprobar los pagos de prueba.")}
          </div>
        )}

        {status && (
          <div className={styles.integrationStatusCard}>
            <div>
              <strong>
                {status.blockedByCustomConfiguration
                  ? "Configuración avanzada detectada"
                  : !status.available
                    ? "Sandbox no disponible"
                    : status.enabled
                      ? "Mercado Pago Sandbox activo"
                      : status.configured
                        ? "Sandbox preparado"
                        : "Pagos de prueba disponibles"}
              </strong>
              <span>
                {status.blockedByCustomConfiguration
                  ? "RecepVoz no modificará una configuración personalizada desde este panel."
                  : !status.available
                    ? "La plataforma todavía no tiene un sandbox administrado disponible."
                    : status.enabled
                      ? "Puedes probar el flujo de pago sin mover dinero real."
                      : status.configured
                        ? "La configuración está guardada, pero los pagos de prueba están desactivados."
                        : "Puedes habilitar el sandbox sin ingresar credenciales."}
              </span>
            </div>
            <span className={status.enabled ? styles.goodPill : styles.mutedPill}>
              {status.enabled ? "Activo" : "Inactivo"}
            </span>
          </div>
        )}

        {canManage && status?.available && !status.blockedByCustomConfiguration && (
          <div className={styles.integrationActions}>
            {!status.enabled ? (
              <button
                className="button primary"
                type="button"
                disabled={busy}
                onClick={activate}
              >
                {enable.isPending ? "Activando…" : "Activar pagos de prueba"}
              </button>
            ) : (
              <button
                className="button secondary"
                type="button"
                disabled={busy}
                onClick={deactivate}
              >
                {disable.isPending ? "Desactivando…" : "Desactivar pagos de prueba"}
              </button>
            )}
          </div>
        )}

        {mutationError && (
          <div className={styles.inlineError} role="alert">
            {errorMessage(mutationError, "No pudimos actualizar los pagos de prueba.")}
          </div>
        )}
      </section>

      <div className={styles.authorityNote}>
        <ShieldCheck size={18} aria-hidden="true" />
        <div>
          <strong>Las herramientas técnicas no viven aquí</strong>
          <span>
            Certificación, diagnósticos y controles de piloto son herramientas internas. Configuración solo muestra
            acciones que pertenecen al negocio.
          </span>
        </div>
      </div>
    </div>
  );
}
