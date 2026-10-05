package com.registry.storesapp;

import javafx.application.Application;
import javafx.stage.Stage;
import java.io.IOException;

public class LoginApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        // --- FIXED: Redirected layout generation through the central SceneManager pipeline ---
        // This automatically injects the perfectly round watermark background and sets the application taskbar icon.
        SceneManager.prepareWindow(
                stage,
                "login-view.fxml",
                "SHMS - Secure Access Gateway",
                400,
                320,
                false // Kept login stage unresizable so the login card fields don't warp layout sizes
        );

        stage.centerOnScreen(); // Anchors the login layout frame dead-center on your desktop monitor
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}