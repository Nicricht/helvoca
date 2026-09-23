package cl.helvoca.messaging.audio;

public interface AudioTranscriptionProvider {
    String id();

    boolean configured();

    TranscriptionResult transcribe(AudioInput input);
}
