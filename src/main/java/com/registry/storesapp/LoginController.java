package com.registry.storesapp;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    @FXML private Button loginButton;

    @FXML
    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Authentication rejected: Empty fields.");
            return;
        }

        // NOTE: CryptoUtil in this project is for AES-decrypting DB credentials
        // only — it is not a password hashing utility. Passwords are currently
        // compared as-is against the users table. For production, consider
        // adding a SHA-256 or BCrypt hashing step here and storing hashed
        // passwords in the database.
        String sql = "SELECT role FROM users WHERE username = ? AND password = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            stmt.setString(2, password);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    navigateToDashboard(event);
                } else {
                    showError("Access Denied: Invalid secure sign-on signature.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showError("Database connection drop error encountered.");
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }

    private void navigateToDashboard(ActionEvent event) {
        try {
            // 1. Build a fresh standalone Stage for the dashboard
            Stage dashboardStage = new Stage();

            // 2. Load the main view through SceneManager — single load path,
            //    one HelloController instance, watermark and icon applied centrally.
            //    Passing -1 lets SceneManager use the FXML's own preferred size.
            SceneManager.prepareWindow(
                    dashboardStage,
                    "hello-view.fxml",
                    "Registry Stores Management System",
                    -1,
                    -1,
                    true
            );

            // FIX 2: Apply the same window constraints that HelloApplication sets,
            // since HelloApplication.start() is NOT called in the login flow.
            // Without these, the dashboard opened after login has no min-size
            // guard and won't launch maximized.
            dashboardStage.setMinWidth(1100);
            dashboardStage.setMinHeight(750);
            dashboardStage.setMaximized(true);
            dashboardStage.centerOnScreen();

            // 3. Close the login window
            Stage loginStage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            loginStage.close();

            // 4. Show the dashboard
            // FIX 3: show() must be called AFTER setMaximized(true) so the OS
            // window manager applies maximization on the first render pass.
            // Previously show() was called before maximization was set, which
            // caused the window to flash at its minimum size before expanding.
            dashboardStage.show();

        } catch (Exception e) {
            e.printStackTrace();
            showError("Failed to initialize system core views.");
        }
    }
}