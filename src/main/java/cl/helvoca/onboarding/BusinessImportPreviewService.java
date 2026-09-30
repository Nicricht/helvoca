package cl.helvoca.onboarding;

import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import cl.helvoca.security.TenantProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class BusinessImportPreviewService {
    private static final int MAX_FILES = 12;
    private static final long MAX_FILE_BYTES = 10L * 1024L * 1024L;
    private static final long MAX_SEMANTIC_BYTES = 20L * 1024L * 1024L;
    private static final int MAX_PRODUCTS = 500;

    private final BusinessImportSpreadsheetParser spreadsheets;
    private final OpenAiRealtimeProperties openAi;
    private final TenantProvider tenantProvider;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public BusinessImportPreviewService(BusinessImportSpreadsheetParser spreadsheets,
                                        OpenAiRealtimeProperties openAi,
                                        TenantProvider tenantProvider) {
        this.spreadsheets = spreadsheets;
        this.openAi = openAi;
        this.tenantProvider = tenantProvider;
    }

    public Preview preview(String businessName, List<MultipartFile> files) {
        tenantProvider.requireBusinessId();
        String safeBusinessName = businessName == null ? "" : businessName.trim();
        if (safeBusinessName.isBlank()) throw new IllegalArgumentException("Business name is required");
        if (safeBusinessName.length() > 150) throw new IllegalArgumentException("Business name is too long");
        if (files == null || files.isEmpty()) throw new IllegalArgumentException("At least one file is required");
        if (files.size() > MAX_FILES) throw new IllegalArgumentException("A maximum of 12 files can be imported at once");

        List<ProductProposal> products = new ArrayList<>();
        List<SourcePreview> sources = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<MultipartFile> semantic = new ArrayList<>();
        long semanticBytes = 0L;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                warnings.add("Se omitió un archivo vacío.");
                continue;
            }
            if (file.getSize() > MAX_FILE_BYTES) {
                warnings.add(displayName(file) + " supera el límite de 10 MB y no fue procesado.");
                continue;
            }

            if (isSpreadsheet(file)) {
                BusinessImportSpreadsheetParser.ParseResult parsed = spreadsheets.parse(file);
                sources.add(new SourcePreview(
                        displayName(file),
                        parsed.kind(),
                        parsed.rowCount(),
                        "SPREADSHEET",
                        parsed.recognized(),
                        parsed.warnings()));
                for (BusinessImportSpreadsheetParser.ProductRow row : parsed.products()) {
                    products.add(fromSpreadsheet(row));
                }
                warnings.addAll(parsed.warnings());
                continue;
            }

            if (isSemanticFile(file)) {
                semanticBytes += file.getSize();
                if (semanticBytes > MAX_SEMANTIC_BYTES) {
                    warnings.add("Las fotos/PDF superan 20 MB combinados. Se omitió " + displayName(file) + ".");
                    semanticBytes -= file.getSize();
                    continue;
                }
                semantic.add(file);
                continue;
            }

            warnings.add(displayName(file) + " usa un formato no soportado. Usa CSV, XLS, XLSX, PDF, JPG, PNG o WEBP.");
            sources.add(new SourcePreview(
                    displayName(file),
                    BusinessImportSpreadsheetParser.DatasetKind.UNKNOWN,
                    0,
                    "UNSUPPORTED",
                    false,
                    List.of("Formato no soportado")));
        }

        boolean aiUsed = false;
        if (!semantic.isEmpty()) {
            if (!openAi.hasApiKey()) {
                warnings.add("Hay fotos o PDF pendientes, pero el análisis con IA no está configurado. Las planillas reconocidas sí fueron procesadas.");
                for (MultipartFile file : semantic) {
                    sources.add(new SourcePreview(displayName(file),
                            BusinessImportSpreadsheetParser.DatasetKind.UNKNOWN,
                            0,
                            "AI_UNAVAILABLE",
                            false,
                            List.of("IA no configurada")));
                }
            } else {
                try {
                    SemanticResult result = analyzeSemantic(safeBusinessName, semantic);
                    aiUsed = true;
                    products.addAll(result.products());
                    warnings.addAll(result.warnings());
                    BusinessImportSpreadsheetParser.DatasetKind semanticKind = result.products().isEmpty()
                            ? BusinessImportSpreadsheetParser.DatasetKind.UNKNOWN
                            : BusinessImportSpreadsheetParser.DatasetKind.PRODUCTS;
                    for (MultipartFile file : semantic) {
                        sources.add(new SourcePreview(displayName(file), semanticKind, 0,
                                "AI", true, List.of()));
                    }
                } catch (Exception e) {
                    warnings.add("No pude analizar las fotos/PDF con IA. Puedes reintentar o cargar una planilla: "
                            + safeMessage(e));
                    for (MultipartFile file : semantic) {
                        sources.add(new SourcePreview(displayName(file),
                                BusinessImportSpreadsheetParser.DatasetKind.UNKNOWN,
                                0,
                                "AI_ERROR",
                                false,
                                List.of("No se pudo analizar con IA")));
                    }
                }
            }
        }

        List<ProductProposal> normalized = dedupeProducts(products, warnings);
        if (normalized.size() > MAX_PRODUCTS) {
            warnings.add("Se detectaron más de " + MAX_PRODUCTS + " productos; la previsualización fue limitada.");
            normalized = normalized.subList(0, MAX_PRODUCTS);
        }
        if (normalized.isEmpty()) {
            warnings.add("No encontré productos listos para importar. Revisa los archivos o agrega datos manualmente.");
        }

        return new Preview(safeBusinessName, List.copyOf(normalized), List.copyOf(sources),
                List.copyOf(dedupeWarnings(warnings)), aiUsed);
    }

    private SemanticResult analyzeSemantic(String businessName, List<MultipartFile> files) throws Exception {
        JSONArray content = new JSONArray();
        content.put(new JSONObject()
                .put("type", "input_text")
                .put("text", semanticInstructions(businessName)));

        for (MultipartFile file : files) {
            byte[] bytes = file.getBytes();
            String mime = normalizedMime(file);
            String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
            if (mime.startsWith("image/")) {
                content.put(new JSONObject()
                        .put("type", "input_image")
                        .put("image_url", dataUrl)
                        .put("detail", "high"));
            } else {
                content.put(new JSONObject()
                        .put("type", "input_file")
                        .put("filename", displayName(file))
                        .put("file_data", dataUrl)
                        .put("detail", "high"));
            }
        }

        JSONObject body = new JSONObject()
                .put("model", openAi.getSummaryModel())
                .put("input", new JSONArray().put(new JSONObject()
                        .put("role", "user")
                        .put("content", content)))
                .put("max_output_tokens", 6000);

        HttpRequest request = HttpRequest.newBuilder(URI.create(openAi.getResponsesUrl()))
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + openAi.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("OpenAI respondió HTTP " + response.statusCode());
        }

        String output = extractOutputText(new JSONObject(response.body()));
        return parseSemanticResult(output, files.size() == 1 ? displayName(files.getFirst()) : "archivos importados");
    }

    private static String semanticInstructions(String businessName) {
        return """
                Analiza SOLO los archivos adjuntos para preparar un BORRADOR de importación de negocio.
                Negocio declarado: %s

                Devuelve SOLO JSON válido, sin Markdown:
                {"products":[{"name":"...","description":"...","price":null,"currency":"CLP","sku":null,"onHand":null,"category":null,"confidence":0.0,"sourceName":"..."}],"warnings":["..."]}

                Reglas obligatorias:
                - Nunca inventes productos, precios, stock, SKU, ingredientes, variantes ni disponibilidad.
                - Extrae únicamente datos visibles o explícitos en los archivos.
                - Si un precio o stock no es claro, usa null.
                - No conviertas ventas históricas, boletas o facturas en pedidos, pagos ni stock actual.
                - Si el archivo parece un reporte de ventas/boletas, adviértelo y no crees productos desde filas transaccionales.
                - currency debe ser un código ISO de tres letras; si el material usa $ en contexto chileno y no hay contradicción, usa CLP.
                - confidence debe estar entre 0 y 1.
                - sourceName debe identificar el archivo que sustenta el producto cuando sea posible.
                - Máximo 500 productos.
                """.formatted(businessName);
    }

    static SemanticResult parseSemanticResult(String text, String fallbackSourceName) {
        JSONObject root = parseJsonObject(text);
        List<ProductProposal> products = new ArrayList<>();
        JSONArray items = root.optJSONArray("products");
        if (items != null) {
            for (int i = 0; i < Math.min(items.length(), MAX_PRODUCTS); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                String name = clean(item.optString("name", null), 150);
                if (name == null) continue;
                BigDecimal price = nonNegativeDecimal(item.opt("price"));
                Integer onHand = nonNegativeInteger(item.opt("onHand"));
                String currency = clean(item.optString("currency", "CLP"), 3);
                if (currency == null || !currency.toUpperCase(Locale.ROOT).matches("[A-Z]{3}")) currency = "CLP";
                else currency = currency.toUpperCase(Locale.ROOT);

                double confidence = item.has("confidence") ? item.optDouble("confidence", 0.5) : 0.5;
                if (Double.isNaN(confidence) || Double.isInfinite(confidence)) confidence = 0.5;
                confidence = Math.max(0.0, Math.min(1.0, confidence));

                products.add(new ProductProposal(
                        name,
                        clean(item.optString("description", null), 500),
                        price,
                        currency,
                        clean(item.optString("sku", null), 80),
                        onHand,
                        clean(item.optString("category", null), 120),
                        confidence,
                        clean(item.optString("sourceName", fallbackSourceName), 180)
                ));
            }
        }

        List<String> warnings = new ArrayList<>();
        JSONArray warningArray = root.optJSONArray("warnings");
        if (warningArray != null) {
            for (int i = 0; i < Math.min(warningArray.length(), 30); i++) {
                String warning = clean(warningArray.optString(i, null), 500);
                if (warning != null) warnings.add(warning);
            }
        }
        return new SemanticResult(List.copyOf(products), List.copyOf(warnings));
    }

    private static ProductProposal fromSpreadsheet(BusinessImportSpreadsheetParser.ProductRow row) {
        return new ProductProposal(row.name(), row.description(), row.price(), row.currency(),
                row.sku(), row.onHand(), row.category(), 1.0, row.sourceName());
    }

    private static List<ProductProposal> dedupeProducts(List<ProductProposal> input, List<String> warnings) {
        Map<String, ProductProposal> byKey = new LinkedHashMap<>();
        for (ProductProposal product : input) {
            String key = product.sku() != null && !product.sku().isBlank()
                    ? "sku:" + product.sku().trim().toLowerCase(Locale.ROOT)
                    : "name:" + product.name().trim().toLowerCase(Locale.ROOT);
            ProductProposal previous = byKey.get(key);
            if (previous == null) {
                byKey.put(key, product);
                continue;
            }
            ProductProposal preferred = previous.confidence() >= product.confidence() ? previous : product;
            byKey.put(key, preferred);
            warnings.add("Se detectó un duplicado para " + product.name() + "; se conservó la versión con mayor confianza.");
        }
        return new ArrayList<>(byKey.values());
    }

    private static JSONObject parseJsonObject(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("La IA devolvió una respuesta vacía");
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            int firstLine = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) cleaned = cleaned.substring(firstLine + 1, lastFence).trim();
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalArgumentException("La IA no devolvió JSON válido");
        return new JSONObject(cleaned.substring(start, end + 1));
    }

    private static String extractOutputText(JSONObject root) {
        String direct = root.optString("output_text", "");
        if (!direct.isBlank()) return direct;
        JSONArray output = root.optJSONArray("output");
        if (output == null) return null;
        for (int i = 0; i < output.length(); i++) {
            JSONObject message = output.optJSONObject(i);
            if (message == null) continue;
            JSONArray parts = message.optJSONArray("content");
            if (parts == null) continue;
            for (int j = 0; j < parts.length(); j++) {
                JSONObject part = parts.optJSONObject(j);
                if (part != null && "output_text".equals(part.optString("type"))) {
                    String value = part.optString("text", "");
                    if (!value.isBlank()) return value;
                }
            }
        }
        return null;
    }

    private static BigDecimal nonNegativeDecimal(Object raw) {
        if (raw == null || raw == JSONObject.NULL) return null;
        try {
            BigDecimal value = new BigDecimal(String.valueOf(raw));
            return value.signum() >= 0 ? value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Integer nonNegativeInteger(Object raw) {
        BigDecimal value = nonNegativeDecimal(raw);
        if (value == null) return null;
        try {
            int parsed = value.intValueExact();
            return parsed >= 0 ? parsed : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String clean(String value, int max) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) return null;
        String result = value.trim();
        return result.length() <= max ? result : result.substring(0, max);
    }

    private static boolean isSpreadsheet(MultipartFile file) {
        String name = displayName(file).toLowerCase(Locale.ROOT);
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        return name.endsWith(".csv") || name.endsWith(".tsv") || name.endsWith(".xls") || name.endsWith(".xlsx")
                || type.contains("spreadsheet") || type.contains("excel") || type.equals("text/csv")
                || type.equals("text/tab-separated-values");
    }

    private static boolean isSemanticFile(MultipartFile file) {
        String name = displayName(file).toLowerCase(Locale.ROOT);
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        return name.endsWith(".pdf") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".png") || name.endsWith(".webp")
                || type.equals("application/pdf") || type.startsWith("image/");
    }

    private static String normalizedMime(MultipartFile file) {
        String type = file.getContentType();
        if (type != null && !type.isBlank() && !"application/octet-stream".equalsIgnoreCase(type)) return type;
        String name = displayName(file).toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private static String displayName(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name == null || name.isBlank() ? "archivo" : name.trim();
    }

    private static Set<String> dedupeWarnings(List<String> warnings) {
        return new LinkedHashSet<>(warnings.stream()
                .filter(value -> value != null && !value.isBlank())
                .toList());
    }

    private static String safeMessage(Exception e) {
        String value = e.getMessage();
        if (value == null || value.isBlank()) return e.getClass().getSimpleName();
        return value.length() <= 180 ? value : value.substring(0, 180);
    }

    public record ProductProposal(
            String name,
            String description,
            BigDecimal price,
            String currency,
            String sku,
            Integer onHand,
            String category,
            double confidence,
            String sourceName
    ) {}

    public record SourcePreview(
            String name,
            BusinessImportSpreadsheetParser.DatasetKind kind,
            int rowCount,
            String method,
            boolean recognized,
            List<String> warnings
    ) {}

    public record Preview(
            String businessName,
            List<ProductProposal> products,
            List<SourcePreview> sources,
            List<String> warnings,
            boolean aiUsed
    ) {}

    record SemanticResult(List<ProductProposal> products, List<String> warnings) {}
}
