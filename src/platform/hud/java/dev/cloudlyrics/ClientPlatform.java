package dev.cloudlyrics;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

/** Minecraft 26.2 / 26.3 keep the chat component in Gui.hud. */
final class ClientPlatform {
    private ClientPlatform() { }

    static void showChat(Component text) {
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(text);
    }

    static ClickEvent suggestCommand(String command) {
        return new ClickEvent.SuggestCommand(command);
    }
}
