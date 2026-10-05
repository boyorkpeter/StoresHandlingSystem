package com.registry.storesapp;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import java.io.File;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class HelloController {

    @FXML private ImageView backgroundImage;
    @FXML private TableView<Item> itemTable;
    @FXML private TableColumn<Item, String> nameColumn;
    @FXML private TableColumn<Item, String> categoryColumn;
    @FXML private TableColumn<Item, Integer> stockColumn;

    @FXML private TableView<TransactionLog> logTable;
    @FXML private TableColumn<TransactionLog, String> typeCol;
    @FXML private TableColumn<TransactionLog, String> logIdCol;
    @FXML private TableColumn<TransactionLog, Integer> qtyCol;
    @FXML private TableColumn<TransactionLog, String> supplierCol;
    @FXML private TableColumn<TransactionLog, String> recipientCol;
    @FXML private TableColumn<TransactionLog, String> dateCol;

    @FXML private TextField nameInput;
    @FXML private ComboBox<String> categoryInput;
    @FXML private TextField stockInput;
    @FXML private ComboBox<String> unitInput;
    @FXML private TextField searchField;

    @FXML private TextField historySearchField;
    @FXML private ComboBox<String> flowTypeFilterComboBox;

    @FXML private Label totalItemsLabel;
    @FXML private Label lowStockLabel;
    @FXML private Label outOfStockLabel;

    // --- INLINE SIDE-SLIDING WORKSPACE MAPPINGS ---
    @FXML private SplitPane sideSplitPane;
    @FXML private VBox slidingFormPane;
    @FXML private Label formPaneTitle;
    @FXML private TextField formItemNameField;
    @FXML private TextField formQuantityField;
    @FXML private Label formEntityLabel;
    @FXML private ComboBox<String> formEntityComboBox;
    @FXML private Button formActionButton;

    // --- DYNAMIC ITEM REGISTRATION ALERT THRESHOLD INPUT ---
    @FXML private TextField thresholdInput;

    private ItemDAO itemDAO;
    private ReportDAO reportDAO;

    private ObservableList<Item> masterData = FXCollections.observableArrayList();
    private FilteredList<Item> filteredData;

    private ObservableList<TransactionLog> auditMasterData = FXCollections.observableArrayList();
    private FilteredList<TransactionLog> filteredAuditData;

    // Tracks current action context for the inline right side-pane panel ("STOCK_IN" or "ISSUE")
    private String currentPaneAction = "";
    private boolean isRegistering = false;

    @FXML
    public void initialize() {
        try {
            itemDAO = new ItemDAO();
            reportDAO = new ReportDAO();

            setupValidation();

            if (backgroundImage != null) {
                backgroundImage.setSmooth(true);
                backgroundImage.setCache(true);
            }

            categoryInput.getItems().addAll("Stationary", "IT");

            if (unitInput != null) {
                unitInput.getItems().addAll("Pieces", "Packs", "Boxes", "Reams", "Cartridges", "Units");
                unitInput.getSelectionModel().selectFirst();
            }

            // Populate Flow Filter Options directly within setup layout lifecycle
            if (flowTypeFilterComboBox != null) {
                flowTypeFilterComboBox.getItems().addAll("All Movements", "STOCK IN", "STOCK OUT");
                flowTypeFilterComboBox.getSelectionModel().selectFirst();
            }

            nameColumn.setCellValueFactory(new PropertyValueFactory<>("itemName"));
            categoryColumn.setCellValueFactory(new PropertyValueFactory<>("category"));
            stockColumn.setCellValueFactory(new PropertyValueFactory<>("quantityInStock"));

            // RowFactory design utilizing dynamic item threshold limits
            itemTable.setRowFactory(tv -> new TableRow<Item>() {
                @Override
                protected void updateItem(Item item, boolean empty) {
                    super.updateItem(item, empty);

                    // Always clear old style states first to prevent background visual bleeding
                    getStyleClass().removeAll("row-out-of-stock", "row-critical-stock", "row-low-stock");

                    if (item == null || empty) {
                        setStyle("");
                    } else if (item.getQuantityInStock() <= 0) {
                        getStyleClass().add("row-out-of-stock");
                    } else {
                        int itemAlertLimit = item.getReorderLevel();

                        // If the stock falls below or matches the item's individual warning level
                        if (item.getQuantityInStock() <= itemAlertLimit) {
                            getStyleClass().add("row-low-stock");
                        } else {
                            setStyle("");
                        }
                    }
                }
            });

            typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
            logIdCol.setCellValueFactory(new PropertyValueFactory<>("itemName"));
            qtyCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));
            supplierCol.setCellValueFactory(new PropertyValueFactory<>("supplier"));
            recipientCol.setCellValueFactory(new PropertyValueFactory<>("recipient"));
            dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));

            filteredData = new FilteredList<>(masterData, p -> true);
            searchField.textProperty().addListener((observable, oldValue, newValue) -> {
                filteredData.setPredicate(item -> {
                    if (newValue == null || newValue.isEmpty()) return true;
                    String lowerCaseFilter = newValue.toLowerCase();
                    return item.getItemName().toLowerCase().contains(lowerCaseFilter) ||
                            item.getCategory().toLowerCase().contains(lowerCaseFilter);
                });
            });
            SortedList<Item> sortedData = new SortedList<>(filteredData);
            sortedData.comparatorProperty().bind(itemTable.comparatorProperty());
            itemTable.setItems(sortedData);

            // Integrated Unified Dynamic Multi-Axis Filter Pipeline
            filteredAuditData = new FilteredList<>(auditMasterData, p -> true);

            Runnable runAuditPredicateFilter = () -> {
                filteredAuditData.setPredicate(log -> {
                    // Part 1: Check Flow Dropdown Value State Axis
                    if (flowTypeFilterComboBox != null) {
                        String selection = flowTypeFilterComboBox.getValue();
                        if (selection != null && !"All Movements".equalsIgnoreCase(selection)) {
                            String normalizedLogType = log.getType() != null ? log.getType().toUpperCase().trim() : "";
                            String normalizedSelection = selection.toUpperCase().trim();
                            if (!normalizedLogType.equals(normalizedSelection)) {
                                return false;
                            }
                        }
                    }

                    // Part 2: Check Input Field Search Terms Axis
                    String txt = (historySearchField != null) ? historySearchField.getText() : "";
                    if (txt == null || txt.trim().isEmpty()) return true;

                    String lowerCaseFilter = txt.toLowerCase().trim();
                    boolean matchType = log.getType() != null && log.getType().toLowerCase().contains(lowerCaseFilter);
                    boolean matchItem = log.getItemName() != null && log.getItemName().toLowerCase().contains(lowerCaseFilter);
                    boolean matchSupplier = log.getSupplier() != null && log.getSupplier().toLowerCase().contains(lowerCaseFilter);
                    boolean matchRecipient = log.getRecipient() != null && log.getRecipient().toLowerCase().contains(lowerCaseFilter);
                    boolean matchDate = log.getDate() != null && log.getDate().toLowerCase().contains(lowerCaseFilter);

                    return matchType || matchItem || matchSupplier || matchRecipient || matchDate;
                });
            };

            if (historySearchField != null) {
                historySearchField.textProperty().addListener((obs, old, nv) -> runAuditPredicateFilter.run());
            }
            if (flowTypeFilterComboBox != null) {
                flowTypeFilterComboBox.valueProperty().addListener((obs, old, nv) -> runAuditPredicateFilter.run());
            }

            SortedList<TransactionLog> sortedAudit = new SortedList<>(filteredAuditData);
            sortedAudit.comparatorProperty().bind(logTable.comparatorProperty());
            logTable.setItems(sortedAudit);

            // Close the inline split side pane structural view layout by default
            handleCloseSlidingPane();

            refreshData();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleFlowTypeFilterChanged() {
        if (logTable != null) logTable.refresh();
    }

    private void clearInputFields() {
        nameInput.clear();
        stockInput.clear();
        categoryInput.getSelectionModel().clearSelection();
        if (unitInput != null) unitInput.getSelectionModel().selectFirst();
        if (thresholdInput != null) thresholdInput.clear();
    }

    private void updateDashboardStats() {
        String totalQuery = "SELECT COUNT(*) FROM inventory i INNER JOIN items it ON i.item_id = it.item_id WHERE it.status = 'ACTIVE'";
        String lowStockQuery = "SELECT COUNT(*) FROM inventory i INNER JOIN items it ON i.item_id = it.item_id WHERE it.status = 'ACTIVE' AND i.quantity_in_stock <= it.reorder_level AND i.quantity_in_stock > 0";
        String outOfStockQuery = "SELECT COUNT(*) FROM inventory i INNER JOIN items it ON i.item_id = it.item_id WHERE it.status = 'ACTIVE' AND i.quantity_in_stock = 0";

        try (Connection conn = DBConnection.getConnection()) {

            // 1. Total Active Registered Items
            try (PreparedStatement stmt = conn.prepareStatement(totalQuery);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) totalItemsLabel.setText(String.valueOf(rs.getInt(1)));
            }

            // 2. Low Stock Alerts Matched Against Dynamic Threshold Level Bound
            try (PreparedStatement stmt = conn.prepareStatement(lowStockQuery);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) lowStockLabel.setText(String.valueOf(rs.getInt(1)));
            }

            // 3. Absolute Empty Out of Stock Count
            try (PreparedStatement stmt = conn.prepareStatement(outOfStockQuery);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) outOfStockLabel.setText(String.valueOf(rs.getInt(1)));
            }

        } catch (SQLException e) {
            System.err.println("Failed to synchronize active dashboard overview summaries: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupValidation() {
        stockInput.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                stockInput.setText(newValue.replaceAll("[^\\d]", ""));
            }
        });

        if (thresholdInput != null) {
            thresholdInput.textProperty().addListener((observable, oldValue, newValue) -> {
                if (!newValue.matches("\\d*")) {
                    thresholdInput.setText(newValue.replaceAll("[^\\d]", ""));
                }
            });
        }

        if (formQuantityField != null) {
            formQuantityField.textProperty().addListener((observable, oldValue, newValue) -> {
                if (!newValue.matches("\\d*")) {
                    formQuantityField.setText(newValue.replaceAll("[^\\d]", ""));
                }
            });
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle("-fx-font-family: 'System'; -fx-font-weight: bold;");

        if (itemTable != null && itemTable.getScene() != null) {
            alert.initOwner(itemTable.getScene().getWindow());
        }

        if (dialogPane.getScene() != null && dialogPane.getScene().getWindow() instanceof javafx.stage.Stage) {
            javafx.stage.Stage alertStage = (javafx.stage.Stage) dialogPane.getScene().getWindow();
            alertStage.getIcons().add(SceneManager.getAppIcon());
        }

        alert.showAndWait();
    }

    // --- SIDE-PANEL WORKFLOW MANAGEMENT CONTROLS ---
    @FXML
    private void handleShowStockInPane() {
        if (masterData == null || masterData.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "Empty Registry Store",
                    "There are no stock items registered in the system yet.\n\n" +
                            "Please fill out the registration form fields at the top first.");
            return;
        }

        Item selectedItem = itemTable.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            showAlert(Alert.AlertType.WARNING, "No Row Selected",
                    "Please select an item row from the table first to record a Goods Received Note.");
            return;
        }

        currentPaneAction = "STOCK_IN";
        formPaneTitle.setText("Goods Received Note (GRN)");
        formItemNameField.setText(selectedItem.getItemName());
        formQuantityField.setText("0");
        formEntityLabel.setText("Supplier / Vendor Source:");
        formActionButton.setText("Confirm Stock In");
        formActionButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");

        populateEntitySuggestions(true);
        openSlidingPane();
    }

    @FXML
    private void handleShowIssuePane() {
        if (masterData == null || masterData.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "Empty Registry Store",
                    "There are no stock items registered in the system yet.\n\n" +
                            "Stock entries must be created before you can generate a Store Issue Voucher.");
            return;
        }

        Item selectedItem = itemTable.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            showAlert(Alert.AlertType.WARNING, "No Row Selected",
                    "Please select an item row from the table first to issue a voucher.");
            return;
        }

        if (selectedItem.getQuantityInStock() <= 0) {
            showAlert(Alert.AlertType.ERROR, "Out of Stock",
                    "Operation Aborted: '" + selectedItem.getItemName() + "' is completely Out of Stock and cannot be issued.");
            return;
        }

        currentPaneAction = "ISSUE";
        formPaneTitle.setText("Store Issue Voucher (SIV)");
        formItemNameField.setText(selectedItem.getItemName());
        formQuantityField.setText("1");
        formEntityLabel.setText("Recipient Department / Officer:");
        formActionButton.setText("Confirm Issue Voucher");
        formActionButton.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white;");

        populateEntitySuggestions(false);
        openSlidingPane();
    }

    @FXML
    private void handleExecuteFormAction() {
        Item selectedItem = itemTable.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            showAlert(Alert.AlertType.WARNING, "Selection Lost", "No active item selected.");
            handleCloseSlidingPane();
            return;
        }

        String qtyStr = formQuantityField.getText();
        String entityStr = (formEntityComboBox.getValue() != null) ? formEntityComboBox.getValue().trim() : "";

        if (qtyStr == null || qtyStr.isEmpty() || entityStr.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Form Incomplete", "Please completely fill out all transaction fields.");
            return;
        }

        try {
            int qty = Integer.parseInt(qtyStr);
            if (qty <= 0) {
                showAlert(Alert.AlertType.ERROR, "Invalid Quantity", "Quantity must be a positive integer value greater than zero.");
                return;
            }

            int currentItemId = selectedItem.getItemId();

            if ("STOCK_IN".equals(currentPaneAction)) {
                itemDAO.receiveGoods(currentItemId, qty, entityStr);
                refreshDashboard();
                handleCloseSlidingPane();
                showAlert(Alert.AlertType.INFORMATION, "Success", "Stock increased successfully.");

            } else if ("ISSUE".equals(currentPaneAction)) {
                int currentStock = selectedItem.getQuantityInStock();
                int finalQtyToLog = qty;

                if (qty > currentStock) {
                    Alert partialConfirm = new Alert(Alert.AlertType.CONFIRMATION);
                    partialConfirm.setTitle("Insufficient Stock Balance");
                    partialConfirm.setHeaderText("Partial Allocation Warning Required");
                    partialConfirm.setContentText("You requested " + qty + " units, but only " + currentStock +
                            " are remaining in warehouse logs.\n\n" +
                            "Would you like to auto-adjust and issue the remaining balance of " + currentStock +
                            " units? This will set this item status to Out Of Stock.");

                    if (itemTable.getScene() != null) {
                        partialConfirm.initOwner(itemTable.getScene().getWindow());
                    }

                    ButtonType yesButton = ButtonType.YES;
                    ButtonType noButton = ButtonType.NO;
                    partialConfirm.getButtonTypes().setAll(yesButton, noButton);

                    Optional<ButtonType> result = partialConfirm.showAndWait();
                    if (result.isPresent() && result.get() == yesButton) {
                        finalQtyToLog = currentStock;
                    } else {
                        return;
                    }
                }

                // Capture required variable parameters safely for lambda expression tracking blocks
                final int allocationVolume = finalQtyToLog;
                itemDAO.issueInventoryItem(currentItemId, allocationVolume, entityStr);

                refreshDashboard();
                handleCloseSlidingPane();

                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.INFORMATION, "Allocation Authorized",
                            "Successfully allocated " + allocationVolume + " units of '" + selectedItem.getItemName() + "' to " + entityStr + ".");
                });
            }
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Input Error", "Please verify quantity contains valid numeric inputs.");
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Transaction Processing Failure", "Database Write Aborted: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    @FXML
    private void handleCloseSlidingPane() {
        if (sideSplitPane != null) {
            sideSplitPane.setDividerPositions(1.0);
        }
        if (slidingFormPane != null) {
            slidingFormPane.setManaged(false);
            slidingFormPane.setVisible(false);
        }
    }

    private void openSlidingPane() {
        if (slidingFormPane != null) {
            slidingFormPane.setManaged(true);
            slidingFormPane.setVisible(true);
        }
        if (sideSplitPane != null) {
            sideSplitPane.setDividerPositions(0.75);
        }
        Platform.runLater(formQuantityField::requestFocus);
    }

    private void populateEntitySuggestions(boolean isSupplier) {
        if (formEntityComboBox == null) return;

        formEntityComboBox.getItems().clear();
        formEntityComboBox.setValue("");

        List<String> suggestions = auditMasterData.stream()
                .map(log -> isSupplier ? log.getSupplier() : log.getRecipient())
                .filter(name -> name != null && !name.trim().isEmpty() && !name.equals("-"))
                .map(String::trim)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        formEntityComboBox.getItems().addAll(suggestions);
    }

    @FXML
    private void handleLowStockReport() {
        List<Item> lowStockItems = reportDAO.getLowStockData();
        if (lowStockItems == null || lowStockItems.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "Inventory Status", "All items are well-stocked!");
            refreshData();
        } else {
            itemTable.setItems(FXCollections.observableArrayList(lowStockItems));

            StringBuilder sb = new StringBuilder();
            for (Item item : lowStockItems) {
                sb.append(String.format("•  %-25s  |  Current Stock: %d\n",
                        item.getItemName(),
                        item.getQuantityInStock()));
            }

            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Inventory Attention Required");
            alert.setHeaderText("Warning: Low Stock Warning Level Met");
            alert.setContentText(sb.toString());

            ButtonType buttonOk = new ButtonType("Acknowledge", ButtonBar.ButtonData.OK_DONE);
            alert.getButtonTypes().setAll(buttonOk);

            DialogPane dialogPane = alert.getDialogPane();

            if (itemTable != null && itemTable.getScene() != null) {
                alert.initOwner(itemTable.getScene().getWindow());
            }

            if (dialogPane.getScene() != null && dialogPane.getScene().getWindow() instanceof javafx.stage.Stage) {
                javafx.stage.Stage alertStage = (javafx.stage.Stage) dialogPane.getScene().getWindow();
                alertStage.getIcons().add(SceneManager.getAppIcon());
            }

            try {
                dialogPane.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
            } catch (Exception e) {
                try {
                    dialogPane.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
                } catch (Exception ex) {
                    System.err.println("Could not load style.css resource into dialog window context.");
                }
            }

            dialogPane.setStyle(
                    "-fx-font-family: 'Segoe UI', Helvetica, Arial, sans-serif;" +
                            "-fx-font-size: 14px;" +
                            "-fx-background-color: #f8fafc;" +
                            "-fx-pref-width: 500px;"
            );

            if (dialogPane.lookup(".header-panel") != null) {
                dialogPane.lookup(".header-panel").setStyle(
                        "-fx-background-color: #2c3e50;" +
                                "-fx-padding: 16px;"
                );
            }

            forceWhiteTextOnHeaderElements(dialogPane);

            if (dialogPane.lookup(".content") != null) {
                dialogPane.lookup(".content").setStyle(
                        "-fx-padding: 22px 18px 12px 18px;" +
                                "-fx-text-fill: #c0392b;" +
                                "-fx-font-family: 'Consolas', 'Courier New', monospace;" +
                                "-fx-font-size: 13px;" +
                                "-fx-line-spacing: 1.4;"
                );
            }

            if (dialogPane.lookup(".button-bar") != null) {
                dialogPane.lookup(".button-bar").setStyle(
                        "-fx-background-color: #f1f2f6;" +
                                "-fx-padding: 12px;"
                );
            }

            Button okButtonNode = (Button) dialogPane.lookupButton(buttonOk);
            if (okButtonNode != null) {
                okButtonNode.setStyle(
                        "-fx-background-color: #e67e22;" +
                                "-fx-text-fill: white;" +
                                "-fx-font-weight: bold;" +
                                "-fx-padding: 6px 22px;"
                );
            }

            alert.showAndWait();
        }
    }

    @FXML
    private void handleAddItem() {
        if (isRegistering) return;
        isRegistering = true;

        try {
            String name = nameInput.getText();
            String category = categoryInput.getValue();
            String stockText = stockInput.getText();
            String unit = (unitInput != null) ? unitInput.getValue() : "";
            String thresholdText = (thresholdInput != null) ? thresholdInput.getText().trim() : "10";

            if (name == null || name.trim().isEmpty() || category == null || stockText.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Form Incomplete", "Please fill in all fields.");
                return;
            }

            try {
                int stock = Integer.parseInt(stockText);
                int alertThreshold = thresholdText.isEmpty() ? 10 : Integer.parseInt(thresholdText);
                String finalItemName = name.trim() + (unit != null && !unit.isEmpty() ? " (" + unit + ")" : "");

                try {
                    itemDAO.addItem(finalItemName, category, alertThreshold, stock);
                    refreshData();
                    Platform.runLater(() -> itemTable.refresh());

                    showAlert(Alert.AlertType.INFORMATION, "Success", finalItemName + " registered!");
                    clearInputFields();

                } catch (RuntimeException e) {
                    if (e.getCause() instanceof SQLIntegrityConstraintViolationException ||
                            (e.getMessage() != null && (e.getMessage().contains("Constraint") || e.getMessage().contains("Duplicate")))) {

                        showAlert(Alert.AlertType.WARNING, "Duplicate Item Entry",
                                "The item entry '" + finalItemName + "' is already registered under the '" + category + "' category.\n\n" +
                                        "Please use the inline 'Stock In (GRN)' sidebar form pane to update its quantity instead.");
                    } else {
                        throw e;
                    }
                }
            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.ERROR, "Input Error", "Stock and threshold values must be valid integers.");
            }
        } finally {
            isRegistering = false;
        }
    }

    @FXML
    private void loadItemData() {
        try {
            List<Item> items = itemDAO.getAllItems();
            masterData.setAll(items);
            updateDashboardStats();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void styleInputDialog(DialogPane dialogPane, String headerColorHex) {
        if (itemTable != null && itemTable.getScene() != null) {
            javafx.stage.Window mainWindow = itemTable.getScene().getWindow();
            if (dialogPane.getScene() != null && dialogPane.getScene().getWindow() instanceof javafx.stage.Stage) {
                javafx.stage.Stage dialogStage = (javafx.stage.Stage) dialogPane.getScene().getWindow();
                if (dialogStage.getOwner() == null) {
                    dialogStage.initOwner(mainWindow);
                }
                dialogStage.getIcons().clear();
                dialogStage.getIcons().add(SceneManager.getAppIcon());
            }
        }

        dialogPane.getStylesheets().clear();
        dialogPane.setStyle(
                "-fx-font-family: 'Segoe UI', Helvetica, Arial, sans-serif;" +
                        "-fx-font-size: 14px;" +
                        "-fx-background-color: transparent;" +
                        "-fx-min-width: 480px;" +
                        "-fx-pref-width: 480px;" +
                        "-fx-min-height: 250px;" +
                        "-fx-pref-height: 250px;"
        );

        if (dialogPane.lookup(".header-panel") != null) {
            dialogPane.lookup(".header-panel").setStyle(
                    "-fx-background-color: " + headerColorHex + ";" +
                            "-fx-padding: 16px;"
            );
        }

        forceWhiteTextOnHeaderElements(dialogPane);

        Node nativeInputField = dialogPane.lookup(".text-field");

        if (nativeInputField instanceof TextField) {
            TextField originalTextField = (TextField) nativeInputField;

            StackPane glassmorphicRootContainer = new StackPane();
            glassmorphicRootContainer.setStyle("-fx-background-color: #f8fafc;");

            ImageView dialogWatermark = new ImageView(SceneManager.getAppIcon());
            dialogWatermark.setOpacity(0.14);
            dialogWatermark.setPreserveRatio(true);
            dialogWatermark.setPickOnBounds(false);
            dialogWatermark.setFitHeight(150);
            dialogWatermark.setFitWidth(150);

            VBox customContentWrapper = new VBox(12);
            customContentWrapper.setStyle("-fx-padding: 20px 25px; -fx-background-color: transparent;");

            Label promptLabel = new Label(dialogPane.getContentText());
            promptLabel.setStyle("-fx-text-fill: #2c3e50; -fx-font-weight: bold; -fx-font-size: 14px;");

            originalTextField.setVisible(true);
            originalTextField.setManaged(true);
            originalTextField.setOpacity(1.0);
            originalTextField.setPrefWidth(430);
            originalTextField.setMinWidth(430);
            originalTextField.setPrefHeight(38);
            originalTextField.setMinHeight(38);
            originalTextField.setStyle(
                    "-fx-background-color: rgba(255, 255, 255, 0.85) !important;" +
                            "-fx-border-color: #cbd5e1 !important;" +
                            "-fx-border-width: 1.5px !important;" +
                            "-fx-border-radius: 4px !important;" +
                            "-fx-background-radius: 4px !important;" +
                            "-fx-padding: 6px 10px !important;" +
                            "-fx-text-fill: #0f172a !important;" +
                            "-fx-font-weight: normal !important;"
            );

            customContentWrapper.getChildren().addAll(promptLabel, originalTextField);
            glassmorphicRootContainer.getChildren().addAll(dialogWatermark, customContentWrapper);
            dialogPane.setContent(glassmorphicRootContainer);

            Platform.runLater(originalTextField::requestFocus);
        }

        if (dialogPane.lookup(".button-bar") != null) {
            dialogPane.lookup(".button-bar").setStyle(
                    "-fx-background-color: #f1f2f6;" +
                            "-fx-padding: 12px;"
            );
        }
    }

    private void forceWhiteTextOnHeaderElements(DialogPane dialogPane) {
        if (dialogPane.lookup(".header-panel") != null) {
            Pane headerPanel = (Pane) dialogPane.lookup(".header-panel");
            for (Node node : headerPanel.getChildren()) {
                if (node instanceof Label) {
                    ((Label) node).setTextFill(Color.WHITE);
                    node.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
                } else if (node instanceof Text) {
                    ((Text) node).setFill(Color.WHITE);
                    node.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
                }
            }
        }
    }

    @FXML
    private void handleDeleteItem() {
        if (masterData == null || masterData.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "Empty Registry Store",
                    "There are no registered items currently available to delete.");
            return;
        }

        Item selectedItem = itemTable.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            showAlert(Alert.AlertType.WARNING, "No Row Selected",
                    "Please select the specific item row you wish to delete from the warehouse inventory.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Are you sure you want to permanently delete '" + selectedItem.getItemName() + "'?\n\n" +
                        "This will instantly delete this item regardless of its stock level.",
                ButtonType.YES, ButtonType.NO);

        if (itemTable.getScene() != null) {
            confirm.initOwner(itemTable.getScene().getWindow());
        }

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    int idToDelete = selectedItem.getItemId();

                    itemTable.getSelectionModel().clearSelection();
                    itemDAO.deleteItem(idToDelete);

                    Platform.runLater(() -> {
                        masterData.removeIf(item -> item.getItemId() == idToDelete);

                        if (filteredData != null) {
                            filteredData.setPredicate(p -> true);
                        }

                        SortedList<Item> sortedData = new SortedList<>(filteredData);
                        sortedData.comparatorProperty().bind(itemTable.comparatorProperty());
                        itemTable.setItems(sortedData);

                        refreshDashboard();
                        showAlert(Alert.AlertType.INFORMATION, "Success", "Item successfully deleted from records.");
                    });

                } catch (Exception e) {
                    showAlert(Alert.AlertType.ERROR, "Database Failure",
                            "Could not complete deletion: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }

    @FXML
    private void handleExportAuditToExcel() {
        ObservableList<TransactionLog> data = logTable.getItems();
        if (data == null || data.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Data Available", "There are no transaction log entries to export in the current view.");
            return;
        }

        String filterState = (flowTypeFilterComboBox != null) ? flowTypeFilterComboBox.getValue() : "All Movements";

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Transaction Ledger Spreadsheet");
        fileChooser.setInitialFileName(filterState.replace(" ", "_") + "_Inventory_Audit.xls");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Spreadsheet (*.xls)", "*.xls"));
        File file = fileChooser.showSaveDialog(logTable.getScene().getWindow());

        if (file != null) {
            try (PrintWriter writer = new PrintWriter(file)) {
                writer.println("Movement Type\tItem Description\tQuantity Transacted\tSupplier Source\tRecipient Department\tTimestamp Ledger");
                for (TransactionLog log : data) {
                    writer.printf("%s\t%s\t%d\t%s\t%s\t%s%n",
                            log.getType(), log.getItemName(), log.getQuantity(), log.getSupplier(), log.getRecipient(), log.getDate());
                }
                showAlert(Alert.AlertType.INFORMATION, "Spreadsheet Built", "Audit ledger successfully exported to an Excel structured format.");
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Export Failed", "Could not write spreadsheet file to disc: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @FXML
    private void handleCompileFilteredPDFReport() {
        ObservableList<TransactionLog> data = logTable.getItems();
        if (data == null || data.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Filtered Data", "No rows are present in the current view to build a compilation summary catalog document.");
            return;
        }

        String filterState = (flowTypeFilterComboBox != null) ? flowTypeFilterComboBox.getValue() : "All Movements";

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Print Summary Ledger Report PDF");
        fileChooser.setInitialFileName(filterState.replace(" ", "_") + "_Summary_Report.pdf");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Document (*.pdf)", "*.pdf"));
        File file = fileChooser.showSaveDialog(logTable.getScene().getWindow());

        if (file != null) {
            try {
                PDFGenerator.generateSummaryLedger(data, filterState, file);
                showAlert(Alert.AlertType.INFORMATION, "Print Complete", "Filtered historical ledger catalog generated and compiled directly to:\n" + file.getAbsolutePath());
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Compilation Interrupted", "PDF layout builder failed: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @FXML
    private void handleExportAudit() {
        handleExportAuditToExcel();
    }

    @FXML
    private void handlePrintSelectedVoucher() {
        if (logTable == null) return;

        TransactionLog selectedLog = logTable.getSelectionModel().getSelectedItem();

        if (selectedLog == null) {
            showAlert(Alert.AlertType.WARNING, "Selection Required",
                    "Please select a transaction row from the Audit Trail table to compile its official PDF Voucher.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save PDF Voucher Documentation");

        String cleanTitle = selectedLog.getItemName().replaceAll("[^a-zA-Z0-9]", "_");
        String predictedName = selectedLog.getType().replace(" ", "_") + "_" + cleanTitle + ".pdf";
        fileChooser.setInitialFileName(predictedName);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Document (*.pdf)", "*.pdf"));

        File saveFile = fileChooser.showSaveDialog(logTable.getScene().getWindow());

        if (saveFile != null) {
            try {
                PDFGenerator.generateVoucher(selectedLog, saveFile);
                showAlert(Alert.AlertType.INFORMATION, "Compilation Success", "Official document compiled and written successfully to:\n" + saveFile.getAbsolutePath());
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Compilation Exception Encountered", "PDF Engine aborted initialization: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @FXML
    private void loadTransactionLog() {
        try {
            List<TransactionLog> databaseLogs = itemDAO.getTransactionHistory();
            auditMasterData.setAll(databaseLogs);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void refreshData() {
        try {
            if (searchField != null) searchField.clear();
            if (historySearchField != null) historySearchField.clear();
            if (flowTypeFilterComboBox != null) flowTypeFilterComboBox.getSelectionModel().selectFirst();

            masterData.setAll(itemDAO.getAllItems());
            auditMasterData.setAll(itemDAO.getTransactionHistory());

            if (filteredData != null) {
                filteredData.setPredicate(p -> true);
            }

            SortedList<Item> sortedData = new SortedList<>(filteredData);
            sortedData.comparatorProperty().bind(itemTable.comparatorProperty());
            itemTable.setItems(sortedData);

            if (filteredAuditData != null) {
                filteredAuditData.setPredicate(p -> true);
            }

            updateDashboardStats();

            Platform.runLater(() -> {
                itemTable.refresh();
                if (logTable != null) logTable.refresh();
            });
        } catch (Exception e) {
            System.err.println("Error encountered during layout workspace data refresh routine:");
            e.printStackTrace();
        }
    }

    @FXML
    private void refreshDashboard() {
        if (itemTable != null) {
            masterData.setAll(itemDAO.getAllItems());
        }
        updateDashboardStats();
        auditMasterData.setAll(itemDAO.getTransactionHistory());

        Platform.runLater(() -> {
            itemTable.refresh();
            if (logTable != null) logTable.refresh();
        });
    }

    @FXML
    private void handleClearHistory() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Are you sure you want to permanently clear ALL transaction history records?",
                ButtonType.YES, ButtonType.NO);

        confirm.setTitle("Clear Audit Trail");
        confirm.setHeaderText("Warning: Data cannot be recovered.");

        if (logTable != null && logTable.getScene() != null) {
            confirm.initOwner(logTable.getScene().getWindow());
        }

        if (confirm.getDialogPane().getScene() != null && confirm.getDialogPane().getScene().getWindow() instanceof javafx.stage.Stage) {
            javafx.stage.Stage alertStage = (javafx.stage.Stage) confirm.getDialogPane().getScene().getWindow();
            alertStage.getIcons().add(SceneManager.getAppIcon());
        }

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                itemDAO.clearTransactionHistory();
                refreshData();
                showAlert(Alert.AlertType.INFORMATION, "Success", "History has been cleared.");
            }
        });
    }
}