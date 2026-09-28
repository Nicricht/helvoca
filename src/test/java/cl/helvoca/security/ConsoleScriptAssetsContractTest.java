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
    private static final Pattern STYLESHEET_HREF =
            Pattern.compile("<link[^>]+rel=\\\"stylesheet\\\"[^>]+href=\\\"([^\\\"]+)\\\"");

    @Test
    void everyConsoleScriptLoadedBeforeAuthenticationRemainsPublic() throws Exception {
        Set<String> publicAssets = Set.of(SecurityConfig.PUBLIC_CONSOLE_ASSETS);
        Set<String> scripts = new HashSet<>();
        scripts.addAll(scriptPaths("src/main/resources/static/index.html"));
        scripts.addAll(scriptPaths("src/main/resources/static/settings.html"));
        scripts.addAll(scriptPaths("src/main/resources/static/operations.html"));

        for (String script : scripts) {
            assertTrue(
                    publicAssets.contains(script),
                    () -> "Console script must remain public: " + script);
        }
    }

    @Test
    void everyLocalConsoleStylesheetLoadedBeforeAuthenticationRemainsPublic() throws Exception {
        Set<String> publicAssets = Set.of(SecurityConfig.PUBLIC_CONSOLE_ASSETS);
        Set<String> stylesheets = new HashSet<>();
        stylesheets.addAll(stylesheetPaths("src/main/resources/static/index.html"));
        stylesheets.addAll(stylesheetPaths("src/main/resources/static/settings.html"));
        stylesheets.addAll(stylesheetPaths("src/main/resources/static/operations.html"));

        for (String stylesheet : stylesheets) {
            assertTrue(
                    publicAssets.contains(stylesheet),
                    () -> "Console stylesheet must remain public: " + stylesheet);
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

    private static Set<String> stylesheetPaths(String source) throws Exception {
        String html = Files.readString(Path.of(source));
        Matcher matcher = STYLESHEET_HREF.matcher(html);
        Set<String> stylesheets = new HashSet<>();
        while (matcher.find()) {
            String raw = matcher.group(1);
            if (!raw.startsWith("/")) continue;
            int query = raw.indexOf('?');
            stylesheets.add(query >= 0 ? raw.substring(0, query) : raw);
        }
        return stylesheets;
    }
}
