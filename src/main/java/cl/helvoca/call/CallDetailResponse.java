package cl.helvoca.call;

import java.util.List;

public record CallDetailResponse(CallResponse call, List<TranscriptResponse> transcript) {}
