package cl.helvoca.telephony;

public class CallCapacityExceededException extends RuntimeException {
    public CallCapacityExceededException(String message) {
        super(message);
    }
}
