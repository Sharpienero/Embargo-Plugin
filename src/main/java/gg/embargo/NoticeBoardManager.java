/*
Almost all of this code was taken from the tob-notice-board plugin by Broooklyn
https://github.com/Broooklyn/runelite-external-plugins/tree/tob-notice-board
Slight modifications were made to work with clans
 */


package gg.embargo;

import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;

import javax.inject.Inject;

public class NoticeBoardManager {
    @Inject
    private static Client client;

    @Inject
    static EmbargoConfig config;

    private static final int DEFAULT_RGB = 0xff981f;

    private static void setNameColors(int clanColor) {
        for (int childID = 17; childID < 62; ++childID) {
            Widget noticeBoard = client.getWidget(364, childID);

            if (noticeBoard != null && noticeBoard.getName() != null && noticeBoard.getChildren() != null) {
                for (Widget noticeBoardChild : noticeBoard.getChildren()) {
                    if (noticeBoardChild.getIndex() == 3) {
                        if (client.getClanChannel() != null) {
                            for (ClanChannelMember member : client.getClanChannel().getMembers()) {
                                if (Text.toJagexName(member.getName()).equals(Text.removeTags(noticeBoard.getName()))) {
                                    noticeBoardChild.setTextColor(config.highlightClan() ? clanColor : DEFAULT_RGB);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    static void setNoticeBoard()
    {
        setNameColors(config.clanColor().getRGB());
    }

    static void unsetNoticeBoard()
    {
        setNameColors(DEFAULT_RGB);
    }
}
