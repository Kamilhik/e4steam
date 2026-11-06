package link.e4steam.lan;

import java.util.function.Consumer;
import link.e4steam.MinecraftUiCompat;
import link.e4steam.Mirror;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

public final class SteamAddressPreviewScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> saveDraft;
    private String value;
    private Button done;
    private int top;

    SteamAddressPreviewScreen(Screen parent, String value, Consumer<String> saveDraft) {
        super(Mirror.literal("Постоянный адрес Steam"));
        this.parent = parent;
        this.value = value;
        this.saveDraft = saveDraft;
    }

    @Override
    protected void init() {
        int fieldWidth = Math.min(240, width - 24);
        int left = (width - fieldWidth) / 2;
        top = Math.max(8, (height - 120) / 2);
        EditBox input = addRenderableWidget(new EditBox(font, left, top + 28, fieldWidth, 20,
                Mirror.literal("Имя адреса")));
        input.setMaxLength(40);
        input.setValue(value);
        int buttonWidth = (fieldWidth - 8) / 2;
        addRenderableWidget(MinecraftUiCompat.button(Mirror.translatable("gui.cancel"),
                button -> onClose(), left, top + 92, buttonWidth, 20));
        done = addRenderableWidget(MinecraftUiCompat.button(Mirror.translatable("gui.done"), button -> {
            saveDraft.accept(value);
            onClose();
        }, left + buttonWidth + 8, top + 92, buttonWidth, 20));
        done.active = valid();
        input.setResponder(text -> {
            value = text;
            done.active = valid();
        });
    }

    private boolean valid() {
        return value.matches("[a-z0-9]+(?:-[a-z0-9]+)*");
    }

    public void renderLabels(Object context) {
        LanScreenGraphics graphics = new LanScreenGraphics(context);
        graphics.drawCenteredString(font, title, width / 2, top, 0xFFFFFF);
        graphics.drawCenteredString(font, Mirror.literal("Имя адреса"), width / 2, top + 15, 0xFFFFFF);
        graphics.drawCenteredString(font, Mirror.literal(valid() ? value + ".steam"
                : "Допустимы a–z, 0–9 и дефис между символами"), width / 2, top + 55,
                valid() ? 0xFFFFFF : 0xFF8888);
        graphics.drawCenteredString(font, Mirror.literal("Предпросмотр · адрес пока не работает"),
                width / 2, top + 72, 0xAAAAAA);
    }

    @Override
    public void onClose() {
        MinecraftUiCompat.setScreen(minecraft, parent);
    }
}
