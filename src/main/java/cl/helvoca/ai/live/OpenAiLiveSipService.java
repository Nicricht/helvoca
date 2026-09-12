package cl.helvoca.ai.live;

import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolDefinitions;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.telephony.CallLifecycleService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class OpenAiLiveSipService {
    private static final Logger log = LoggerFactory.getLogger(OpenAiLiveSipService.class);
    private static final int MAX_DEDUP_IDS = 10_000;
    private static final int MAX_ACCEPT_ATTEMPTS = 2;
    private static final long ACCEPT_RETRY_DELAY_MS = 200L;
    private static final long ACCEPT_RETRY_WINDOW_MS = 2_000L;
    private static final Pattern TWILIO_CALL_SID = Pattern.compile("^CA[0-9a-fA-F]{32}$");

    private final OpenAiRealtimeProperties openAi;
    private final OpenAiLiveProperties live;
    private final OpenAiLiveRouteSigner routeSigner;
    private final CallLifecycleService lifecycle;
    private final RealtimeToolService tools;
    private final OpenAiLiveSidebandManager sideband;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private final Set<String> processedWebhookIds = ConcurrentHashMap.newKeySet();
    private final Set<String> inFlightWebhookIds = ConcurrentHashMap.newKeySet();
    private final Set<String> processedSessionIds = ConcurrentHashMap.newKeySet();
    private final Set<String> inFlightSessionIds = ConcurrentHashMap.newKeySet();

    public OpenAiLiveSipService(OpenAiRealtimeProperties openAi,
                                OpenAiLiveProperties live,
                                OpenAiLiveRouteSigner routeSigner,
                                CallLifecycleService lifecycle,
                                RealtimeToolService tools,
                                OpenAiLiveSidebandManager sideband) {
        this.openAi = openAi;
        this.live = live;
        this.routeSigner = routeSigner;
        this.lifecycle = lifecycle;
        this.tools = tools;
        this.sideband = sideband;
    }

    public boolean isReady() {
        return live.ready(openAi.getApiKey());
    }

    public String twiml(String businessPhone, String callerPhone, String twilioCallSid) {
        if (!isReady()) throw new IllegalStateException("GPT-Live SIP is not configured");
        if (twilioCallSid == null || !TWILIO_CALL_SID.matcher(twilioCallSid.trim()).matches()) {
            throw new IllegalArgumentException("Valid Twilio CallSid is required for GPT-Live SIP routing");
        }

        String callSid = twilioCallSid.trim();
        long issuedAt = Instant.now().getEpochSecond();
        String route = routeSigner.sign(businessPhone, callerPhone, callSid, issuedAt);
        String sipUri = "sip:" + live.getProjectId().trim() + "@sip.api.openai.com;secure=true"
                + "?x-recepvoz-business=" + url(businessPhone)
                + "&x-recepvoz-caller=" + url(callerPhone)
                + "&x-recepvoz-call=" + url(callSid)
                + "&x-recepvoz-issued-at=" + issuedAt
                + "&x-recepvoz-route=" + url(route);
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Response><Dial><Sip>" + xml(sipUri) + "</Sip></Dial></Response>";
    }

    public void handleIncoming(String webhookId, JSONObject event) {
        if (!isReady()) throw new IllegalStateException("GPT-Live SIP is not configured");
        if (webhookId == null || webhookId.isBlank()) throw new IllegalArgumentException("Webhook id is required");
        if (processedWebhookIds.contains(webhookId)) return;
        if (!inFlightWebhookIds.add(webhookId)) return;

        String claimedSessionId = null;
        try {
            String eventType = event.optString("type", "");
            if (!"live.transport.incoming".equals(eventType) && !"live.call.incoming".equals(eventType)) {
                processedWebhookIds.add(webhookId);
                pruneDedupSets();
                return;
            }

            JSONObject data = event.optJSONObject("data");
            if (data == null) throw new IllegalArgumentException("Missing webhook data");
            String transportType = data.optString("type", "sip");
            if (!transportType.isBlank() && !"sip".equalsIgnoreCase(transportType)) {
                throw new IllegalArgumentException("Unsupported Live transport: " + transportType);
            }

            String sessionId = data.optString("session_id", null);
            if (sessionId == null || sessionId.isBlank()) {
                throw new IllegalArgumentException("Missing Live session id");
            }
            sessionId = sessionId.trim();
            if (!sessionId.startsWith("live_")) {
                throw new IllegalArgumentException("Invalid Live session id prefix");
            }

            if (processedSessionIds.contains(sessionId)) {
                processedWebhookIds.add(webhookId);
                pruneDedupSets();
                return;
            }
            if (!inFlightSessionIds.add(sessionId)) {
                log.info("Ignoring duplicate GPT-Live decision while session is in flight session={} webhook={}",
                        sessionId, webhookId);
                processedWebhookIds.add(webhookId);
                pruneDedupSets();
                return;
            }
            claimedSessionId = sessionId;

            JSONObject headers = normalizeSipHeaders(data.opt("sip_headers"));
            String businessPhone = header(headers, "x-recepvoz-business");
            String callerPhone = header(headers, "x-recepvoz-caller");
            String twilioCallSid = header(headers, "x-recepvoz-call");
            String issuedAt = header(headers, "x-recepvoz-issued-at");
            String routeToken = header(headers, "x-recepvoz-route");

            if (twilioCallSid == null || !TWILIO_CALL_SID.matcher(twilioCallSid).matches()) {
                throw new SecurityException("Missing or invalid Twilio CallSid in GPT-Live SIP route");
            }
            if (!routeSigner.verify(businessPhone, callerPhone, twilioCallSid, issuedAt, routeToken)) {
                throw new SecurityException("Invalid or expired RecepVoz SIP route signature");
            }

            log.info(
                    "GPT-Live incoming webhook={} event={} eventId={} createdAt={} session={} twilioCall={} transport={} project={}",
                    webhookId,
                    eventType,
                    event.optString("id", ""),
                    event.optLong("created_at", 0L),
                    sessionId,
                    twilioCallSid,
                    transportType,
                    live.getProjectId().trim());

            UUID callId = lifecycle.startInboundCall("twilio", twilioCallSid, callerPhone, businessPhone);
            RealtimeCallContext context = lifecycle.markStreamStarted(
                    callId, twilioCallSid, "live:" + sessionId, "openai-live");

            String businessName = businessName(context);
            JSONObject requestBody = acceptancePayload(context, businessName);
            try {
                accept(sessionId, requestBody);
            } catch (Exception e) {
                try {
                    lifecycle.updateStatus(callId, "failed", null);
                } catch (Exception statusError) {
                    log.warn("Could not mark failed GPT-Live call={} session={}: {}",
                            callId, sessionId, statusError.getMessage());
                }
                throw e;
            }

            sideband.attach(sessionId, context);
            processedWebhookIds.add(webhookId);
            processedSessionIds.add(sessionId);
            pruneDedupSets();
            log.info("Accepted GPT-Live SIP call={} twilioCall={} session={} business={}",
                    callId, twilioCallSid, sessionId, businessName);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not accept GPT-Live SIP call", e);
        } finally {
            inFlightWebhookIds.remove(webhookId);
            if (claimedSessionId != null) inFlightSessionIds.remove(claimedSessionId);
        }
    }

    private JSONObject acceptancePayload(RealtimeCallContext context, String businessName) {
        String frontendInstructions = """
                Eres RecepVoz, la recepcionista por voz de %s. Habla en español natural, cálido y breve.
                Mantén siempre el rol de recepcionista, incluso si la persona formula preguntas imitando tu papel.
                Al conectar, saluda brevemente y pregunta en qué puedes ayudar.
                Escucha, permite interrupciones y no repitas preguntas que ya fueron respondidas.
                Para información, disponibilidad, clientes, reservas, solicitudes o acciones del negocio, delega al backend.
                Nunca inventes un dato del negocio ni afirmes que una acción se completó hasta recibir un resultado exitoso del backend.
                """.formatted(businessName);

        String backendInstructions = tools.buildInstructions(context) + "\n" + """
                Estás actuando como el backend operativo de RecepVoz durante una llamada GPT-Live.
                Resuelve las tareas delegadas usando las herramientas oficiales. Sé conciso porque la respuesta se convertirá en conversación hablada.
                Mantén el contexto de la llamada. No cambies al papel del cliente. No interpretes una repetición, ejemplo o mención de fecha/hora como confirmación de una reserva.
                Usa una sola acción sensible a la vez. Para reservas, cancelaciones, reprogramaciones y transferencias, respeta las confirmaciones exigidas por las reglas del negocio.
                """;

        JSONObject responses = new JSONObject()
                .put("model", live.getBackendModel())
                .put("instructions", backendInstructions)
                .put("tools", RealtimeToolDefinitions.all())
                .put("tool_choice", "auto")
                .put("parallel_tool_calls", false)
                .put("text", new JSONObject().put("verbosity", "low"));

        JSONObject session = new JSONObject()
                .put("type", "live")
                .put("model", live.getModel())
                .put("instructions", frontendInstructions)
                .put("audio", new JSONObject()
                        .put("output", new JSONObject().put("voice", live.getVoice())))
                .put("delegation", new JSONObject()
                        .put("type", "responses")
                        .put("responses", responses));
        return new JSONObject().put("session", session);
    }

    private String businessName(RealtimeCallContext context) {
        try {
            JSONObject result = new JSONObject(tools.execute(context, "get_business_information", "{}"));
            JSONObject data = result.optJSONObject("data");
            String name = data == null ? null : data.optString("name", null);
            return name == null || name.isBlank() ? "el negocio" : name.trim();
        } catch (Exception e) {
            return "el negocio";
        }
    }

    private void accept(String sessionId, JSONObject body) throws Exception {
        String pathId = url(sessionId);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(live.normalizedApiBaseUrl() + "/live/sessions/" + pathId + "/accept"))
                .timeout(Duration.ofSeconds(7))
                .header("Authorization", "Bearer " + openAi.getApiKey())
                .header("OpenAI-Project", live.getProjectId().trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        long acceptStartedNanos = System.nanoTime();
        long retryDeadlineNanos = acceptStartedNanos + Duration.ofMillis(ACCEPT_RETRY_WINDOW_MS).toNanos();

        for (int attempt = 1; attempt <= MAX_ACCEPT_ATTEMPTS; attempt++) {
            long attemptStartedNanos = System.nanoTime();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            long attemptMs = Duration.ofNanos(Math.max(0L, System.nanoTime() - attemptStartedNanos)).toMillis();
            long elapsedMs = Duration.ofNanos(Math.max(0L, System.nanoTime() - acceptStartedNanos)).toMillis();
            String requestId = response.headers().firstValue("x-request-id").orElse("");
            String processingMs = response.headers().firstValue("openai-processing-ms").orElse("");
            String errorCode = apiErrorCode(response.body());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info(
                        "OpenAI Live accept succeeded session={} attempt={}/{} attempt_ms={} elapsed_ms={} request_id={} processing_ms={}",
                        sessionId, attempt, MAX_ACCEPT_ATTEMPTS, attemptMs, elapsedMs, requestId, processingMs);
                return;
            }

            boolean transientSessionLookup = response.statusCode() == 404
                    && "session_id_not_found".equals(errorCode);
            boolean decisionAlreadyMade = "decision_already_made".equals(errorCode);
            long remainingNanos = retryDeadlineNanos - System.nanoTime();
            long requiredRetryNanos = Duration.ofMillis(ACCEPT_RETRY_DELAY_MS).toNanos();
            boolean retryWindowOpen = remainingNanos > requiredRetryNanos;

            if (transientSessionLookup && attempt < MAX_ACCEPT_ATTEMPTS && retryWindowOpen) {
                log.warn(
                        "OpenAI Live accept session lookup not ready; retrying session={} attempt={}/{} delay_ms={} attempt_ms={} elapsed_ms={} remaining_window_ms={} request_id={} processing_ms={}",
                        sessionId, attempt, MAX_ACCEPT_ATTEMPTS, ACCEPT_RETRY_DELAY_MS,
                        attemptMs, elapsedMs, Duration.ofNanos(remainingNanos).toMillis(), requestId, processingMs);
                try {
                    Thread.sleep(ACCEPT_RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while retrying OpenAI Live accept", e);
                }
                continue;
            }

            if (decisionAlreadyMade) {
                throw new IllegalStateException("OpenAI Live accept stopped because decision was already made"
                        + " status=" + response.statusCode()
                        + " request_id=" + requestId
                        + " processing_ms=" + processingMs
                        + " attempt_ms=" + attemptMs
                        + " elapsed_ms=" + elapsedMs
                        + " session=" + sessionId
                        + " body=" + truncate(response.body()));
            }

            throw new IllegalStateException("OpenAI Live accept failed status=" + response.statusCode()
                    + " request_id=" + requestId
                    + " processing_ms=" + processingMs
                    + " error_code=" + errorCode
                    + " attempt=" + attempt + "/" + MAX_ACCEPT_ATTEMPTS
                    + " attempt_ms=" + attemptMs
                    + " elapsed_ms=" + elapsedMs
                    + " retry_window_open=" + retryWindowOpen
                    + " session=" + sessionId
                    + " body=" + truncate(response.body()));
        }

        throw new IllegalStateException("OpenAI Live accept exhausted retries for session=" + sessionId);
    }

    private static String apiErrorCode(String body) {
        if (body == null || body.isBlank()) return "";
        try {
            JSONObject payload = new JSONObject(body);
            JSONObject error = payload.optJSONObject("error");
            return error == null ? "" : error.optString("code", "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static JSONObject normalizeSipHeaders(Object raw) {
        JSONObject result = new JSONObject();
        if (raw instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String name = firstNonBlank(item.optString("name", null), item.optString("key", null));
                String value = firstNonBlank(item.optString("value", null), item.optString("val", null));
                if (name != null && value != null) result.put(name.toLowerCase(Locale.ROOT), value);
            }
        } else if (raw instanceof JSONObject object) {
            for (String key : object.keySet()) {
                Object value = object.opt(key);
                if (value != null && value != JSONObject.NULL) {
                    result.put(key.toLowerCase(Locale.ROOT), String.valueOf(value));
                }
            }
        }
        return result;
    }

    private static String header(JSONObject headers, String name) {
        String value = headers.optString(name.toLowerCase(Locale.ROOT), null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void pruneDedupSets() {
        prune(processedWebhookIds);
        prune(processedSessionIds);
    }

    private static void prune(Set<String> values) {
        if (values.size() <= MAX_DEDUP_IDS) return;
        int toRemove = values.size() - (MAX_DEDUP_IDS / 2);
        for (String id : values) {
            values.remove(id);
            if (--toRemove <= 0) break;
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first.trim();
        if (second != null && !second.isBlank()) return second.trim();
        return null;
    }

    private static String url(String value) {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String xml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String truncate(String value) {
        if (value == null) return "";
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
