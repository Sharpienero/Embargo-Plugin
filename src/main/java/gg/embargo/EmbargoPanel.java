package gg.embargo;

import gg.embargo.EmbargoConfig;
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
    private final JPanel supportButtons;
    final JPanel fetchedInfoPanel;





    private final JPanel sidePanel;

    @Inject
    EmbargoPanel(EmbargoPlugin plugin, EmbargoConfig config) {
        JPanel supportButtons = new JPanel();
        this.supportButtons = new JPanel();
        this.sidePanel = new JPanel();
        this.titlePanel = new JPanel();
        this.fetchedInfoPanel = new JPanel();
    }

    public void sidePanelInitializer()
    {
        this.setLayout(new BorderLayout());
        this.setBorder(new EmptyBorder(10, 10, 10, 10));
        this.sidePanel.setLayout(new BoxLayout(this.sidePanel,BoxLayout.Y_AXIS));
        this.sidePanel.add(this.buildTitlePanel());
        this.sidePanel.add(Box.createRigidArea(new Dimension(0, 5)));

        this.sidePanel.add(this.buildSupportbuttons());

        //ensure to build all the panels on the initial load, do not build them everytime i add or remove.
        buildFetchedInfoPanel();

        this.add(sidePanel, "North");
    }

    private JPanel buildFetchedInfoPanel()
    {
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

    private JPanel buildTitlePanel()
    {
        titlePanel.setBorder(new CompoundBorder(new EmptyBorder(5, 0, 0, 0), new MatteBorder(0, 0, 1, 0, new Color(37, 125, 141))));
        titlePanel.setLayout(new BorderLayout());
        PluginErrorPanel errorPanel = new PluginErrorPanel();
        errorPanel.setBorder(new EmptyBorder(2, 0, 3, 0));
        errorPanel.setContent("Embargo Clan", "We Don't Trade");
        titlePanel.add(errorPanel, "Center");
        return titlePanel;
    }

    private JPanel buildSupportButtons() {
        //sets the main panels layout
        supportButtons.setLayout(new BorderLayout());
        //sets the main panels border
        supportButtons.setBorder(new EmptyBorder(4, 5, 0, 10));

        //creates the sub panel which the buttons are contained in
        JPanel myButtons = new JPanel(new GridBagLayout());
        myButtons.setLayout(new GridLayout(1, 2, 8, 0));
        myButtons.setBorder(new EmptyBorder(10, 5, 0, 0));

        //creates the individual buttons and assings there text or icon ect...
        JButton discordButton = new JButton(DISCORD_ICON);
        JButton githubButton = new JButton(GITHUB_ICON);

        //sets what happens when the button is hovered over
        discordButton.setRolloverIcon(DISCORD_HOVER);
        githubButton.setRolloverIcon(GITHUB_HOVER);

        //sets the buttons preferred size
        discordButton.setPreferredSize(new Dimension(23, 25));
        githubButton.setPreferredSize(new Dimension(20, 23));

        //removes the box around the button
        SwingUtil.removeButtonDecorations(githubButton);
        SwingUtil.removeButtonDecorations(discordButton);

        //links button press to method call
        githubButton.addActionListener(e -> githubLink());
        discordButton.addActionListener(e -> discordLink());

        //adds buttons to JPanel
        myButtons.add(githubButton);
        myButtons.add(discordButton);

        //adds Panel to master/main panel
        supportButtons.add(myButtons, "East");

        return supportButtons;
    }

    private JPanel buildSupportbuttons()
    {
        //sets the main panles layout
        supportButtons.setLayout(new BorderLayout());
        //sets the main panels border
        supportButtons.setBorder(new EmptyBorder(4, 5, 0, 10));

        //creates the sub panel which the buttons are contained in
        JPanel myButtons = new JPanel(new GridBagLayout());
        myButtons.setLayout(new GridLayout(1, 2, 8, 0));
        myButtons.setBorder(new EmptyBorder(10, 5, 0, 0));

        //creates the individual buttons and assets there text or icon ect...
        JButton discordButton = new JButton(DISCORD_ICON);
        JButton githubButton = new JButton(GITHUB_ICON);

        //sets what happens when the button is hovered over
        discordButton.setRolloverIcon(DISCORD_HOVER);
        githubButton.setRolloverIcon(GITHUB_HOVER);

        //sets the buttons preferred size
        discordButton.setPreferredSize(new Dimension(23, 25));
        githubButton.setPreferredSize(new Dimension(20, 23));

        //removes the box around the button
        SwingUtil.removeButtonDecorations(githubButton);
        SwingUtil.removeButtonDecorations(discordButton);

        //links button press to method call
        githubButton.addActionListener(e -> githubLink());
        discordButton.addActionListener(e -> discordLink());

        //adds buttons to JPanel
        myButtons.add(githubButton);
        myButtons.add(discordButton);

        //adds Panel to master/main panel
        supportButtons.add(myButtons, "East");

        return supportButtons;
    }

    //uses the default browser on the machine to open the attached link (my discord for support & my github)
    public void discordLink()
    {
        try { Desktop.getDesktop().browse(new URI("https://embargo.gg/discord")); }
        catch (IOException | URISyntaxException e1) { e1.printStackTrace(); }
    }

    public void githubLink()
    {
        try { Desktop.getDesktop().browse(new URI("https://github.com/Sharpienero/Embargo-Plugin")); }
        catch (IOException | URISyntaxException e1) { e1.printStackTrace(); }
    }
}






