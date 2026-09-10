package cl.helvoca.onboarding;

import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class PublicBusinessSourceService {
    private static final int MAX_BYTES = 250_000;
    private static final int MAX_TEXT = 24_000;
    private static final int MAX_REDIRECTS = 3;
    private static final Pattern SCRIPT = Pattern.compile("(?is)<(script|style|noscript)[^>]*>.*?</\\1>");
    private static final Pattern TAG = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern SPACE = Pattern.compile("[\\s\\u00A0]+");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public SourceReadResult read(String rawUrl) {
        try {
            URI uri = normalize(rawUrl);
            for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
                validatePublicTarget(uri);
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(10))
                        .header("User-Agent", "HelvocaBusinessSetup/1.0")
                        .header("Accept", "text/html,text/plain,application/xhtml+xml;q=0.9,*/*;q=0.2")
                        .GET()
                        .build();
                HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status >= 300 && status < 400) {
                    String location = response.headers().firstValue("location").orElse(null);
                    response.body().close();
                    if (location == null || redirect == MAX_REDIRECTS) {
                        return unreadable(uri, "La fuente redirigió demasiadas veces o sin un destino válido.");
                    }
                    uri = uri.resolve(location);
                    continue;
                }
                if (status / 100 != 2) {
                    response.body().close();
                    return unreadable(uri, "La fuente pública respondió HTTP " + status + ". Prueba con el sitio web oficial si la red social bloquea lecturas automáticas.");
                }

                String contentType = response.headers().firstValue("content-type").orElse("").toLowerCase(Locale.ROOT);
                if (!(contentType.contains("text/html") || contentType.contains("text/plain") || contentType.contains("xhtml"))) {
                    response.body().close();
                    return unreadable(uri, "El enlace no devolvió una página de texto que Helvoca pueda analizar.");
                }

                byte[] bytes;
                try (InputStream input = response.body()) {
                    bytes = input.readNBytes(MAX_BYTES + 1);
                }
                boolean partial = bytes.length > MAX_BYTES;
                String html = new String(bytes, 0, Math.min(bytes.length, MAX_BYTES), StandardCharsets.UTF_8);
                String text = extractText(html);
                if (text.length() < 80) {
                    return unreadable(uri, "La fuente no expuso suficiente texto público. Algunas páginas de Instagram o Google Maps requieren usar el sitio web oficial.");
                }
                if (text.length() > MAX_TEXT) text = text.substring(0, MAX_TEXT);
                String warning = partial ? "La página era grande, así que Helvoca analizó solo una porción inicial de su contenido público." : null;
                return new SourceReadResult(uri.toString(), true, text, warning);
            }
            return unreadable(uri, "No se pudo leer la fuente pública.");
        } catch (IllegalArgumentException e) {
            return new SourceReadResult(rawUrl == null ? "" : rawUrl, false, "", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SourceReadResult(rawUrl, false, "", "La lectura de la fuente fue interrumpida.");
        } catch (Exception e) {
            return new SourceReadResult(rawUrl, false, "", "No pude leer esa fuente pública. Prueba con el sitio web oficial o configura manualmente.");
        }
    }

    static URI normalize(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) throw new IllegalArgumentException("Debes indicar un enlace público del negocio.");
        String value = rawUrl.trim();
        if (!value.contains("://")) value = "https://" + value;
        URI uri;
        try {
            uri = URI.create(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("El enlace no es válido.");
        }
        return uri;
    }

    static void validatePublicTarget(URI uri) throws Exception {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Solo se permiten enlaces públicos http o https.");
        }
        if (uri.getUserInfo() != null) throw new IllegalArgumentException("El enlace no puede incluir credenciales.");
        String host = uri.getHost();
        if (host == null || host.isBlank()) throw new IllegalArgumentException("El enlace debe incluir un dominio público válido.");
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) throw new IllegalArgumentException("El enlace usa un puerto no permitido.");
        if (host.equalsIgnoreCase("localhost") || host.endsWith(".localhost") || host.endsWith(".local")) {
            throw new IllegalArgumentException("No se permiten direcciones locales o internas.");
        }
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (isPrivateOrLocal(address)) throw new IllegalArgumentException("No se permiten direcciones locales o internas.");
        }
    }

    static boolean isPrivateOrLocal(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return true;
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int a = bytes[0] & 0xff;
            int b = bytes[1] & 0xff;
            return (a == 100 && b >= 64 && b <= 127) || (a == 169 && b == 254) || a == 0;
        }
        if (bytes.length == 16) {
            int first = bytes[0] & 0xff;
            return (first & 0xfe) == 0xfc;
        }
        return false;
    }

    static String extractText(String html) {
        String cleaned = SCRIPT.matcher(html == null ? "" : html).replaceAll(" ");
        cleaned = TAG.matcher(cleaned).replaceAll(" ");
        cleaned = cleaned.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
        return SPACE.matcher(cleaned).replaceAll(" ").trim();
    }

    private static SourceReadResult unreadable(URI uri, String warning) {
        return new SourceReadResult(uri == null ? "" : uri.toString(), false, "", warning);
    }

    public record SourceReadResult(String resolvedUrl, boolean readable, String text, String warning) {}
}
