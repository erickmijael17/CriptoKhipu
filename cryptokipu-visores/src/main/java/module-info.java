import dev.erick.cryptokipu.app.puertos.VisorDocumentos;
import dev.erick.cryptokipu.visores.VisoresEnMemoria;

/**
 * Visores de documentos en RAM (opcional). PDFBox y Apache POI solo se cargan si este módulo está en la imagen;
 * la interfaz lo encuentra con {@code ServiceLoader}.
 */
module dev.erick.cryptokipu.visores {
    requires dev.erick.cryptokipu.app;
    requires java.desktop;
    requires org.apache.pdfbox;
    requires org.apache.pdfbox.io;
    requires org.apache.poi.ooxml;

    provides VisorDocumentos with VisoresEnMemoria;
}
