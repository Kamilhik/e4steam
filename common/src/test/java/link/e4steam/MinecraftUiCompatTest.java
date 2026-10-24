package link.e4steam;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinecraftUiCompatTest {
    @Test
    void readsPrivateHudFieldUsedByMinecraftTwentySix() throws Exception {
        Object hud = MinecraftUiCompat.readZeroArgumentMember(new GuiWithPrivateHud(), "hud", "getHud");

        assertEquals("private-hud", hud);
    }

    @Test
    void readsAccessorWhenHudIsNotAField() throws Exception {
        Object hud = MinecraftUiCompat.readZeroArgumentMember(new GuiWithAccessor(), "hud", "getHud");

        assertEquals("accessor-hud", hud);
    }

    @Test
    void readsInheritedPrivateChatField() throws Exception {
        Object chat = MinecraftUiCompat.readZeroArgumentMember(new HudWithInheritedChat(), "getChat", "chat");

        assertEquals("inherited-chat", chat);
    }

    @Test
    void resolvesChatDirectlyFromGuiBeforeLookingForNestedHud() throws Exception {
        Object chat = MinecraftUiCompat.resolveChatComponent(new GuiWithDirectChat());

        assertEquals("direct-chat", chat);
    }

    @Test
    void resolvesChatFromNestedHudWhenGuiHasNoDirectChat() throws Exception {
        Object chat = MinecraftUiCompat.resolveChatComponent(new GuiWithNestedHud());

        assertEquals("nested-chat", chat);
    }

    @Test
    void tooltipNeverWritesIntoStringValueOrMessageSetters() {
        TextFieldProbe field = new TextFieldProbe();
        MinecraftUiCompat.tooltip(field, Mirror.literal("help"));
        assertEquals("25565", field.value);
    }

    public static final class TextFieldProbe {
        String value = "25565";
        public void setValue(String value) { this.value = value; }
    }
    private static final class GuiWithPrivateHud {
        private final String hud = "private-hud";
    }

    private static final class GuiWithAccessor {
        private String getHud() {
            return "accessor-hud";
        }
    }

    private static final class GuiWithDirectChat {
        public String getChat() {
            return "direct-chat";
        }
    }

    private static final class GuiWithNestedHud {
        private final HudWithDirectChat hud = new HudWithDirectChat();
    }

    private static final class HudWithDirectChat {
        public String getChat() {
            return "nested-chat";
        }
    }

    private static class HudBase {
        private final String chat = "inherited-chat";
    }

    private static final class HudWithInheritedChat extends HudBase {
    }
}
