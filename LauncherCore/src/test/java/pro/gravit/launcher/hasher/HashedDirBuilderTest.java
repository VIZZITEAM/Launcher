package pro.gravit.launcher.hasher;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class HashedDirBuilderTest {
    @Test
    public void canBuildNestedHashedDir() {
        HashedDir root = new HashedDir();
        HashedDir mods = root.getOrCreateDir("mods");
        HashedDir nested = new HashedDir();
        mods.putEntry("nested", nested);

        Assertions.assertTrue(root.getEntry("mods") instanceof HashedDir);
        HashedDir resolvedMods = (HashedDir) root.getEntry("mods");
        Assertions.assertSame(nested, resolvedMods.getEntry("nested"));
        Assertions.assertSame(mods, root.getOrCreateDir("mods"));
    }
}
