package cl.helvoca.messaging.outbound;

import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.omnichannel.CustomerIdentity;
import cl.helvoca.omnichannel.CustomerIdentityRepository;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboundMessagingServiceSmsTest {
    @Mock OutboundMessageRepository messages;
    @Mock CustomerRepository customers;
    @Mock CustomerIdentityRepository identities;
    @Mock BusinessOperationRepository operations;
    @Mock OutboundContentResolver content;
    @Mock MessagingProviderRegistry providers;
    @Mock JdbcTemplate jdbc;

    @Test
    void preparesSmsForVerifiedCustomerPhone() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID identityId = UUID.randomUUID();

        Customer customer = mock(Customer.class);
        BusinessOperation operation = mock(BusinessOperation.class);
        CustomerIdentity identity = mock(CustomerIdentity.class);

        when(customers.findByIdAndBusinessId(customerId, businessId)).thenReturn(Optional.of(customer));
        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));
        when(operation.getCustomerId()).thenReturn(customerId);
        when(operation.getRevision()).thenReturn(1);
        when(identities.findAllByBusinessIdAndCustomerIdAndIdentityTypeAndVerificationStatusIn(
                eq(businessId), eq(customerId), eq(CustomerIdentity.Type.PHONE), anyList()))
                .thenReturn(List.of(identity));
        when(identity.getId()).thenReturn(identityId);
        when(identity.getNormalizedValue()).thenReturn("+56966939611");
        when(content.render(businessId, customerId, OutboundMessage.Purpose.BOOKING_CONFIRMATION, operation))
                .thenReturn("Reserva confirmada");
        when(messages.findByBusinessIdAndIdempotencyKey(eq(businessId), anyString()))
                .thenReturn(Optional.empty());
        when(messages.saveAndFlush(any(OutboundMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OutboundMessagingService service = new OutboundMessagingService(
                messages,
                customers,
                identities,
                operations,
                content,
                new OutboundMessagingProperties(),
                providers,
                jdbc);

        OutboundMessage prepared = service.prepare(
                businessId,
                customerId,
                OutboundMessage.Channel.SMS,
                OutboundMessage.Purpose.BOOKING_CONFIRMATION,
                operationId,
                null);

        assertEquals(OutboundMessage.Channel.SMS, prepared.getChannel());
        assertEquals("+56966939611", prepared.getRecipientAddress());
    }
}
