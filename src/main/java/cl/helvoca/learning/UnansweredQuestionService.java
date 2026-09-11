package cl.helvoca.learning;

import cl.helvoca.common.NotFoundException;
import cl.helvoca.knowledge.KnowledgeItem;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class UnansweredQuestionService {
    private static final String LEARNED_CATEGORY = "Aprendido por Helvoca";

    private final UnansweredQuestionRepository repository;
    private final KnowledgeItemRepository knowledge;
    private final TenantProvider tenantProvider;

    public UnansweredQuestionService(UnansweredQuestionRepository repository,
                                     KnowledgeItemRepository knowledge,
                                     TenantProvider tenantProvider) {
        this.repository = repository;
        this.knowledge = knowledge;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public List<UnansweredQuestionResponse> list() {
        UUID businessId = tenantProvider.requireBusinessId();
        return repository.findAllByBusinessIdOrderByLastAskedAtDesc(businessId)
                .stream().map(UnansweredQuestionResponse::from).toList();
    }

    @Transactional
    public UnansweredQuestion recordFromCall(UUID businessId, UUID customerId, UUID callId, String question) {
        String cleaned = cleanQuestion(question);
        String key = key(cleaned);
        Instant now = Instant.now();
        UnansweredQuestion item = repository.findByBusinessIdAndQuestionKey(businessId, key)
                .orElseGet(UnansweredQuestion::new);
        if (item.getId() == null) {
            item.setBusinessId(businessId);
            item.setQuestionKey(key);
            item.setQuestion(cleaned);
            item.setFirstAskedAt(now);
            item.setOccurrences(1);
        } else {
            item.setOccurrences(item.getOccurrences() + 1);
            item.setQuestion(cleaned);
        }
        item.setCustomerId(customerId);
        item.setCallId(callId);
        item.setLastAskedAt(now);
        item.setStatus(UnansweredQuestionStatus.OPEN);
        item.setAnswer(null);
        item.setAnsweredAt(null);
        return repository.saveAndFlush(item);
    }

    @Transactional
    public UnansweredQuestionResponse resolve(UUID id, ResolveUnansweredQuestionRequest request) {
        UUID businessId = tenantProvider.requireBusinessId();
        UnansweredQuestion item = repository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new NotFoundException("Pregunta no encontrada"));
        String answer = request.answer().trim();
        item.setAnswer(answer);
        item.setStatus(UnansweredQuestionStatus.RESOLVED);
        item.setAnsweredAt(Instant.now());
        repository.save(item);

        String title = truncate(item.getQuestion(), 200);
        KnowledgeItem learned = knowledge.findFirstByBusinessIdAndCategoryAndTitleIgnoreCase(
                        businessId, LEARNED_CATEGORY, title)
                .orElseGet(KnowledgeItem::new);
        if (learned.getId() == null) {
            learned.setBusinessId(businessId);
            learned.setTitle(title);
            learned.setCategory(LEARNED_CATEGORY);
        }
        learned.setContent(answer);
        learned.setActive(true);
        knowledge.saveAndFlush(learned);
        return UnansweredQuestionResponse.from(item);
    }

    @Transactional
    public UnansweredQuestionResponse ignore(UUID id) {
        UUID businessId = tenantProvider.requireBusinessId();
        UnansweredQuestion item = repository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new NotFoundException("Pregunta no encontrada"));
        item.setStatus(UnansweredQuestionStatus.IGNORED);
        return UnansweredQuestionResponse.from(repository.save(item));
    }

    private static String cleanQuestion(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("La pregunta no puede estar vacía.");
        return truncate(value.trim().replaceAll("\\s+", " "), 1000);
    }

    private static String key(String question) {
        String normalized = question.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim().replaceAll("\\s+", " ");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
