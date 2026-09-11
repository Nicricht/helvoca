package cl.helvoca.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    List<Customer> findAllByBusinessIdOrderByCreatedAtDesc(UUID businessId);
    Optional<Customer> findByIdAndBusinessId(UUID id, UUID businessId);
    Optional<Customer> findFirstByBusinessIdAndPhone(UUID businessId, String phone);
    long countByBusinessId(UUID businessId);
}
