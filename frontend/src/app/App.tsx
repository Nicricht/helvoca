import { Navigate, Route, Routes } from "react-router-dom";
import { AuthBoundary } from "./AuthBoundary";
import { PlanConsumptionPage } from "../pages/PlanConsumption/PlanConsumptionPage";
import { InventoryPage } from "../pages/Inventory/InventoryPage";
import { SettingsPage } from "../pages/Settings/SettingsPage";

export function App() {
  return (
    <AuthBoundary>
      <Routes>
        <Route path="/plan" element={<PlanConsumptionPage />} />
        <Route path="/inventory" element={<InventoryPage />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="*" element={<Navigate to="/plan" replace />} />
      </Routes>
    </AuthBoundary>
  );
}
