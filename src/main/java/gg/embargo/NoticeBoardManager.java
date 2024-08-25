/*
Almost all of this code was taken from the tob-notice-board plugin by Broooklyn
https://github.com/Broooklyn/runelite-external-plugins/tree/tob-notice-board
Slight modifications were made to work with clans

Added TOA code
 */


package gg.embargo;

import lombok.extern.slf4j.Slf4j;
import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;

import javax.inject.Inject;

@Slf4j
public class NoticeBoardManager {
    @Inject
    private Client client;

    @Inject
    private EmbargoConfig config;

    @Provides
    EmbargoConfig getConfig(ConfigManager configManager)
    {
        return configManager.getConfig(EmbargoConfig.class);
    }

    private static final int DEFAULT_RGB = 0xff981f;
    private static final int STARTING_PARTY_CHILD_ID = 17;
    private static final int ENDING_PARTY_CHILD_ID = 62;

    private void setNoticeBoardWidget(int parent, int index, int clanColor) {
        for (int childID = STARTING_PARTY_CHILD_ID; childID < ENDING_PARTY_CHILD_ID; ++childID) {
            Widget noticeBoard = client.getWidget(parent, childID);

            if (noticeBoard != null && noticeBoard.getName() != null && noticeBoard.getChildren() != null) {
                for (Widget noticeBoardChild : noticeBoard.getChildren()) {
                    if (noticeBoardChild.getIndex() == index) {
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

    private void setApplicationWidget(int parent, int child, int clanColor) {
        //tob applicant board
        Widget acceptWidgetMembers = client.getWidget(parent, child);
        if (acceptWidgetMembers != null && acceptWidgetMembers.getChildren() != null) {
            Widget[] acceptWidgetChildren = acceptWidgetMembers.getChildren();
            log.debug(String.valueOf(acceptWidgetMembers));
            for (Widget w : acceptWidgetChildren) {
                if (client != null && client.getClanChannel() != null) {
                    for (ClanChannelMember member : client.getClanChannel().getMembers()) {
                        if (w.getText().contains(member.getName())) {
                            String hex = Integer.toHexString(clanColor).substring(2);
                            String builtName = "<col=" + hex + ">" + member.getName() + "</col>";log.debug(builtName);
                            log.debug(builtName);

                            w.setName("<col=" + hex + ">" + member.getName() + "</col>");
                            w.setText(builtName);
                        }
                    }

                }
            }
        }
    }

    private void setTOBNameColors(int clanColor) {
        setApplicationWidget(50, 42, clanColor);
        setNoticeBoardWidget(364, 3, clanColor);
    }

    private void setTOANameColors(int clanColor) {
        setApplicationWidget(774, 48, clanColor);
        setNoticeBoardWidget(772, 2, clanColor);
    }

    void setTOBNoticeBoard()
    {
        setTOBNameColors(config.clanColor().getRGB());
    }

    void setTOANoticeBoard() {
        setTOANameColors(config.clanColor().getRGB());
    }

    void unsetNoticeBoard()
    {
        setTOBNameColors(DEFAULT_RGB);
        setTOANameColors(DEFAULT_RGB);
    }
}
