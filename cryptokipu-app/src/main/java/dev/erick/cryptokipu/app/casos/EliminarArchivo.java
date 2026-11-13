package dev.erick.cryptokipu.app.casos;

import java.io.IOException;
import java.util.Objects;

/**
 * Quita un archivo del manifest. Sus bloques siguen cifrados dentro del archivo hasta {@link Compactar};
 * con {@code compactarMija = true} se hace "Eliminar y compactar".
 */
public final class EliminarArchivo {

    public EliminarArchivo() {
        // Sin dependencias: opera sobre la sesión del comando.
    }

    public record Comando(SesionBoveda sesionMija, byte[] fileIdMija, boolean compactarMija) {
        public Comando {
            Objects.requireNonNull(sesionMija, "sesionMija");
            fileIdMija = Objects.requireNonNull(fileIdMija, "fileIdMija").clone();
        }
    }

    public void ejecutar(Comando comandoMija) throws IOException {
        var contenedorMija = comandoMija.sesionMija().contenedor();
        contenedorMija.eliminar(comandoMija.fileIdMija());
        if (comandoMija.compactarMija()) {
            contenedorMija.compactar();
        }
    }
}
