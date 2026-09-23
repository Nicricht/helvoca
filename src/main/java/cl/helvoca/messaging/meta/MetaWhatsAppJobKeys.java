package cl.helvoca.messaging.meta;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class MetaWhatsAppJobKeys {
    private static final int MAX_SAFE_WAMID = 120;

    private MetaWhatsAppJobKeys() {}

    public static String text(String wamid) {
        return "wa-in-text:" + safeWamid(wamid);
    }

    public static String audio(String wamid) {
        return "wa-in-audio:" + safeWamid(wamid);
    }

    public static String recovery(String wamid) {
        return "wa-audio-recovery:" + safeWamid(wamid);
    }

    public static UUID correlationId(UUID businessId, String wamid) {
        if (businessId == null) throw new IllegalArgumentException("businessId is required");
        String material = "wa:" + businessId + ":" + requireWamid(wamid);
        return UUID.nameUUIDFromBytes(material.getBytes(StandardCharsets.UTF_8));
    }

    static String safeWamid(String wamid) {
        String clean = requireWamid(wamid).replaceAll("[^A-Za-z0-9._:-]", "_");
        return clean.length() <= MAX_SAFE_WAMID ? clean : clean.substring(0, MAX_SAFE_WAMID);
    }

    private static String requireWamid(String wamid) {
        if (wamid == null || wamid.trim().isBlank()) {
            throw new IllegalArgumentException("Meta WhatsApp message id is required");
        }
        return wamid.trim();
    }
}
