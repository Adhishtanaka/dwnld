package at.dwnld.controllers;

import at.dwnld.models.SettingModel;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import java.io.*;

public class SettingsController {
    @FXML private TextField pathField;
    @FXML private Spinner<Integer> maxParallelSpinner;
    @FXML private CheckBox defaultAppCheck;
    @FXML private Button saveButton;
    @FXML private Button browseButton;

    private SettingModel settings;

    @FXML
    public void initialize() {
        loadSetting();

        if (settings == null) {
            settings = SettingModel.getInstance();
        }

        pathField.setText(settings.getDefault_path() != null ? settings.getDefault_path() : "");
        maxParallelSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10, settings.getMax_parallel()));
        defaultAppCheck.setSelected(settings.isDefaultApplication());

        saveButton.setOnAction(event -> {
            saveSettings();
            Window window = saveButton.getScene().getWindow();
            if (window != null) {
                window.hide();
            }
        });

        browseButton.setOnAction(event -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("Select Download Folder");
            Window window = browseButton.getScene().getWindow();
            File selectedFolder = directoryChooser.showDialog(window);
            if (selectedFolder != null) {
                pathField.setText(selectedFolder.getAbsolutePath());
            }
        });
    }

    private void saveSettings() {
        settings.setDefault_path(pathField.getText());
        settings.setMax_parallel(maxParallelSpinner.getValue());
        settings.setDefaultApplication(defaultAppCheck.isSelected());

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("settings.dat"))) {
            oos.writeObject(settings);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadSetting() {
        File file = new File("settings.dat");
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                settings = (SettingModel) ois.readObject();
            } catch (IOException | ClassNotFoundException e) {
                settings = SettingModel.getInstance();
            }
        } else {
            settings = SettingModel.getInstance();
        }
    }
}
