import { Navigate, Route, Routes } from "react-router-dom";
import { AuthBoundary } from "./AuthBoundary";
import { PlanFoundation } from "../pages/PlanFoundation/PlanFoundation";

export function App() {
  return (
    <AuthBoundary>
      <Routes>
        <Route path="/plan" element={<PlanFoundation />} />
        <Route path="*" element={<Navigate to="/plan" replace />} />
      </Routes>
    </AuthBoundary>
  );
}
