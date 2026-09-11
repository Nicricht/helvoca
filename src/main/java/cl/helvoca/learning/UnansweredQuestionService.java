package cl.helvoca.learning;

import cl.helvoca.common.NotFoundException;
import cl.helvoca.knowledge.KnowledgeItem;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class UnansweredQuestionService {
    private final UnansweredQuestionRepository questions;
    private final KnowledgeItemRepository knowledge;
    private final TenantProvider tenantProvider;

    public UnansweredQuestionService(UnansweredQuestionRepository questions,
                                     KnowledgeItemRepository knowledge,
                                     TenantProvider tenantProvider) {
        this.questions = questions;
        this.knowledge = knowledge;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public List<UnansweredQuestionDtos.Response> listOpen() {
        UUID businessId = tenantProvider.requireBusinessId();
        return questions.findAllByBusinessIdAndStatusOrderByLastSeenAtDesc(businessId, QuestionStatus.OPEN)
                .stream().map(UnansweredQuestionDtos.Response::from).toList();
    }

    @Transactional
    public UnansweredQuestionDtos.Response answer(UUID id, String answer) {
        UUID businessId = tenantProvider.requireBusinessId();
        UnansweredQuestion question = questions.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new NotFoundException("Question not found"));
        String cleanAnswer = required(answer);

        KnowledgeItem item = new KnowledgeItem();
        item.setBusinessId(businessId);
        item.setTitle(trim(question.getQuestion(), 200));
        item.setCategory("learned-from-calls");
        item.setContent(cleanAnswer);
        item.setActive(true);
        item = knowledge.saveAndFlush(item);

        question.setAnswer(cleanAnswer);
        question.setKnowledgeItemId(item.getId());
        question.setStatus(QuestionStatus.ANSWERED);
        question.setAnsweredAt(Instant.now());
        return UnansweredQuestionDtos.Response.from(questions.save(question));
    }

    @Transactional
    public void dismiss(UUID id) {
        UUID businessId = tenantProvider.requireBusinessId();
        UnansweredQuestion question = questions.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new NotFoundException("Question not found"));
        question.setStatus(QuestionStatus.DISMISSED);
        questions.save(question);
    }

    @Transactional
    public UnansweredQuestion record(UUID businessId, UUID callId, UUID customerId, String rawQuestion) {
        String question = required(rawQuestion);
        String normalized = normalize(question);
        UnansweredQuestion item = questions
                .findFirstByBusinessIdAndNormalizedQuestionAndStatus(businessId, normalized, QuestionStatus.OPEN)
                .orElse(null);
        if (item != null) {
            item.setOccurrences(item.getOccurrences() + 1);
            item.setLastSeenAt(Instant.now());
            if (item.getCallId() == null) item.setCallId(callId);
            if (item.getCustomerId() == null) item.setCustomerId(customerId);
            return questions.save(item);
        }
        item = new UnansweredQuestion();
        item.setBusinessId(businessId);
        item.setCallId(callId);
        item.setCustomerId(customerId);
        item.setQuestion(question);
        item.setNormalizedQuestion(normalized);
        item.setOccurrences(1);
        item.setStatus(QuestionStatus.OPEN);
        return questions.saveAndFlush(item);
    }

    private static String normalize(String value) {
        String clean = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        return trim(clean, 500);
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Value is required");
        return value.trim();
    }

    private static String trim(String value, int max) {
        if (value == null) return null;
        return value.length() > max ? value.substring(0, max) : value;
    }
}
