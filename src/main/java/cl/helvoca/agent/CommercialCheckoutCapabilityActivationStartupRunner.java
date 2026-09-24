package cl.helvoca.agent;

import cl.helvoca.operations.BusinessOperationCapability;
import cl.helvoca.security.TenantDatabaseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.EnumSet;
import java.util.UUID;

@Component
public class CommercialCheckoutCapabilityActivationStartupRunner implements ApplicationRunner {
    private static final Logger log =
            LoggerFactory.getLogger(CommercialCheckoutCapabilityActivationStartupRunner.class);

    private final boolean enabled;
    private final String businessId;
    private final TenantDatabaseContext databaseContext;
    private final PlatformTransactionManager transactionManager;
    private final AiAgentRepository agents;

    public CommercialCheckoutCapabilityActivationStartupRunner(
            @Value("${HELVOCA_COMMERCIAL_CHECKOUT_ACTIVATE_ON_STARTUP:false}") boolean enabled,
            @Value("${HELVOCA_COMMERCIAL_CHECKOUT_BUSINESS_ID:}") String businessId,
            TenantDatabaseContext databaseContext,
            PlatformTransactionManager transactionManager,
            AiAgentRepository agents) {
        this.enabled = enabled;
        this.businessId = businessId == null ? "" : businessId.trim();
        this.databaseContext = databaseContext;
        this.transactionManager = transactionManager;
        this.agents = agents;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;

        UUID tenantId;
        try {
            tenantId = UUID.fromString(businessId);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "HELVOCA_COMMERCIAL_CHECKOUT_BUSINESS_ID must be a valid UUID", e);
        }

        databaseContext.runAsTenant(tenantId, () -> {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            ActivationResult result = tx.execute(status -> activate(tenantId));
            if (result == null) {
                throw new IllegalStateException("Commercial checkout capability activation returned no result");
            }
            log.info(
                    "COMMERCIAL_CHECKOUT_CAPABILITY_ACTIVATED businessId={} changed={} catalog={} quote={} order={} payment={} whatsapp={}",
                    tenantId,
                    result.changed(),
                    result.catalog(),
                    result.quote(),
                    result.order(),
                    result.payment(),
                    result.whatsapp());
        });
    }

    ActivationResult activate(UUID businessId) {
        AiAgent agent = agents.findByBusinessId(businessId)
                .orElseThrow(() -> new IllegalStateException("AI agent not found for business"));

        EnumSet<AiCapability> capabilities = agent.getCapabilities() == null
                || agent.getCapabilities().isEmpty()
                ? EnumSet.noneOf(AiCapability.class)
                : EnumSet.copyOf(agent.getCapabilities());

        boolean changed = false;
        changed |= capabilities.add(AiCapability.LIST_CATALOG);
        changed |= capabilities.add(AiCapability.CREATE_QUOTE);
        changed |= capabilities.add(AiCapability.QUOTE_ORDER);
        changed |= capabilities.add(AiCapability.UPDATE_ORDER);
        changed |= capabilities.add(AiCapability.CREATE_ORDER);
        changed |= capabilities.add(AiCapability.GET_ORDER_STATUS);
        changed |= capabilities.add(AiCapability.CANCEL_ORDER);
        changed |= capabilities.add(AiCapability.QUOTE_PAYMENT);
        changed |= capabilities.add(AiCapability.UPDATE_PAYMENT);
        changed |= capabilities.add(AiCapability.CREATE_PAYMENT);
        changed |= capabilities.add(AiCapability.GET_PAYMENT_STATUS);
        changed |= capabilities.add(AiCapability.CANCEL_PAYMENT);
        changed |= capabilities.add(AiCapability.SEND_WHATSAPP_OPERATION);

        if (changed) {
            agent.setCapabilities(capabilities);
            agents.saveAndFlush(agent);
        }

        return new ActivationResult(
                changed,
                capabilities.contains(AiCapability.LIST_CATALOG),
                capabilities.contains(AiCapability.CREATE_QUOTE),
                capabilities.containsAll(BusinessOperationCapability.ORDER.aiCapabilities()),
                capabilities.containsAll(BusinessOperationCapability.PAYMENT.aiCapabilities()),
                capabilities.contains(AiCapability.SEND_WHATSAPP_OPERATION));
    }

    record ActivationResult(boolean changed,
                            boolean catalog,
                            boolean quote,
                            boolean order,
                            boolean payment,
                            boolean whatsapp) {}
}
