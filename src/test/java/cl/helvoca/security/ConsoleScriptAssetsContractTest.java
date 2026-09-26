package cl.helvoca.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleScriptAssetsContractTest {
    private static final Pattern SCRIPT_SRC =
            Pattern.compile("<script[^>]+src=\\\"([^\\\"]+)\\\"");

    @Test
    void everyConsoleScriptLoadedBeforeAuthenticationRemainsPublic() throws Exception {
        Set<String> publicAssets = Set.of(SecurityConfig.PUBLIC_CONSOLE_ASSETS);
        Set<String> scripts = new HashSet<>();
        scripts.addAll(scriptPaths("src/main/resources/static/index.html"));
        scripts.addAll(scriptPaths("src/main/resources/static/settings.html"));

        for (String script : scripts) {
            assertTrue(
                    publicAssets.contains(script),
                    () -> "Console script must remain public: " + script);
        }
    }

    private static Set<String> scriptPaths(String source) throws Exception {
        String html = Files.readString(Path.of(source));
        Matcher matcher = SCRIPT_SRC.matcher(html);
        Set<String> scripts = new HashSet<>();
        while (matcher.find()) {
            String raw = matcher.group(1);
            int query = raw.indexOf('?');
            scripts.add(query >= 0 ? raw.substring(0, query) : raw);
        }
        return scripts;
    }
}
