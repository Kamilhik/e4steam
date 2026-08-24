package link.e4steam.retro.fabric;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

/** Minecraft 1.14-specific chat controls with clickable Steam actions. */
public final class E4steamFabric114 extends E4steamFabric {
    @Override
    public void showSharingReady(String endpoint) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui == null) {
            return;
        }

        Component address = new TextComponent(endpoint).setStyle(new Style()
                .setColor(ChatFormatting.GREEN)
                .setClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND, "/e4steam copy"))
                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new TranslatableComponent(
                                "text.e4steam_minecraft.addressCopyHelp"))));
        Component message = new TranslatableComponent(
                "text.e4steam_minecraft.domainAssigned", address);

        Component invite = new TranslatableComponent(
                "text.e4steam_minecraft.inviteFriends").setStyle(new Style()
                .setColor(ChatFormatting.BLUE)
                .setClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND, "/e4steam invite"))
                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new TranslatableComponent(
                                "text.e4steam_minecraft.inviteFriendsHelp"))));
        message.append(" [").append(invite).append("]");

        Component stop = new TranslatableComponent(
                "text.e4steam_minecraft.clickToStop").setStyle(new Style()
                .setColor(ChatFormatting.RED)
                .setClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND, "/e4steam stop"))
                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new TranslatableComponent(
                                "text.e4steam_minecraft.stopSharingHelp"))));
        message.append(" [").append(stop).append("]");

        minecraft.gui.getChat().addMessage(message);
    }
}
