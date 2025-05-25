package gg.embargo.commands;

import gg.embargo.DataManager;
import gg.embargo.EmbargoConfig;
import gg.embargo.commands.embargo.Rank;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.chatcommands.ChatKeyboardListener;
import net.runelite.client.input.KeyManager;

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

    @Inject
    private ChatCommandManager chatCommandManager;

    @Inject
    private ChatKeyboardListener chatKeyboardListener;

    @Inject
    private KeyManager keyManager;

    private final EventBus eventBus;

    private EmbargoConfig config;

    private static final String EMBARGO_COMMAND = "!embargo";

    @Inject
    public CommandManager(Client client, ClientThread clientThread, EventBus eventBus, EmbargoConfig config) {
        this.client = client;
        this.clientThread = clientThread;
        this.eventBus = eventBus;
        this.config = config;
    }

    public void startUp() {
        eventBus.register(this);
        keyManager.registerKeyListener(chatKeyboardListener);
        chatCommandManager.registerCommandAsync(EMBARGO_COMMAND, this::processEmbargoLookupChatCommand);

    }

    public void shutDown() {
        eventBus.unregister(this);
        keyManager.unregisterKeyListener(chatKeyboardListener);
        chatCommandManager.unregisterCommand(EMBARGO_COMMAND);
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
        dataManager.getProfileAsync(finalMemberName, true).thenAccept(embargoProfileData -> {
            // Null checks for safety
            if (embargoProfileData == null
                    || embargoProfileData.get("accountPoints") == null
                    || embargoProfileData.getAsJsonPrimitive("communityPoints") == null
                    || embargoProfileData.getAsJsonPrimitive("currentRank") == null) {
                String memberNotFound = new ChatMessageBuilder()
                        .append(ChatColorType.HIGHLIGHT)
                        .append("Error retrieving data for member: " + finalMemberName)
                        .build();
                updateChatMessage(chatMessage, memberNotFound);
                return;
            }

            String currentRankName = embargoProfileData.getAsJsonPrimitive("currentRank").getAsString();
            String leaderboardPosition = embargoProfileData.getAsJsonObject("leaderboardRank").get("currentPosition")
                    + "/"
                    + embargoProfileData.getAsJsonObject("leaderboardRank").get("totalPositions");
            Color rankColor = Rank.getColorByName(currentRankName);
            Color labelColor = config.chatCommandOutputColor();

            String outputMessage = new ChatMessageBuilder()
                    .append(labelColor, "Member: ")
                    .append(finalMemberName)
                    .append(labelColor, " Rank: ")
                    .append(rankColor, currentRankName)
                    .append(labelColor, " Account Points: ")
                    .append(ChatColorType.HIGHLIGHT)
                    .append(String.valueOf(embargoProfileData.get("accountPoints")))
                    .append(labelColor, " Community Points: ")
                    .append(ChatColorType.HIGHLIGHT)
                    .append(String.valueOf(embargoProfileData.getAsJsonPrimitive("communityPoints")))
                    .append(labelColor, " Leaderboard Rank: ")
                    .append(ChatColorType.HIGHLIGHT)
                    .append(leaderboardPosition)
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
