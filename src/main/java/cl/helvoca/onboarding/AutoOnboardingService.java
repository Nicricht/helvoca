package cl.helvoca.onboarding;

import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AutoOnboardingService {
    private static final int MAX_SERVICES = 12;
    private static final int MAX_HOURS = 20;
    private static final int MAX_KNOWLEDGE = 15;

    private final PublicBusinessSourceService sources;
    private final OpenAiRealtimeProperties openAi;
    private final BusinessRepository businesses;
    private final TenantProvider tenantProvider;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public AutoOnboardingService(PublicBusinessSourceService sources,
                                 OpenAiRealtimeProperties openAi,
                                 BusinessRepository businesses,
                                 TenantProvider tenantProvider) {
        this.sources = sources;
        this.openAi = openAi;
        this.businesses = businesses;
        this.tenantProvider = tenantProvider;
    }

    public AutoOnboardingProposal analyze(AutoOnboardingRequest request) {
        UUID businessId = tenantProvider.requireBusinessId();
        Business business = businesses.findById(businessId)
                .orElseThrow(() -> new NotFoundException("Business not found"));

        PublicBusinessSourceService.SourceReadResult source = sources.read(request.sourceUrl());
        String fallbackTimezone = safeTimezone(business.getTimezone(), "America/Santiago");
        String fallbackLanguage = safeLanguage(business.getLanguage(), "es");
        List<String> warnings = new ArrayList<>();
        if (source.warning() != null && !source.warning().isBlank()) warnings.add(source.warning());

        if (!source.readable()) {
            return emptyProposal(request.businessName(), source.resolvedUrl(), false, fallbackTimezone, fallbackLanguage, warnings);
        }
        if (!openAi.hasApiKey()) {
            warnings.add("La fuente se pudo leer, pero el análisis con IA no está disponible porque OpenAI no está configurado.");
            return emptyProposal(request.businessName(), source.resolvedUrl(), true, fallbackTimezone, fallbackLanguage, warnings);
        }

        try {
            JSONObject body = new JSONObject()
                    .put("model", openAi.getSummaryModel())
                    .put("instructions", instructions())
                    .put("input", "NOMBRE DECLARADO: " + request.businessName().trim()
                            + "\nURL FUENTE: " + source.resolvedUrl()
                            + "\nZONA HORARIA ACTUAL DEL TENANT: " + fallbackTimezone
                            + "\nIDIOMA ACTUAL DEL TENANT: " + fallbackLanguage
                            + "\n\nTEXTO PÚBLICO EXTRAÍDO:\n" + source.text())
                    .put("max_output_tokens", 1800);

            HttpRequest aiRequest = HttpRequest.newBuilder(URI.create(openAi.getResponsesUrl()))
                    .timeout(Duration.ofSeconds(35))
                    .header("Authorization", "Bearer " + openAi.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> response = http.send(aiRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                warnings.add("OpenAI no pudo analizar la fuente en este momento. Puedes reintentar o usar la edición manual.");
                return emptyProposal(request.businessName(), source.resolvedUrl(), true, fallbackTimezone, fallbackLanguage, warnings);
            }
            String output = extractOutputText(new JSONObject(response.body()));
            JSONObject proposal = parseJsonObject(output);
            return sanitizeProposal(request.businessName(), source, fallbackTimezone, fallbackLanguage, proposal, warnings);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            warnings.add("El análisis con IA fue interrumpido. Puedes volver a intentarlo.");
            return emptyProposal(request.businessName(), source.resolvedUrl(), true, fallbackTimezone, fallbackLanguage, warnings);
        } catch (Exception e) {
            warnings.add("No pude convertir el análisis de IA en una configuración segura. Puedes reintentar o editar manualmente.");
            return emptyProposal(request.businessName(), source.resolvedUrl(), true, fallbackTimezone, fallbackLanguage, warnings);
        }
    }

    private static String instructions() {
        return """
                Eres el analizador de onboarding de Helvoca. Devuelve SOLO un objeto JSON válido, sin Markdown.
                Tu trabajo es extraer una propuesta factual desde el TEXTO PÚBLICO proporcionado. No uses conocimiento externo.
                Nunca inventes horarios, precios, duración, dirección, servicios, políticas ni prestaciones.
                Si un dato no está explícitamente sustentado, omítelo, usa null o deja la lista vacía.
                El nombre declarado por el usuario tiene prioridad si la fuente es ambigua.
                Para timezone e idioma puedes conservar los valores actuales del tenant cuando la fuente no permita mejorarlos.
                dayOfWeek usa ISO: lunes=1 ... domingo=7. Horarios deben usar HH:mm y solo aparecer si son explícitos.
                durationMinutes solo si la fuente indica duración de forma clara; si existe un servicio pero no duración, usa 30 y agrega una advertencia para que el dueño la revise.
                price solo si aparece explícitamente. No conviertas monedas.
                knowledge debe contener únicamente preguntas/respuestas útiles para llamadas y sustentadas por la fuente.
                Máximo 12 servicios, 20 intervalos horarios y 15 elementos de knowledge.
                Formato exacto:
                {"businessName":"...","sourceSummary":"...","timezone":"...","language":"...","services":[{"name":"...","description":"...","durationMinutes":30,"price":null}],"hours":[{"dayOfWeek":1,"openTime":"09:00","closeTime":"18:00"}],"knowledge":[{"title":"...","category":"Información","content":"..."}],"warnings":["..."]}
                """;
    }

    static AutoOnboardingProposal sanitizeProposal(String requestedBusinessName,
                                                    PublicBusinessSourceService.SourceReadResult source,
                                                    String fallbackTimezone,
                                                    String fallbackLanguage,
                                                    JSONObject root,
                                                    List<String> initialWarnings) {
        List<String> warnings = new ArrayList<>(initialWarnings);
        JSONArray warningArray = root.optJSONArray("warnings");
        if (warningArray != null) {
            for (int i = 0; i < Math.min(warningArray.length(), 10); i++) {
                String warning = warningArray.optString(i, "").trim();
                if (!warning.isBlank()) warnings.add(warning);
            }
        }

        String businessName = root.optString("businessName", requestedBusinessName).trim();
        if (businessName.isBlank()) businessName = requestedBusinessName.trim();
        String timezone = safeTimezone(root.optString("timezone", fallbackTimezone), fallbackTimezone);
        String language = safeLanguage(root.optString("language", fallbackLanguage), fallbackLanguage);
        String summary = root.optString("sourceSummary", "").trim();
        if (summary.length() > 700) summary = summary.substring(0, 700);

        List<AutoOnboardingProposal.ServiceProposal> services = new ArrayList<>();
        JSONArray serviceArray = root.optJSONArray("services");
        if (serviceArray != null) {
            for (int i = 0; i < Math.min(serviceArray.length(), MAX_SERVICES); i++) {
                JSONObject item = serviceArray.optJSONObject(i);
                if (item == null) continue;
                String name = item.optString("name", "").trim();
                if (name.isBlank()) continue;
                String description = nullableText(item.optString("description", null), 500);
                int duration = item.optInt("durationMinutes", 30);
                if (duration < 5 || duration > 480) duration = 30;
                BigDecimal price = null;
                Object rawPrice = item.opt("price");
                if (rawPrice != null && rawPrice != JSONObject.NULL) {
                    try {
                        BigDecimal parsed = new BigDecimal(String.valueOf(rawPrice));
                        if (parsed.signum() >= 0) price = parsed;
                    } catch (Exception ignored) { }
                }
                services.add(new AutoOnboardingProposal.ServiceProposal(name, description, duration, price));
            }
        }

        List<AutoOnboardingProposal.HourProposal> hours = new ArrayList<>();
        JSONArray hourArray = root.optJSONArray("hours");
        if (hourArray != null) {
            for (int i = 0; i < Math.min(hourArray.length(), MAX_HOURS); i++) {
                JSONObject item = hourArray.optJSONObject(i);
                if (item == null) continue;
                int day = item.optInt("dayOfWeek", 0);
                if (day < 1 || day > 7) continue;
                try {
                    LocalTime open = LocalTime.parse(item.optString("openTime", ""));
                    LocalTime close = LocalTime.parse(item.optString("closeTime", ""));
                    if (open.isBefore(close)) hours.add(new AutoOnboardingProposal.HourProposal(day, open, close));
                } catch (Exception ignored) { }
            }
        }

        List<AutoOnboardingProposal.KnowledgeProposal> knowledge = new ArrayList<>();
        JSONArray knowledgeArray = root.optJSONArray("knowledge");
        if (knowledgeArray != null) {
            for (int i = 0; i < Math.min(knowledgeArray.length(), MAX_KNOWLEDGE); i++) {
                JSONObject item = knowledgeArray.optJSONObject(i);
                if (item == null) continue;
                String title = item.optString("title", "").trim();
                String content = item.optString("content", "").trim();
                if (title.isBlank() || content.isBlank()) continue;
                knowledge.add(new AutoOnboardingProposal.KnowledgeProposal(
                        truncate(title, 200), nullableText(item.optString("category", null), 100), truncate(content, 1500)));
            }
        }

        if (services.isEmpty()) warnings.add("No encontré servicios reservables suficientemente claros. Revísalos antes de activar llamadas.");
        if (hours.isEmpty()) warnings.add("No encontré horarios suficientemente claros. Debes confirmarlos manualmente.");

        return new AutoOnboardingProposal(truncate(businessName, 150), source.resolvedUrl(), source.readable(), summary,
                timezone, language, List.copyOf(services), List.copyOf(hours), List.copyOf(knowledge), List.copyOf(warnings));
    }

    private static AutoOnboardingProposal emptyProposal(String businessName, String sourceUrl, boolean readable,
                                                        String timezone, String language, List<String> warnings) {
        return new AutoOnboardingProposal(truncate(businessName.trim(), 150), sourceUrl, readable, "", timezone, language,
                List.of(), List.of(), List.of(), List.copyOf(warnings));
    }

    static JSONObject parseJsonObject(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Empty AI response");
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            int firstNewLine = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (firstNewLine >= 0 && lastFence > firstNewLine) cleaned = cleaned.substring(firstNewLine + 1, lastFence).trim();
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalArgumentException("AI response was not JSON");
        return new JSONObject(cleaned.substring(start, end + 1));
    }

    static String extractOutputText(JSONObject root) {
        String direct = root.optString("output_text", "");
        if (!direct.isBlank()) return direct;
        JSONArray output = root.optJSONArray("output");
        if (output == null) return null;
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null) continue;
            JSONArray content = item.optJSONArray("content");
            if (content == null) continue;
            for (int j = 0; j < content.length(); j++) {
                JSONObject part = content.optJSONObject(j);
                if (part != null && "output_text".equals(part.optString("type"))) {
                    String text = part.optString("text", "");
                    if (!text.isBlank()) return text;
                }
            }
        }
        return null;
    }

    private static String safeTimezone(String candidate, String fallback) {
        String value = candidate == null || candidate.isBlank() ? fallback : candidate.trim();
        try {
            ZoneId.of(value);
            return value;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String safeLanguage(String candidate, String fallback) {
        String value = candidate == null || candidate.isBlank() ? fallback : candidate.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z]{2,3}([_-][a-z0-9]{2,8})?")) return fallback;
        return truncate(value, 10);
    }

    private static String nullableText(String value, int max) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) return null;
        return truncate(value.trim(), max);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
