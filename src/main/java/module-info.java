module at.dwnld {
    requires javafx.fxml;
    requires okhttp3;
    requires org.apache.commons.io;
    requires annotations;
    requires java.desktop;
    requires com.pixelduke.transit;

    opens at.dwnld to javafx.fxml;
    exports at.dwnld;
}