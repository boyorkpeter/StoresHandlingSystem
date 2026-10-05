package com.registry.storesapp;

import javafx.application.Application;
import javafx.stage.Stage;

public class HelloApplication extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        // FIX: Use SceneManager.prepareWindow() — the single authoritative
        // load path for hello-view.fxml. Previously this method had its own
        // FXMLLoader call, creating a second HelloController instance alongside
        // the one created by LoginController's SceneManager call. Having two
        // controllers alive at the same time caused every button event to fire
        // twice (duplicate console errors, double DB calls).
        SceneManager.prepareWindow(
                stage,
                "hello-view.fxml",
                "Registry Stores Management System",
                1100,
                750,
                true
        );

        stage.setMinWidth(1100);
        stage.setMinHeight(750);
        stage.setMaximized(true);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}