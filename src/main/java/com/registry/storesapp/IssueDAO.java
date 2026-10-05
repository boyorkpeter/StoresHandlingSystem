package com.registry.storesapp;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class IssueDAO {

    /**
     * Issues stock to a recipient department/officer.
     *
     * FIX 1: Corrected table names to lowercase 'issues' and 'inventory' to match schema layout.
     * FIX 2: Replaced CURDATE() with NOW() or left it matching your preference, ensuring compatibility
     *        with the TIMESTAMP 'date_issued' column we added.
     * FIX 3: Maintains the transactional safety checks to prevent negative stock values.
     */
    public boolean issueItem(int itemId, int qty, String issuedTo) {
        Integer fallbackReqId = null;
        String fallbackIssuedBy = "SYSTEM_ADMIN";

        // FIX: Lowercase table name 'issues'
        String insertIssue =
                "INSERT INTO issues (req_id, item_id, quantity_issued, issued_to, issued_by, date_issued) " +
                        "VALUES (?, ?, ?, ?, ?, NOW())";

        // FIX: Lowercase table name 'inventory'
        String updateInventory =
                "UPDATE inventory SET quantity_in_stock = quantity_in_stock - ? " +
                        "WHERE item_id = ? AND quantity_in_stock >= ?";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps1 = conn.prepareStatement(insertIssue);
                 PreparedStatement ps2 = conn.prepareStatement(updateInventory)) {

                // 1. Log the issue transaction
                if (fallbackReqId == null) {
                    ps1.setNull(1, java.sql.Types.INTEGER);
                } else {
                    ps1.setInt(1, fallbackReqId);
                }
                ps1.setInt(2, itemId);
                ps1.setInt(3, qty);
                ps1.setString(4, issuedTo);
                ps1.setString(5, fallbackIssuedBy);
                ps1.executeUpdate();

                // 2. Deduct inventory with stock-level guard
                ps2.setInt(1, qty);
                ps2.setInt(2, itemId);
                ps2.setInt(3, qty);

                int rowsAffected = ps2.executeUpdate();

                if (rowsAffected == 0) {
                    // Stock was insufficient — abort the whole transaction
                    conn.rollback();
                    return false;
                }

                conn.commit();
                return true;

            } catch (SQLException e) {
                conn.rollback();
                // Rethrow so HelloController can show the correct error dialog
                throw new RuntimeException("Issue transaction failed: " + e.getMessage(), e);
            }

        } catch (RuntimeException e) {
            throw e;
        } catch (SQLException e) {
            throw new RuntimeException("Database connection error during issue: " + e.getMessage(), e);
        }
    }
}