package com.registry.storesapp;

public class Item {
    private int itemId;
    private String itemName;
    private String category;
    private int quantityInStock; // Linked from the database inventory warehouse records
    private int reorderLevel;    // Custom dynamic warning/alert limit threshold

    public Item(int itemId, String itemName, String category, int quantityInStock, int reorderLevel) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.category = category;
        this.quantityInStock = quantityInStock;
        this.reorderLevel = reorderLevel;
    }

    // Standard structural encapsulation properties required for JavaFX TableView reflections

    public int getItemId() {
        return itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public String getCategory() {
        return category;
    }

    public int getQuantityInStock() {
        return quantityInStock;
    }

    public int getReorderLevel() {
        return reorderLevel;
    }

    // Setters provided for future runtime modification support safely

    public void setItemId(int itemId) {
        this.itemId = itemId;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setQuantityInStock(int quantityInStock) {
        this.quantityInStock = quantityInStock;
    }

    public void setReorderLevel(int reorderLevel) {
        this.reorderLevel = reorderLevel;
    }
}