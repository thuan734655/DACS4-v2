module org.example.dacs4_v2 {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.rmi;

    exports org.example.dacs4_v2.network.rmi to java.rmi;
    opens org.example.dacs4_v2 to javafx.fxml;
    opens org.example.dacs4_v2.viewModels to javafx.fxml;
    exports org.example.dacs4_v2;
}