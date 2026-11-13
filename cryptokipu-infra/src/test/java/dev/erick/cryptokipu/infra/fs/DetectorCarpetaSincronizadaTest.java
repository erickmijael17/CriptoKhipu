package dev.erick.cryptokipu.infra.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DetectorCarpetaSincronizadaTest {

    @ParameterizedTest
    @CsvSource({
        "/home/ana/Google Drive/b.kipu, Google Drive",
        "/Users/ana/Library/CloudStorage/GoogleDrive-ana@x.com/Mi unidad/b.kipu, Google Drive",
        "/home/ana/OneDrive - Empresa/b.kipu, OneDrive",
        "/home/ana/Dropbox/fotos/b.kipu, Dropbox",
        "/Users/ana/Library/Mobile Documents/com~apple~CloudDocs/b.kipu, iCloud",
        "/media/usb/b.kipu, ''",
        "/home/ana/documentos/b.kipu, ''"})
    void reconoceLasCarpetasDeLosServiciosConocidos(String rutaMija, String esperadoMija) {
        assertEquals(esperadoMija.isEmpty() ? Optional.empty() : Optional.of(esperadoMija),
                DetectorCarpetaSincronizada.servicio(Path.of(rutaMija)));
    }
}
