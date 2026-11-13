package dev.erick.cryptokipu.infra.fs;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Detecta si una ruta está dentro de una carpeta que sincroniza un cliente de nube (Google Drive, OneDrive,
 * Dropbox, iCloud). Ahí el bloqueo del archivo no funciona y el cliente puede subir la bóveda a medio escribir.
 * Es una heurística por nombres de carpeta: puede no reconocer configuraciones personalizadas.
 */
public final class DetectorCarpetaSincronizada {

    private record Patron(String fragmentoMija, String servicioMija) {
    }

    private static final List<Patron> PATRONES_MIJA = List.of(
            new Patron("/google drive/", "Google Drive"),
            new Patron("/googledrive/", "Google Drive"),
            new Patron("/my drive/", "Google Drive"),
            new Patron("/mi unidad/", "Google Drive"),
            new Patron("/cloudstorage/googledrive", "Google Drive"),
            new Patron("/onedrive", "OneDrive"),
            new Patron("/cloudstorage/onedrive", "OneDrive"),
            new Patron("/dropbox/", "Dropbox"),
            new Patron("/cloudstorage/dropbox", "Dropbox"),
            new Patron("/icloud drive/", "iCloud"),
            new Patron("/iclouddrive/", "iCloud"),
            new Patron("/mobile documents/com~apple~clouddocs/", "iCloud"));

    private DetectorCarpetaSincronizada() {
    }

    /** Nombre del servicio que sincroniza la carpeta, o vacío si no se reconoce ninguno. */
    public static Optional<String> servicio(Path rutaMija) {
        String normalizadaMija = (rutaMija.toAbsolutePath().normalize().toString().replace('\\', '/') + "/")
                .toLowerCase(Locale.ROOT);
        return PATRONES_MIJA.stream().filter(patronMija -> normalizadaMija.contains(patronMija.fragmentoMija()))
                .map(Patron::servicioMija).findFirst();
    }
}
