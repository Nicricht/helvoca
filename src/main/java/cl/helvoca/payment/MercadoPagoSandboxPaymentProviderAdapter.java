package cl.helvoca.payment;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class MercadoPagoSandboxPaymentProviderAdapter implements PaymentProviderAdapter {
    private static final String PROVIDER = "mercadopago";

    private final PaymentProviderConfigService configs;
    private final PaymentProviderCredentialResolver credentialResolver;
    private final MercadoPagoOrderClient client;

    public MercadoPagoSandboxPaymentProviderAdapter(PaymentProviderConfigService configs,
                                                     PaymentProviderCredentialResolver credentialResolver,
                                                     MercadoPagoOrderClient client) {
        this.configs = configs;
        this.credentialResolver = credentialResolver;
        this.client = client;
    }

    @Override
    public String providerCode() { return PROVIDER; }

    @Override
    public boolean supports(UUID businessId) {
        PaymentProviderConfig config = configs.requireSandboxMercadoPago(businessId);
        return config != null && credentialResolver.resolve(config.getCredentialRef()).isPresent();
    }

    @Override
    public CreateResult create(CreateCommand command) {
        Context context = requireContext(command.businessId());
        requireSupportedMoney(command.amount(), command.currency());
        MercadoPagoOrderClient.RemoteOrder order = client.create(
                context.credentials().accessToken(),
                command.idempotencyKey(),
                command.paymentOperationId(),
                command.amount());
        verifyExternalReference(order, command.paymentOperationId());
        return new CreateResult(
                order.id(),
                order.checkoutUrl(),
                mapStatus(order.status(), order.statusDetail()),
                metadata(order));
    }

    @Override
    public StatusResult getStatus(StatusCommand command) {
        Context context = requireContext(command.businessId());
        MercadoPagoOrderClient.RemoteOrder order = client.get(
                context.credentials().accessToken(), command.externalId());
        return new StatusResult(mapStatus(order.status(), order.statusDetail()), metadata(order));
    }

    @Override
    public CancelResult cancel(CancelCommand command) {
        Context context = requireContext(command.businessId());
        MercadoPagoOrderClient.RemoteOrder order = client.cancel(
                context.credentials().accessToken(),
                command.externalId(),
                command.idempotencyKey() + ":cancel");
        return new CancelResult(mapStatus(order.status(), order.statusDetail()), metadata(order));
    }

    PaymentProviderCredentialResolver.Credentials credentialsForWebhook(UUID businessId) {
        return requireContext(businessId).credentials();
    }

    private Context requireContext(UUID businessId) {
        PaymentProviderConfig config = configs.requireSandboxMercadoPago(businessId);
        if (config == null) throw new IllegalStateException("Mercado Pago sandbox is not enabled for tenant.");
        PaymentProviderCredentialResolver.Credentials credentials = credentialResolver
                .resolve(config.getCredentialRef())
                .orElseThrow(() -> new IllegalStateException("Mercado Pago sandbox credentials are unavailable."));
        return new Context(config, credentials);
    }

    private static void requireSupportedMoney(BigDecimal amount, String currency) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive.");
        }
        if (!"CLP".equalsIgnoreCase(currency)) {
            throw new IllegalArgumentException("The current Mercado Pago sandbox adapter only supports CLP.");
        }
        if (amount.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("Mercado Pago Orders requires an integer CLP amount.");
        }
    }

    private static void verifyExternalReference(MercadoPagoOrderClient.RemoteOrder order, UUID operationId) {
        if (order.externalReference() != null
                && !operationId.toString().equals(order.externalReference())) {
            throw new IllegalStateException("Mercado Pago returned an unexpected external reference.");
        }
    }

    static BusinessPayment.Status mapStatus(String remoteStatus, String remoteDetail) {
        String status = normalize(remoteStatus);
        String detail = normalize(remoteDetail);
        return switch (status) {
            case "created", "action_required" -> BusinessPayment.Status.REQUIRES_ACTION;
            case "processing" -> BusinessPayment.Status.PENDING;
            case "processed" -> {
                if ("refunded".equals(detail)) yield BusinessPayment.Status.REFUNDED;
                if ("partially_refunded".equals(detail)) yield BusinessPayment.Status.FAILED;
                yield "accredited".equals(detail)
                        ? BusinessPayment.Status.SUCCEEDED
                        : BusinessPayment.Status.PENDING;
            }
            case "refunded" -> BusinessPayment.Status.REFUNDED;
            case "canceled", "cancelled" -> BusinessPayment.Status.CANCELLED;
            case "expired" -> BusinessPayment.Status.EXPIRED;
            case "failed", "charged_back" -> BusinessPayment.Status.FAILED;
            default -> BusinessPayment.Status.PENDING;
        };
    }

    private static Map<String, Object> metadata(MercadoPagoOrderClient.RemoteOrder order) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("providerApi", "mercadopago-orders");
        metadata.put("sandbox", true);
        if (order.status() != null) metadata.put("remoteStatus", order.status());
        if (order.statusDetail() != null) metadata.put("remoteStatusDetail", order.statusDetail());
        if (order.externalReference() != null) metadata.put("externalReference", order.externalReference());
        if (order.currency() != null) metadata.put("remoteCurrency", order.currency());
        return metadata;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record Context(PaymentProviderConfig config,
                           PaymentProviderCredentialResolver.Credentials credentials) {}
}
