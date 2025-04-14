package gg.embargo.manifest;

import com.google.gson.*;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static java.lang.System.in;

@Slf4j
@Singleton
public class ManifestManager {


    @Inject
    private Gson gson;

    @Inject
    private OkHttpClient okHttpClient;

    @Getter
    @Setter
    private Manifest manifest;

    @Getter
    @Setter
    private float lastCheckedManifestVersion = -1;

    private static final String API_URI = "https://a278d141-927f-433b-8e4b-6d994067900d.mock.pstmn.io/api/";
    //private static final String API_URI = "https://embargo.gg/api/";
    private static final String MANIFEST_ENDPOINT = API_URI + "runelite/manifest";

    public Manifest getLatestManifest() {
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

                            setManifest(gson.fromJson(new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8), Manifest.class));
                            if (lastCheckedManifestVersion != manifest.getVersion()) {
                                lastCheckedManifestVersion = manifest.getVersion();
                            }
                        } catch (JsonSyntaxException e) {
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
            log.error("Bad URL given: {}", e.getLocalizedMessage());
        }
        return null;
    }
}