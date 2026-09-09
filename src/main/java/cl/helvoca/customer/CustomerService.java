package cl.helvoca.customer;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CustomerService {
    private final CustomerRepository customers;
    private final TenantProvider tenantProvider;
    private final AuditService auditService;

    public CustomerService(CustomerRepository customers, TenantProvider tenantProvider, AuditService auditService) {
        this.customers = customers;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> list() {
        UUID businessId = tenantProvider.requireBusinessId();
        return customers.findAllByBusinessIdOrderByCreatedAtDesc(businessId)
                .stream().map(CustomerResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(UUID id) {
        UUID businessId = tenantProvider.requireBusinessId();
        return CustomerResponse.from(requireCustomer(id, businessId));
    }

    @Transactional(readOnly = true)
    public CustomerResponse findByPhone(String phone) {
        UUID businessId = tenantProvider.requireBusinessId();
        return CustomerResponse.from(customers.findFirstByBusinessIdAndPhone(businessId, phone)
                .orElseThrow(() -> new NotFoundException("Customer not found")));
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        UUID businessId = tenantProvider.requireBusinessId();
        Customer customer = new Customer();
        customer.setBusinessId(businessId);
        apply(customer, request);
        Customer saved = customers.save(customer);
        auditService.success(businessId, "CUSTOMER_CREATE", "CUSTOMER", saved.getId());
        return CustomerResponse.from(saved);
    }

    @Transactional
    public CustomerResponse update(UUID id, CustomerRequest request) {
        UUID businessId = tenantProvider.requireBusinessId();
        Customer customer = requireCustomer(id, businessId);
        apply(customer, request);
        auditService.success(businessId, "CUSTOMER_UPDATE", "CUSTOMER", id);
        return CustomerResponse.from(customer);
    }

    private Customer requireCustomer(UUID id, UUID businessId) {
        return customers.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new NotFoundException("Customer not found"));
    }

    private static void apply(Customer customer, CustomerRequest request) {
        customer.setName(request.name());
        customer.setPhone(request.phone());
        customer.setEmail(request.email());
        customer.setNotes(request.notes());
    }
}
