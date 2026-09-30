package dev.cloudlyrics;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

/** Minecraft 26.1.1 / 26.1.2 client chat APIs. */
final class ClientPlatform {
    private ClientPlatform() { }

    static void showChat(Component text) {
        Minecraft.getInstance().gui.getChat().addClientSystemMessage(text);
    }

    static ClickEvent suggestCommand(String command) {
        return new ClickEvent.SuggestCommand(command);
    }
}
