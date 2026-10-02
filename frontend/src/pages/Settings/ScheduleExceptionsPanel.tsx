import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarClock, Pencil, ShieldCheck, Trash2 } from "lucide-react";
import { useMemo, useState } from "react";
import {
  deleteScheduleException,
  getScheduleExceptions,
  saveScheduleException,
  type ScheduleException
} from "../../features/settings/api";
import styles from "./SettingsPage.module.css";

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}

function shortTime(value?: string | null) {
  return value ? String(value).slice(0, 5) : "";
}

export function ScheduleExceptionsPanel({
  canRead,
  canManage
}: {
  canRead: boolean;
  canManage: boolean;
}) {
  const queryClient = useQueryClient();
  const [date, setDate] = useState("");
  const [kind, setKind] = useState<"closed" | "special">("closed");
  const [openTime, setOpenTime] = useState("");
  const [closeTime, setCloseTime] = useState("");
  const [reason, setReason] = useState("");
  const [message, setMessage] = useState("");

  const exceptions = useQuery({
    queryKey: ["settings", "schedule-exceptions"],
    queryFn: getScheduleExceptions,
    enabled: canRead,
    retry: false,
    refetchOnWindowFocus: false
  });

  const sorted = useMemo(
    () => [...(exceptions.data ?? [])].sort((a, b) => a.exceptionDate.localeCompare(b.exceptionDate)),
    [exceptions.data]
  );

  const saveMutation = useMutation({
    mutationFn: ({ exceptionDate, input }: {
      exceptionDate: string;
      input: {
        closed: boolean;
        openTime: string | null;
        closeTime: string | null;
        reason: string | null;
      };
    }) => saveScheduleException(exceptionDate, input),
    onSuccess: async () => {
      setMessage("Día especial guardado.");
      resetFields();
      await queryClient.invalidateQueries({ queryKey: ["settings", "schedule-exceptions"] });
    },
    onError: error => setMessage(errorMessage(error, "No pudimos guardar el día especial."))
  });

  const deleteMutation = useMutation({
    mutationFn: deleteScheduleException,
    onSuccess: async () => {
      setMessage("Día especial eliminado.");
      await queryClient.invalidateQueries({ queryKey: ["settings", "schedule-exceptions"] });
    },
    onError: error => setMessage(errorMessage(error, "No pudimos eliminar el día especial."))
  });

  function resetFields() {
    setDate("");
    setKind("closed");
    setOpenTime("");
    setCloseTime("");
    setReason("");
  }

  function edit(item: ScheduleException) {
    if (!canManage) return;
    setDate(item.exceptionDate);
    setKind(item.closed ? "closed" : "special");
    setOpenTime(shortTime(item.openTime));
    setCloseTime(shortTime(item.closeTime));
    setReason(item.reason ?? "");
    setMessage("");
  }

  function save() {
    if (!canManage || saveMutation.isPending) return;
    setMessage("");
    if (!date) {
      setMessage("Selecciona una fecha.");
      return;
    }
    const closed = kind === "closed";
    if (!closed && (!openTime || !closeTime || openTime >= closeTime)) {
      setMessage("Define un horario especial válido.");
      return;
    }
    saveMutation.mutate({
      exceptionDate: date,
      input: {
        closed,
        openTime: closed ? null : openTime,
        closeTime: closed ? null : closeTime,
        reason: reason.trim() || null
      }
    });
  }

  function remove(item: ScheduleException) {
    if (!canManage || deleteMutation.isPending) return;
    if (!window.confirm("¿Eliminar el día especial del " + item.exceptionDate + "?")) return;
    deleteMutation.mutate(item.exceptionDate);
  }

  if (!canRead) {
    return (
      <div className={styles.authorityNote}>
        <ShieldCheck size={18} aria-hidden="true" />
        <div>
          <strong>Días especiales protegidos por rol</strong>
          <span>Solo administradores y operadores autorizados pueden consultar esta configuración.</span>
        </div>
      </div>
    );
  }

  return (
    <section className={styles.subPanel} aria-label="Días especiales">
      <div className={styles.subPanelHeading}>
        <div>
          <h3>Días especiales</h3>
          <span>Cierra una fecha concreta o reemplaza el horario normal solo para ese día.</span>
        </div>
        <CalendarClock size={18} aria-hidden="true" />
      </div>

      {canManage ? (
        <div className={styles.exceptionEditor}>
          <label className={styles.field}>
            <span>Fecha</span>
            <input
              aria-label="Fecha especial"
              type="date"
              value={date}
              onChange={event => setDate(event.target.value)}
            />
          </label>

          <label className={styles.field}>
            <span>Tipo</span>
            <select
              aria-label="Tipo de día especial"
              value={kind}
              onChange={event => setKind(event.target.value as "closed" | "special")}
            >
              <option value="closed">Cerrado</option>
              <option value="special">Horario especial</option>
            </select>
          </label>

          {kind === "special" && (
            <>
              <label className={styles.field}>
                <span>Apertura</span>
                <input
                  aria-label="Apertura especial"
                  type="time"
                  value={openTime}
                  onChange={event => setOpenTime(event.target.value)}
                />
              </label>
              <label className={styles.field}>
                <span>Cierre</span>
                <input
                  aria-label="Cierre especial"
                  type="time"
                  value={closeTime}
                  onChange={event => setCloseTime(event.target.value)}
                />
              </label>
            </>
          )}

          <label className={styles.field + " " + styles.exceptionReason}>
            <span>Motivo opcional</span>
            <input
              aria-label="Motivo del día especial"
              maxLength={200}
              value={reason}
              onChange={event => setReason(event.target.value)}
            />
          </label>

          <div className={styles.exceptionActions}>
            {(date || reason || openTime || closeTime || kind !== "closed") && (
              <button className="button ghost" type="button" onClick={resetFields}>
                Limpiar
              </button>
            )}
            <button
              className="button primary"
              type="button"
              disabled={saveMutation.isPending}
              onClick={save}
            >
              {saveMutation.isPending ? "Guardando…" : "Guardar día especial"}
            </button>
          </div>
        </div>
      ) : (
        <div className={styles.mutedState}>
          Puedes revisar estos días, pero solo un administrador puede modificarlos.
        </div>
      )}

      {message && <div className={styles.inlineMessage} role="status">{message}</div>}
      {exceptions.isPending && <div className={styles.mutedState}>Cargando días especiales…</div>}
      {exceptions.isError && <div className={styles.inlineError}>No pudimos cargar los días especiales.</div>}
      {!exceptions.isPending && !exceptions.isError && sorted.length === 0 && (
        <div className={styles.mutedState}>No hay días especiales configurados.</div>
      )}

      <div className={styles.compactList}>
        {sorted.map(item => (
          <article className={styles.compactRow} key={item.exceptionDate}>
            <div>
              <strong>{item.exceptionDate}</strong>
              <span>
                {item.closed
                  ? "Cerrado todo el día"
                  : "Horario especial · " + shortTime(item.openTime) + "–" + shortTime(item.closeTime)}
                {item.reason ? " · " + item.reason : ""}
              </span>
            </div>
            {canManage && (
              <div className={styles.rowActions}>
                <button
                  className={styles.iconActionStatic}
                  type="button"
                  aria-label={"Editar día especial " + item.exceptionDate}
                  onClick={() => edit(item)}
                >
                  <Pencil size={16} aria-hidden="true" />
                </button>
                <button
                  className={styles.iconDangerStatic}
                  type="button"
                  aria-label={"Eliminar día especial " + item.exceptionDate}
                  disabled={deleteMutation.isPending}
                  onClick={() => remove(item)}
                >
                  <Trash2 size={16} aria-hidden="true" />
                </button>
              </div>
            )}
          </article>
        ))}
      </div>
    </section>
  );
}
