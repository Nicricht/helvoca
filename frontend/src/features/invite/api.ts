import { apiRequest } from "../../api/client";

export type PublicInvitation = {
  id: string;
  businessId: string;
  businessName: string;
  name: string;
  email: string;
  role: string;
  expiresAt: string;
  status: "PENDING" | "ACCEPTED" | "EXPIRED" | "REVOKED" | string;
  invitePath?: string | null;
};

export type InvitationLogin = {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: {
    id: string;
    businessId: string;
    name: string;
    email: string;
    roles: string[];
  };
};

export function previewInvitation(businessId: string, token: string) {
  return apiRequest<PublicInvitation>(
    "/api/v1/auth/invitations/" +
      encodeURIComponent(businessId) +
      "/" +
      encodeURIComponent(token),
    {},
    false
  );
}

export function acceptInvitation(businessId: string, token: string, password: string) {
  return apiRequest<InvitationLogin>(
    "/api/v1/auth/invitations/" +
      encodeURIComponent(businessId) +
      "/" +
      encodeURIComponent(token) +
      "/accept",
    {
      method: "POST",
      body: JSON.stringify({ password })
    },
    false
  );
}
