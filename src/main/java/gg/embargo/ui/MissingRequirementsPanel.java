package gg.embargo.ui;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import net.runelite.http.api.item.ItemPrice;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;



@Slf4j
public class MissingRequirementsPanel extends PluginPanel {
    private static final String OSRS_WIKI_BASE_URL = "https://oldschool.runescape.wiki/w/";
    private static final int ITEMS_PER_ROW = 5; // Changed to 5 items per row
    private static final int ICON_SIZE = 32; //
    private static final int CELL_SIZE = 32; //
    private static final int TOOLTIP_PADDING = 3;

    private final ItemManager itemManager;
    private final JPanel itemsContainer;
    private List<MissingItem> missingItems = new ArrayList<>();

    @Inject
    public MissingRequirementsPanel(ItemManager itemManager) {
        super(false);
        this.itemManager = itemManager;


        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setBorder(new EmptyBorder(0, 0, 10, 0));
        titlePanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel("Missing Requirements");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        titlePanel.add(title, BorderLayout.NORTH);

        JLabel subtitle = new JLabel("Hover for details, click to open wiki");
        subtitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        subtitle.setFont(new Font("SansSerif", Font.ITALIC, 10));
        titlePanel.add(subtitle, BorderLayout.SOUTH);

        add(titlePanel, BorderLayout.NORTH);

        itemsContainer = new JPanel();
        itemsContainer.setLayout(new GridLayout(0, ITEMS_PER_ROW, 2, 2)); // Using GridLayout with 5 columns
        itemsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scrollPane = new JScrollPane(itemsContainer);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Adds a missing item to the panel if it doesn't already exist
     * 
     * @param itemName The name of the item
     * @param itemId The item ID in RuneScape
     */
    public void addMissingItem(String itemName, int itemId) {
        boolean itemExists = missingItems.stream()
                .anyMatch(item -> item.getItemName().contains(itemName.replace("\"", "")));
      
        // Only add if it doesn't exist
        if (!itemExists) {
            if (itemName.contains("Combat Achievement")) {
                // Add combat achievement item
                missingItems.add(new MissingItem(itemName.replace("\"", ""), getHiltIdFromName(itemName), getHiltImageFromName(itemName.split(" ")[0])));
                updatePanel();
            } else {
                BufferedImage itemIcon = getItemIcon(itemId, itemName);
                missingItems.add(new MissingItem(itemName.replace("\"", ""), itemId, itemIcon));
                updatePanel();
            }
        }
    }

    @Getter
    public enum AchievementHilts {
        EASY(25926),
        MEDIUM(25928),
        HARD(25930),
        ELITE(25932),
        MASTER(25934),
        GRANDMASTER(25936);

        private final int itemId;

        AchievementHilts(int itemId) {
            this.itemId = itemId;
        }
    }

    public BufferedImage getHiltImageFromName(String name) {
        String lowercaseName = name.toLowerCase();
        for (AchievementHilts hilt : AchievementHilts.values()) {
            if (lowercaseName.contains(hilt.name().toLowerCase())) {
                return getItemIcon(hilt.getItemId(), "Ghommal's_avernic_defender_" + (hilt.ordinal() + 1));
            }
        }
        return getItemIcon(882, "Bronze arrow");
    }

    public int getHiltIdFromName(String name) {
        String lowercaseName = name.toLowerCase();

        // Special case for Grandmaster since conflicting master in name
        if (lowercaseName.contains("grand")) {
            return AchievementHilts.GRANDMASTER.getItemId();
        }
        
        // Check for other hilt types
        for (AchievementHilts hilt : AchievementHilts.values()) {
            if (lowercaseName.contains(hilt.name().toLowerCase())) {
                return hilt.getItemId();
            }
        }
        
        return 882;
    }

    /**
     * Clears all missing items from the panel
     */
    public void clearItems() {
        missingItems.clear();
        updatePanel();
    }

    /**
     * Updates the panel with the current list of missing items
     */
    private void updatePanel() {
        itemsContainer.removeAll();
        
        for (MissingItem item : missingItems) {
            JPanel itemPanel = createItemPanel(item, item.getItemId() != -1);
            itemPanel.setBorder(new LineBorder(Color.BLACK, 1));
            itemsContainer.add(itemPanel);
        }
        
        revalidate();
        repaint();
    }

    /**
     * Creates a panel for a single item with icon and hover/click functionality
     */
    private JPanel createItemPanel(MissingItem item, boolean showTooltip) {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR, 1));
        panel.setPreferredSize(new Dimension(CELL_SIZE, CELL_SIZE));
        panel.setMinimumSize(new Dimension(CELL_SIZE, CELL_SIZE));
        panel.setMaximumSize(new Dimension(CELL_SIZE, CELL_SIZE));
    
        // Get the item icon
        BufferedImage iconImage = getItemIcon(item.getItemId(), item.getItemName());
        JLabel iconLabel = new JLabel(new ImageIcon(iconImage));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(iconLabel, BorderLayout.CENTER);
        panel.setToolTipText(buildTooltipText(item));
    
        // Add hover effect and click handler to the panel
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                panel.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
                panel.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }
        
            @Override
            public void mouseExited(MouseEvent e) {
                panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                panel.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            }
        
            @Override
            public void mouseClicked(MouseEvent e) {
                String wikiUrl = OSRS_WIKI_BASE_URL + item.getItemName().replace(" ", "_").replace("\"", "");
                LinkBrowser.browse(wikiUrl);
            }
        });
    
        return panel;
    }

    /**
     * Gets the item icon from the ItemManager or a default icon if not found
     */
    private BufferedImage getItemIcon(int itemId, String itemName) {
        BufferedImage icon;
        
        if (itemId != -1) {
            icon = itemManager.getImage(itemId);
            return ImageUtil.resizeImage(icon, CELL_SIZE, CELL_SIZE);
        } else {
            icon = new BufferedImage(CELL_SIZE + 2, CELL_SIZE + 2, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = icon.createGraphics();

            // Fill the entire image with the background color
            g2d.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
            g2d.fillRect(0, 0, CELL_SIZE + 2, CELL_SIZE + 2);
            g2d.setColor(Color.WHITE);
            
            String letter = itemName.substring(0, 1).toUpperCase();
            if (itemName.contains("community")) {
                letter = "CP";
            } else if (itemName.contains("account")) {
                letter = "AP";
            } else if (itemName.contains("EHB")) {
                letter = "EHB";
            } else if (itemName.contains("EHP")) {
                letter = "EHP";
            }
            
            // Calculate appropriate font size based on text length
            int fontSize = 16; // Default font size
            if (letter.length() > 1) {
                // Reduce font size for longer text
                fontSize = Math.max(8, 16 - (letter.length() - 1) * 2);
            }
            
            g2d.setFont(new Font("SansSerif", Font.BOLD, fontSize));
            
            // Ensure text fits within the cell
            FontMetrics fm = g2d.getFontMetrics();
            while (fm.stringWidth(letter) > CELL_SIZE - 2 && fontSize > 8) {
                fontSize--;
                g2d.setFont(new Font("SansSerif", Font.BOLD, fontSize));
                fm = g2d.getFontMetrics();
            }
            
            int x = (CELL_SIZE + 2 - fm.stringWidth(letter)) / 2;
            int y = ((CELL_SIZE + 2 - fm.getHeight()) / 2) + fm.getAscent();
            g2d.drawString(letter, x, y);
            g2d.dispose();
            
            // Resize the image to exactly match CELL_SIZE
            return ImageUtil.resizeImage(icon, CELL_SIZE, CELL_SIZE);
        }
    }

    /**
     * Builds the tooltip text for an item
     */
    private String buildTooltipText(MissingItem item) {
        if (item.getItemId() == -1) {
            return "<html><body style='padding: " + TOOLTIP_PADDING + "px; text-align: center;'>" +
                    "<div style='font-weight: bold;'>" + item.getItemName() + "</div>" +
                    "</body></html>";
        }

        return "<html><body style='padding: " + TOOLTIP_PADDING + "px;'>" +
                "<div style='font-weight: bold; margin-bottom: 3px;'>" + item.getItemName() + "</div>" +
                "<div style='color: #99FFFF; font-style: italic;'>Click to open wiki</div>" +
                "</body></html>";
    }

    /**
     * Attempts to find an item ID by name using the ItemManager
     */
    public int findItemIdByName(String itemName) {
        // Convert the search name to lowercase for case-insensitive comparison
        String searchName = itemName.replace("\"", "");
        List<ItemPrice> itemPrices = itemManager.search(searchName);
        if (itemPrices.isEmpty()) {
            return -1;
        }

        return itemPrices.get(0).getId();
    }

    /**
     * Class to represent a missing item
     */
    @Getter
    private static class MissingItem {
        private final String itemName;
        private final int itemId;

        @Setter
        private BufferedImage icon;

        public MissingItem(String itemName, int itemId, BufferedImage icon) {
            this.itemName = itemName;
            this.itemId = itemId;
            this.icon = icon;
        }
    }
}
