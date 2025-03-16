package at.dwnld;

import com.pixelduke.transit.Style;
import com.pixelduke.transit.TransitStyleClass;
import com.pixelduke.transit.TransitTheme;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.Objects;
import java.util.List;

import static at.dwnld.services.defaultAppService.URL_SCHEME;
import static at.dwnld.services.defaultAppService.setupDeepLink;

public class MainActivity extends Application {

    private static final String LOCK_FILE_NAME = "dwnld.lock";
    private static FileLock lock;
    private static RandomAccessFile randomAccessFile;
    private static String downloadUrlFromArgs = null;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(MainActivity.class.getResource("activity_main.fxml"));
        stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/at/dwnld/icon.png"))));
        BorderPane borderPane = new BorderPane();
        borderPane.setCenter(fxmlLoader.load());
        TabPane tabPane = new TabPane();
        borderPane.setTop(tabPane);
        borderPane.getStyleClass().add(TransitStyleClass.BACKGROUND);
        Scene scene = new Scene(borderPane, 600, 400);
        stage.setMinWidth(612);
        stage.setMinHeight(412);
        TransitTheme transitTheme = new TransitTheme(Style.DARK);
        transitTheme.setScene(scene);
        stage.setTitle("dwnld");
        stage.setScene(scene);
        stage.show();

    }

    @Override
    public void stop() {
        releaseLock();
    }

    @Override
    public void init() {
        // Get parameters passed to the application
        Parameters params = getParameters();
        List<String> rawParams = params.getRaw();

        // Process URL parameters if present
        if (!rawParams.isEmpty() && rawParams.get(0).startsWith("dwnld://")) {
            downloadUrlFromArgs = handleUrl(rawParams.get(0));
        }
    }

    public static String handleUrl(String url) {
        if (url != null && url.startsWith(URL_SCHEME + "://")) {
            try {
                String params = url.substring((URL_SCHEME + "://download?").length());
                String[] paramPairs = params.split("&");

                for (String pair : paramPairs) {
                    if (pair.startsWith("url=")) {
                        return java.net.URLDecoder.decode(pair.substring("url=".length()), "UTF-8");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static void main(String[] args) {
        setupDeepLink(true);

        for (String arg : args) {
            if (arg.startsWith("dwnld://")) {
                System.out.println("Application launched with URL: " + arg);
                break;
            }
        }

        if (obtainLock()) {
            launch(args);
        } else {
            System.out.println("Application is already running.");
            if (args.length > 0 && args[0].startsWith("dwnld://")) {
                sendUrlToRunningInstance(args[0]);
            }
            Platform.exit();
        }
    }

    private static void sendUrlToRunningInstance(String url) {
        System.out.println("Sending URL to running instance: " + url);

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