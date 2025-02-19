/*
 * Copyright (c) 2021, andmcadams
 * modified by Sharpienero, Contronym
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package gg.embargo;

import com.google.gson.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import okhttp3.*;
import okio.BufferedSource;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
public class DataManager {
    @Inject
    private Client client;

    @Inject
    private ItemManager itemManager;

    @Inject
    private ClientThread clientThread;

    @Inject
    private OkHttpClient okHttpClient;

    @Inject
    private Gson gson;

    @Inject
    private EmbargoPlugin plugin;

    private final HashMap<Integer, Integer> varbData = new HashMap<>();
    private final HashMap<Integer, Integer> varpData = new HashMap<>();
    private final HashMap<String, Integer> levelData = new HashMap<>();

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

     enum APIRoutes {
         MANIFEST("runelite/manifest"),
         UNTRACKABLES("untrackables"),
         CHECKREGISTRATION("checkregistration"),
         GET_PROFILE("getgear"),
         SUBMIT_LOOT("loot"),
         GET_RAID_MONSTERS_TO_TRACK_LOOT("lootBosses"),
         PREPARE_RAID("raid"),
         UPLOAD_CLOG("collectionlog"),
         MINIGAME_COMPLETE("minigame");

         APIRoutes(String route) {
             this.route = route;
         }

         private final String route;

         @Override
         public String toString() {
             return route;
         }
     }

    private static final String API_URI = "https://embargo.gg/api/";
    private static final String MANIFEST_ENDPOINT = API_URI + APIRoutes.MANIFEST;
    private static final String UNTRACKABLE_POST_ENDPOINT = API_URI + APIRoutes.UNTRACKABLES;
    private static final String CHECK_REGISTRATION_ENDPOINT = API_URI + APIRoutes.CHECKREGISTRATION;
    private static final String GET_PROFILE_ENDPOINT = API_URI + APIRoutes.GET_PROFILE;
    private static final String SUBMIT_LOOT_ENDPOINT = API_URI + APIRoutes.SUBMIT_LOOT;
    private static final String TRACK_MONSTERS_ENDPOINT = API_URI + APIRoutes.GET_RAID_MONSTERS_TO_TRACK_LOOT;
    private static final String PREPARE_RAID_ENDPOINT = API_URI + APIRoutes.PREPARE_RAID;
    private static final String MINIGAME_COMPLETION_ENDPOINT = API_URI + APIRoutes.MINIGAME_COMPLETE;
    private static final String CLOG_UNLOCK_ENDPOINT = API_URI + APIRoutes.UPLOAD_CLOG;

    public static ArrayList BossesToTrack = null;

    public void storeVarbitChanged(int varbIndex, int varbValue) {
        synchronized (this) {
            varbData.put(varbIndex, varbValue);
        }
    }

    public List<Player> getSurroundingPlayers() {
        return client.getPlayers();
    }

    public boolean shouldTrackLoot(String bossName) {
        if (bossName == null || bossName.isEmpty()) {
            return false;
        }

        var bosses = getTrackableBosses();

        for (Object boss : bosses) {
            if (boss.equals(bossName)) {
                return true;
            }
        }

        return false;
    }

    public ArrayList getTrackableBosses() {
        if (BossesToTrack != null) {
            return BossesToTrack;
        }
        okHttpClient.newCall(new Request.Builder().url(TRACK_MONSTERS_ENDPOINT).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                log.debug("Failed to get raid boss list", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                //Update what we want to track on the fly
                if (response.isSuccessful()) {
                    //convert response.body().string() to ArrayList<String>
                    BufferedSource source = response.body().source();
                    String json = source.readUtf8();
                    response.close();

                    // convert json to an ArrayList<String>
                    BossesToTrack = gson.fromJson(json, ArrayList.class);
                }
            }
        });
        return null;
    }

    public void uploadCollectionLogUnlock(String item, String player)
    {
        JsonObject payload = getClogUploadPayload(item, player);
        log.debug(String.valueOf(payload));

        okHttpClient.newCall(new Request.Builder().url(CLOG_UNLOCK_ENDPOINT).post(RequestBody.create(JSON, payload.toString())).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                log.debug("Failed to upload new clog slot to Embargo", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                //Update what we want to track on the fly
                if (response.isSuccessful()) {
                    log.debug("Successfully uploaded new collection log slot");
                    response.close();
                    return;
                }

                response.close();
            }
        });
    }

    public void uploadRaidCompletion(String raid, String message) {
        if (client == null || client.getLocalPlayer() == null) {
            return;
        }

        JsonObject payload = getRaidCompletionPayload(raid, message);
        okHttpClient.newCall(new Request.Builder().url(PREPARE_RAID_ENDPOINT).post(RequestBody.create(JSON, payload.toString())).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                log.debug("Failed to upload upload raid completion", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    log.debug("Successfully uploaded raid preparation");
                }
            }
        });
    }

    public void uploadMinigameCompletion(String minigameName, String message) {
        if (client == null || client.getLocalPlayer() == null) {
            return;
        }

        JsonObject payload = getMinigamePayload(minigameName, message);
        okHttpClient.newCall(new Request.Builder().url(MINIGAME_COMPLETION_ENDPOINT).post(RequestBody.create(JSON, payload.toString())).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                log.debug("Failed to upload upload minigame completion", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    log.debug("Successfully uploaded minigame preparation");
                }
            }
        });
    }

    private JsonObject getClogUploadPayload(String itemName, String username)
    {

        JsonObject payload = new JsonObject();
        payload.addProperty("playerName", username);
        payload.addProperty("itemName", itemName);

        return payload;
    }

    @NonNull
    private JsonObject getMinigamePayload(String minigame, String message) {
        var user = client.getLocalPlayer().getName();
        var world = client.getWorld();
        List<Player> players = getSurroundingPlayers();

        //convert List<Player> to JSON
        JsonArray playersJson = new JsonArray();
        for (Player player : players) {
            JsonObject playerJson = new JsonObject();
            playerJson.addProperty("name", player.getName());
            playersJson.add(playerJson);
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("minigame", minigame);
        payload.addProperty("world", world);
        payload.addProperty("message", message);
        payload.addProperty("user", user);
        payload.add("players", playersJson);
        return payload;
    }

    @NonNull
    private JsonObject getRaidCompletionPayload(String raid, String message) {
        var user = client.getLocalPlayer().getName();
        List<Player> players = getSurroundingPlayers();

        //convert List<Player> to JSON
        JsonArray playersJson = new JsonArray();
        for (Player player : players) {
            JsonObject playerJson = new JsonObject();
            playerJson.addProperty("name", player.getName());
            playersJson.add(playerJson);
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("raid", raid);
        payload.addProperty("message", message);
        payload.addProperty("user", user);
        payload.add("players", playersJson);
        return payload;
    }

    public JsonObject getProfile(String username) {
        Request request = new Request.Builder()
                .url(GET_PROFILE_ENDPOINT + '/' + username)
                .get()
                .build();

        OkHttpClient shortTimeoutClient = okHttpClient.newBuilder()
                .callTimeout(5, TimeUnit.SECONDS)
                .build();

        try (Response response = shortTimeoutClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                BufferedSource source = response.body().source();
                String json = source.readUtf8();

                response.close();
                return gson.fromJson(json, JsonObject.class);
            }

            response.close();
            return new JsonObject();
        } catch (IOException ioException) {
            log.error("Failed to check if user is registered.");
        }
        return new JsonObject();
    }

    public boolean checkRegistered(String username) {
        Request request = new Request.Builder()
                .url(CHECK_REGISTRATION_ENDPOINT + '/' + username)
                .get()
                .build();

        OkHttpClient shortTimeoutClient = okHttpClient.newBuilder()
                .callTimeout(5, TimeUnit.SECONDS)
                .build();

        try (Response response = shortTimeoutClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                response.close();
                return true;

            } else {
                log.error("Failed to check if user is registered.");
                response.close();
            }
        } catch (IOException ioException) {
            log.error("Failed to check if user is registered.");
        }

        return false;
    }

    public void uploadLoot(LootReceived event) {
        JsonObject payload = getJsonObject(event);

        log.debug("Uploading payload: " + payload);

        Request request = new Request.Builder()
                .url(SUBMIT_LOOT_ENDPOINT)
                .post(RequestBody.create(JSON, payload.toString()))
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                log.error("Error uploading loot", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    log.debug("Loot uploaded successfully");
                } else {
                    log.error("Loot upload failed with status " + response.code());
                }
                response.close();
            }
        });
    }

    @NonNull
    private JsonObject getJsonObject(LootReceived event) {
        Collection<ItemStack> itemStacks = event.getItems();

        var user = client.getLocalPlayer().getName();
        List<Player> players = getSurroundingPlayers();

        //convert List<Player> to JSON
        JsonArray playersJson = new JsonArray();
        for (Player player : players) {
            JsonObject playerJson = new JsonObject();
            playerJson.addProperty("name", player.getName());
            playersJson.add(playerJson);
        }

        //convert itemStacks to JSON using gson
        JsonArray itemStacksJson = new JsonArray();
        for (ItemStack itemStack : itemStacks) {
            JsonObject itemStackJson = new JsonObject();
            itemStackJson.addProperty("id", itemStack.getId());
            itemStackJson.addProperty("quantity", itemStack.getQuantity());
            itemStackJson.addProperty("price", itemManager.getItemPrice(itemStack.getId()));
            itemStackJson.addProperty("name", itemManager.getItemComposition(itemStack.getId()).getName());

            itemStacksJson.add(itemStackJson);
        }

        //convert json array to String
        String itemStacksJsonString = itemStacksJson.toString();

        //build payload with bossName and itemStacks
        JsonObject payload = new JsonObject();
        payload.addProperty("bossName", event.getName());
        payload.addProperty("user", user);
        payload.addProperty("itemStacks", itemStacksJsonString);
        payload.add("players", playersJson);
        return payload;
    }

    public void storeVarbitChangedIfNotStored(int varbIndex, int varbValue) {
        synchronized (this) {
            if (!varbData.containsKey(varbIndex))
                this.storeVarbitChanged(varbIndex, varbValue);
        }
    }

    public void storeVarpChanged(int varpIndex, int varpValue) {
        synchronized (this) {
            varpData.put(varpIndex, varpValue);
        }
    }

    public void storeVarpChangedIfNotStored(int varpIndex, int varpValue) {
        synchronized (this) {
            if (!varpData.containsKey(varpIndex))
                this.storeVarpChanged(varpIndex, varpValue);
        }
    }

    public void storeSkillChanged(String skill, int skillLevel) {
        synchronized (this) {
            levelData.put(skill, skillLevel);
        }
    }

    public void storeSkillChangedIfNotChanged(String skill, int skillLevel) {
        synchronized (this) {
            if (!levelData.containsKey(skill))
                storeSkillChanged(skill, skillLevel);
        }
    }

    private <K, V> HashMap<K, V> clearChanges(HashMap<K, V> h) {
        HashMap<K, V> temp;
        synchronized (this) {
            if (h.isEmpty()) {
                return new HashMap<>();
            }
            temp = new HashMap<>(h);
            h.clear();
        }
        return temp;
    }

    public void clearData() {
        synchronized (this) {
            varbData.clear();
            varpData.clear();
            levelData.clear();
        }
    }

    private boolean hasDataToPush() {
        return !(varbData.isEmpty() && varpData.isEmpty() && levelData.isEmpty());
    }

    private JsonObject convertToJson() {
        JsonObject j = new JsonObject();
        JsonObject parent = new JsonObject();
        // We need to synchronize this to handle the case where the RuneScapeProfileType changes
        synchronized (this) {
            RuneScapeProfileType r = RuneScapeProfileType.getCurrent(client);
            HashMap<Integer, Integer> tempVarbData = clearChanges(varbData);
            HashMap<Integer, Integer> tempVarpData = clearChanges(varpData);
            HashMap<String, Integer> tempLevelData = clearChanges(levelData);

            j.add("varb", gson.toJsonTree(tempVarbData));
            j.add("varp", gson.toJsonTree(tempVarpData));
            j.add("level", gson.toJsonTree(tempLevelData));

            parent.addProperty("username", client.getLocalPlayer().getName());
            parent.addProperty("profile", r.name());
            parent.add("data", j);
        }
        log.debug(parent.toString());
        return parent;
    }

    private void restoreData(JsonObject jObj) {
        synchronized (this) {
            if (!jObj.get("profile").getAsString().equals(RuneScapeProfileType.getCurrent(client).name())) {
                log.error("Not restoring data from failed call since the profile type has changed");
                return;
            }
            JsonObject dataObj = jObj.getAsJsonObject("data");
            JsonObject varbObj = dataObj.getAsJsonObject("varb");
            JsonObject varpObj = dataObj.getAsJsonObject("varp");
            JsonObject levelObj = dataObj.getAsJsonObject("level");
            for (String k : varbObj.keySet()) {
                this.storeVarbitChangedIfNotStored(Integer.parseInt(k), varbObj.get(k).getAsInt());
            }
            for (String k : varpObj.keySet()) {
                this.storeVarpChangedIfNotStored(Integer.parseInt(k), varpObj.get(k).getAsInt());
            }
            for (String k : levelObj.keySet()) {
                this.storeSkillChangedIfNotChanged(k, levelObj.get(k).getAsInt());
            }
        }
    }

    protected void submitToAPI() {
        if (!hasDataToPush() || client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null)
            return;

        if (RuneScapeProfileType.getCurrent(client) == RuneScapeProfileType.BETA)
            return;

        if (!checkRegistered(client.getLocalPlayer().getName())) {
            return;
        }

        if (client.getGameState() == GameState.LOGIN_SCREEN || client.getGameState() == GameState.HOPPING) {
            return;
        }

        log.debug("Submitting changed data to endpoint...");
        JsonObject postRequestBody = convertToJson();
        Request request = new Request.Builder()
                .url(UNTRACKABLE_POST_ENDPOINT)
                .post(RequestBody.create(JSON, postRequestBody.toString()))
                .build();

        OkHttpClient shortTimeoutClient = okHttpClient.newBuilder()
                .callTimeout(5, TimeUnit.SECONDS)
                .build();
        try (Response response = shortTimeoutClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                // If we failed to submit, read the data to the data lists (unless there are newer ones)
                log.error("[submitToAPI !response.isSuccessful(): 496] Failed to submit data, attempting to reload dropped data");
                this.restoreData(postRequestBody);
            }
        } catch (IOException ioException) {
            log.error("[submitToAPI IOException: 496] Failed to submit data, attempting to reload dropped data");
            this.restoreData(postRequestBody);
        }
    }

    private HashSet<Integer> parseSet(JsonArray j) {
        HashSet<Integer> h = new HashSet<>();
        for (JsonElement jObj : j) {
            h.add(jObj.getAsInt());
        }
        return h;
    }

    protected void getManifest() {
        log.debug("Getting manifest file...");
        try {
            Request r = new Request.Builder()
                    .url(MANIFEST_ENDPOINT)
                    .build();
            okHttpClient.newCall(r).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    log.error("Error retrieving manifest", e);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    if (response.isSuccessful()) {
                        try {
                            // We want to be able to change the varbs and varps we get on the fly. To do so, we tell
                            // the client what to send the server on startup via the manifest.
                            if (response.body() == null) {
                                log.error("Manifest request succeeded but returned empty body");
                                response.close();
                            }

                            JsonObject j = gson.fromJson(response.body().string(), JsonObject.class);
                            try {
                                plugin.setVarbitsToCheck(parseSet(j.getAsJsonArray("varbits")));
                                plugin.setVarpsToCheck(parseSet(j.getAsJsonArray("varps")));
                                try {
                                    int manifestVersion = j.get("version").getAsInt();
                                    if (plugin.getLastManifestVersion() != manifestVersion) {
                                        plugin.setLastManifestVersion(manifestVersion);
                                        clientThread.invoke(() -> plugin.loadInitialData());
                                    }
                                } catch (UnsupportedOperationException | NullPointerException exception) {
                                    plugin.setLastManifestVersion(-1);
                                }
                            } catch (NullPointerException e) {
                                log.error("Manifest possibly missing varbits or varps entry from /manifest call");
                                log.error(e.getLocalizedMessage());
                            } catch (ClassCastException e) {
                                log.error("Manifest from /manifest call might have varbits or varps as not a list");
                                log.error(e.getLocalizedMessage());
                            }
                        } catch (IOException | JsonSyntaxException e) {
                            log.error(e.getLocalizedMessage());
                        }
                    } else {
                        log.error("Manifest request returned with status " + response.code());
                        if (response.body() == null) {
                            log.error("Manifest request returned empty body");
                        } else {
                            log.error(response.body().toString());
                        }
                    }
                    response.close();
                }
            });
        } catch (IllegalArgumentException e) {
            log.error("Bad URL given: " + e.getLocalizedMessage());
        }
    }

    protected int getVersion() {
        log.debug("Attempting to get manifest version...");
        Request request = new Request.Builder()
                .url(MANIFEST_ENDPOINT)
                .build();

        var serverManifestVersion = -1;

        try {
            okHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, IOException e) {
                    log.error("Error retrieving manifest", e);
                }

                @Override
                public void onResponse(@NonNull Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        try {
                            // We want to be able to change the varbs and varps we get on the fly. To do so, we tell
                            // the client what to send the server on startup via the manifest.
                            if (response.body() == null) {
                                log.error("Manifest request succeeded but returned empty body");
                                response.close();
                            }

                            JsonObject j = gson.fromJson(response.body().string(), JsonObject.class);

                            try {
                                try {
                                    int manifestVersion = j.get("version").getAsInt();
                                    if (plugin.getLastManifestVersion() != manifestVersion) {
                                        plugin.setLastManifestVersion(manifestVersion);
                                        clientThread.invoke(() -> plugin.loadInitialData());
                                    }
                                } catch (UnsupportedOperationException | NullPointerException exception) {
                                    plugin.setLastManifestVersion(-1);
                                }
                            } catch (NullPointerException | ClassCastException e) {
                                log.error(e.getLocalizedMessage());
                            }
                        } catch (IOException | JsonSyntaxException e) {
                            log.error(e.getLocalizedMessage());
                        }
                    } else {
                        log.error("Manifest request returned with status " + response.code());
                        if (response.body() == null) {
                            log.error("Manifest request returned empty body");
                        } else {
                            log.error(response.body().toString());
                        }
                    }
                    response.close();
                }
            });
        } catch (IllegalArgumentException e) {
            log.error("asd");
        }
        return -1;
    }
}