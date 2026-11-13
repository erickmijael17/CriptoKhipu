import dev.erick.cryptokipu.app.puertos.VisorDocumentos;

/**
 * Interfaz JavaFX de CryptoKipu. Los visores de PDF y Office llegan por {@code ServiceLoader} desde el módulo
 * opcional {@code dev.erick.cryptokipu.visores}; el servidor de medios ({@code ui.medios}) es el único código con
 * red, y solo en 127.0.0.1.
 */
module dev.erick.cryptokipu.ui {
    requires dev.erick.cryptokipu.infra;
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires javafx.media;
    requires jdk.httpserver;
    requires jdk.management;
    requires atlantafx.base;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.feather;

    uses VisorDocumentos;

    exports dev.erick.cryptokipu.ui to javafx.graphics;
    exports dev.erick.cryptokipu.ui.capturas to javafx.graphics;
    opens dev.erick.cryptokipu.ui.vista to javafx.fxml;
}
