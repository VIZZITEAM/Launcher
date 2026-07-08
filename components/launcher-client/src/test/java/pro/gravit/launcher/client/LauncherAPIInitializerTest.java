package pro.gravit.launcher.client;

import org.junit.jupiter.api.Test;
import pro.gravit.launcher.core.api.LauncherAPIHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class LauncherAPIInitializerTest {
    @Test
    void httpModeProvidesProfileApiBeforeAuthMethodSelection() throws Exception {
        LauncherAPIInitializer.initialize(null, "https://example.test/api", List.of());

        assertNotNull(LauncherAPIHolder.get());
        assertNotNull(LauncherAPIHolder.profile());
    }
}
