import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Copy, ShieldCheck, UserPlus, XCircle } from "lucide-react";
import { useMemo, useState } from "react";
import {
  createTeamInvitation,
  getTeamInvitations,
  getTeamMembers,
  revokeTeamInvitation,
  type TeamInvitation
} from "../../features/settings/api";
import styles from "./SettingsPage.module.css";

const roleOptions = [
  ["BUSINESS_ADMIN", "Administrador"],
  ["MANAGER", "Encargado"],
  ["RECEPTION", "Recepción / Caja"],
  ["STAFF", "Personal"],
  ["KITCHEN", "Preparación / Cocina"],
  ["DISPATCH", "Despacho"],
  ["PROFESSIONAL", "Profesional"],
  ["WAREHOUSE", "Bodega / Inventario"],
  ["SALES", "Ventas"],
  ["OPERATOR", "Operador"]
] as const;

const roleLabels = Object.fromEntries(roleOptions) as Record<string, string>;

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}

export function TeamSettingsPanel({ canManage }: { canManage: boolean }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [role, setRole] = useState("STAFF");
  const [inviteUrl, setInviteUrl] = useState("");
  const [message, setMessage] = useState("");

  const members = useQuery({
    queryKey: ["settings", "team-members"],
    queryFn: getTeamMembers,
    enabled: canManage,
    retry: false,
    refetchOnWindowFocus: false
  });

  const invitations = useQuery({
    queryKey: ["settings", "team-invitations"],
    queryFn: getTeamInvitations,
    enabled: canManage,
    retry: false,
    refetchOnWindowFocus: false
  });

  const createInvitation = useMutation({
    mutationFn: createTeamInvitation,
    onSuccess: async invitation => {
      setInviteUrl(
        invitation.invitePath
          ? new URL(invitation.invitePath, window.location.origin).href
          : ""
      );
      setName("");
      setEmail("");
      setRole("STAFF");
      setMessage("Invitación creada. Comparte el enlace solamente con la persona invitada.");
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["settings", "team-members"] }),
        queryClient.invalidateQueries({ queryKey: ["settings", "team-invitations"] })
      ]);
    },
    onError: error => setMessage(errorMessage(error, "No pudimos crear la invitación."))
  });

  const revokeInvitation = useMutation({
    mutationFn: revokeTeamInvitation,
    onSuccess: async () => {
      setMessage("Invitación revocada.");
      await queryClient.invalidateQueries({ queryKey: ["settings", "team-invitations"] });
    },
    onError: error => setMessage(errorMessage(error, "No pudimos revocar la invitación."))
  });

  const activeMembers = useMemo(() => members.data ?? [], [members.data]);
  const inviteList = useMemo(() => invitations.data ?? [], [invitations.data]);

  function submitInvitation() {
    setMessage("");
    const cleanName = name.trim();
    const cleanEmail = email.trim();
    if (!cleanName || !cleanEmail) {
      setMessage("Completa nombre y correo antes de crear la invitación.");
      return;
    }
    createInvitation.mutate({ name: cleanName, email: cleanEmail, role });
  }

  function revoke(item: TeamInvitation) {
    if (item.status !== "PENDING" || revokeInvitation.isPending) return;
    if (!window.confirm("¿Revocar la invitación de " + item.email + "?")) return;
    revokeInvitation.mutate(item.id);
  }

  async function copyInvite() {
    if (!inviteUrl) return;
    try {
      await navigator.clipboard.writeText(inviteUrl);
      setMessage("Enlace copiado.");
    } catch {
      setMessage("No pudimos copiar automáticamente. Selecciona el enlace y cópialo manualmente.");
    }
  }

  if (!canManage) {
    return (
      <div className={styles.authorityNote}>
        <ShieldCheck size={18} aria-hidden="true" />
        <div>
          <strong>Equipo protegido por permisos</strong>
          <span>Solo una persona con permiso para administrar el equipo puede ver o crear invitaciones.</span>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.teamGrid}>
      <section className={styles.subPanel} aria-label="Invitar persona">
        <div className={styles.subPanelHeading}>
          <div>
            <strong>Invitar persona</strong>
            <span>La persona crea su propia contraseña. Tú nunca la ves.</span>
          </div>
          <UserPlus size={18} aria-hidden="true" />
        </div>

        <div className={styles.formGrid}>
          <label className={styles.field}>
            <span>Nombre</span>
            <input
              aria-label="Nombre de la persona"
              maxLength={150}
              value={name}
              onChange={event => setName(event.target.value)}
            />
          </label>
          <label className={styles.field}>
            <span>Correo</span>
            <input
              aria-label="Correo de la persona"
              type="email"
              maxLength={180}
              value={email}
              onChange={event => setEmail(event.target.value)}
            />
          </label>
          <label className={`${styles.field} ${styles.fieldWide}`}>
            <span>Rol</span>
            <select
              aria-label="Rol de la persona"
              value={role}
              onChange={event => setRole(event.target.value)}
            >
              {roleOptions.map(([value, label]) => (
                <option value={value} key={value}>{label}</option>
              ))}
            </select>
          </label>
        </div>

        <button
          className="button primary"
          type="button"
          disabled={createInvitation.isPending}
          onClick={submitInvitation}
        >
          {createInvitation.isPending ? "Creando…" : "Crear invitación"}
        </button>

        {inviteUrl && (
          <div className={styles.inviteLinkBox}>
            <label className={styles.field}>
              <span>Enlace de invitación</span>
              <input aria-label="Enlace de invitación" readOnly value={inviteUrl} />
            </label>
            <button className="button secondary" type="button" onClick={copyInvite}>
              <Copy size={15} aria-hidden="true" />
              Copiar
            </button>
          </div>
        )}

        {message && <div className={styles.inlineMessage} role="status">{message}</div>}
      </section>

      <section className={styles.subPanel} aria-label="Miembros del equipo">
        <div className={styles.subPanelHeading}>
          <div>
            <strong>Miembros</strong>
            <span>Personas que ya tienen acceso al negocio.</span>
          </div>
        </div>

        {members.isPending && <div className={styles.mutedState}>Cargando miembros…</div>}
        {members.isError && <div className={styles.inlineError}>No pudimos cargar los miembros.</div>}
        {!members.isPending && !members.isError && activeMembers.length === 0 && (
          <div className={styles.mutedState}>No hay miembros activos.</div>
        )}
        <div className={styles.compactList}>
          {activeMembers.map(member => (
            <article className={styles.compactRow} key={member.id || member.email}>
              <div>
                <strong>{member.name || member.email || "Miembro"}</strong>
                <span>
                  {member.email || ""} · {(member.roles ?? []).map(value => roleLabels[value] || value).join(", ") || "Sin rol"}
                </span>
              </div>
              <span className={member.active === false ? styles.mutedPill : styles.goodPill}>
                {member.active === false ? "Inactivo" : "Activo"}
              </span>
            </article>
          ))}
        </div>
      </section>

      <section className={`${styles.subPanel} ${styles.teamInvitations}`} aria-label="Invitaciones del equipo">
        <div className={styles.subPanelHeading}>
          <div>
            <strong>Invitaciones</strong>
            <span>Puedes revocar una invitación mientras siga pendiente.</span>
          </div>
        </div>

        {invitations.isPending && <div className={styles.mutedState}>Cargando invitaciones…</div>}
        {invitations.isError && <div className={styles.inlineError}>No pudimos cargar las invitaciones.</div>}
        {!invitations.isPending && !invitations.isError && inviteList.length === 0 && (
          <div className={styles.mutedState}>No hay invitaciones.</div>
        )}
        <div className={styles.compactList}>
          {inviteList.map(item => (
            <article className={styles.compactRow} key={item.id}>
              <div>
                <strong>{item.name || item.email}</strong>
                <span>{item.email} · {roleLabels[item.role] || item.role}</span>
              </div>
              <div className={styles.rowActions}>
                <span className={item.status === "PENDING" ? styles.warningPill : styles.mutedPill}>
                  {item.status || "Sin estado"}
                </span>
                {item.status === "PENDING" && (
                  <button
                    className={styles.iconDangerStatic}
                    type="button"
                    aria-label={"Revocar invitación de " + item.email}
                    disabled={revokeInvitation.isPending}
                    onClick={() => revoke(item)}
                  >
                    <XCircle size={16} aria-hidden="true" />
                  </button>
                )}
              </div>
            </article>
          ))}
        </div>
      </section>
    </div>
  );
}
