package com.registry.storesapp;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ReportDAO {

    /**
     * Returns items that are at or below their custom reorder warning alert thresholds.
     * Synchronized with lowercase table name references and 5-argument data models.
     */
    public List<Item> getLowStockData() {
        List<Item> reportData = new ArrayList<>();

        String query = "SELECT i.item_id, i.item_name, i.category, i.reorder_level, inv.quantity_in_stock " +
                "FROM items i " +
                "JOIN inventory inv ON i.item_id = inv.item_id " +
                "WHERE inv.quantity_in_stock <= i.reorder_level " +
                "AND i.status = 'ACTIVE'";

        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                reportData.add(new Item(
                        rs.getInt("item_id"),
                        rs.getString("item_name"),
                        rs.getString("category"),
                        rs.getInt("quantity_in_stock"),
                        rs.getInt("reorder_level")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error encountered compiling low stock ledger metrics: " + e.getMessage());
            e.printStackTrace();
        }
        return reportData;
    }
}