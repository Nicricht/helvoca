package cl.helvoca.delivery;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Conservative, tenant-configured delivery coverage matcher.
 *
 * coverageTerms is a comma/semicolon/newline separated list of human-readable
 * area markers such as "Huechuraba, Pedro Fontova, Ciudad Empresarial".
 * This intentionally does not pretend to be geocoding. If no configured marker
 * matches the normalized address, coverage is not confirmed.
 */
@Component
public class DeliveryCoverageMatcher {

    public boolean matches(DeliveryZone zone, String address) {
        if (zone == null || !zone.isActive() || address == null || address.isBlank()) return false;
        String normalizedAddress = normalize(address);
        if (normalizedAddress.isBlank()) return false;
        for (String marker : markers(zone)) {
            if (!marker.isBlank() && normalizedAddress.contains(marker)) return true;
        }
        return false;
    }

    public List<String> configuredMarkers(DeliveryZone zone) {
        return new ArrayList<>(markers(zone));
    }

    private static Set<String> markers(DeliveryZone zone) {
        LinkedHashSet<String> markers = new LinkedHashSet<>();
        add(markers, zone.getName());
        String terms = zone.getCoverageTerms();
        if (terms != null) {
            for (String value : terms.split("[,;\\n\\r]+")) add(markers, value);
        }
        markers.removeIf(String::isBlank);
        return markers;
    }

    private static void add(Set<String> out, String value) {
        String normalized = normalize(value);
        if (!normalized.isBlank()) out.add(normalized);
    }

    static String normalize(String value) {
        if (value == null) return "";
        String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return withoutAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
