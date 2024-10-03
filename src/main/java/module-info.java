module com.guiyomi {
    requires javafx.controls;
    requires javafx.fxml;
    requires transitive java.sql;
    requires transitive javafx.graphics;

    opens com.guiyomi to javafx.fxml;
    exports com.guiyomi;
}
