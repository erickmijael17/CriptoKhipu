package dev.erick.cryptokipu.app.puertos;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Puerto: pone un archivo ya verificado ({@code .part}) en su destino final.
 * Sin reemplazo nunca debe pisar un archivo que exista, ni siquiera uno creado en el último instante.
 */
@FunctionalInterface
public interface ColocadorArchivos {

    /**
     * @return {@code true} si el archivo quedó en {@code destinoMija}; {@code false} si el destino ya existía
     *         (en ese caso {@code parcialMija} sigue existiendo y lo borra quien llama)
     */
    boolean colocar(Path parcialMija, Path destinoMija, boolean reemplazarMija) throws IOException;
}
