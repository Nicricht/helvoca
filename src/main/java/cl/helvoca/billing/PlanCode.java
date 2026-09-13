package cl.helvoca.billing;

public enum PlanCode {
    EMPRENDE("Emprende", 1, 100, 24_990, 249, false, false),
    NEGOCIO("Negocio", 3, 300, 49_990, 199, false, true),
    PRO("Pro", 10, 600, 99_990, 169, false, false),
    ENTERPRISE("Enterprise", 10, 600, 199_990, null, true, false);

    private final String displayName;
    private final int maxConcurrentCalls;
    private final int includedMinutesPerPeriod;
    private final int monthlyPriceClp;
    private final Integer overagePerMinuteClp;
    private final boolean customPricing;
    private final boolean recommended;

    PlanCode(String displayName,
             int maxConcurrentCalls,
             int includedMinutesPerPeriod,
             int monthlyPriceClp,
             Integer overagePerMinuteClp,
             boolean customPricing,
             boolean recommended) {
        this.displayName = displayName;
        this.maxConcurrentCalls = maxConcurrentCalls;
        this.includedMinutesPerPeriod = includedMinutesPerPeriod;
        this.monthlyPriceClp = monthlyPriceClp;
        this.overagePerMinuteClp = overagePerMinuteClp;
        this.customPricing = customPricing;
        this.recommended = recommended;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMaxConcurrentCalls() {
        return maxConcurrentCalls;
    }

    public int getIncludedMinutesPerPeriod() {
        return includedMinutesPerPeriod;
    }

    public int getMonthlyPriceClp() {
        return monthlyPriceClp;
    }

    public Integer getOveragePerMinuteClp() {
        return overagePerMinuteClp;
    }

    public boolean isCustomPricing() {
        return customPricing;
    }

    public boolean isRecommended() {
        return recommended;
    }
}
