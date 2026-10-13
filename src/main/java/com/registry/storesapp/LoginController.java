package com.registry.storesapp;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
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
    @FXML private TextField visiblePasswordField; // Injected for password visibility toggle
    @FXML private Button togglePasswordButton;    // Injected eye toggle button
    @FXML private Label errorLabel;
    @FXML private Button loginButton;
    @FXML private Hyperlink registerLink;

    private boolean isPasswordVisible = false;

    @FXML
    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText().trim();
        // Extract password dynamically from whichever field is currently active/visible
        String password = isPasswordVisible ? visiblePasswordField.getText() : passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Authentication rejected: Empty fields.");
            return;
        }

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

    @FXML
    private void handleTogglePassword(ActionEvent event) {
        isPasswordVisible = !isPasswordVisible;

        if (isPasswordVisible) {
            visiblePasswordField.setText(passwordField.getText());
            visiblePasswordField.setVisible(true);
            visiblePasswordField.setManaged(true);

            passwordField.setVisible(false);
            passwordField.setManaged(false);

            togglePasswordButton.setText("👁‍🗨"); // Change icon to indicate visible state
        } else {
            passwordField.setText(visiblePasswordField.getText());
            passwordField.setVisible(true);
            passwordField.setManaged(true);

            visiblePasswordField.setVisible(false);
            visiblePasswordField.setManaged(false);

            togglePasswordButton.setText("👁"); // Change back to hidden eye icon
        }
    }

    @FXML
    private void handleOpenRegister(ActionEvent event) {
        try {
            Stage registerStage = new Stage();

            SceneManager.prepareWindow(
                    registerStage,
                    "register-view.fxml",
                    "SHMS - Secure User Registration",
                    450,
                    550,
                    true
            );

            registerStage.centerOnScreen();

            Stage loginStage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            loginStage.close();

            registerStage.show();

        } catch (Exception e) {
            e.printStackTrace();
            showError("Failed to initialize registration view.");
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }

    private void navigateToDashboard(ActionEvent event) {
        try {
            Stage dashboardStage = new Stage();

            SceneManager.prepareWindow(
                    dashboardStage,
                    "hello-view.fxml",
                    "Registry Stores Management System",
                    -1,
                    -1,
                    true
            );

            dashboardStage.setMinWidth(1100);
            dashboardStage.setMinHeight(750);
            dashboardStage.setMaximized(true);
            dashboardStage.centerOnScreen();

            Stage loginStage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            loginStage.close();

            dashboardStage.show();

        } catch (Exception e) {
            e.printStackTrace();
            showError("Failed to initialize system core views.");
        }
    }
}