import { apiRequest } from "../../api/client";
import type { EmbeddedConversationContext } from "../conversations";

export interface CurrentUser {
  email?: string;
  roles?: string[];
  permissions?: string[];
}

export interface Booking {
  id: string;
  customerId: string;
  serviceId: string;
  startAt: string;
  endAt?: string | null;
  status: string;
  source: string;
  notes?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface Customer {
  id: string;
  name: string;
  phone?: string | null;
  email?: string | null;
  notes?: string | null;
}

export interface ServiceItem {
  id: string;
  name: string;
  description?: string | null;
  durationMinutes?: number | null;
  price?: number | null;
  active?: boolean;
}

export interface BookingActivity {
  id: string;
  action?: string | null;
  actorType?: string | null;
  actorName?: string | null;
  actorRole?: string | null;
  beforeState?: Record<string, unknown> | null;
  afterState?: Record<string, unknown> | null;
  createdAt?: string | null;
}

export interface OperationEvent {
  id: string;
  eventType?: string | null;
  channel?: string | null;
  sourceReferenceId?: string | null;
  createdAt?: string | null;
}

export interface BookingContext extends EmbeddedConversationContext {
  events?: OperationEvent[];
}

export interface AvailabilityResponse {
  serviceId?: string;
  startAt?: string;
  available: boolean;
}

export interface CreateBookingInput {
  customerId: string;
  serviceId: string;
  startAt: string;
  source: "ADMIN";
  notes: string | null;
}

export interface RescheduleBookingInput {
  startAt: string;
  notes: string | null;
}

export function getCurrentUser() {
  return apiRequest<CurrentUser>("/api/v1/auth/me");
}

export function getBookings() {
  return apiRequest<Booking[]>("/api/v1/bookings");
}

export function getCustomers() {
  return apiRequest<Customer[]>("/api/v1/customers");
}

export function getServices() {
  return apiRequest<ServiceItem[]>("/api/v1/services");
}

export function getBookingContext(id: string) {
  return apiRequest<BookingContext>(
    "/api/v1/bookings/" + encodeURIComponent(id) + "/context"
  );
}

export function getBookingActivity(id: string) {
  return apiRequest<BookingActivity[]>(
    "/api/v1/bookings/" + encodeURIComponent(id) + "/activity"
  );
}

export function checkAvailability(
  serviceId: string,
  startAt: string,
  excludeBookingId?: string | null
) {
  const params = new URLSearchParams({ serviceId, startAt });
  if (excludeBookingId) params.set("excludeBookingId", excludeBookingId);
  return apiRequest<AvailabilityResponse>(
    "/api/v1/bookings/availability?" + params.toString()
  );
}

export function createBooking(input: CreateBookingInput) {
  return apiRequest<Booking>("/api/v1/bookings", {
    method: "POST",
    body: JSON.stringify(input)
  });
}

export function rescheduleBooking(id: string, input: RescheduleBookingInput) {
  return apiRequest<Booking>(
    "/api/v1/bookings/" + encodeURIComponent(id),
    {
      method: "PATCH",
      body: JSON.stringify(input)
    }
  );
}

export function cancelBooking(id: string) {
  return apiRequest<void>(
    "/api/v1/bookings/" + encodeURIComponent(id),
    { method: "DELETE" }
  );
}
