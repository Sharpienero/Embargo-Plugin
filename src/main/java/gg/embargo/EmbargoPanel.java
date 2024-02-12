package gg.embargo;

import gg.embargo.EmbargoConfig;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class EmbargoPanel extends PluginPanel {
    private static final ImageIcon DISCORD_ICON;
    private static final ImageIcon DISCORD_HOVER;
    private static final ImageIcon GITHUB_ICON;
    private static final ImageIcon GITHUB_HOVER;

    private final JPanel titlePanel;

    private final JPanel introductionPanel;
    private final JPanel supportButtons;
    final JPanel fetchedInfoPanel;

    private final PluginErrorPanel errorPanel = new PluginErrorPanel();
    private final PluginErrorPanel futureFunctionalityPanel = new PluginErrorPanel();

    private final JLabel introductionLabel = new JLabel("Welcome to Embargo");

    private final JPanel sidePanel;

    @Inject
    EmbargoPanel(EmbargoPlugin plugin, EmbargoConfig config) {
        this.supportButtons = new JPanel();
        this.sidePanel = new JPanel();
        this.titlePanel = new JPanel();
        this.fetchedInfoPanel = new JPanel();
        this.introductionPanel = new JPanel();
    }

    public void sidePanelInitializer() {
        this.setLayout(new BorderLayout());
        this.setBorder(new EmptyBorder(10, 10, 10, 10));
        this.sidePanel.setLayout(new BoxLayout(this.sidePanel, BoxLayout.Y_AXIS));
        this.sidePanel.add(this.buildTitlePanel());
        this.sidePanel.add(this.buildIntroductionPanel());


        // 5px tall Spacer
        this.sidePanel.add(Box.createRigidArea(new Dimension(0, 5)));

        // Discord + Github button at the bottom of the panel
        this.sidePanel.add(this.buildSupportbuttons());

        // Ensure to build all the panels on the initial load, do not build them every time I add or remove.
        buildFetchedInfoPanel();

        this.add(sidePanel, "North");
    }

    private JPanel buildFetchedInfoPanel() {
        fetchedInfoPanel.setLayout(new BorderLayout());
        fetchedInfoPanel.setBorder(new EmptyBorder(0, 0, 0, 10));

        JPanel fetchedInfoSection = new JPanel();
        fetchedInfoSection.setLayout(new GridLayout(9, 0, 0, 10));
        fetchedInfoSection.setBorder(new EmptyBorder(15, 5, 3, 0));

        fetchedInfoPanel.setBorder(new MatteBorder(0, 0, 1, 0, new Color(37, 125, 141)));
        fetchedInfoPanel.add(fetchedInfoSection, "West");

        return fetchedInfoPanel;
    }

    static {
        BufferedImage discordPNG = ImageUtil.loadImageResource(EmbargoPlugin.class, "/discord_icon.png");
        BufferedImage githubPNG = ImageUtil.loadImageResource(EmbargoPlugin.class, "/github_icon.png");
        DISCORD_ICON = new ImageIcon(discordPNG);
        DISCORD_HOVER = new ImageIcon(ImageUtil.luminanceOffset(discordPNG, -80));

        GITHUB_ICON = new ImageIcon(githubPNG);
        GITHUB_HOVER = new ImageIcon(ImageUtil.luminanceOffset(githubPNG, -80));
    }

    private JPanel buildIntroductionPanel() {
        introductionPanel.setLayout(new BorderLayout());
        introductionPanel.setBorder(new EmptyBorder(2, 0, 3, 0));

        JPanel introductionSection = new JPanel(new CardLayout());
        introductionSection.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        introductionSection.setOpaque(false);

        introductionSection.add(introductionLabel);
        errorPanel.setContent("Current Functionality", "Detects and uploads your Quest Points, Achievement Diaries, Combat Achievement, Untrackable Items, and your skill levels.");
        futureFunctionalityPanel.setContent("Future Functionality", "Display your clan rank, account score, community score, what you are missing for a rank up, and much more!");
        introductionPanel.add(errorPanel, "North");
        introductionPanel.add(futureFunctionalityPanel, "South");

        return introductionPanel;
    }

    private JPanel buildTitlePanel() {
        titlePanel.setBorder(new CompoundBorder(new EmptyBorder(5, 0, 0, 0), new MatteBorder(0, 0, 1, 0, new Color(37, 125, 141))));
        titlePanel.setLayout(new BorderLayout());
        PluginErrorPanel errorPanel = new PluginErrorPanel();
        errorPanel.setBorder(new EmptyBorder(2, 0, 1, 0));
        errorPanel.setContent("Embargo Clan Plugin", "");
        titlePanel.add(errorPanel, "Center");
        return titlePanel;
    }

    private JPanel buildSupportbuttons() {
        supportButtons.setLayout(new BorderLayout());
        supportButtons.setBorder(new EmptyBorder(4, 5, 0, 10));

        JPanel myButtons = new JPanel(new GridBagLayout());
        myButtons.setLayout(new GridLayout(1, 2, 8, 0));
        myButtons.setBorder(new EmptyBorder(10, 5, 0, 0));

        JButton discordButton = new JButton(DISCORD_ICON);
        JButton githubButton = new JButton(GITHUB_ICON);

        discordButton.setRolloverIcon(DISCORD_HOVER);
        githubButton.setRolloverIcon(GITHUB_HOVER);

        discordButton.setPreferredSize(new Dimension(23, 25));
        githubButton.setPreferredSize(new Dimension(20, 23));

        SwingUtil.removeButtonDecorations(githubButton);
        SwingUtil.removeButtonDecorations(discordButton);

        githubButton.addActionListener(e -> githubLink());
        discordButton.addActionListener(e -> discordLink());

        myButtons.add(githubButton);
        myButtons.add(discordButton);

        supportButtons.add(myButtons, "East");

        return supportButtons;
    }

    public void discordLink() {
        try {
            Desktop.getDesktop().browse(new URI("https://embargo.gg/discord"));
        } catch (IOException | URISyntaxException e1) {
            // Implement a more efficient error handling strategy here
            e1.printStackTrace();
        }
    }

    public void githubLink() {
        try {
            Desktop.getDesktop().browse(new URI("https://github.com/Sharpienero/Embargo-Plugin"));
        } catch (IOException | URISyntaxException e1) {
            // Implement a more efficient error handling strategy here
            e1.printStackTrace();
        }
    }
}
