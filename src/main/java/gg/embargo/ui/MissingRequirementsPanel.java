package gg.embargo.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Slf4j
public class MissingRequirementsPanel extends PluginPanel {
    private static final String OSRS_WIKI_BASE_URL = "https://oldschool.runescape.wiki/w/";
    private static final int ITEMS_PER_ROW = 5;
    private static final int CELL_SIZE = 32;
    private static final int TOOLTIP_PADDING = 3;
    private static final Color HOVER_COLOR = ColorScheme.DARKER_GRAY_HOVER_COLOR;
    private static final Color NORMAL_COLOR = ColorScheme.DARKER_GRAY_COLOR;
    
    // Cache for item icons to avoid recreating them
    private final Map<Integer, BufferedImage> iconCache = new ConcurrentHashMap<>();
    private final Map<String, BufferedImage> letterIconCache = new ConcurrentHashMap<>();
    
    private final ItemManager itemManager;
    private final JPanel itemsContainer;
    private final List<MissingItem> missingItems = new ArrayList<>();
    private final MouseAdapter itemMouseAdapter = createMouseAdapter();

    @Inject
    public MissingRequirementsPanel(ItemManager itemManager) {
        super(false);
        this.itemManager = itemManager;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // Create title panel
        add(createTitlePanel(), BorderLayout.NORTH);

        // Create items container
        itemsContainer = new JPanel();
        itemsContainer.setLayout(new GridLayout(0, ITEMS_PER_ROW, 2, 2));
        itemsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scrollPane = new JScrollPane(itemsContainer);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createTitlePanel() {
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
        
        return titlePanel;
    }

    /**
     * Adds a missing item to the panel if it doesn't already exist
     * 
     * @param itemName The name of the item
     * @param itemId The item ID in RuneScape
     */
    public void addMissingItem(String itemName, int itemId) {
        if (itemName.isEmpty() && itemId != -1) {
            // get item name by id
            ItemComposition ic = itemManager.getItemComposition(itemId);

            // generate icon
            BufferedImage itemIcon = getItemIcon(itemId, ic.getName());
            // add to missing items panel
            missingItems.add(new MissingItem(ic.getName(), itemId, itemIcon));
            updatePanel();
            return;
        }

        String cleanedName = itemName.replace("\"", "");
        
        // Check if item already exists
        boolean itemExists = missingItems.stream()
                .anyMatch(item -> item.getItemName().contains(cleanedName));
      
        // Only add if it doesn't exist
        if (!itemExists) {
            BufferedImage itemIcon;
            int finalItemId;
            
            if (itemName.contains("Combat Achievement")) {
                finalItemId = getHiltIdFromName(itemName);
                itemIcon = getHiltImageFromName(itemName.split(" ")[0]);
            } else {
                finalItemId = itemId;
                itemIcon = getItemIcon(itemId, cleanedName);
            }
            
            missingItems.add(new MissingItem(cleanedName, finalItemId, itemIcon));
            updatePanel();
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
        // Strip out extra "'s
        lowercaseName = lowercaseName.replace("\"", "");

        
        for (AchievementHilts hilt : AchievementHilts.values()) {
            //Shortcut to bypass conflicting name with "Master" being in "Grandmaster"
            if (lowercaseName.contains("grand")) {
                return getItemIcon(AchievementHilts.GRANDMASTER.getItemId(), "Ghommal's_avernic_defender_6");
            }

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
            JPanel itemPanel = createItemPanel(item);
            itemsContainer.add(itemPanel);
        }
        
        revalidate();
        repaint();
    }

    /**
     * Creates a panel for a single item with icon and hover/click functionality
     */
    private JPanel createItemPanel(MissingItem item) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(NORMAL_COLOR);
        panel.setBorder(new LineBorder(Color.BLACK, 1));
        panel.setPreferredSize(new Dimension(CELL_SIZE, CELL_SIZE));
        panel.setMinimumSize(new Dimension(CELL_SIZE, CELL_SIZE));
        panel.setMaximumSize(new Dimension(CELL_SIZE, CELL_SIZE));
    
        // Get the item icon
        JLabel iconLabel = new JLabel(new ImageIcon(item.getIcon()));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(iconLabel, BorderLayout.CENTER);
        panel.setToolTipText(buildTooltipText(item));
    
        // Add hover effect and click handler to the panel
        panel.addMouseListener(itemMouseAdapter);
    
        return panel;
    }
    
    private MouseAdapter createMouseAdapter() {
        return new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                JPanel panel = (JPanel) e.getSource();
                panel.setBackground(HOVER_COLOR);
                panel.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }
        
            @Override
            public void mouseExited(MouseEvent e) {
                JPanel panel = (JPanel) e.getSource();
                panel.setBackground(NORMAL_COLOR);
                panel.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            }
        
            @Override
            public void mouseClicked(MouseEvent e) {
                JPanel panel = (JPanel) e.getSource();
                Component[] components = itemsContainer.getComponents();
                int index = -1;
                
                for (int i = 0; i < components.length; i++) {
                    if (components[i] == panel) {
                        index = i;
                        break;
                    }
                }
                
                if (index >= 0 && index < missingItems.size()) {
                    MissingItem item = missingItems.get(index);
                    String wikiUrl = OSRS_WIKI_BASE_URL + item.getItemName().replace(" ", "_");
                    LinkBrowser.browse(wikiUrl);
                }
            }
        };
    }

    /**
     * Gets the item icon from the ItemManager or a default icon if not found
     */
    private BufferedImage getItemIcon(int itemId, String itemName) {
        // Check cache first
        if (itemId != -1) {
            BufferedImage cachedIcon = iconCache.get(itemId);
            if (cachedIcon != null) {
                return cachedIcon;
            }
            
            BufferedImage icon = itemManager.getImage(itemId);
            BufferedImage resizedIcon = ImageUtil.resizeImage(icon, CELL_SIZE, CELL_SIZE);
            iconCache.put(itemId, resizedIcon);
            return resizedIcon;
        } else {
            // For letter-based icons, create a cache key
            String letter = getLetterForItem(itemName);
            String cacheKey = letter + "_" + CELL_SIZE;
            
            BufferedImage cachedIcon = letterIconCache.get(cacheKey);
            if (cachedIcon != null) {
                return cachedIcon;
            }
            
            BufferedImage icon = createLetterIcon(letter);
            letterIconCache.put(cacheKey, icon);
            return icon;
        }
    }
    
    private String getLetterForItem(String itemName) {
        if (itemName.contains("community")) {
            return "CP";
        } else if (itemName.contains("account")) {
            return "AP";
        } else if (itemName.contains("EHB")) {
            return "EHB";
        } else if (itemName.contains("EHP")) {
            return "EHP";
        }
        return itemName.substring(0, 1).toUpperCase();
    }
    
    private BufferedImage createLetterIcon(String letter) {
        BufferedImage icon = new BufferedImage(CELL_SIZE, CELL_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = icon.createGraphics();

        // Fill the entire image with the background color
        g2d.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
        g2d.fillRect(0, 0, CELL_SIZE, CELL_SIZE);
        g2d.setColor(Color.WHITE);
        
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
        
        int x = (CELL_SIZE - fm.stringWidth(letter)) / 2;
        int y = ((CELL_SIZE - fm.getHeight()) / 2) + fm.getAscent();
        g2d.drawString(letter, x, y);
        g2d.dispose();
        
        return icon;
    }

    /**
     * Builds the tooltip text for an item
     */
    private String buildTooltipText(MissingItem item) {
        StringBuilder sb = new StringBuilder("<html><body style='padding: ");
        sb.append(TOOLTIP_PADDING).append("px;");
        
        if (item.getItemId() == -1) {
            sb.append(" text-align: center;");
        }
        
        sb.append("'><div style='font-weight: bold;");
        
        if (item.getItemId() != -1) {
            sb.append(" margin-bottom: 3px;");
        }
        
        sb.append("'>").append(item.getItemName()).append("</div>");
        
        if (item.getItemId() != -1) {
            sb.append("<div style='color: #99FFFF; font-style: italic;'>Click to open wiki</div>");
        }
        
        sb.append("</body></html>");
        return sb.toString();
    }

    /**
     * Attempts to find an item ID by name using the ItemManager
     */
    public int findItemIdByName(String itemName) {
        String searchName = itemName.replace("\"", "");
        List<ItemPrice> itemPrices = itemManager.search(searchName);
        if (itemPrices.isEmpty()) {
            return -1;
        }

        return itemPrices.get(0).getId();
    }

    public String getItemNameFromId(int itemId) {
        ItemComposition ic = itemManager.getItemComposition(itemId);
        return ic.getName();
    }

    public boolean skipProcessingByName(String itemName, JsonArray untradableItemIds) {
        String searchName = itemName.replace("\"", "");
        List<ItemPrice> itemPrices = itemManager.search(searchName);
        if (itemPrices.isEmpty()) {
            return true;
        }

        Stream<JsonElement> stream = StreamSupport.stream(untradableItemIds.spliterator(), true);
        return stream.anyMatch(e -> e.getAsInt() == itemPrices.get(0).getId());
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
