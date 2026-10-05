package com.registry.storesapp;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

public class SceneManager {

    private static final String BACKGROUND_PATH = "/com/registry/storesapp/background.jpg";
    private static Image sharedImage = null;

    /**
     * Retrieves the window icon or background image efficiently from the resources folder.
     */
    public static Image getAppIcon() {
        if (sharedImage == null) {
            sharedImage = new Image(Objects.requireNonNull(SceneManager.class.getResourceAsStream(BACKGROUND_PATH)));
        }
        return sharedImage;
    }

    /**
     * Initializes a window stage with a consistent title, global taskbar icon,
     * and a programmatically injected, responsive background watermark layer.
     */
    public static void prepareWindow(Stage stage, String fxmlName, String title, double initialWidth, double initialHeight, boolean isResizable) throws IOException {
        FXMLLoader loader = new FXMLLoader(SceneManager.class.getResource(fxmlName));
        Parent rootNode = loader.load();

        StackPane globalDecoratorContainer = new StackPane();
        globalDecoratorContainer.setStyle("-fx-background-color: #f8fafc;");

        // Configures a responsive background watermark layer
        ImageView watermarkView = new ImageView(getAppIcon());
        watermarkView.setOpacity(0.20); // Balanced clarity level
        watermarkView.setPreserveRatio(true);
        watermarkView.setPickOnBounds(false);

        // Dynamic multi-screen scale bindings
        watermarkView.fitWidthProperty().bind(globalDecoratorContainer.widthProperty().multiply(0.80));
        watermarkView.fitHeightProperty().bind(globalDecoratorContainer.heightProperty().multiply(0.80));

        // Assemble layout tree layers
        globalDecoratorContainer.getChildren().addAll(watermarkView, rootNode);

        Scene scene = (initialWidth > 0 && initialHeight > 0)
                ? new Scene(globalDecoratorContainer, initialWidth, initialHeight)
                : new Scene(globalDecoratorContainer);

        // Apply standard system window parameters
        stage.getIcons().clear();
        stage.getIcons().add(getAppIcon()); // Sets taskbar and window decoration corner icons
        stage.setTitle(title);
        stage.setScene(scene);
        stage.setResizable(isResizable);
    }
}