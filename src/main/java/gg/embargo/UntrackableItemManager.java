package gg.embargo;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.widgets.Widget;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class UntrackableItemManager {

    @Inject
    private Client client;

    @Inject
    private OkHttpClient okHttpClient;

    private static final String UNTRACKABLE_ENDPOINT = "https://embargo.gg/api/untrackables";

    @Getter
    enum UntrackableItems {

        BOOK_OF_THE_DEAD(25818),
        MUSIC_CAPE(13221),
        MUSIC_CAPE_T(13222),
        BARROWS_GLOVES(7462),
        IMBUED_SARADOMIN_CAPE(21791),
        IMBUED_GUTHIX_CAPE(21793),
        IMBUED_ZAMORAK_CAPE(21795),
        IMBUED_SARADOMIN_MAX_CAPE(21776),
        IMBUED_ZAMORAK_MAX_CAPE(21780),
        IMBUED_GUTHIX_MAX_CAPE(21784),
        IMBUED_SARADOMIN_MAX_CAPE_I(24232),
        IMBUED_ZAMORAK_MAX_CAPE_I(24233),
        IMBUED_GUTHIX_MAX_CAPE_I(24234);

        private final int itemId;

        UntrackableItems(int itemId) {
            this.itemId = itemId;
        }
    }

    void getUntrackableItems(String username) {
        Widget widget = client.getWidget(786445);
        ItemContainer itemContainer = client.getItemContainer(InventoryID.BANK);
        Widget[] children;
        if (widget != null) {
            children = widget.getChildren();
        } else {
            return;
        }
        if (itemContainer != null && children != null) {

            var itemMap = Arrays.stream(UntrackableItems.values()).map(UntrackableItems::getItemId)
                    .collect(Collectors.toCollection(HashSet::new));
            List<Integer> playerItems = Arrays.stream(children)
                    .map(Widget::getItemId)
                    .filter(itemMap::contains)
                    .collect(Collectors.toList());

            final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

            JsonObject payload = new JsonObject();
            payload.addProperty("username", username);
            JsonArray items = new JsonArray();
            playerItems.forEach(items::add);
            payload.add("itemIds", items);

            RequestBody requestBody = RequestBody.create(JSON, payload.toString());

            Request request = new Request.Builder()
                    .url(UNTRACKABLE_ENDPOINT)
                    .post(requestBody)
                    .build();

            try {
                okHttpClient.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        log.error("Something went wrong inside of untrackable items");
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) {
                        handleResponse(response, "submit untrackable items");
                    }

                    private void handleResponse(Response response, String operation) {
                        if (response.isSuccessful()) {
                            log.debug("Successfully {}", operation);
                        } else {
                            log.error("Failed to {} with status {}", operation, response.code());
                        }
                        response.close();
                    }
                });
            } catch (IllegalArgumentException e) {
                log.error("Bad URL given: {}", e.getLocalizedMessage());
            }
        }
    }

    boolean isValidState(Widget widget, ItemContainer container) {
        return widget != null && widget.getChildren() != null && container != null;
    }

    public void processPlayerBatch(List<String> usernames) {
        JsonObject batchPayload = new JsonObject();
        JsonArray players = new JsonArray();
        usernames.forEach(username -> {
            JsonObject playerData = new JsonObject();
            playerData.addProperty("username", username);
            playerData.add("items", getPlayerUntrackableItems(username));
            players.add(playerData);
        });
        batchPayload.add("players", players);
        submitBatch(batchPayload);
    }

    @Getter
    private final MetricsCollector metrics = new MetricsCollector();

    class MetricsCollector {
        private final AtomicInteger successfulSubmissions = new AtomicInteger();
        private final AtomicInteger failedSubmissions = new AtomicInteger();
    }

    private JsonArray getPlayerUntrackableItems(String username) {
        Widget widget = client.getWidget(786445);
        ItemContainer itemContainer = client.getItemContainer(InventoryID.BANK);

        if (!isValidState(widget, itemContainer)) {
            return new JsonArray();
        }

        var itemMap = Arrays.stream(UntrackableItems.values())
                .map(UntrackableItems::getItemId)
                .collect(Collectors.toCollection(HashSet::new));

        JsonArray items = new JsonArray();
        Arrays.stream(widget.getChildren())
                .map(Widget::getItemId)
                .filter(itemMap::contains)
                .forEach(items::add);

        return items;
    }

    private void submitBatch(JsonObject batchPayload) {
        Request request = new Request.Builder()
                .url(UNTRACKABLE_ENDPOINT)
                .post(RequestBody.create(MediaType.parse("application/json"), batchPayload.toString()))
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                metrics.failedSubmissions.incrementAndGet();
                log.error("Failed to submit batch of untrackable items", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                metrics.successfulSubmissions.incrementAndGet();
                if (response.isSuccessful()) {
                    log.info("Successfully submitted batch of untrackable items");
                } else {
                    log.error("Failed to submit batch of untrackable items. Response code: " + response.code());
                }
                response.close();
            }
        });
    }
}
