import type { PropsWithChildren } from "react";
import { useQuery } from "@tanstack/react-query";
import { useLocation } from "react-router-dom";
import {
  AudioLines,
  CalendarDays,
  ChevronRight,
  CircleUserRound,
  Crown,
  LayoutDashboard,
  LogOut,
  PackageSearch,
  Settings,
  ShoppingBag,
  Sparkles,
  WalletCards
} from "lucide-react";
import { clearAccessToken } from "../../api/client";
import { getCurrentUser } from "../../features/dashboard/api";
import { getSubscription } from "../../features/billing/api";
import styles from "./AppShell.module.css";

const navigation = [
  { href: "/app", label: "Inicio", icon: LayoutDashboard },
  { href: "/app/agenda", label: "Agenda", icon: CalendarDays },
  { href: "/app/orders", label: "Operaciones", icon: ShoppingBag },
  { href: "/app/inventory", label: "Inventario", icon: PackageSearch },
  { href: "/app/settings", label: "Configuración", icon: Settings },
  { href: "/app/plan", label: "Plan y consumo", icon: WalletCards }
];

function roleLabel(roles: string[] | undefined) {
  if (!roles?.length) return "Mi cuenta";
  if (roles.includes("BUSINESS_OWNER")) return "Propietario";
  if (roles.includes("BUSINESS_ADMIN")) return "Administrador";
  if (roles.includes("OPERATOR")) return "Operador";
  return "Mi cuenta";
}

export function AppShell({ children }: PropsWithChildren) {
  const location = useLocation();
  const me = useQuery({
    queryKey: ["auth", "me"],
    queryFn: getCurrentUser,
    retry: false,
    staleTime: 60_000
  });
  const subscription = useQuery({
    queryKey: ["billing", "subscription"],
    queryFn: getSubscription,
    retry: false,
    staleTime: 60_000
  });

  function logout() {
    clearAccessToken();
    window.location.assign("/");
  }

  function isCurrentNavigation(href: string): boolean {
    const route = href.replace(/^\/app/, "") || "/";
    if (route === "/") return location.pathname === "/" || location.pathname === "";
    return location.pathname === route || location.pathname.startsWith(route + "/");
  }

  const planName =
    subscription.data?.planName ||
    subscription.data?.publicPlanCode ||
    subscription.data?.plan ||
    "Plan y consumo";
  const included = Math.max(0, Number(subscription.data?.includedMinutes ?? 0));
  const used = Math.max(0, Number(subscription.data?.usedMinutes ?? 0));
  const usagePercent = included > 0 ? Math.min(100, Math.round((used / included) * 100)) : 0;
  const accountLabel = roleLabel(me.data?.roles);
  const accountDetail = me.data?.email || "Sesión protegida";

  return (
    <div className={styles.layout} data-react-app="recepvoz" data-visual-system="v2">
      <aside className={styles.sidebar} aria-label="Navegación de RecepVoz">
        <div className={styles.sidebarGlow} aria-hidden="true" />
        <a className={styles.brand} href="/app" aria-label="RecepVoz">
          <span className={styles.brandMark} aria-hidden="true">
            <AudioLines size={24} />
          </span>
          <span className={styles.brandCopy}>
            <strong>Recep<span>Voz</span></strong>
            <small>Tu recepcionista IA</small>
          </span>
        </a>

        <div className={styles.liveStatus} aria-label="RecepVoz activo">
          <span className={styles.liveDot} aria-hidden="true" />
          <span>
            <strong>RecepVoz activo</strong>
            <small>Atendiendo en segundo plano</small>
          </span>
        </div>

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
                <span className={styles.navIcon} aria-hidden="true"><Icon size={18} /></span>
                <span>{label}</span>
                <ChevronRight className={styles.navArrow} size={15} aria-hidden="true" />
              </a>
            );
          })}
        </nav>

        <div className={styles.sidebarMeta}>
          <a className={styles.planCard} href="/app/plan">
            <span className={styles.planIcon} aria-hidden="true"><Crown size={18} /></span>
            <span className={styles.planCopy}>
              <small>Tu plan</small>
              <strong>{planName}</strong>
              <span>Minutos IA{included > 0 ? ` · ${Math.round(used)} / ${Math.round(included)}` : ""}</span>
              <i aria-hidden="true"><b style={{ width: `${usagePercent}%` }} /></i>
            </span>
            <ChevronRight size={15} aria-hidden="true" />
          </a>

          <button className={styles.accountCard} type="button" onClick={logout} aria-label="Salir">
            <span className={styles.avatar} aria-hidden="true">
              <CircleUserRound size={20} />
            </span>
            <span className={styles.accountCopy}>
              <strong>{accountLabel}</strong>
              <small>{accountDetail}</small>
            </span>
            <LogOut size={15} aria-hidden="true" />
          </button>
        </div>
      </aside>

      <div className={styles.main}>
        <div className={styles.ambient} aria-hidden="true">
          <span className={styles.ambientOrbA} />
          <span className={styles.ambientOrbB} />
          <span className={styles.ambientGrid} />
          <span className={styles.ambientSweep} />
        </div>

        <header className="rv-frame-topbar">
          <div className={styles.topbarInner}>
            <div className={styles.topbarStatus}>
              <Sparkles size={14} aria-hidden="true" />
              <span>RecepVoz está trabajando</span>
            </div>
            <button className={styles.mobileLogout} type="button" onClick={logout} aria-label="Salir">
              <LogOut size={16} aria-hidden="true" />
              <span>Salir</span>
            </button>
          </div>
        </header>
        {children}
      </div>
    </div>
  );
}
