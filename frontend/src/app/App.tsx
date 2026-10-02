import { Navigate, Route, Routes } from "react-router-dom";
import { AuthBoundary } from "./AuthBoundary";
import { HomePage } from "../pages/Home/HomePage";
import { PlanConsumptionPage } from "../pages/PlanConsumption/PlanConsumptionPage";
import { InventoryPage } from "../pages/Inventory/InventoryPage";

export function App() {
  return (
    <AuthBoundary>
      <Routes>
        <Route index element={<HomePage />} />
        <Route path="/plan" element={<PlanConsumptionPage />} />
        <Route path="/inventory" element={<InventoryPage />} />
        <Route path="*" element={<Navigate to="/plan" replace />} />
      </Routes>
    </AuthBoundary>
  );
}
