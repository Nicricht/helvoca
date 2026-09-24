package cl.helvoca.messaging.meta;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

@Service
public class MetaWhatsAppAudioMediaService {
    private final MetaWhatsAppCloudClient meta;
    private final MetaWhatsAppAccessTokenResolver accessTokens;

    public MetaWhatsAppAudioMediaService(
            MetaWhatsAppCloudClient meta,
            MetaWhatsAppAccessTokenResolver accessTokens) {
        this.meta = meta;
        this.accessTokens = accessTokens;
    }

    public DownloadedAudio download(UUID businessId, String mediaId) {
        if (businessId == null) {
            throw new IllegalArgumentException("businessId is required");
        }
        String id = require(mediaId, "mediaId is required");
        String token = accessTokens.resolve(businessId)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new AudioMediaException(
                        "META_CREDENTIAL_MISSING",
                        false,
                        "Meta WhatsApp credential is not configured"));

        try {
            MetaWhatsAppCloudClient.DownloadedMedia media = meta.downloadMedia(id, token);
            return new DownloadedAudio(media.bytes(), normalizeMime(media.contentType()));
        } catch (MetaWhatsAppApiException failure) {
            throw new AudioMediaException(
                    failure.failureCode(),
                    failure.retryable(),
                    "Meta WhatsApp media download failed",
                    failure);
        } catch (AudioMediaException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new AudioMediaException(
                    "META_MEDIA_DOWNLOAD_FAILED",
                    true,
                    "Meta WhatsApp media download failed",
                    failure);
        }
    }

    static String normalizeMime(String contentType) {
        String value = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        int separator = value.indexOf(';');
        if (separator >= 0) value = value.substring(0, separator).trim();
        return value.startsWith("audio/") ? value : "audio/ogg";
    }

    private static String require(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    public record DownloadedAudio(byte[] bytes, String mimeType) {
    }

    public static final class AudioMediaException extends RuntimeException {
        private final String code;
        private final boolean retryable;

        AudioMediaException(String code, boolean retryable, String message) {
            super(message);
            this.code = code;
            this.retryable = retryable;
        }

        AudioMediaException(String code, boolean retryable, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
            this.retryable = retryable;
        }

        public String code() {
            return code;
        }

        public boolean retryable() {
            return retryable;
        }
    }
}
