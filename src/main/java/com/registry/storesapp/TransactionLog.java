package com.registry.storesapp;

public class TransactionLog {
    private String type;
    private String itemName;
    private int quantity;
    private String supplier;  // Extracted target column reference for STOCK IN logs
    private String recipient; // Extracted target column reference for STOCK OUT logs
    private String date;
    private String docRef;    // Tracking identity property mapped into system PDF structures

    // Consolidated Core Constructor
    public TransactionLog(String type, String itemName, int quantity, String supplier, String recipient, String date) {
        this.type = type;
        this.itemName = itemName;
        this.quantity = quantity;
        this.supplier = supplier;
        this.recipient = recipient;
        this.date = date;

        // Dynamically assigns a clean, pseudo-unique sequential document identity tracking reference number
        this.docRef = "STORES-" + (System.currentTimeMillis() % 100000);
    }

    // --- STANDARD ENCAPSULATION PROPERTY INTERFACES ---

    public String getType() {
        return type;
    }

    public String getItemName() {
        return itemName;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getSupplier() {
        return supplier;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getDate() {
        return date;
    }

    // =========================================================================
    // HELPER ALIAS GETTERS (Clears compilation dependencies in PDFGenerator)
    // =========================================================================

    public String getDateAndTime() {
        return date;
    }

    public String getStationeryName() {
        return itemName;
    }

    public int getQty() {
        return quantity;
    }

    public String getDocRef() {
        return docRef;
    }
}