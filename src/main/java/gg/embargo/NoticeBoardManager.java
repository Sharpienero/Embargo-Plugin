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
    EmbargoConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(EmbargoConfig.class);
    }

    // Default color for highlighting clan members
    private static final int DEFAULT_RGB = 0xff981f;

    // Widget constants
    private static final int STARTING_PARTY_CHILD_ID = 17;
    private static final int ENDING_PARTY_CHILD_ID = 62;
    private static final int TOB_APPLICATION_PARENT = 50;
    private static final int TOB_APPLICATION_CHILD = 42;
    private static final int TOB_NOTICEBOARD_PARENT = 364;
    private static final int TOB_NOTICEBOARD_INDEX = 3;
    private static final int TOA_APPLICATION_PARENT = 774;
    private static final int TOA_APPLICATION_CHILD = 48;
    private static final int TOA_NOTICEBOARD_PARENT = 772;
    private static final int TOA_NOTICEBOARD_INDEX = 2;

    private void processWidgetChildren(Widget noticeBoard, int index, int clanColor) {
        if (!isValidWidget(noticeBoard)) {
            return;
        }

        for (Widget child : noticeBoard.getChildren()) {
            if (child.getIndex() == index) {
                updateClanMemberColor(noticeBoard.getName(), child, clanColor);
            }
        }
    }

    private boolean isValidWidget(Widget widget) {
        return widget != null && widget.getName() != null && widget.getChildren() != null;
    }

    private void updateClanMemberColor(String noticeBoardName, Widget widget, int clanColor) {
        if (client.getClanChannel() == null) {
            return;
        }

        client.getClanChannel().getMembers().stream()
                .filter(member -> Text.toJagexName(member.getName()).equals(Text.removeTags(noticeBoardName)))
                .findFirst()
                .ifPresent(member -> widget.setTextColor(config.highlightClan() ? clanColor : DEFAULT_RGB));
    }

    private void setNoticeBoardWidget(int parent, int index, int clanColor) {
        for (int childID = STARTING_PARTY_CHILD_ID; childID < ENDING_PARTY_CHILD_ID; ++childID) {
            processWidgetChildren(client.getWidget(parent, childID), index, clanColor);
        }
    }

    private void setApplicationWidget(int parent, int child, int clanColor) {
        Widget acceptWidgetMembers = client.getWidget(parent, child);
        if (!isValidWidget(acceptWidgetMembers) || client.getClanChannel() == null) {
            return;
        }

        String hex = Integer.toHexString(clanColor).substring(2);
        for (Widget w : acceptWidgetMembers.getChildren()) {
            client.getClanChannel().getMembers().stream()
                    .filter(member -> w.getText().contains(member.getName()))
                    .findFirst()
                    .ifPresent(member -> {
                        String coloredName = String.format("<col=%s>%s</col>", hex, member.getName());
                        w.setName(coloredName);
                        w.setText(coloredName);
                    });
        }
    }

    private void setTOBNameColors(int clanColor) {
        setApplicationWidget(TOB_APPLICATION_PARENT, TOB_APPLICATION_CHILD, clanColor);
        setNoticeBoardWidget(TOB_NOTICEBOARD_PARENT, TOB_NOTICEBOARD_INDEX, clanColor);
    }

    private void setTOANameColors(int clanColor) {
        setApplicationWidget(TOA_APPLICATION_PARENT, TOA_APPLICATION_CHILD, clanColor);
        setNoticeBoardWidget(TOA_NOTICEBOARD_PARENT, TOA_NOTICEBOARD_INDEX, clanColor);
    }

    void setTOBNoticeBoard() {
        setTOBNameColors(config.clanColor().getRGB());
    }

    void setTOANoticeBoard() {
        setTOANameColors(config.clanColor().getRGB());
    }

    void unsetNoticeBoard() {
        setTOBNameColors(DEFAULT_RGB);
        setTOANameColors(DEFAULT_RGB);
    }
}