package com.registry.storesapp;

import javafx.application.Application;
import javafx.stage.Stage;
import java.io.IOException;

public class LoginApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        // --- FIXED: Redirected layout generation through the central SceneManager pipeline ---
        // Expanded window dimensions and set resizable to true to enable titlebar controls and prevent card clipping.
        SceneManager.prepareWindow(
                stage,
                "login-view.fxml",
                "SHMS - Secure Access Gateway",
                500,
                600,
                true // Enabled stage resizability to activate the maximize button and fit the neumorphic card layout
        );

        stage.centerOnScreen(); // Anchors the login layout frame dead-center on your desktop monitor
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}