module at.dwnld {
    requires javafx.controls;
    requires javafx.fxml;


    opens at.dwnld to javafx.fxml;
    exports at.dwnld;
}