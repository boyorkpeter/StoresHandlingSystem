package com.registry.storesapp;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ItemDAO {

    /**
     * 1. GET ALL ACTIVE ITEMS
     * Retrieves all items flagged as ACTIVE along with their current consolidated stock levels.
     */
    public List<Item> getAllItems() {
        List<Item> items = new ArrayList<>();
        String query = "SELECT i.item_id, i.item_name, i.category, i.reorder_level, " +
                "SUM(COALESCE(inv.quantity_in_stock, 0)) as stock " +
                "FROM items i " +
                "LEFT JOIN inventory inv ON i.item_id = inv.item_id " +
                "WHERE i.status = 'ACTIVE' " +
                "GROUP BY i.item_id, i.item_name, i.category, i.reorder_level";

        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                items.add(new Item(
                        rs.getInt("item_id"),
                        rs.getString("item_name"),
                        rs.getString("category"),
                        rs.getInt("stock"),
                        rs.getInt("reorder_level")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }

    /**
     * 2. ADD NEW ITEM
     * Creates an active ledger entry for an item and inserts its initial baseline stock parameters.
     */
    public void addItem(String name, String category, int reorderLevel, int initialStock) {
        String insertItem = "INSERT INTO items (item_name, category, reorder_level, status) VALUES (?, ?, ?, 'ACTIVE')";
        String insertInventory = "INSERT INTO inventory (item_id, quantity_in_stock) VALUES (LAST_INSERT_ID(), ?) " +
                "ON DUPLICATE KEY UPDATE quantity_in_stock = quantity_in_stock + VALUES(quantity_in_stock)";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps1 = conn.prepareStatement(insertItem);
                 PreparedStatement ps2 = conn.prepareStatement(insertInventory)) {

                ps1.setString(1, name);
                ps1.setString(2, category);
                ps1.setInt(3, reorderLevel);
                ps1.executeUpdate();

                ps2.setInt(1, initialStock);
                ps2.executeUpdate();

                conn.commit();

            } catch (SQLException e) {
                conn.rollback();
                throw new RuntimeException("Item registration failed: " + e.getMessage(), e);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (SQLException e) {
            throw new RuntimeException("Database connection error during item registration: " + e.getMessage(), e);
        }
    }

    /**
     * 3. RECEIVE GOODS (GRN)
     * Logs the incoming batch delivery and updates the warehouse stock balance.
     */
    public void receiveGoods(int itemId, int qtyReceived, String supplier) {
        String logGRN = "INSERT INTO goods_received (item_id, quantity_received, supplier_name, date_received) VALUES (?, ?, ?, NOW())";
        String updateInv = "UPDATE inventory SET quantity_in_stock = quantity_in_stock + ? WHERE item_id = ?";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps1 = conn.prepareStatement(logGRN);
                 PreparedStatement ps2 = conn.prepareStatement(updateInv)) {

                ps1.setInt(1, itemId);
                ps1.setInt(2, qtyReceived);
                ps1.setString(3, supplier != null ? supplier : "-");
                ps1.executeUpdate();

                ps2.setInt(1, qtyReceived);
                ps2.setInt(2, itemId);
                ps2.executeUpdate();

                conn.commit();

            } catch (SQLException e) {
                conn.rollback();
                throw new RuntimeException("Stock-in (GRN) transaction failed: " + e.getMessage(), e);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (SQLException e) {
            throw new RuntimeException("Database connection error during GRN: " + e.getMessage(), e);
        }
    }

    /**
     * 4. ISSUE ITEMS (SIV)
     * Authenticates allocations against current stock metrics to prevent falling into negative stock volumes.
     */
    public void issueInventoryItem(int itemId, int qtyIssued, String issuedTo) {
        String logIssue = "INSERT INTO issues (item_id, quantity_issued, issued_to, date_issued) VALUES (?, ?, ?, NOW())";
        String updateInv = "UPDATE inventory SET quantity_in_stock = quantity_in_stock - ? WHERE item_id = ? AND quantity_in_stock >= ?";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps1 = conn.prepareStatement(logIssue);
                 PreparedStatement ps2 = conn.prepareStatement(updateInv)) {

                ps1.setInt(1, itemId);
                ps1.setInt(2, qtyIssued);
                ps1.setString(3, issuedTo != null ? issuedTo : "-");
                ps1.executeUpdate();

                ps2.setInt(1, qtyIssued);
                ps2.setInt(2, itemId);
                ps2.setInt(3, qtyIssued); // Strict guard check against falling below 0 units

                int rowsAffected = ps2.executeUpdate();
                if (rowsAffected == 0) {
                    conn.rollback();
                    throw new RuntimeException("Insufficient warehouse stock remaining to fulfill this allocation volume.");
                }

                conn.commit();

            } catch (SQLException e) {
                conn.rollback();
                throw new RuntimeException("Issue transaction failed: " + e.getMessage(), e);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (SQLException e) {
            throw new RuntimeException("Database connection error during issue: " + e.getMessage(), e);
        }
    }

    /**
     * 5. FORCE FULL DELETE
     * Drops inventory dependencies before deleting the core item reference record.
     */
    public void deleteItem(int itemId) {
        String disableFK = "SET FOREIGN_KEY_CHECKS = 0";
        String deleteInventory = "DELETE FROM inventory WHERE item_id = ?";
        String deleteItem = "DELETE FROM items WHERE item_id = ?";
        String enableFK = "SET FOREIGN_KEY_CHECKS = 1";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt0 = conn.createStatement();
                 PreparedStatement ps1 = conn.prepareStatement(deleteInventory);
                 PreparedStatement ps2 = conn.prepareStatement(deleteItem);
                 Statement stmt3 = conn.createStatement()) {

                stmt0.execute(disableFK);

                ps1.setInt(1, itemId);
                ps1.executeUpdate();

                ps2.setInt(1, itemId);
                ps2.executeUpdate();

                stmt3.execute(enableFK);

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new RuntimeException("Hard delete execution aborted on database layer: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database connection loss during force delete routine: " + e.getMessage(), e);
        }
    }

    /**
     * 6. MONITOR FLOW (Transaction History Logs)
     * Compiles an organized transaction history by tracking Goods Received Notes (GRN),
     * Store Issue Vouchers (SIV), and legacy standalone ledger logs.
     */
    public List<TransactionLog> getTransactionHistory() {
        List<TransactionLog> logs = new ArrayList<>();

        String query =
                "SELECT 'STOCK IN' as type, i.item_name, gr.quantity_received as qty, gr.supplier_name as supplier, '-' as recipient, gr.date_received as t_date " +
                        "FROM goods_received gr " +
                        "JOIN items i ON gr.item_id = i.item_id " +
                        "UNION ALL " +
                        "SELECT 'STOCK OUT' as type, i.item_name, iss.quantity_issued as qty, '-' as supplier, iss.issued_to as recipient, iss.date_issued as t_date " +
                        "FROM issues iss " +
                        "JOIN items i ON iss.item_id = i.item_id " +
                        "UNION ALL " +
                        "SELECT tl.type, i.item_name, tl.quantity as qty, tl.supplier, tl.recipient, tl.date_issued as t_date " +
                        "FROM transaction_log tl " +
                        "JOIN items i ON tl.item_id = i.item_id " +
                        "ORDER BY t_date DESC";

        DateTimeFormatter cleanFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                String timestampStr = "-";
                Timestamp ts = rs.getTimestamp("t_date");

                if (ts != null) {
                    LocalDateTime ldt = ts.toLocalDateTime();
                    timestampStr = ldt.format(cleanFormatter);
                }

                logs.add(new TransactionLog(
                        rs.getString("type"),
                        rs.getString("item_name"),
                        rs.getInt("qty"),
                        rs.getString("supplier"),
                        rs.getString("recipient"),
                        timestampStr
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching transaction history: " + e.getMessage());
            e.printStackTrace();
        }
        return logs;
    }

    /**
     * 7. PURGE LOG AUDIT RECORDS
     * Erases all operational transaction ledger entries from history.
     */
    public void clearTransactionHistory() {
        String deleteReceived = "DELETE FROM goods_received";
        String deleteIssues = "DELETE FROM issues";
        String deleteLogs = "DELETE FROM transaction_log";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(deleteReceived);
                stmt.executeUpdate(deleteIssues);
                stmt.executeUpdate(deleteLogs);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}