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
    @FXML private Label errorLabel;
    @FXML private Button loginButton;
    @FXML private Hyperlink registerLink; // Injected interactive registration link

    @FXML
    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

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
    private void handleOpenRegister(ActionEvent event) {
        try {
            // 1. Build a fresh standalone Stage for the registration portal
            Stage registerStage = new Stage();

            // 2. Prepare the registration view through SceneManager
            // (Make sure your registration fxml file name matches what you use, e.g., "register-view.fxml")
            SceneManager.prepareWindow(
                    registerStage,
                    "register-view.fxml",
                    "SHMS - Secure User Registration",
                    450,
                    550,
                    true
            );

            registerStage.centerOnScreen();

            // 3. Close the current login window
            Stage loginStage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            loginStage.close();

            // 4. Show the registration window
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