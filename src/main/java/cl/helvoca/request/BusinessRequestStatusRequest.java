package cl.helvoca.request;

import jakarta.validation.constraints.NotNull;

public record BusinessRequestStatusRequest(@NotNull BusinessRequestStatus status) {}
