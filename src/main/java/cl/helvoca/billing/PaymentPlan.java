package cl.helvoca.billing;

public record PaymentPlan(
        String code,
        String displayName,
        Integer monthlyPriceClp,
        boolean customPricing) {

    public PaymentPlan {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Payment plan code is required");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("Payment plan name is required");
        if (!customPricing && (monthlyPriceClp == null || monthlyPriceClp <= 0)) {
            throw new IllegalArgumentException("Fixed-price payment plan requires a positive monthly price");
        }
    }
}
