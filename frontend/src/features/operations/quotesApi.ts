import { apiRequest } from "../../api/client";

export type QuoteStatus = "REQUESTED" | "READY" | "ACCEPTED" | "REJECTED" | "CANCELLED";

export interface CommercialQuote {
  id: string;
  title: string;
  description?: string | null;
  amount?: number | null;
  currency: string;
  status: QuoteStatus;
  contactName?: string | null;
  contactPhone?: string | null;
  source: "VOICE" | "WHATSAPP" | "MANUAL" | "API";
  createdAt: string;
}

export function getCommercialQuotes() {
  return apiRequest<CommercialQuote[]>("/api/v1/commercial/quotes");
}

export function updateCommercialQuoteStatus(id: string, status: QuoteStatus) {
  return apiRequest<CommercialQuote>(
    `/api/v1/commercial/quotes/${encodeURIComponent(id)}/status`,
    {
      method: "PATCH",
      body: JSON.stringify({ status })
    }
  );
}
