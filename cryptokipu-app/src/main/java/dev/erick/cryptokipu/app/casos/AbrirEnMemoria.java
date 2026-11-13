package dev.erick.cryptokipu.app.casos;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.util.Objects;

/**
 * Canal de solo lectura con acceso aleatorio que descifra por bloque y nunca escribe en disco: base del
 * visor y del servidor de medios con soporte de Range (Fase 7). Un bloque manipulado falla al leer esa posición.
 */
public final class AbrirEnMemoria {

    public AbrirEnMemoria() {
        // Sin dependencias: opera sobre la sesión del comando.
    }

    public record Comando(SesionBoveda sesionMija, byte[] fileIdMija) {
        public Comando {
            Objects.requireNonNull(sesionMija, "sesionMija");
            fileIdMija = Objects.requireNonNull(fileIdMija, "fileIdMija").clone();
        }
    }

    public SeekableByteChannel ejecutar(Comando comandoMija) throws IOException {
        return comandoMija.sesionMija().contenedor().abrirLectura(comandoMija.fileIdMija());
    }
}
