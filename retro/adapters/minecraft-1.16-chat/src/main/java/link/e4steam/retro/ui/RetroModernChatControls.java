package link.e4steam.retro.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

/** Minecraft 1.16 clickable chat controls. */
public final class RetroModernChatControls {
    private RetroModernChatControls() {
    }

    public static void showTranslatedMessage(String translationKey, String fallback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui == null) return;
        Component message = I18n.get(translationKey).equals(translationKey)
                ? new TextComponent(fallback)
                : new TranslatableComponent(translationKey);
        minecraft.gui.getChat().addMessage(message);
    }

    public static void showSharingReady(String endpoint) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui == null) return;

        MutableComponent address = new TextComponent(endpoint).withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND, "/e4steam copy"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new TranslatableComponent(
                                "text.e4steam_minecraft.addressCopyHelp"))));
        MutableComponent message = new TranslatableComponent(
                "text.e4steam_minecraft.domainAssigned", address);

        MutableComponent invite = new TranslatableComponent(
                "text.e4steam_minecraft.inviteFriends").withStyle(style -> style
                .withColor(ChatFormatting.BLUE)
                .withClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND, "/e4steam invite"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new TranslatableComponent(
                                "text.e4steam_minecraft.inviteFriendsHelp"))));
        message.append(" [").append(invite).append("]");

        MutableComponent stop = new TranslatableComponent(
                "text.e4steam_minecraft.clickToStop").withStyle(style -> style
                .withColor(ChatFormatting.RED)
                .withClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND, "/e4steam stop"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new TranslatableComponent(
                                "text.e4steam_minecraft.stopSharingHelp"))));
        message.append(" [").append(stop).append("]");
        minecraft.gui.getChat().addMessage(message);
    }
}
