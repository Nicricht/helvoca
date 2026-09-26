package cl.helvoca.agent;

import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.CommercialOperationToolService;
import cl.helvoca.payment.BusinessPayment;
import cl.helvoca.payment.BusinessPaymentRepository;
import cl.helvoca.security.TenantDatabaseContext;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class CommercialSandboxE2eCertificationStartupRunner implements ApplicationRunner {
    private static final Logger log =
            LoggerFactory.getLogger(CommercialSandboxE2eCertificationStartupRunner.class);
    private static final String TEST_PHONE = "+56900009999";
    private static final String TEST_CUSTOMER_NAME = "RecepVoz Sandbox Buyer";
    private static final String TEST_PRODUCT_NAME = "RecepVoz Sandbox E2E Product";

    private final boolean enabled;
    private final String businessId;
    private final String runId;
    private final String amountClp;
    private final TenantDatabaseContext databaseContext;
    private final PlatformTransactionManager transactionManager;
    private final CommercialCheckoutCapabilityActivationStartupRunner activation;
    private final CustomerRepository customers;
    private final CatalogItemRepository catalog;
    private final BusinessOperationRepository operations;
    private final BusinessPaymentRepository payments;
    private final CommercialOperationToolService commercial;

    public CommercialSandboxE2eCertificationStartupRunner(
            @Value("${HELVOCA_COMMERCIAL_SANDBOX_CERTIFY_ON_STARTUP:false}") boolean enabled,
            @Value("${HELVOCA_COMMERCIAL_SANDBOX_CERTIFY_BUSINESS_ID:}") String businessId,
            @Value("${HELVOCA_COMMERCIAL_SANDBOX_CERTIFY_RUN_ID:}") String runId,
            @Value("${HELVOCA_COMMERCIAL_SANDBOX_CERTIFY_AMOUNT_CLP:1000}") String amountClp,
            TenantDatabaseContext databaseContext,
            PlatformTransactionManager transactionManager,
            CommercialCheckoutCapabilityActivationStartupRunner activation,
            CustomerRepository customers,
            CatalogItemRepository catalog,
            BusinessOperationRepository operations,
            BusinessPaymentRepository payments,
            CommercialOperationToolService commercial) {
        this.enabled = enabled;
        this.businessId = safe(businessId);
        this.runId = safe(runId);
        this.amountClp = safe(amountClp);
        this.databaseContext = databaseContext;
        this.transactionManager = transactionManager;
        this.activation = activation;
        this.customers = customers;
        this.catalog = catalog;
        this.operations = operations;
        this.payments = payments;
        this.commercial = commercial;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;

        UUID tenantId = parseTenant(businessId);
        String certificationRunId = requireRunId(runId);
        BigDecimal amount = parseAmount(amountClp);

        databaseContext.runAsTenant(tenantId, () -> {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            Seed seed = tx.execute(status -> prepareSeed(tenantId, certificationRunId, amount));
            if (seed == null) {
                throw new IllegalStateException("Commercial sandbox certification seed returned no result");
            }

            CertificationResult result = certify(seed);
            log.info(
                    "COMMERCIAL_SANDBOX_E2E_READY businessId={} runId={} journeyOperationId={} orderOperationId={} paymentOperationId={} paymentId={} externalId={} paymentStatus={} checkoutUrl={}",
                    tenantId,
                    certificationRunId,
                    result.journeyOperationId(),
                    result.orderOperationId(),
                    result.paymentOperationId(),
                    result.paymentId(),
                    result.externalId(),
                    result.paymentStatus(),
                    result.checkoutUrl());
        });
    }

    Seed prepareSeed(UUID businessId, String runId, BigDecimal amount) {
        activation.activate(businessId);

        Customer customer = customers.findFirstRawByBusinessIdAndPhone(businessId, TEST_PHONE)
                .orElseGet(() -> {
                    Customer created = new Customer();
                    created.setBusinessId(businessId);
                    created.setName(TEST_CUSTOMER_NAME);
                    created.setPhone(TEST_PHONE);
                    created.setNotes("Sandbox commercial E2E certification only");
                    return customers.saveAndFlush(created);
                });

        CatalogItem item = catalog.findAllByBusinessIdOrderByNameAsc(businessId).stream()
                .filter(candidate -> TEST_PRODUCT_NAME.equals(candidate.getName()))
                .findFirst()
                .orElseGet(() -> {
                    CatalogItem created = new CatalogItem();
                    created.setBusinessId(businessId);
                    created.setKind(CatalogItem.Kind.PRODUCT);
                    created.setName(TEST_PRODUCT_NAME);
                    created.setDescription("Producto interno para certificación Sandbox de RecepVoz");
                    created.setActive(true);
                    return created;
                });

        boolean itemChanged = false;
        if (!item.isActive()) {
            item.setActive(true);
            itemChanged = true;
        }
        if (item.getPrice() == null || item.getPrice().compareTo(amount) != 0) {
            item.setPrice(amount);
            itemChanged = true;
        }
        if (!"CLP".equalsIgnoreCase(item.getCurrency())) {
            item.setCurrency("CLP");
            itemChanged = true;
        }
        if (item.getId() == null || itemChanged) {
            item = catalog.saveAndFlush(item);
        }

        BusinessOperation journey = operations
                .findFirstSandboxCertificationJourney(businessId, runId)
                .orElse(null);
        if (journey == null) {
            journey = new BusinessOperation();
            journey.setBusinessId(businessId);
            journey.setCustomerId(customer.getId());
            journey.setType(BusinessOperation.Type.REQUEST);
            journey.setStatus(BusinessOperation.Status.CONFIRMED);
            journey.setSource(BusinessOrder.Source.WHATSAPP);
            journey.setRevision(1);
            journey.setContactName(TEST_CUSTOMER_NAME);
            journey.setContactPhone(TEST_PHONE);
            journey.setCurrency("CLP");
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("sandboxCertification", true);
            metadata.put("certificationRunId", runId);
            metadata.put("showcaseCatalogItemIds", List.of(item.getId().toString()));
            metadata.put("commercialStage", "MEDIA_QUEUED");
            metadata.put("lastAction", "PRODUCT_SHOWCASE_QUEUED");
            journey.setMetadata(metadata);
            journey = operations.saveAndFlush(journey);
        } else {
            if (!customer.getId().equals(journey.getCustomerId())) {
                throw new IllegalStateException(
                        "Certification journey already exists with a different customer");
            }
            Map<String, Object> metadata = journey.getMetadata();
            Object marker = metadata == null ? null : metadata.get("certificationRunId");
            if (!runId.equals(String.valueOf(marker))) {
                throw new IllegalStateException(
                        "Certification journey id collision detected");
            }
        }

        return new Seed(businessId, runId, customer.getId(), item.getId(), journey.getId());
    }

    CertificationResult certify(Seed seed) {
        BusinessOperation journey = requireJourney(seed);

        UUID existingPaymentOperationId = metadataUuid(journey, "paymentOperationId");
        if (existingPaymentOperationId != null) {
            BusinessPayment existing = payments
                    .findByOperationIdAndBusinessId(existingPaymentOperationId, seed.businessId())
                    .orElse(null);
            if (existing != null) {
                return result(seed.journeyOperationId(), journey, existing);
            }
        }

        if (metadataUuid(journey, "selectedCatalogItemId") == null) {
            requireSuccess(execute(seed, CommercialOperationToolService.SHOWCASE_SELECTION_TOOL,
                    new JSONObject()
                            .put("operationId", seed.journeyOperationId().toString())
                            .put("selectionIndex", 1)));
        }

        journey = requireJourney(seed);
        if (metadataUuid(journey, "quotedCatalogItemId") == null) {
            requireSuccess(execute(seed, CommercialOperationToolService.SHOWCASE_QUOTE_TOOL,
                    new JSONObject()
                            .put("operationId", seed.journeyOperationId().toString())
                            .put("quantity", 1)));
        }

        journey = requireJourney(seed);
        UUID orderOperationId = metadataUuid(journey, "orderOperationId");
        BusinessOperation orderOperation = orderOperationId == null
                ? null
                : operations.findByIdAndBusinessId(orderOperationId, seed.businessId()).orElse(null);

        if (orderOperation == null) {
            JSONObject quoted = requireSuccess(execute(
                    seed,
                    CommercialOperationToolService.SHOWCASE_ORDER_TOOL,
                    new JSONObject()
                            .put("operationId", seed.journeyOperationId().toString())
                            .put("quantity", 1)
                            .put("fulfillmentType", "PICKUP")));
            orderOperationId = UUID.fromString(quoted.getString("operationId"));
            orderOperation = operations.findByIdAndBusinessId(orderOperationId, seed.businessId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Commercial certification order draft was not persisted"));
        }

        if (orderOperation.getStatus() == BusinessOperation.Status.AWAITING_CONFIRMATION) {
            JSONObject confirmed = confirmWithOneRequote(
                    seed,
                    "create_order",
                    orderOperation.getId(),
                    orderOperation.getConfirmationToken(),
                    "ORDER_TOTAL_CHANGED");
            requireSuccess(confirmed);
        }

        orderOperation = operations.findByIdAndBusinessId(orderOperationId, seed.businessId())
                .orElseThrow(() -> new IllegalStateException(
                        "Commercial certification order operation disappeared"));
        if (orderOperation.getStatus() != BusinessOperation.Status.CONFIRMED) {
            throw new IllegalStateException(
                    "Commercial certification order is not CONFIRMED");
        }

        journey = requireJourney(seed);
        UUID paymentOperationId = metadataUuid(journey, "paymentOperationId");
        BusinessOperation paymentOperation = paymentOperationId == null
                ? null
                : operations.findByIdAndBusinessId(paymentOperationId, seed.businessId()).orElse(null);

        if (paymentOperation == null) {
            JSONObject quotedPayment = requireSuccess(execute(
                    seed,
                    "quote_payment",
                    new JSONObject().put("targetOperationId", orderOperationId.toString())));
            paymentOperationId = UUID.fromString(quotedPayment.getString("operationId"));
            paymentOperation = operations.findByIdAndBusinessId(paymentOperationId, seed.businessId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Commercial certification payment draft was not persisted"));
        }

        BusinessPayment existing = payments
                .findByOperationIdAndBusinessId(paymentOperationId, seed.businessId())
                .orElse(null);
        if (existing != null) {
            return result(seed.journeyOperationId(), requireJourney(seed), existing);
        }

        if (paymentOperation.getStatus() != BusinessOperation.Status.AWAITING_CONFIRMATION
                || paymentOperation.getConfirmationToken() == null) {
            throw new IllegalStateException(
                    "Commercial certification payment is not awaiting confirmation");
        }

        JSONObject createdPayment = confirmWithOneRequote(
                seed,
                "create_payment",
                paymentOperation.getId(),
                paymentOperation.getConfirmationToken(),
                "PAYMENT_TERMS_CHANGED");
        requireSuccess(createdPayment);

        BusinessPayment payment = payments
                .findByOperationIdAndBusinessId(paymentOperationId, seed.businessId())
                .orElseThrow(() -> new IllegalStateException(
                        "Commercial certification payment projection was not persisted"));

        return result(seed.journeyOperationId(), requireJourney(seed), payment);
    }

    private JSONObject confirmWithOneRequote(Seed seed,
                                             String toolName,
                                             UUID operationId,
                                             UUID confirmationToken,
                                             String requoteCode) {
        if (confirmationToken == null) {
            throw new IllegalStateException(toolName + " has no confirmation token");
        }
        JSONObject first = new JSONObject(execute(
                seed,
                toolName,
                new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("confirmationToken", confirmationToken.toString())));
        if (first.optBoolean("success", false)) return first;

        JSONObject error = first.optJSONObject("error");
        if (error == null || !requoteCode.equals(error.optString("code", ""))) return first;
        JSONObject data = first.optJSONObject("data");
        if (data == null || data.isNull("confirmationToken")) return first;

        return new JSONObject(execute(
                seed,
                toolName,
                new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("confirmationToken", data.getString("confirmationToken"))));
    }

    private String execute(Seed seed, String toolName, JSONObject args) {
        return commercial.execute(
                seed.businessId(),
                seed.customerId(),
                null,
                TEST_PHONE,
                BusinessOrder.Source.WHATSAPP,
                toolName,
                args.toString());
    }

    private BusinessOperation requireJourney(Seed seed) {
        return operations.findByIdAndBusinessId(
                        seed.journeyOperationId(), seed.businessId())
                .orElseThrow(() -> new IllegalStateException(
                        "Commercial certification journey is missing"));
    }

    private CertificationResult result(UUID journeyId,
                                       BusinessOperation journey,
                                       BusinessPayment payment) {
        UUID orderOperationId = metadataUuid(journey, "orderOperationId");
        return new CertificationResult(
                journeyId,
                orderOperationId,
                payment.getOperationId(),
                payment.getId(),
                payment.getExternalId(),
                payment.getStatus(),
                payment.getCheckoutUrl());
    }

    private static JSONObject requireSuccess(String raw) {
        return requireSuccess(new JSONObject(raw));
    }

    private static JSONObject requireSuccess(JSONObject result) {
        if (result == null || !result.optBoolean("success", false)) {
            JSONObject error = result == null ? null : result.optJSONObject("error");
            String code = error == null ? "UNKNOWN" : error.optString("code", "UNKNOWN");
            String message = error == null ? "Commercial tool failed" : error.optString("message", "");
            throw new IllegalStateException(code + ": " + message);
        }
        JSONObject data = result.optJSONObject("data");
        if (data == null) {
            throw new IllegalStateException("Commercial tool returned no data");
        }
        return data;
    }

    private static UUID metadataUuid(BusinessOperation operation, String key) {
        if (operation == null || operation.getMetadata() == null) return null;
        Object value = operation.getMetadata().get(key);
        if (value == null) return null;
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private static UUID parseTenant(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "HELVOCA_COMMERCIAL_SANDBOX_CERTIFY_BUSINESS_ID must be a valid UUID", e);
        }
    }

    private static String requireRunId(String raw) {
        if (!raw.matches("[A-Za-z0-9_-]{3,80}")) {
            throw new IllegalStateException(
                    "HELVOCA_COMMERCIAL_SANDBOX_CERTIFY_RUN_ID must match [A-Za-z0-9_-]{3,80}");
        }
        return raw;
    }

    private static BigDecimal parseAmount(String raw) {
        try {
            BigDecimal amount = new BigDecimal(raw);
            if (amount.compareTo(BigDecimal.ONE) < 0
                    || amount.compareTo(new BigDecimal("100000")) > 0
                    || amount.stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException();
            }
            return amount;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "HELVOCA_COMMERCIAL_SANDBOX_CERTIFY_AMOUNT_CLP must be an integer between 1 and 100000", e);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    record Seed(UUID businessId,
                String runId,
                UUID customerId,
                UUID catalogItemId,
                UUID journeyOperationId) {}

    record CertificationResult(UUID journeyOperationId,
                               UUID orderOperationId,
                               UUID paymentOperationId,
                               UUID paymentId,
                               String externalId,
                               BusinessPayment.Status paymentStatus,
                               String checkoutUrl) {}
}
