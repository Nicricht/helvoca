package cl.helvoca.phone;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhoneNumberRepository extends JpaRepository<PhoneNumber, UUID> {
    List<PhoneNumber> findAllByBusinessIdOrderByCreatedAtDesc(UUID businessId);
    Optional<PhoneNumber> findByIdAndBusinessId(UUID id, UUID businessId);
    Optional<PhoneNumber> findByPhoneNumber(String phoneNumber);
    Optional<PhoneNumber> findByPhoneNumberAndActiveTrue(String phoneNumber);
    boolean existsByPhoneNumber(String phoneNumber);
}
