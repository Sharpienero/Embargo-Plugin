package gg.embargo;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

@Slf4j
public class EmbargoPanel extends PluginPanel {
    private static final ImageIcon DISCORD_ICON = new ImageIcon(
            ImageUtil.loadImageResource(EmbargoPanel.class, "/discord_icon.png"));
    private static final String DISCORD_INVITE = "https://discord.gg/embargo";

    @Inject
    @Nullable
    private Client client;

    @Inject
    private DataManager dataManager;

    @Setter
    private boolean isLoggedIn = false;

    private final JPanel versionPanel = new JPanel();
    private final JLabel loggedLabel = new JLabel();
    private final JLabel emailLabel = new JLabel();
    private final JLabel isRegisteredWithClanLabel = new JLabel();
    private final JLabel embargoScoreLabel = new JLabel();
    private final JLabel accountScoreLabel = new JLabel();
    private final JLabel communityScoreLabel = new JLabel();
    private final JLabel currentRankLabel = new JLabel();
    private final JLabel currentCALabel = new JLabel();

    public EmbargoPanel() {
        super(false);
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel container = new JPanel();
        container.setLayout(new BorderLayout(0, 0));
        container.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel embargoPanel = new JPanel();
        embargoPanel.setBorder(new EmptyBorder(10, 10, 0, 10));
        embargoPanel.setLayout(new BorderLayout(0, 0));
        embargoPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel();
        title.setText("Embargo Plugin");
        title.setForeground(Color.WHITE);
        title.setFont(FontManager.getRunescapeBoldFont());

        embargoPanel.add(title, BorderLayout.NORTH);
        container.add(embargoPanel, BorderLayout.NORTH);

        initializeVersionPanel();
        container.add(versionPanel, BorderLayout.CENTER);

        add(container, BorderLayout.NORTH);
        initializeDiscordButton();
    }

    private void initializeVersionPanel() {
        versionPanel.setLayout(new GridLayout(0, 1));
        versionPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        versionPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        loggedLabel.setText(htmlLabel("Signed in as ", ""));
        emailLabel.setText(htmlLabel("Sign in to send data to Embargo", ""));

        versionPanel.add(loggedLabel);
        versionPanel.add(emailLabel);
    }

    private void initializeDiscordButton() {
        JPanel bottomContainer = new JPanel();
        bottomContainer.setBorder(new EmptyBorder(10, 0, 0, 0));
        bottomContainer.setLayout(new BorderLayout());
        bottomContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JButton discordButton = new JButton();
        discordButton.setToolTipText("Join the Embargo Discord");
        discordButton.setIcon(DISCORD_ICON);
        discordButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
        discordButton.setFocusPainted(false);
        discordButton.setBorder(new EmptyBorder(0, 0, 0, 0));

        discordButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                LinkBrowser.browse(DISCORD_INVITE);
            }
        });

        bottomContainer.add(discordButton, BorderLayout.CENTER);
        add(bottomContainer, BorderLayout.SOUTH);
    }

    public void updateLoggedIn(boolean scheduled) {
        if (!isLoggedIn || scheduled) {
            SwingUtilities.invokeLater(() -> {
                if (client != null && client.getLocalPlayer() != null) {
                    String username = client.getLocalPlayer().getName();
                    updateUserInterface(username);
                }
            });
        }
    }

    private void updateUserInterface(String username) {
        loggedLabel.setText(htmlLabel("Signed in as ", username));
        boolean isRegistered = dataManager.checkRegistered(username);

        if (isRegistered) {
            updateRegisteredUserInterface(username);
        }
    }

    private void updateRegisteredUserInterface(String username) {
        versionPanel.remove(emailLabel);
        rebuildPanel();
        updateProfileData(username);
    }

    private void updateProfileData(String username) {
        JsonObject profile = dataManager.getProfile(username);

        JsonElement currentAccountPoints = profile.get("accountPoints");
        JsonElement currentCommunityPoints = profile.get("communityPoints");
        JsonElement currentHighestCAName = profile.get("currentHighestCAName");

        int accountPoints = Integer.parseInt(currentAccountPoints.toString());
        int communityPoints = Integer.parseInt(currentCommunityPoints.toString());

        isRegisteredWithClanLabel.setText(htmlLabel("Account registered:", " Yes"));
        embargoScoreLabel.setText(htmlLabel("Embargo Score:", " " + (accountPoints + communityPoints)));
        accountScoreLabel.setText(htmlLabel("Account Score: ", String.valueOf(accountPoints)));
        communityScoreLabel.setText(htmlLabel("Community Score: ", String.valueOf(communityPoints)));
        currentCALabel.setText(htmlLabel("Highest Combat Achievement: ", currentHighestCAName.toString()));
    }

    private void rebuildPanel() {
        versionPanel.add(isRegisteredWithClanLabel);
        versionPanel.add(embargoScoreLabel);
        versionPanel.add(accountScoreLabel);
        versionPanel.add(communityScoreLabel);
        versionPanel.add(currentRankLabel);
        versionPanel.add(currentCALabel);
        versionPanel.revalidate();
        versionPanel.repaint();
    }

    private String htmlLabel(String key, String value) {
        return "<html><body style = 'color:#a5a5a5'>" + key + "<span style = 'color:white'>" + value
                + "</span></body></html>";
    }
}