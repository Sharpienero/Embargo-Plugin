// Most code taken from https://github.com/DapperMickie/Embargo-sounds

package gg.embargo.eastereggs.sounds;

import gg.embargo.EmbargoConfig;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.ObjectID;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;

@Singleton
@Slf4j
public class TobChestLight
{

    @Inject
    private Client client;

    @Inject
    private EmbargoConfig config;

    @Inject
    private SoundEngine soundEngine;

    @Inject
    private ScheduledExecutorService executor;

    private static final int YOUR_TOB_CHEST_PURPLE_OBJ = ObjectID.TOB_TREASUREROOM_CHEST_MINE_RARE;
    private static final int YOUR_TOB_CHEST_NORMAL_OBJ = ObjectID.TOB_TREASUREROOM_CHEST_MINE_STANDARD;
    private static final int OTHER_TOB_CHEST_PURPLE_OBJ = ObjectID.TOB_TREASUREROOM_CHEST_NOTMINE_RARE;

    private static final List<Integer> REWARD_CHEST_IDS = Arrays.asList(ObjectID.TOB_TREASUREROOM_CHEST_LOC0, ObjectID.TOB_TREASUREROOM_CHEST_LOC1, ObjectID.TOB_TREASUREROOM_CHEST_LOC2, ObjectID.TOB_TREASUREROOM_CHEST_LOC3, ObjectID.TOB_TREASUREROOM_CHEST_LOC4);

    private boolean isPurple = false;
    private boolean isMine = false;
    private boolean inRaid = false;
    private boolean loadedPlayers = false;
    private int playerCount = 0;
    private int loadedObjectCount = 0;


    public static final int THEATRE_RAIDERS_VARP = 330;
    public static final int MAX_RAIDERS = 5;

    public static final int STATE_NO_PARTY = 0;
    public static final int STATE_IN_PARTY = 1;

    int raidState;

    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        if (!config.enableClanEasterEggs())
        {
            return;
        }

        int objId = event.getGameObject().getId();
        if (REWARD_CHEST_IDS.contains(objId))
        {
            int impostorId = client.getObjectDefinition(objId).getImpostor().getId();

            if (impostorId == YOUR_TOB_CHEST_PURPLE_OBJ)
            {
                isPurple = true;
                isMine = true;
            }
            else if (impostorId == OTHER_TOB_CHEST_PURPLE_OBJ)
            {
                isPurple = true;
                isMine = false;
            }
            else if (impostorId == YOUR_TOB_CHEST_NORMAL_OBJ)
            {
                isMine = false;
            }
            loadedObjectCount++;

            if (loadedObjectCount == playerCount)
            {
                if (isPurple) {
                    // TODO: Maybe change sound if it's yours
                    if (isMine) {
                        soundEngine.playClip(Sound.MY_TOB_PURPLE, executor);
//                      } else {
//                          soundEngine.playClip(Sound.GETTING_PURPLE_1, executor);
//                      }
                    }
                else {
                    soundEngine.playClip(Sound.TOB_WHITE_LIGHT, executor);
                    }
                }
            }
        }
    }

    public void onGameTick(GameTick event)
    {
        if (inRaid && !loadedPlayers)
        {
            Map<Integer, Object> varcmap = client.getVarcMap();
            for (int i = 0; i < MAX_RAIDERS; i++)
            {
                Integer playervarp = THEATRE_RAIDERS_VARP + i;
                if (varcmap.containsKey(playervarp) && !varcmap.get(playervarp).equals(""))
                {
                    playerCount++;
                }
            }

            loadedPlayers = true;
        }
    }

    // Yoinked from https://github.com/Adam-/runelite-plugins/blob/tob-drop-chance/src/main/java/com/tobdropchance/TobDropChancePlugin.java
    public void onVarbitChanged(VarbitChanged event)
    {
        int nextState = client.getVarbitValue(VarbitID.TOB_VERZIK_THRONE_VISIBLE);
        if (raidState != nextState)
        {
            if (nextState == STATE_NO_PARTY || nextState == STATE_IN_PARTY)
            { // Player is not in a raid.
                reset();
                raidState = nextState;
            }
            else
            { // Player has entered the theatre.
                if (raidState == STATE_IN_PARTY)
                { // Player was in a party. They are a raider.
                    reset();
                    inRaid = true;
                }

                raidState = nextState;
            }
        }
    }

    private void reset()
    {
        isPurple = false;
        isMine = false;
        inRaid = false;
        loadedPlayers = false;
        playerCount = 0;
        loadedObjectCount = 0;
    }

    public void onGameObjectDespawned(GameObjectDespawned event)
    {
        int objId = event.getGameObject().getId();
        if (REWARD_CHEST_IDS.contains(objId))
        {
            isMine = false;
            isPurple = false;
            loadedObjectCount--;
        }
    }
}