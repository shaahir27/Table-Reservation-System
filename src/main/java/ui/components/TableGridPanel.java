package ui.components;

import model.RestaurantTable;
import model.TableAvailability;
import model.TableState;
import ui.Theme;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Responsive grid panel wrapping TableCards with WrapLayout and handling single-selection state.
 */
public class TableGridPanel extends JPanel {
    private final JPanel cardsContainer;
    private final JLabel emptyStateLabel;
    private final List<TableCard> cardList = new ArrayList<>();
    private TableCard selectedCard = null;
    private Consumer<RestaurantTable> onTableSelected;

    public TableGridPanel() {
        setLayout(new BorderLayout());
        setOpaque(false);

        cardsContainer = new JPanel(new WrapLayout(FlowLayout.LEFT, Theme.SPACE_16, Theme.SPACE_16));
        cardsContainer.setOpaque(false);

        emptyStateLabel = new JLabel("No tables match your selection criteria.", JLabel.CENTER);
        emptyStateLabel.setFont(Theme.FONT_BODY);
        emptyStateLabel.setForeground(Theme.TEXT_MUTED);
        emptyStateLabel.setBorder(BorderFactory.createEmptyBorder(40, 20, 40, 20));
        emptyStateLabel.setVisible(false);

        add(emptyStateLabel, BorderLayout.NORTH);
        add(cardsContainer, BorderLayout.CENTER);
    }

    private Consumer<TableAvailability> onAnyCardClicked;

    public void setOnTableSelected(Consumer<RestaurantTable> onTableSelected) {
        this.onTableSelected = onTableSelected;
    }

    public void setOnAnyCardClicked(Consumer<TableAvailability> onAnyCardClicked) {
        this.onAnyCardClicked = onAnyCardClicked;
    }

    public void setData(List<TableAvailability> tables) {
        setData(tables, false);
    }

    public void setData(List<TableAvailability> tables, boolean allowAllClicks) {
        cardsContainer.removeAll();
        cardList.clear();
        selectedCard = null;

        if (tables == null || tables.isEmpty()) {
            emptyStateLabel.setVisible(true);
            cardsContainer.setVisible(false);
            revalidate();
            repaint();
            if (onTableSelected != null) {
                onTableSelected.accept(null);
            }
            if (onAnyCardClicked != null) {
                onAnyCardClicked.accept(null);
            }
            return;
        }

        emptyStateLabel.setVisible(false);
        cardsContainer.setVisible(true);

        TableCard preselect = null;

        for (TableAvailability ta : tables) {
            TableCard card = new TableCard(ta, allowAllClicks);
            if (allowAllClicks) {
                card.setOnClick(clicked -> {
                    for (TableCard c : cardList) {
                        c.setSelected(c == clicked);
                    }
                    selectedCard = clicked;
                    if (onAnyCardClicked != null) {
                        onAnyCardClicked.accept(clicked.getAvailability());
                    }
                });
            } else {
                card.setOnClick(this::handleCardClick);
            }
            cardsContainer.add(card);
            cardList.add(card);

            if (ta.isRecommended() && ta.getState() == TableState.AVAILABLE) {
                preselect = card;
            }
        }

        if (!allowAllClicks) {
            // Auto-select the recommended best-fit card if available
            if (preselect != null) {
                handleCardClick(preselect);
            } else {
                // Otherwise select the first available card if any
                for (TableCard card : cardList) {
                    if (card.getAvailability().getState() == TableState.AVAILABLE) {
                        handleCardClick(card);
                        break;
                    }
                }
            }
        }

        revalidate();
        repaint();
    }

    private void handleCardClick(TableCard clicked) {
        if (clicked.getAvailability().getState() != TableState.AVAILABLE) {
            return; // Not selectable
        }

        for (TableCard c : cardList) {
            c.setSelected(c == clicked);
        }
        selectedCard = clicked;

        if (onTableSelected != null) {
            onTableSelected.accept(clicked.getTable());
        }
    }

    public RestaurantTable getSelectedTable() {
        return selectedCard != null ? selectedCard.getTable() : null;
    }

    public void clearSelection() {
        for (TableCard c : cardList) {
            c.setSelected(false);
        }
        selectedCard = null;
        if (onTableSelected != null) {
            onTableSelected.accept(null);
        }
    }

    public void markTableBooked(String tableNumber) {
        for (TableCard c : cardList) {
            if (c.getTable() != null && c.getTable().getTableNumber().equalsIgnoreCase(tableNumber)) {
                c.getAvailability().setState(TableState.RESERVED);
                c.setSelected(false);
                c.repaint();
                if (selectedCard == c) {
                    selectedCard = null;
                    if (onTableSelected != null) {
                        onTableSelected.accept(null);
                    }
                }
            }
        }
    }
}
