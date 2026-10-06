import { Navigate, Outlet, Route, Routes } from "react-router-dom";
import { AuthBoundary } from "./AuthBoundary";
import { HomePage } from "../pages/Home/HomePage";
import { PlanConsumptionPage } from "../pages/PlanConsumption/PlanConsumptionPage";
import { InventoryPage } from "../pages/Inventory/InventoryPage";
import { AgendaPage } from "../pages/Agenda/AgendaPage";
import { OrdersPage } from "../pages/Orders/OrdersPage";
import { SettingsPage } from "../pages/Settings/SettingsPage";
import { SimulatorPage } from "../pages/Simulator/SimulatorPage";
import { InternalOperationsPage } from "../pages/InternalOperations/InternalOperationsPage";
import { BusinessImportPage } from "../pages/BusinessImport/BusinessImportPage";
import { PlatformPage } from "../pages/Platform/PlatformPage";
import { InvitePage } from "../pages/Invite/InvitePage";

function ProtectedOutlet() {
  return (
    <AuthBoundary>
      <Outlet />
    </AuthBoundary>
  );
}

export function App() {
  return (
    <Routes>
      <Route path="/invite" element={<InvitePage />} />
      <Route element={<ProtectedOutlet />}>
        <Route index element={<HomePage />} />
        <Route path="/plan" element={<PlanConsumptionPage />} />
        <Route path="/inventory" element={<InventoryPage />} />
        <Route path="/agenda" element={<AgendaPage />} />
        <Route path="/orders" element={<OrdersPage />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/settings/import" element={<BusinessImportPage />} />
        <Route path="/simulator" element={<SimulatorPage />} />
        <Route path="/internal/operations" element={<InternalOperationsPage />} />
        <Route path="/platform" element={<PlatformPage />} />
        <Route path="*" element={<Navigate to="/plan" replace />} />
      </Route>
    </Routes>
  );
}
