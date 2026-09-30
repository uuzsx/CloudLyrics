package dev.cloudlyrics;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

/** Minecraft 1.21.1 / 1.21.2 client chat APIs. */
final class ClientPlatform {
    private ClientPlatform() { }

    static void showChat(Component text) {
        Minecraft.getInstance().gui.getChat().addMessage(text);
    }

    static ClickEvent suggestCommand(String command) {
        return new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command);
    }
}
