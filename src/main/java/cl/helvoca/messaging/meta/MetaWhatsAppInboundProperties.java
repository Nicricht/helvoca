package cl.helvoca.messaging.meta;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.meta.whatsapp.inbound")
public class MetaWhatsAppInboundProperties {
    private boolean asyncTextEnabled = false;
    private boolean asyncAudioEnabled = false;
    private int textMaxAttempts = 5;
    private int audioMaxAttempts = 6;

    public boolean isAsyncTextEnabled() { return asyncTextEnabled; }
    public void setAsyncTextEnabled(boolean asyncTextEnabled) { this.asyncTextEnabled = asyncTextEnabled; }

    public boolean isAsyncAudioEnabled() { return asyncAudioEnabled; }
    public void setAsyncAudioEnabled(boolean asyncAudioEnabled) { this.asyncAudioEnabled = asyncAudioEnabled; }

    public int getTextMaxAttempts() { return textMaxAttempts; }
    public void setTextMaxAttempts(int textMaxAttempts) {
        this.textMaxAttempts = Math.max(1, Math.min(textMaxAttempts, 20));
    }

    public int getAudioMaxAttempts() { return audioMaxAttempts; }
    public void setAudioMaxAttempts(int audioMaxAttempts) {
        this.audioMaxAttempts = Math.max(1, Math.min(audioMaxAttempts, 20));
    }
}
