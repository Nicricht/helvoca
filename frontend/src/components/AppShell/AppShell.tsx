import type { PropsWithChildren } from "react";
import { useLocation } from "react-router-dom";
import {
  AudioLines,
  CalendarDays,
  ChevronRight,
  Crown,
  LayoutDashboard,
  LogOut,
  PackageSearch,
  Settings,
  ShoppingBag
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
          <span className={styles.brandMark} aria-hidden="true">
            <AudioLines size={25} />
          </span>
          <span className={styles.brandCopy}>
            <strong>Recep<span>Voz</span></strong>
            <small>Tu recepcionista IA</small>
          </span>
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
                <Icon size={19} aria-hidden="true" />
                <span>{label}</span>
              </a>
            );
          })}
        </nav>

        <div className={styles.sidebarMeta}>
          <a
            className={styles.planCard}
            href="/app/plan"
            data-current={planIsCurrent ? "true" : undefined}
            aria-current={planIsCurrent ? "page" : undefined}
            aria-label="Plan y consumo"
          >
            <span className={styles.planIcon} aria-hidden="true"><Crown size={18} /></span>
            <span className={styles.planCopy}>
              <strong>Plan y consumo</strong>
              <small>Uso, minutos y facturación</small>
            </span>
            <ChevronRight size={16} aria-hidden="true" />
          </a>

          <button className={styles.accountCard} type="button" onClick={logout} aria-label="Salir">
            <span className={styles.avatar} aria-hidden="true">R</span>
            <span className={styles.accountCopy}>
              <strong>Mi cuenta</strong>
              <small>Cerrar sesión</small>
            </span>
            <LogOut size={16} aria-hidden="true" />
          </button>
        </div>
      </aside>

      <div className={styles.main}>
        <header className="rv-frame-topbar">
          <div className={styles.topbarInner} aria-hidden="true" />
        </header>
        {children}
      </div>
    </div>
  );
}
