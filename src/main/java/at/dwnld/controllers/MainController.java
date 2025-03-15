package at.dwnld.controllers;

import at.dwnld.MainActivity;
import at.dwnld.models.FileModel;
import at.dwnld.models.FileStatus;
import at.dwnld.models.SettingModel;
import at.dwnld.services.DownloadService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import jfxtras.styles.jmetro.JMetro;
import jfxtras.styles.jmetro.JMetroStyleClass;
import jfxtras.styles.jmetro.Style;
import java.awt.*;
import java.io.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.io.FileUtils;

public class MainController {

    @FXML private Label lblTotalDownloads;
    @FXML private Label lblActiveDownloads;
    @FXML private Label lblTotalSpeed;
    @FXML private TextField searchField;
    @FXML private Button btnAddDownload;
    @FXML private Button btnDelete;
    @FXML private Button btnSettings;
    @FXML private Button btnResume;
    @FXML private Button btnPause;
    @FXML private TableView<FileModel> tableView;
    @FXML private TableColumn<FileModel, String> columnName;
    @FXML private TableColumn<FileModel, String> columnSize;
    @FXML private TableColumn<FileModel, String> columnDate;
    @FXML private TableColumn<FileModel, String> columnStatus;
    @FXML private TableColumn<FileModel, String> columnProgress;

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");

    private static final ObservableList<FileModel> downloads = FXCollections.observableArrayList();
    SettingModel sm;

    @FXML
    private void initialize() {
        sm = SettingModel.getInstance();
        tableView.setItems(downloads);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        columnName.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getName()));
        columnSize.setCellValueFactory(cellData -> new SimpleStringProperty(FileUtils.byteCountToDisplaySize(cellData.getValue().getSize())));
        columnDate.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getAdded().format(formatter)));
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
        ContextMenu contextMenu = new ContextMenu();
        MenuItem openItem = new MenuItem("Open");
        MenuItem showItem = new MenuItem("Show in Folder");
        MenuItem removeItem = new MenuItem("Remove File");
        MenuItem removeFromListItem = new MenuItem("Remove");
        MenuItem openPageItem = new MenuItem("Open Download Page");
        MenuItem changeUrlItem = new MenuItem("Change URL");
        MenuItem fileInformationItem = new MenuItem("File Information");
        MenuItem pauseResumeItem = new MenuItem();
        MenuItem cancelItem = new MenuItem("Cancel");
        contextMenu.getItems().addAll(openItem,showItem,removeFromListItem,removeItem,openPageItem,changeUrlItem,fileInformationItem);
        tableView.setRowFactory(tv -> {
            TableRow<FileModel> row = new TableRow<>();

            row.setOnContextMenuRequested(event -> {
                if (!row.isEmpty()) {
                    FileModel file = row.getItem();
                    tableView.getSelectionModel().select(file);
                    if (file.getStatus() == FileStatus.inProgress) {
                        pauseResumeItem.setText("Pause");
                        pauseResumeItem.setOnAction(e -> pauseDownloadItem(file));
                        contextMenu.getItems().addFirst(pauseResumeItem);
                    } else if (file.getStatus() == FileStatus.paused) {
                        pauseResumeItem.setText("Resume");
                        pauseResumeItem.setOnAction(e -> resumeDownloadItem(file));
                        contextMenu.getItems().addFirst(pauseResumeItem);
                    }
                    if (file.getStatus() != FileStatus.completed){
                        cancelItem.setOnAction(e -> cancleFileItem(file));
                        contextMenu.getItems().add(1,cancelItem);
                    }
                    removeFromListItem.setOnAction(e ->  removeFromList(file));
                    openItem.setOnAction(e -> openFileItem(file));
                    showItem.setOnAction(e -> openFileFolder(file));
                    removeItem.setOnAction(e -> removeFileItem(file));
                    openPageItem.setOnAction(e -> System.out.println("not implemented yet"));
                    changeUrlItem.setOnAction(e -> changeFileUrl(file));
                    fileInformationItem.setOnAction(e -> openInformationDialog(file));
                    contextMenu.show(row, event.getScreenX(), event.getScreenY());
                } else {
                    contextMenu.hide();
                }
            });

            return row;
        });

        btnAddDownload.setOnAction(event -> openAddDownloadDialog());
        btnDelete.setOnAction(event -> openDeleteDialog());
        btnSettings.setOnAction(event -> openSettings());
        btnResume.setOnAction(event -> resumeAllDownloads());
        btnPause.setOnAction(event -> pauseAllDownloads());
        loadDownloads();
        updateStatusBar();
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filterTable(newValue);
        });
    }

    private void removeFromList(FileModel file) {
        downloads.remove(file);
        refreshTable();
    }

    private void resumeDownloadItem(FileModel file) {
        DownloadService ds = new DownloadService(this);
        ds.resumeDownload(file);
    }

    private void pauseDownloadItem(FileModel file) {
        DownloadService ds = new DownloadService(this);
        ds.pauseDownload(file);
    }

    private void changeFileUrl(FileModel file) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Change Download URL");
        Stage dialogStage = (Stage) dialog.getDialogPane().getScene().getWindow();
        dialogStage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/at/dwnld/icon.png"))));
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("File:"), 0, 0);
        grid.add(new Label(file.getName()), 1, 0);
        grid.add(new Label("New URL:"), 0, 1);
        TextField urlField = new TextField(file.getUrl());
        grid.add(urlField, 1, 1);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        JMetro jMetro = new JMetro(Style.DARK);
        jMetro.setScene(dialog.getDialogPane().getScene());
        dialog.setResultConverter(button -> button == ButtonType.OK ? urlField.getText() : null);
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newUrl -> {
            if (!newUrl.isEmpty()) {
                file.setUrl(newUrl);
            }
        });
    }

    private void openInformationDialog(FileModel file) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("File Information");

        Stage dialogStage = (Stage) dialog.getDialogPane().getScene().getWindow();
        dialogStage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/at/dwnld/icon.png"))));
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Name:"), 0, 0);
        grid.add(new Label(file.getName()), 1, 0);
        grid.add(new Label("URL:"), 0, 1);
        grid.add(new Label(file.getUrl()), 1, 1);
        grid.add(new Label("Path:"), 0, 2);
        grid.add(new Label(file.getPath()), 1, 2);
        grid.add(new Label("Added:"), 0, 3);
        grid.add(new Label(file.getAdded().format(formatter)), 1, 3);
        grid.add(new Label("Size:"), 0, 4);
        grid.add(new Label(FileUtils.byteCountToDisplaySize(file.getSize())), 1, 4);
        grid.add(new Label("Last Tried:"), 0, 5);
        grid.add(new Label(file.getLastTried().format(formatter)), 1, 5);
        grid.add(new Label("Status:"), 0, 6);
        grid.add(new Label(file.getStatus().toString()), 1, 6);
        JMetro jMetro = new JMetro(Style.DARK);
        jMetro.setScene(dialog.getDialogPane().getScene());
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dialog.showAndWait();
    }

    private void cancleFileItem(FileModel file) {
        DownloadService ds = new DownloadService(this);
        ds.cancelDownload(file);
    }

    private void removeFileItem(FileModel file) {
        DownloadService ds = new DownloadService(this);
        downloads.remove(file);
        ds.cancelDownload(file);

    }

    private void openFileFolder(FileModel file) {
        File f = new File(file.getPath());
        File folder = f.getParentFile();

        if (folder.exists() && folder.isDirectory()) {
            try {
                Desktop.getDesktop().open(folder);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void openFileItem(FileModel file) {
        File f = new File(file.getPath());
        if (f.exists()) {
            try {
                Desktop.getDesktop().open(f);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void pauseAllDownloads() {
        DownloadService ds = new DownloadService(this);
        for (FileModel file : downloads) {
            if (file.getStatus() == FileStatus.inProgress) {
                ds.pauseDownload(file);
            }
        }
    }

    private void resumeAllDownloads() {
        DownloadService ds = new DownloadService(this);
        for (FileModel file : downloads) {
            if (file.getStatus() == FileStatus.paused) {
                ds.resumeDownload(file);
            }
        }
    }

    private void openDeleteDialog() {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Clear All Downloads");

        Stage dialogStage = (Stage) dialog.getDialogPane().getScene().getWindow();
        dialogStage.getIcons().add(new Image(Objects.requireNonNull(
                getClass().getResourceAsStream("/at/dwnld/icon.png"))));

        Label messageLabel = new Label("Are you sure you want to delete all downloads?");
        messageLabel.setWrapText(true);

        HBox contentBox = new HBox(10, messageLabel);
        contentBox.setAlignment(Pos.CENTER_LEFT);

        dialog.getDialogPane().setContent(contentBox);

        ButtonType deleteButtonType = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(deleteButtonType, ButtonType.CANCEL);

        Button deleteButton = (Button) dialog.getDialogPane().lookupButton(deleteButtonType);
        deleteButton.setStyle("-fx-background-color: #d9534f; -fx-text-fill: white;");

        JMetro jMetro = new JMetro(Style.DARK);
        jMetro.setScene(dialog.getDialogPane().getScene());

        dialog.setResultConverter(button -> button == deleteButtonType);
        Optional<Boolean> result = dialog.showAndWait();

        result.ifPresent(shouldDelete -> {
            if (shouldDelete) {
                File dataFile = new File("downloads.dat");
                if (dataFile.exists()) {
                    if (dataFile.delete()) {
                        downloads.clear();
                    }}
                Platform.runLater(() -> tableView.refresh());
            }
        });
    }


    public void addDownload(FileModel file) {
        downloads.add(file);
        updateStatusBar();
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

        TextField pathField = new TextField(sm.getDefault_path());
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

    public void saveDownloads() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("downloads.dat"))) {
            oos.writeObject(new ArrayList<>(downloads));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadDownloads() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream("downloads.dat"))) {
            @SuppressWarnings("unchecked")
            List<FileModel> savedDownloads = (List<FileModel>) ois.readObject();
            downloads.addAll(savedDownloads);
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("No previous downloads found.");
        }
    }

    @FXML private void openSettings() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(MainActivity.class.getResource("activity_settings.fxml"));
            Parent root = fxmlLoader.load();
            root.getStyleClass().add(JMetroStyleClass.BACKGROUND);
            Scene scene = new Scene(root, 300, 260);
            JMetro jMetro = new JMetro(Style.DARK);
            jMetro.setScene(scene);
            Stage settingsStage = new Stage();
            settingsStage.setMinWidth(312);
            settingsStage.setMinHeight(272);
            settingsStage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/at/dwnld/icon.png"))));
            settingsStage.setTitle("Settings");
            settingsStage.setScene(scene);
            settingsStage.initModality(Modality.APPLICATION_MODAL); // Blocks interaction with main window
            settingsStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void filterTable(String searchText) {
        if (searchText == null || searchText.isEmpty()) {
            tableView.setItems(downloads);
        } else {
            ObservableList<FileModel> filteredList = FXCollections.observableArrayList();
            String lowerCaseFilter = searchText.toLowerCase();

            for (FileModel file : downloads) {
                if (file.getName().toLowerCase().contains(lowerCaseFilter)) {
                    filteredList.add(file);
                }
            }

            tableView.setItems(filteredList);
        }
    }

    private void updateStatusBar() {
        int totalDownloads = downloads.size();
        int activeDownloads = 0;
        double totalSpeed = 0;

        for (FileModel file : downloads) {
            if (file.getStatus() == FileStatus.inProgress) {
                activeDownloads++;
                totalSpeed += file.getSpeed();
            }
        }
        double finalTotalSpeed = totalSpeed;
        int finalActiveDownloads = activeDownloads;
        Platform.runLater(() -> {
            lblTotalDownloads.setText("Downloads: " + totalDownloads);
            lblActiveDownloads.setText("Active: " + finalActiveDownloads);
            lblTotalSpeed.setText("Speed: " + FileUtils.byteCountToDisplaySize(finalTotalSpeed) + "/s");
        });
    }

    public static ObservableList<FileModel> getDownloads() {
        return downloads;
    }

    public void refreshTable() {
        Platform.runLater(() -> {
            tableView.refresh();
            updateStatusBar();
        });
        saveDownloads();
    }
}