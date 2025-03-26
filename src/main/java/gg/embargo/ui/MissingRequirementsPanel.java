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
    private static final int ITEMS_PER_ROW = 3;
    private static final int ICON_SIZE = 32;
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
        itemsContainer.setLayout(new GridBagLayout());
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
     * @param description Additional information about the item requirement
     */
      public void addMissingItem(String itemName, int itemId, String description) {
          boolean itemExists = missingItems.stream()
                  .anyMatch(item -> item.getItemName().contains(itemName.replace("\"", "")));
        
          // Only add if it doesn't exist
          if (!itemExists) {
              if (itemName.contains("Combat Achievement")) {
                  // Add combat achievement item
                  missingItems.add(new MissingItem(itemName.replace("\"", ""), 27550,  getHiltImageFromName(itemName.split(" ")[0])));
                  updatePanel();
              } else {
                  BufferedImage itemIcon = getItemIcon(itemId, itemName);
                  missingItems.add(new MissingItem(itemName.replace("\"", ""), itemId, itemIcon));
                  updatePanel();
              }
          } else {
              return;
          }
      }


      public BufferedImage getHiltImageFromName(String name) {
          if (Objects.equals(name, "Master")) {
              int ItemId = 27550;
              BufferedImage bi = getItemIcon(ItemId, "Ghommal's_avernic_defender_5");
              return bi;
          } else {
              return getItemIcon(882, "Bronze arrow");
          }
      }

      /**
     * Adds a missing item to the panel if it doesn't already exist
     * 
     * @param itemName The name of the item
     * @param description Additional information about the item requirement
     */
      public void addMissingItem(String itemName, String description) {
          // Try to find the item ID from the item manager
          int itemId = findItemIdByName(itemName);
          addMissingItem(itemName, itemId, description);
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
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.weighty = 0;
        gbc.insets = new Insets(4, 4, 4, 4);
        
        int row = 0;
        int col = 0;
        
        for (MissingItem item : missingItems) {
            JPanel itemPanel = createItemPanel(item);
            
            gbc.gridx = col;
            gbc.gridy = row;
            
            itemsContainer.add(itemPanel, gbc);
            
            col++;
            if (col >= ITEMS_PER_ROW) {
                col = 0;
                row++;
            }
        }
        
        // Add an empty filler panel to push everything up
        gbc.gridx = 0;
        gbc.gridy = row + 1;
        gbc.gridwidth = ITEMS_PER_ROW;
        gbc.weighty = 1.0;
        itemsContainer.add(Box.createGlue(), gbc);
        
        revalidate();
        repaint();
    }
        /**
       * Creates a panel for a single item with icon and hover/click functionality
       */
        private JPanel createItemPanel(MissingItem item) {
            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());
            panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            panel.setBorder(BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR, 1));
            panel.setPreferredSize(new Dimension(ICON_SIZE + 8, ICON_SIZE + 8));
        
            // Get the item icon
            BufferedImage iconImage = getItemIcon(item.getItemId(), item.getItemName());
            JLabel iconLabel = new JLabel(new ImageIcon(iconImage));
            iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
            panel.add(iconLabel, BorderLayout.CENTER);
        
            // Set the tooltip on the panel instead of just the icon
            panel.setToolTipText(buildTooltipText(item));
        
            // Add hover effect and click handler to the panel
            panel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    panel.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
                    panel.setCursor(new Cursor(Cursor.HAND_CURSOR));
                }
            
                @Override
                public void mouseExited(MouseEvent e) {
                    panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                    panel.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                }
            
                @Override
                public void mouseClicked(MouseEvent e) {
                    log.info("Clicked on item: {}", item.getItemName());
                    String wikiUrl = OSRS_WIKI_BASE_URL + item.getItemName().replace(" ", "_").replace("\"", "");
                    log.info("Opening wiki URL: {}", wikiUrl);
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
        } else {
            // Create a default icon with the first letter of the item name
            icon = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = icon.createGraphics();
            g2d.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
            g2d.fillRect(0, 0, ICON_SIZE, ICON_SIZE);
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 20));
            String letter = itemName.substring(0, 1).toUpperCase();
            FontMetrics fm = g2d.getFontMetrics();
            int x = (ICON_SIZE - fm.stringWidth(letter)) / 2;
            int y = ((ICON_SIZE - fm.getHeight()) / 2) + fm.getAscent();
            g2d.drawString(letter, x, y);
            g2d.dispose();
        }
        
        return ImageUtil.resizeImage(icon, ICON_SIZE, ICON_SIZE);
    }

    /**
     * Builds the tooltip text for an item
     */
    private String buildTooltipText(MissingItem item) {
        return "<html><body style='padding: " + TOOLTIP_PADDING + "px;'>" +
                "<div style='font-weight: bold; margin-bottom: 5px;'>" + item.getItemName() + "</div>" +
                "<div style='color: #99FFFF; margin-top: 5px; font-style: italic;'>Click to open wiki</div>" +
                "</body></html>";
    }

    /**
     * Attempts to find an item ID by name using the ItemManager
     */
        private int findItemIdByName(String itemName) {
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
    private static class MissingItem {
        private final String itemName;
        private final int itemId;

        @Getter
        @Setter
        private BufferedImage icon;

        public MissingItem(String itemName, int itemId, BufferedImage icon) {
            this.itemName = itemName;
            this.itemId = itemId;
            this.icon = icon;
        }

        public String getItemName() {
            return itemName;
        }

        public int getItemId() {
            return itemId;
        }
    }
}
