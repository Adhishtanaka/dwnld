package at.dwnld;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import jfxtras.styles.jmetro.JMetro;
import jfxtras.styles.jmetro.JMetroStyleClass;
import jfxtras.styles.jmetro.Style;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;

public class MainActivity extends Application {

    private static final String LOCK_FILE_NAME = "dwnld.lock";
    private static FileLock lock;
    private static RandomAccessFile randomAccessFile;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(MainActivity.class.getResource("activity_main.fxml"));
        BorderPane borderPane = new BorderPane();
        borderPane.setCenter(fxmlLoader.load());
        TabPane tabPane = new TabPane();
        borderPane.setTop(tabPane);
        borderPane.getStyleClass().add(JMetroStyleClass.BACKGROUND);
        Scene scene = new Scene(borderPane, 600, 400);
        stage.setMinWidth(612);
        stage.setMinHeight(412);
        JMetro jMetro = new JMetro(Style.DARK);
        jMetro.setScene(scene);
        stage.setTitle("dwnld");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        releaseLock();
    }

    public static void main(String[] args) {
        if (obtainLock()) {
            launch(args);
        } else {
            System.out.println("Application is already running.");
            Platform.exit();
        }
    }

    private static boolean obtainLock() {
        try {
            File lockFile = new File(System.getProperty("user.home"), LOCK_FILE_NAME);
            randomAccessFile = new RandomAccessFile(lockFile, "rw");
            lock = randomAccessFile.getChannel().tryLock();

            if (lock == null) {
                randomAccessFile.close();
                return false;
            }

            Runtime.getRuntime().addShutdownHook(new Thread(MainActivity::releaseLock));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void releaseLock() {
        try {
            if (lock != null) {
                lock.release();
                randomAccessFile.close();
                new File(System.getProperty("user.home"), LOCK_FILE_NAME).delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}