import type { PropsWithChildren } from "react";
import { useLocation } from "react-router-dom";
import {
  CalendarDays,
  LayoutDashboard,
  PackageSearch,
  Settings,
  ShoppingBag,
  WalletCards
} from "lucide-react";
import { clearAccessToken } from "../../api/client";
import styles from "./AppShell.module.css";

const navigation = [
  { href: "/app", label: "Inicio", icon: LayoutDashboard },
  { href: "/app/agenda", label: "Agenda", icon: CalendarDays },
  { href: "/app/orders", label: "Operaciones", icon: ShoppingBag },
  { href: "/app/inventory", label: "Inventario", icon: PackageSearch },
  { href: "/app/settings", label: "Configuración", icon: Settings }
];

export function AppShell({ children }: PropsWithChildren) {
  const location = useLocation();

  function logout() {
    clearAccessToken();
    window.location.assign("/");
  }

  function isCurrentNavigation(href: string): boolean {
    if (href === "/app") {
      return location.pathname === "/app" || location.pathname === "/app/";
    }
    return location.pathname === href || location.pathname.startsWith(`${href}/`);
  }

  const planIsCurrent =
    location.pathname === "/app/plan" || location.pathname.startsWith("/app/plan/");

  return (
    <div className={styles.layout} data-react-app="recepvoz">
      <aside className={styles.sidebar} aria-label="Navegación de RecepVoz">
        <a className={styles.brand} href="/app" aria-label="RecepVoz, ir al inicio">
          <span className={styles.brandMark} aria-hidden="true">R</span>
          <span>RECEPVOZ</span>
        </a>

        <nav className={styles.nav} aria-label="Navegación principal">
          {navigation.map(({ href, label, icon: Icon }) => {
            const current = isCurrentNavigation(href);
            return (
              <a
                key={label}
                href={href}
                data-current={current ? "true" : undefined}
                aria-current={current ? "page" : undefined}
              >
                <Icon size={18} aria-hidden="true" />
                <span>{label}</span>
              </a>
            );
          })}
          <a
            className={styles.accountLink}
            href="/app/plan"
            data-current={planIsCurrent ? "true" : undefined}
            aria-current={planIsCurrent ? "page" : undefined}
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
