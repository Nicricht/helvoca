package cl.helvoca.messaging.meta;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MetaWhatsAppTenantConfigRepository
        extends JpaRepository<MetaWhatsAppTenantConfig, UUID> {
}
