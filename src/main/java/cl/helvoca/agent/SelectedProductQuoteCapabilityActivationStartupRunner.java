package cl.helvoca.agent;

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
public class SelectedProductQuoteCapabilityActivationStartupRunner implements ApplicationRunner {
    private static final Logger log =
            LoggerFactory.getLogger(SelectedProductQuoteCapabilityActivationStartupRunner.class);

    private final boolean enabled;
    private final String businessId;
    private final TenantDatabaseContext databaseContext;
    private final PlatformTransactionManager transactionManager;
    private final AiAgentRepository agents;

    public SelectedProductQuoteCapabilityActivationStartupRunner(
            @Value("${HELVOCA_SELECTED_PRODUCT_QUOTE_ACTIVATE_ON_STARTUP:false}") boolean enabled,
            @Value("${HELVOCA_SELECTED_PRODUCT_QUOTE_BUSINESS_ID:}") String businessId,
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
                    "HELVOCA_SELECTED_PRODUCT_QUOTE_BUSINESS_ID must be a valid UUID", e);
        }

        databaseContext.runAsTenant(tenantId, () -> {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            ActivationResult result = tx.execute(status -> activate(tenantId));
            if (result == null) {
                throw new IllegalStateException(
                        "Selected product quote capability activation returned no result");
            }
            log.info(
                    "SELECTED_PRODUCT_QUOTE_CAPABILITY_ACTIVATED businessId={} changed={} listCatalog={} createQuote={}",
                    tenantId,
                    result.changed(),
                    result.listCatalog(),
                    result.createQuote());
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

        if (changed) {
            agent.setCapabilities(capabilities);
            agents.saveAndFlush(agent);
        }

        return new ActivationResult(
                changed,
                capabilities.contains(AiCapability.LIST_CATALOG),
                capabilities.contains(AiCapability.CREATE_QUOTE));
    }

    record ActivationResult(boolean changed, boolean listCatalog, boolean createQuote) {}
}
