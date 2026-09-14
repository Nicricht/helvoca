package cl.helvoca.operations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessQuoteRepository extends JpaRepository<BusinessQuote, UUID> {
    Optional<BusinessQuote> findByIdAndBusinessId(UUID id, UUID businessId);
    List<BusinessQuote> findAllByBusinessIdOrderByCreatedAtDesc(UUID businessId);
}
