package link.e4steam.lan;

import java.util.ArrayList;
import java.util.List;
import link.e4steam.MinecraftUiCompat;
import link.e4steam.Mirror;
import link.e4steam.lan.UiTime;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Optional support information, opened only by an explicit button press. */
public final class SupportScreen extends Screen {
    private final Screen parent;
    private final List<Button> buttons = new ArrayList<>();
    private long opened = UiTime.millis();
    private long closing = -1;
    private float closeOpacity;
    private int left, top, panelWidth, panelHeight, premiumTitleY, detailsY, donateY, linkY, thanksY;
    private boolean compact;
    private String feedback;

    public SupportScreen(Screen parent) {
        super(text("title"));
        this.parent = parent;
    }

    private static Component text(String key) {
        return Mirror.translatable("text.e4steam_minecraft.support." + key);
    }

    @Override protected void init() {
        buttons.clear();
        panelWidth = Math.min(340, width - 16);
        int content = panelWidth - 24;
        int introLines = wrap(text("intro").getString(), content).size();
        int premiumLines = wrap(text("premium").getString(), content).size();
        int donateLines = wrap(text("donate").getString(), content).size();
        compact = height < 230;
        premiumTitleY = compact ? 28 : 34 + introLines * 11 + 8;
        detailsY = premiumTitleY + 15;
        donateY = detailsY + premiumLines * 11 + 8;
        linkY = donateY + donateLines * 11 + 8;
        thanksY = linkY + 29;
        panelHeight = compact ? linkY + 56
                : thanksY + wrap(text("thanks").getString(), content).size() * 11 + 37;
        if (!compact && panelHeight > height - 8) {
            compact = true;
            premiumTitleY = 28;
            detailsY = premiumTitleY + 15;
            donateY = detailsY + premiumLines * 11 + 8;
            linkY = donateY + donateLines * 11 + 8;
            panelHeight = linkY + 56;
        }
        left = (width - panelWidth) / 2;
        top = Math.max(4, (height - panelHeight) / 2);
        add(text("premiumButton"), linkY, () -> confirm(PremiumPurchaseLink.URL));
        add(Mirror.translatable("gui.back"), panelHeight - 28, this::onClose);
        updateButtons();
    }

    private void add(Component label, int y, Runnable action) {
        buttons.add(addRenderableWidget(MinecraftUiCompat.button(label,
                button -> action.run(), left + 12, top + y, panelWidth - 24, 20)));
    }

    private void confirm(String url) {
        MinecraftUiCompat.setScreen(minecraft, new ConfirmScreen(accepted -> {
            if (accepted && !PremiumPurchaseLink.open(url)) feedback = text("openFailed").getString();
            MinecraftUiCompat.setScreen(minecraft, this);
        }, Mirror.translatable("chat.link.confirm"), Mirror.literal(url)));
    }

    @Override public void tick() {
        if (closing >= 0 && UiTime.millis() - closing >= 220) {
            MinecraftUiCompat.setScreen(minecraft, parent);
            return;
        }
        updateButtons();
    }

    @Override public void onClose() {
        if (closing < 0) {
            closeOpacity = opacity();
            closing = UiTime.millis();
            updateButtons();
        }
    }

    private float opacity() {
        float value = closing < 0 ? Math.min(1F, (UiTime.millis() - opened) / 260F)
                : closeOpacity * Math.max(0F, 1F - (UiTime.millis() - closing) / 220F);
        return value * value * (3F - 2F * value);
    }

    private void updateButtons() {
        float alpha = opacity();
        for (Button button : buttons) {
            button.active = closing < 0 && alpha > .95F;
            button.visible = alpha > .04F;
            button.setAlpha(alpha);
        }
    }

    public void background(Object context) {
        updateButtons();
        float alpha = opacity();
        if (alpha < .02F) return;
        SupportGraphics graphics = new SupportGraphics(context);
        graphics.fill(0, 0, width, height, color(0, alpha * .65F));
        graphics.fill(left, top, left + panelWidth, top + panelHeight, color(0x18232F, alpha));
        graphics.fill(left, top, left + panelWidth, top + 2, color(0x68D5BD, alpha));
    }

    public void labels(Object context) {
        float alpha = opacity();
        if (alpha < .02F) return;
        SupportGraphics graphics = new SupportGraphics(context);
        graphics.drawCenteredString(font, title, width / 2, top + 13, color(0xFFFFFF, alpha));
        if (!compact) lines(graphics, text("intro").getString(), 34, 0xCBD6DF, alpha);
        graphics.drawCenteredString(font, Mirror.literal("e4steam Premium"), width / 2,
                top + premiumTitleY, color(0xE8C875, alpha));
        lines(graphics, text("premium").getString(), detailsY, 0xCBD6DF, alpha);
        lines(graphics, text("donate").getString(), donateY, 0xCBD6DF, alpha);
        if (!compact) lines(graphics, feedback == null ? text("thanks").getString() : feedback, thanksY, 0x93BCAF, alpha);
    }

    private void lines(SupportGraphics graphics, String value, int y, int rgb, float alpha) {
        for (String line : wrap(value, panelWidth - 24)) {
            graphics.drawCenteredString(font, Mirror.literal(line), width / 2, top + y, color(rgb, alpha));
            y += 11;
        }
    }

    private List<String> wrap(String value, int maxWidth) {
        List<String> result = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : value.split(" ")) {
            if (line.length() > 0 && font.width(line + " " + word) > maxWidth) {
                result.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        result.add(line.toString());
        return result;
    }

    private static int color(int rgb, float alpha) {
        return Math.round(255F * alpha) << 24 | rgb;
    }
}
