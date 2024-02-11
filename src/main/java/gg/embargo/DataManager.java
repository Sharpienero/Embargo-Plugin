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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.RuneScapeProfileType;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

@Slf4j
@Singleton
public class DataManager {
    @Inject
    private Client client;

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
         VERSION("version"),
         UNTRACKABLES("untrackables"),
         REGISTER("register");

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
    private static final String VERSION_ENDPOINT = API_URI + APIRoutes.VERSION;
    private static final String UNTRACKABLE_POST_ENDPOINT = API_URI + APIRoutes.UNTRACKABLES;
    private static final String REGISTER_ENDPOINT = API_URI + APIRoutes.REGISTER;

    public void storeVarbitChanged(int varbIndex, int varbValue) {
        synchronized (this) {
            varbData.put(varbIndex, varbValue);
        }
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
                log.error("Failed to submit data, attempting to reload dropped data...");
                this.restoreData(postRequestBody);
            }
        } catch (IOException ioException) {
            log.error("Failed to submit data, attempting to reload dropped data...");
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
        log.info("Getting manifest file...");
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
                        log.info("response.isSuccessful = True");
                        try {
                            // We want to be able to change the varbs and varps we get on the fly. To do so, we tell
                            // the client what to send the server on startup via the manifest.
                            if (response.body() == null) {
                                log.info("Response.body() == null");
                                log.error("Manifest request succeeded but returned empty body");
                                response.close();
                                return;
                            }

                            log.info("Response.body() != null");
                            JsonObject j = new Gson().fromJson(response.body().string(), JsonObject.class);
                            log.info(j.toString());

                            try {
                                log.info("Inside of try block");
                                plugin.setVarbitsToCheck(parseSet(j.getAsJsonArray("varbits")));
                                plugin.setVarpsToCheck(parseSet(j.getAsJsonArray("varps")));
                                log.info("After plugin.set");
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
        log.info(MANIFEST_ENDPOINT);
        Request request = new Request.Builder()
                .url(MANIFEST_ENDPOINT)
                .build();

        var serverManifestVersion = -1;

        try {
            okHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    log.error("Error retrieving manifest", e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        try {
                            // We want to be able to change the varbs and varps we get on the fly. To do so, we tell
                            // the client what to send the server on startup via the manifest.
                            if (response.body() == null) {
                                log.error("Manifest request succeeded but returned empty body");
                                response.close();
                                return;
                            }

                            JsonObject j = new Gson().fromJson(response.body().string(), JsonObject.class);

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
                            } catch (NullPointerException e) {
                                log.error(e.getLocalizedMessage());
                            } catch (ClassCastException e) {
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

    protected int registerUserWithClan(String discordId, String username) {
        log.info("Attempting to register user with clan");

        Request request = new Request.Builder()
                .url(REGISTER_ENDPOINT)
                .post(RequestBody.create(JSON, "{\"discordId\":\"" + discordId + "\",\"username\":\"" + username + "\"}"))
                .build();
        try {
            Response response = okHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                //200 success, 409 for already exists
                return response.code();
            }

            response.close();
        } catch (IOException e) {
            log.error("Failed to register user with clan", e);
        }

        return -1;
    }
}