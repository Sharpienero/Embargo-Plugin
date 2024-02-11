package gg.embargo;

import com.google.gson.*;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;


@Slf4j
@Singleton
public class UntrackableItemManager {

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private OkHttpClient okHttpClient;

    @Inject
    private EmbargoPlugin plugin;

    private static final String UNTRACKABLE_ENDPOINT = "https://8964a381-c461-455d-912a-0967c58d89a6.mock.pstmn.io/untrackables";

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

        private UntrackableItems(int itemId) {
            this.itemId = itemId;
        }
    }

    private HashSet<Integer> parseSet(JsonArray j) {
        HashSet<Integer> h = new HashSet<>();
        for (JsonElement jObj : j) {
            h.add(jObj.getAsInt());
        }
        return h;
    }

    void getUntrackableItems(int componentId, InventoryID inventoryID) {
        Widget widget = this.client.getWidget(componentId);
        ItemContainer itemContainer = this.client.getItemContainer(inventoryID);
        Widget[] children = widget.getChildren();
        if (itemContainer != null && children != null) {

            var itemMap = Arrays.stream(UntrackableItems.values()).map(UntrackableItems::getItemId).collect(Collectors.toCollection(HashSet::new));
            List<Integer> playerItems = new ArrayList<>();
            for (int i = 0; i < itemContainer.size(); ++i) {

                Widget child = children[i];
                var currentItem = child.getItemId();
                if (itemMap.contains(currentItem)) {
                    playerItems.add(currentItem);
                }
            }

            OkHttpClient client = new OkHttpClient();

            RequestBody requestBody = new FormBody.Builder()
                    .add("itemIds", Arrays.toString(playerItems.toArray()))
                    .build();
            Request request = new Request.Builder()
                    .url(UNTRACKABLE_ENDPOINT)
                    .post(requestBody)
                    .build();

            try {
                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        log.error("Something went wrong inside of untrackable items");
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (response.isSuccessful()) {
                            log.info("good");
                        }

                        response.close();
                    }
                });
            } catch (IllegalArgumentException e) {
                log.error("Bad URL given: " + e.getLocalizedMessage());
            }
        }
    }
}
