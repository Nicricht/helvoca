package cl.helvoca.billing;

public enum PlanCode {
    BASIC(2, 300),
    PRO(10, 2_000),
    BUSINESS(50, 10_000);

    private final int maxConcurrentCalls;
    private final int includedMinutesPerPeriod;

    PlanCode(int maxConcurrentCalls, int includedMinutesPerPeriod) {
        this.maxConcurrentCalls = maxConcurrentCalls;
        this.includedMinutesPerPeriod = includedMinutesPerPeriod;
    }

    public int getMaxConcurrentCalls() {
        return maxConcurrentCalls;
    }

    public int getIncludedMinutesPerPeriod() {
        return includedMinutesPerPeriod;
    }
}
