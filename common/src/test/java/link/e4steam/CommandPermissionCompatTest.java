package link.e4steam;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandPermissionCompatTest {
    @Test
    void delegatesToLegacyIntegerPermissionApi() {
        assertTrue(CommandPermissionCompat.hasPermission(new LegacySource(4), 4));
        assertFalse(CommandPermissionCompat.hasPermission(new LegacySource(3), 4));
    }

    @Test
    void unknownApiFailsClosed() {
        assertFalse(CommandPermissionCompat.hasPermission(new UnknownSource(null), 4));
        assertFalse(CommandPermissionCompat.hasPermission(new UnknownSource(new Object()), 4));
        assertFalse(CommandPermissionCompat.hasPermission(new Object(), 4));
    }

    public static final class LegacySource {
        private final int level;

        LegacySource(int level) {
            this.level = level;
        }

        public boolean hasPermission(int required) {
            return level >= required;
        }
    }

    public static final class UnknownSource {
        private final Object entity;

        UnknownSource(Object entity) {
            this.entity = entity;
        }

        public Object getEntity() {
            return entity;
        }
    }
}
