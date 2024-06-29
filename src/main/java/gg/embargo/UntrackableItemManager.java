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

            var itemMap = Arrays.stream(UntrackableItems.values()).map(UntrackableItems::getItemId).collect(Collectors.toCollection(HashSet::new));
            List<Integer> playerItems = new ArrayList<>();
            for (int i = 0; i < itemContainer.size(); ++i) {

                Widget child = children[i];
                var currentItem = child.getItemId();
                if (itemMap.contains(currentItem)) {
                    playerItems.add(currentItem);
                }
            }

            var RequestBody = new FormBody.Builder();
            for (int i=0; i < playerItems.size(); i++) {
                RequestBody.add("itemIds[" + i + "]", String.valueOf(playerItems.get(i)));
            }

            //Need to send profile type with this

            RequestBody.add("username", username);

            Request request = new Request.Builder()
                    .url(UNTRACKABLE_ENDPOINT)
                    .post(RequestBody.build())
                    .addHeader("Content-Type", "application/json")
                    .build();

            try {
                okHttpClient.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        log.error("Something went wrong inside of untrackable items");
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) {
                        if (response.isSuccessful()) {
                            log.debug("Successfully submitted untrackable items");
                        }
                        response.close();
                    }
                });
            } catch (IllegalArgumentException e) {
                log.error("Bad URL given: {}", e.getLocalizedMessage());
            }
        }
    }
}
