package cl.helvoca.quality;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationReplayFixtureSuiteTest {

    @TestFactory
    Collection<DynamicTest> everyCheckedInReplayMatchesItsExpectedQualitySignature() throws Exception {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:quality/replays/*.json");
        assertTrue(resources.length > 0, "At least one replay fixture must be checked in");

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ConversationReplayRunner runner = new ConversationReplayRunner();

        return Arrays.stream(resources)
                .sorted(Comparator.comparing(Resource::getFilename, Comparator.nullsLast(String::compareTo)))
                .map(resource -> DynamicTest.dynamicTest(
                        resource.getFilename(),
                        () -> {
                            ConversationReplayFixture fixture;
                            try (var input = resource.getInputStream()) {
                                fixture = mapper.readValue(input, ConversationReplayFixture.class);
                            }
                            ConversationReplayRunner.ReplayResult result = runner.run(fixture);
                            assertTrue(result.matchesExpected(), () ->
                                    "Replay " + fixture.name()
                                            + " changed quality signature. missing="
                                            + result.missingFindingCodes()
                                            + " unexpected=" + result.unexpectedFindingCodes()
                                            + " expectedPass=" + fixture.expected().passed()
                                            + " actualPass=" + result.report().passed());
                        }))
                .toList();
    }
}
