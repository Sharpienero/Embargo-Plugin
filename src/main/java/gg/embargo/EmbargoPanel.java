package gg.embargo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.info.JRichTextPane;
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
import java.util.ArrayList;

@Slf4j
public class EmbargoPanel extends PluginPanel {
    @Inject
    @Nullable
    private Client client;
    @Inject
    private EventBus eventBus;

    @Inject
    private DataManager dataManager;

    @Setter
    public boolean isLoggedIn = false;

    @Inject
    private ItemManager itemManager;

    // Keep track of all boxes
    private final ArrayList<ItemID> items = new ArrayList<>();
    JPanel versionPanel = new JPanel();
    JPanel missingRequirementsPanel = new JPanel();
    private static final ImageIcon ARROW_RIGHT_ICON = new ImageIcon(ImageUtil.loadImageResource(EmbargoPanel.class, "/util/arrow_right.png"));
    private static final ImageIcon DISCORD_ICON = new ImageIcon(ImageUtil.loadImageResource(EmbargoPanel.class, "/discord_icon.png"));
    static ImageIcon GITHUB_ICON = new ImageIcon(ImageUtil.loadImageResource(EmbargoPanel.class, "/github_icon.png"));
    static ImageIcon WEBSITE_ICON = new ImageIcon(ImageUtil.loadImageResource(EmbargoPanel.class, "/website_icon.png"));
    private final JRichTextPane emailLabel = new JRichTextPane();
    private final JLabel loggedLabel = new JLabel();
    private final JLabel embargoScoreLabel = new JLabel(htmlLabel("Embargo Score:", " N/A"));
    private final JLabel accountScoreLabel = new JLabel(htmlLabel("Account Score:", " N/A"));
    private final JLabel communityScoreLabel = new JLabel(htmlLabel("Community Score:", " N/A"));
    private final JLabel currentRankLabel = new JLabel(htmlLabel("Current Rank:", " N/A"));
    private final JLabel isRegisteredWithClanLabel = new JLabel(htmlLabel("Account registered:", " No"));
    private final JLabel currentCALabel = new JLabel(htmlLabel("Current TA Tier:", " N/A"));
    private final JLabel missingRequiredItemsLabel = new JLabel(htmlLabel("Sign in to see what requirements", " you are missing for rank up"));
    private final Font smallFont = FontManager.getRunescapeSmallFont();
    final JPanel missingRequirementsContainer = new JPanel(new BorderLayout(5, 0));

    final JLabel playerNameLabel = new JLabel("Missing Requirements For Next Rank", JLabel.LEFT);
    @Inject
    private EmbargoPanel(ItemManager itemManager) {
        this.itemManager = itemManager;
    }
    private String htmlLabel(String key, String value)
    {
        return "<html><body style = 'color:#a5a5a5'>" + key + "<span style = 'color:white'>" + value + "</span></body></html>";
    }

    void setupVersionPanel() {
        //Set up Embargo Clan Version at top of Version panel
        JLabel version = new JLabel(htmlLabel("Embargo Clan Version: ", "1.1.0"));
        version.setFont(smallFont);

        //Set version's font
        JLabel revision = new JLabel();
        revision.setFont(smallFont);

        //Set up versionPanel
        versionPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        versionPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        versionPanel.setLayout(new GridLayout(0, 1));

        //Set up custom embargo labels
        isRegisteredWithClanLabel.setFont(smallFont);
        isRegisteredWithClanLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        embargoScoreLabel.setFont(smallFont);
        embargoScoreLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        accountScoreLabel.setFont(smallFont);
        accountScoreLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        communityScoreLabel.setFont(smallFont);
        communityScoreLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        currentCALabel.setFont(smallFont);
        currentCALabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        loggedLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        loggedLabel.setFont(smallFont);

        currentRankLabel.setFont(smallFont);
        currentRankLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        emailLabel.setForeground(Color.WHITE);
        emailLabel.setFont(smallFont);

        versionPanel.add(version);
        versionPanel.add(Box.createGlue());
        versionPanel.add(loggedLabel);
        versionPanel.add(emailLabel);
        versionPanel.add(isRegisteredWithClanLabel);
        versionPanel.add(embargoScoreLabel);
        versionPanel.add(accountScoreLabel);
        versionPanel.add(communityScoreLabel);
        versionPanel.add(currentCALabel);
    }

    javax.swing.JPanel setUpQuickLinks() {
        JPanel actionsContainer = new JPanel();
        actionsContainer.setBorder(new EmptyBorder(10, 0, 0, 0));
        actionsContainer.setLayout(new GridLayout(0, 1, 0, 10));

        actionsContainer.add(buildLinkPanel(DISCORD_ICON, "Join us on our", "Discord", "https://embargo.gg/discord"));
        actionsContainer.add(buildLinkPanel(WEBSITE_ICON, "Go to our", "clan website", "https://embargo.gg/"));
        actionsContainer.add(buildLinkPanel(GITHUB_ICON, "Report a bug or", "inspect the plugin code", "https://github.com/Sharpienero/Embargo-Plugin"));

        return actionsContainer;
    }

    void setupMissingItemsPanel() {
        missingRequirementsContainer.setBorder(new EmptyBorder(7, 7, 7, 7));
        missingRequirementsContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        playerNameLabel.setFont(FontManager.getRunescapeSmallFont());
        playerNameLabel.setForeground(Color.WHITE);
        playerNameLabel.setBorder(new EmptyBorder(5, 0, 5, 0));

        missingRequirementsContainer.add(playerNameLabel, BorderLayout.NORTH);
        missingRequirementsContainer.setFont(FontManager.getRunescapeSmallFont());
        missingRequirementsContainer.setForeground(Color.WHITE);
        missingRequirementsContainer.add(missingRequirementsPanel);

        missingRequirementsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        missingRequirementsPanel.setBorder(new EmptyBorder(10, 0, 10, 0));
        missingRequirementsPanel.setLayout(new GridLayout(1, 1));

        missingRequiredItemsLabel.setFont(FontManager.getRunescapeSmallFont());
        missingRequiredItemsLabel.setForeground(Color.WHITE);
        missingRequiredItemsLabel.setHorizontalAlignment(JLabel.LEFT);

        missingRequirementsPanel.add(missingRequiredItemsLabel, BorderLayout.NORTH);

        //Push text to top of component
        this.add(missingRequirementsContainer, BorderLayout.NORTH);
    }

    void addSidePanel() {
        //Add the panels to the side plugin
        this.add(versionPanel, BorderLayout.NORTH);
        setupMissingItemsPanel();
        this.add(this.setUpQuickLinks(), BorderLayout.SOUTH);

    }

    void setupSidePanel() {
        this.setupVersionPanel();
        this.setUpQuickLinks();
        this.addSidePanel();

        //Update version panel with Embargo plugin information
        updateLoggedIn(false);
    }

    void init()
    {
        this.setupSidePanel();
    }

    public void updateLoggedIn(boolean scheduled) {
        if (!isLoggedIn || scheduled) {
            if (client != null && client.getLocalPlayer() != null) {
                var username = client.getLocalPlayer().getName();
                loggedLabel.setText(htmlLabel("Signed in as ", " " + username));

                boolean isRegisteredWithClan = dataManager.checkRegistered(username);

                if (isRegisteredWithClan) {
                    //remove "Sign in to send..."
                    versionPanel.remove(emailLabel);

                    //re-register labels with panel
                    versionPanel.add(isRegisteredWithClanLabel);
                    versionPanel.add(embargoScoreLabel);
                    versionPanel.add(accountScoreLabel);
                    versionPanel.add(communityScoreLabel);
                    versionPanel.add(currentRankLabel);
                    versionPanel.add(currentCALabel);

                    isRegisteredWithClanLabel.setText(htmlLabel("Account registered:", " Yes"));

                    //get gear
                    var embargoProfileData = dataManager.getProfile(username);
                    JsonElement currentAccountPoints = embargoProfileData.get("accountPoints");

                    JsonElement currentCommunityPoints = embargoProfileData.getAsJsonPrimitive("communityPoints");
                    embargoScoreLabel.setText((htmlLabel("Embargo Score:", " " + (Integer.parseInt(String.valueOf(currentAccountPoints)) + Integer.parseInt(String.valueOf(currentCommunityPoints))))));
                    //JsonObject currentHighestCombatAchievementTier = embargoProfileData.getAsJsonObject("currentHighestCombatAchievementTier");
                    JsonElement getCurrentCAName = embargoProfileData.get("currentHighestCAName");
                    accountScoreLabel.setText(htmlLabel("Account Score: ", String.valueOf(Integer.parseInt(String.valueOf(currentAccountPoints)))));
                    communityScoreLabel.setText(htmlLabel("Community Score: ", String.valueOf(Integer.parseInt(String.valueOf(currentCommunityPoints)))));
                    //JsonArray currentGearReqs = embargoProfileData.getAsJsonArray("currentGearRequirements");
                    JsonArray missingGearReqs = embargoProfileData.getAsJsonArray("missingGearRequirements");
                    JsonObject nextRank = embargoProfileData.getAsJsonObject("nextRank");
                    JsonObject currentRank = embargoProfileData.getAsJsonObject("currentRank");
                    JsonElement currentRankName = currentRank.get("name");

                    var currentRankDisplay = String.valueOf(currentRankName).replace("\"", "");
                    currentRankLabel.setText(htmlLabel("Current Rank:", " " + currentRankDisplay));

                    var displayCAName = String.valueOf(getCurrentCAName).replace("\"", "");
                    displayCAName = displayCAName.replace(" Combat Achievement", "");

                    JsonElement nextRankName = nextRank.get("name");

                    currentCALabel.setText(htmlLabel("Current CA Tier:", " " + displayCAName));

                    //Set Missing Gear Requirements Label to display missing gear
                    StringBuilder missingGearReqsString = new StringBuilder();
                    missingGearReqsString.append("<html>");
                    for (int i = 0; i < missingGearReqs.size(); i++) {
                        missingGearReqsString.append("<div>- ").append(missingGearReqs.get(i).getAsString()).append("</div>");
                    }

                    missingGearReqsString.append("<html>");

                    //Build out the missing requirements panel
                    if (missingGearReqs.size() > 0) {
                        missingRequiredItemsLabel.setText(missingGearReqsString.toString());
                        //append panel under missingRequiredItemsLabel
                        missingRequiredItemsLabel.setForeground(Color.LIGHT_GRAY);
                        missingRequirementsPanel.add(missingRequiredItemsLabel);
                        
                    } else {
                        missingRequiredItemsLabel.setText(htmlLabel("Missing Requirements: ", "None"));
                    }


                    log.debug(username + " currently has " + currentAccountPoints + " account points and " + currentCommunityPoints + " community points.\n");
                    log.debug(username + " is currently rank " + currentRankName + ".\nThe next rank is: " + nextRankName + "\nThey need missing the following gear: " + missingGearReqs.toString());
                    log.debug(username + " currently has " + getCurrentCAName);
                } else {
                    emailLabel.setText("Account not registered with Embargo");
                }
                this.isLoggedIn = true;
            } else {
                this.logOut();
            }
        } else {
            logOut();
        }
    }

    public void logOut() {
        this.isLoggedIn = false;
        emailLabel.setContentType("text/html");
        emailLabel.setText("Sign in to send data to Embargo.");
        loggedLabel.setText("Not signed in");

        //Reset missing gear requirements
        missingRequiredItemsLabel.setText(htmlLabel("Sign in to see what requirements", " you are missing for rank up"));

        //Set to NA
        isRegisteredWithClanLabel.setText(htmlLabel("Account registered:", " No"));
        embargoScoreLabel.setText(htmlLabel("Embargo Score:", " N/A"));
        currentRankLabel.setText(htmlLabel("Current Rank:", " N/A"));
        accountScoreLabel.setText(htmlLabel("Account Score:", " N/A"));
        communityScoreLabel.setText(htmlLabel("Community Score:", " N/A"));

        //Add back email label
        versionPanel.add(emailLabel);

        //Unregister from the component
        versionPanel.remove(isRegisteredWithClanLabel);
        versionPanel.remove(embargoScoreLabel);
        versionPanel.remove(accountScoreLabel);
        versionPanel.remove(communityScoreLabel);
        versionPanel.remove(currentRankLabel);
        versionPanel.remove(currentCALabel);

    }

    void reset()
    {
        eventBus.unregister(this);
        this.updateLoggedIn(false);
    }

    /**
     * Builds a link panel with a given icon, text and url to redirect to.
     */
    private static JPanel buildLinkPanel(ImageIcon icon, String topText, String bottomText, String url)
    {
        return buildLinkPanel(icon, topText, bottomText, () -> LinkBrowser.browse(url));
    }

    /**
     * Builds a link panel with a given icon, text and callable to call.
     */
    private static JPanel buildLinkPanel(ImageIcon icon, String topText, String bottomText, Runnable callback) {
        JPanel container = new JPanel();
        container.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        container.setLayout(new BorderLayout());
        container.setBorder(new EmptyBorder(10, 10, 10, 10));

        final Color hoverColor = ColorScheme.DARKER_GRAY_HOVER_COLOR;
        final Color pressedColor = ColorScheme.DARKER_GRAY_COLOR.brighter();

        JLabel iconLabel = new JLabel(icon);
        container.add(iconLabel, BorderLayout.WEST);

        JPanel textContainer = new JPanel();
        textContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        textContainer.setLayout(new GridLayout(2, 1));
        textContainer.setBorder(new EmptyBorder(5, 10, 5, 10));

        container.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent mouseEvent) {
                container.setBackground(pressedColor);
                textContainer.setBackground(pressedColor);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                callback.run();
                container.setBackground(hoverColor);
                textContainer.setBackground(hoverColor);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                container.setBackground(hoverColor);
                textContainer.setBackground(hoverColor);
                container.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                container.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                textContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                container.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            }
        });

        JLabel topLine = new JLabel(topText);
        topLine.setForeground(Color.WHITE);
        topLine.setFont(FontManager.getRunescapeSmallFont());

        JLabel bottomLine = new JLabel(bottomText);
        bottomLine.setForeground(Color.WHITE);
        bottomLine.setFont(FontManager.getRunescapeSmallFont());

        textContainer.add(topLine);
        textContainer.add(bottomLine);

        container.add(textContainer, BorderLayout.CENTER);

        JLabel arrowLabel = new JLabel(ARROW_RIGHT_ICON);
        container.add(arrowLabel, BorderLayout.EAST);

        return container;
    }
}
