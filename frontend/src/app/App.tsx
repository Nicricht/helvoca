import { Navigate, Route, Routes } from "react-router-dom";
import { AuthBoundary } from "./AuthBoundary";
import { PlanConsumptionPage } from "../pages/PlanConsumption/PlanConsumptionPage";

export function App() {
  return (
    <AuthBoundary>
      <Routes>
        <Route path="/plan" element={<PlanConsumptionPage />} />
        <Route path="*" element={<Navigate to="/plan" replace />} />
      </Routes>
    </AuthBoundary>
  );
}
