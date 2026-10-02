import type { PropsWithChildren } from "react";
import {
  CalendarDays,
  LayoutDashboard,
  PackageSearch,
  Settings,
  Users,
  WalletCards
} from "lucide-react";
import { clearAccessToken } from "../../api/client";
import styles from "./AppShell.module.css";

const navigation = [
  { href: "/", label: "Inicio", icon: LayoutDashboard },
  { href: "/app/agenda", label: "Agenda", icon: CalendarDays },
  { href: "/#customers", label: "Clientes", icon: Users },
  { href: "/app/inventory", label: "Inventario", icon: PackageSearch },
  { href: "/app/settings", label: "Configuración", icon: Settings }
];

export function AppShell({ children }: PropsWithChildren) {
  const currentPath = window.location.pathname;

  function logout() {
    clearAccessToken();
    window.location.assign("/");
  }

  return (
    <div className={styles.layout} data-react-app="recepvoz">
      <aside className={styles.sidebar} aria-label="Navegación de RecepVoz">
        <a className={styles.brand} href="/" aria-label="RecepVoz, ir al inicio">
          <span className={styles.brandMark} aria-hidden="true">R</span>
          <span>RECEPVOZ</span>
        </a>

        <nav className={styles.nav} aria-label="Navegación principal">
          {navigation.map(({ href, label, icon: Icon }) => (
            <a
              key={label}
              href={href}
              data-current={currentPath === href ? "true" : undefined}
              aria-current={currentPath === href ? "page" : undefined}
            >
              <Icon size={18} aria-hidden="true" />
              <span>{label}</span>
            </a>
          ))}
          <a
            className={styles.accountLink}
            href="/app/plan"
            data-current={currentPath === "/app/plan" ? "true" : undefined}
            aria-current={currentPath === "/app/plan" ? "page" : undefined}
          >
            <WalletCards size={18} aria-hidden="true" />
            <span>Plan y consumo</span>
          </a>
        </nav>
      </aside>

      <div className={styles.main}>
        <header className="rv-frame-topbar">
          <div className={styles.topbarInner}>
            <button className={`button ghost ${styles.logout}`} type="button" onClick={logout}>
              Salir
            </button>
          </div>
        </header>
        {children}
      </div>
    </div>
  );
}
