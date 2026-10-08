import { useState } from "react";
import { Download } from "lucide-react";
import { exportCustomers } from "./api";
import styles from "./CustomerExportTools.module.css";

type Viewer = { roles?: string[]; permissions?: string[] };

export function CustomerExportTools({ user }: { user?: Viewer }) {
  const canExport = Array.isArray(user?.permissions)
    ? user.permissions.includes("CUSTOMERS_EXPORT") || user.permissions.includes("PERM_CUSTOMERS_EXPORT")
    : (user?.roles ?? []).some(role => role === "BUSINESS_ADMIN" || role === "BUSINESS_OWNER");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");

  if (!canExport) return null;

  async function download(format: "csv" | "xlsx") {
    if (busy) return;
    setBusy(true);
    setMessage("");
    try {
      await exportCustomers(format);
      setMessage("Exportación preparada.");
    } catch (e) {
      setMessage(e instanceof Error ? e.message : "No pudimos preparar la exportación.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className={styles.panel} aria-label="Exportación de clientes">
      <div>
        <strong>Exportación de clientes</strong>
        <p>Herramienta de clientes autorizada para tu rol.</p>
      </div>
      <div className={styles.actions}>
        <button type="button" disabled={busy} onClick={() => { void download("csv"); }}>
          <Download size={15} aria-hidden="true" /> Exportar CSV
        </button>
        <button type="button" disabled={busy} onClick={() => { void download("xlsx"); }}>
          <Download size={15} aria-hidden="true" /> Exportar XLSX
        </button>
      </div>
      {message && <p role="status" className={styles.feedback}>{message}</p>}
    </section>
  );
}
