package cl.helvoca.messaging.audio;

public interface AudioTranscriber {
    TranscriptionResult transcribe(AudioInput input);
}
