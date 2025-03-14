package at.dwnld.controllers;

import at.dwnld.models.FileModel;
import at.dwnld.models.SettingModel;
import at.dwnld.services.DownloadService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import jfxtras.styles.jmetro.JMetro;
import jfxtras.styles.jmetro.Style;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

public class MainController {

    @FXML private Button btnAddDownload;
    @FXML private TableView<FileModel> tableView;
    @FXML private TableColumn<FileModel, String> columnName;
    @FXML private TableColumn<FileModel, String> columnSize;
    @FXML private TableColumn<FileModel, String> columnDate;
    @FXML private TableColumn<FileModel, String> columnStatus;
    @FXML private TableColumn<FileModel, String> columnProgress;

    private final ObservableList<FileModel> downloads = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        tableView.setItems(downloads);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        columnName.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getName()));
        columnSize.setCellValueFactory(cellData -> new SimpleStringProperty(String.valueOf(cellData.getValue().getSize())));
        columnDate.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getAdded().toString()));
        columnStatus.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getStatus().toString()));
        columnProgress.setCellFactory(column -> new TableCell<FileModel, String>() {
            private final ProgressBar progressBar = new ProgressBar();

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    FileModel file = getTableRow().getItem();
                    double progress = (file.getDownloadedSize() * 1.0) / file.getSize();
                    progressBar.setProgress(progress);
                    setGraphic(progressBar);
                }
            }
        });
        btnAddDownload.setOnAction(event -> openAddDownloadDialog());
    }

    public void addDownload(FileModel file) {
        downloads.add(file);
    }

    private void openAddDownloadDialog() {
        Stage primaryStage = (Stage) btnAddDownload.getScene().getWindow();
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("Add Download");
        dialog.setHeaderText("Enter the download URL and select a save location:");

        Stage dialogStage = (Stage) dialog.getDialogPane().getScene().getWindow();
        dialogStage.getIcons().add(new Image(Objects.requireNonNull(
                getClass().getResourceAsStream("/at/dwnld/icon.png"))));

        JMetro jMetro = new JMetro(Style.DARK);
        jMetro.setScene(dialog.getDialogPane().getScene());

        TextField urlField = new TextField();
        urlField.setPromptText("Enter URL here");
        urlField.setPrefHeight(30);
        GridPane.setHgrow(urlField, Priority.ALWAYS);

        TextField pathField = new TextField(SettingModel.getDefaultDownloadDirectory());
        pathField.setPromptText("Choose download location");
        pathField.setPrefHeight(30);
        GridPane.setHgrow(pathField, Priority.ALWAYS);

        Button browseButton = new Button("Browse...");
        browseButton.setOnAction(e -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("Select Download Folder");
            File selectedFolder = directoryChooser.showDialog(primaryStage);
            if (selectedFolder != null) {
                pathField.setText(selectedFolder.getAbsolutePath());
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Download URL:"), 0, 0);
        grid.add(urlField, 1, 0);
        grid.add(new Label("Save Path:"), 0, 1);
        grid.add(pathField, 1, 1);
        grid.add(new HBox(10, browseButton), 1, 2);

        ColumnConstraints column1 = new ColumnConstraints();
        column1.setPercentWidth(30);

        ColumnConstraints column2 = new ColumnConstraints();
        column2.setPercentWidth(70);
        column2.setHgrow(Priority.ALWAYS);

        grid.getColumnConstraints().addAll(column1, column2);

        dialog.getDialogPane().setContent(grid);

        ButtonType downloadButtonType = new ButtonType("Download", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(downloadButtonType, ButtonType.CANCEL);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == downloadButtonType) {
                return new String[]{urlField.getText(), pathField.getText()};
            }
            return null;
        });

        Optional<String[]> result = dialog.showAndWait();
        result.ifPresent(data -> {
            String url = data[0];
            String savePath = data[1];
            DownloadService ds = new DownloadService(this);
            try {
                ds.download(url, savePath, null);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }


    public void refreshTable() {
        Platform.runLater(() -> tableView.refresh());
    }
}
