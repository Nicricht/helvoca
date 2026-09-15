package cl.helvoca.delivery;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class DeliveryCoverageService {
    private final DeliveryZoneRepository deliveryZones;

    public DeliveryCoverageService(DeliveryZoneRepository deliveryZones) {
        this.deliveryZones = deliveryZones;
    }

    public DeliveryZone resolve(UUID businessId, String address) {
        if (businessId == null) throw new IllegalArgumentException("El negocio es obligatorio para validar el despacho.");
        String normalizedAddress = normalizeCoverage(address);
        if (normalizedAddress.isBlank()) {
            throw new IllegalArgumentException("La dirección de despacho es inválida.");
        }

        List<ZoneMatch> matches = new ArrayList<>();
        for (DeliveryZone zone : deliveryZones.findAllByBusinessIdAndActiveTrueOrderByNameAsc(businessId)) {
            int score = coverageScore(zone, normalizedAddress);
            if (score > 0) matches.add(new ZoneMatch(zone, score));
        }

        if (matches.isEmpty()) {
            throw new IllegalArgumentException("La dirección no coincide con ninguna zona de despacho configurada.");
        }

        matches.sort(Comparator.comparingInt(ZoneMatch::score).reversed());
        if (matches.size() > 1 && matches.get(0).score() == matches.get(1).score()) {
            throw new IllegalArgumentException(
                    "La dirección coincide con más de una zona de despacho; la cobertura debe revisarse antes de confirmar.");
        }
        return matches.get(0).zone();
    }

    private static int coverageScore(DeliveryZone zone, String normalizedAddress) {
        int best = 0;
        String terms = zone.getCoverageTerms();
        if (terms == null || terms.isBlank()) return 0;
        for (String raw : terms.split("[,;|\\n\\r]+")) {
            String term = normalizeCoverage(raw);
            if (term.length() >= 3 && normalizedAddress.contains(term)) {
                best = Math.max(best, term.length());
            }
        }
        return best;
    }

    private static String normalizeCoverage(String value) {
        if (value == null) return "";
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return decomposed.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record ZoneMatch(DeliveryZone zone, int score) {}
}
