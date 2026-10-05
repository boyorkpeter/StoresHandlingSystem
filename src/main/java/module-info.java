module com.registry.storesapp {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires java.desktop;
    requires com.github.librepdf.openpdf;

    opens com.registry.storesapp to javafx.fxml;
    exports com.registry.storesapp;
}