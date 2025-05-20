package gg.embargo.commands;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gg.embargo.DataManager;
import gg.embargo.commands.embargo.Rank;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import java.awt.*;

@Slf4j
public class CommandManager {
    @Inject
    private ClientThread clientThread;

    @Inject
    private Client client;

    @Inject
    private DataManager dataManager;

    private final EventBus eventBus;

    @Inject
    public CommandManager(Client client, ClientThread clientThread, EventBus eventBus) {
        this.client = client;
        this.clientThread = clientThread;
        this.eventBus = eventBus;
    }

    public void startUp() {
        eventBus.register(this);
    }

    public void shutDown() {
        eventBus.unregister(this);
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if (client == null)
            return;

        Player player = client.getLocalPlayer();
        if (player == null)
            return;

        String message = chatMessage.getMessage();

        if (message.toLowerCase().startsWith("!embargo")) {
            processEmbargoLookupChatCommand(chatMessage, message);
        }
    }

    // Helper method to update chat message safely on the client thread
    private void updateChatMessage(ChatMessage chatMessage, String message) {
        clientThread.invokeLater(() -> {
            chatMessage.getMessageNode().setRuneLiteFormatMessage(message);
            client.refreshChat();
        });
    }

    public void processEmbargoLookupChatCommand(ChatMessage chatMessage, String message) {
        int firstWhitespace = message.indexOf(' ');
        String memberName = (firstWhitespace != -1 && firstWhitespace + 1 < message.length())
                ? message.substring(firstWhitespace + 1)
                : chatMessage.getName().replaceAll("<[^>]*>", "");

        String loadingMessage = new ChatMessageBuilder()
                .append(ChatColorType.HIGHLIGHT)
                .append("Looking up Embargo member...")
                .build();

        updateChatMessage(chatMessage, loadingMessage);

        String finalMemberName = memberName.trim();
        dataManager.getProfileAsync(finalMemberName).thenAccept(embargoProfileData -> {
            // Null checks for safety
            if (embargoProfileData == null
                    || embargoProfileData.get("accountPoints") == null
                    || embargoProfileData.getAsJsonPrimitive("communityPoints") == null
                    || embargoProfileData.getAsJsonObject("currentRank") == null
                    || embargoProfileData.getAsJsonObject("currentRank").get("name") == null) {
                String memberNotFound = new ChatMessageBuilder()
                        .append(ChatColorType.HIGHLIGHT)
                        .append("Member " + finalMemberName + " not found.")
                        .build();
                updateChatMessage(chatMessage, memberNotFound);
                return;
            }

            String currentRankName = embargoProfileData.getAsJsonObject("currentRank").get("name").getAsString();
            Color rankColor = Rank.getColorByName(currentRankName);

            String outputMessage = new ChatMessageBuilder()
                    .append(Color.RED, "Member: ")
                    .append(ChatColorType.NORMAL)
                    .append(finalMemberName)
                    .append(ChatColorType.HIGHLIGHT)
                    .append(" Rank: ")
                    .append(rankColor, currentRankName)
                    .append(ChatColorType.HIGHLIGHT)
                    .append(" Account Points: ")
                    .append(ChatColorType.NORMAL)
                    .append(String.valueOf(embargoProfileData.get("accountPoints")))
                    .append(ChatColorType.HIGHLIGHT)
                    .append(" Community Points: ")
                    .append(ChatColorType.NORMAL)
                    .append(String.valueOf(embargoProfileData.getAsJsonPrimitive("communityPoints")))
                    .build();

            updateChatMessage(chatMessage, outputMessage);
        }).exceptionally(ex -> {
            String memberNotFound = new ChatMessageBuilder()
                    .append(ChatColorType.HIGHLIGHT)
                    .append("Member " + finalMemberName + " not found.")
                    .build();
            updateChatMessage(chatMessage, memberNotFound);
            return null;
        });
    }

}
