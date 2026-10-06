import { useEffect, type PropsWithChildren } from "react";
import { getAccessToken } from "../api/client";

export function AuthBoundary({ children }: PropsWithChildren) {
  const authenticated = Boolean(getAccessToken());

  useEffect(() => {
    if (!authenticated) {
      window.location.replace("/app/auth");
    }
  }, [authenticated]);

  if (!authenticated) return null;
  return children;
}
