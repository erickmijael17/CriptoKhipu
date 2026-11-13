package dev.erick.cryptokipu.app.puertos;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Archivo a agregar a la bóveda.
 *
 * @param archivoMija      archivo en el disco
 * @param rutaInternaMija  ruta con la que se guardará dentro de la bóveda
 */
public record Origen(Path archivoMija, String rutaInternaMija) {

    public Origen {
        Objects.requireNonNull(archivoMija, "archivoMija");
        Objects.requireNonNull(rutaInternaMija, "rutaInternaMija");
    }
}
