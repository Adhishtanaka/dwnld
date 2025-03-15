module at.dwnld {
    requires javafx.fxml;
    requires org.jfxtras.styles.jmetro;
    requires okhttp3;
    requires org.apache.commons.io;
    requires annotations;
    requires java.desktop;

    opens at.dwnld to javafx.fxml;
    exports at.dwnld;
}