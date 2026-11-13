package dev.erick.cryptokipu.infra.autenticacion;

import dev.erick.cryptokipu.core.auth.KeyfileDigest;
import dev.erick.cryptokipu.core.crypto.FuenteAleatoria;
import dev.erick.cryptokipu.core.crypto.SecureBytes;
import dev.erick.cryptokipu.core.progreso.ReporteProgreso;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/** Llave de seguridad (F4) en cualquier medio: USB, teléfono, disco, Drive. Solo se usan sus bytes. */
public final class LlaveSeguridad {

    public static final String EXTENSION_MIJA = ".kipukey";

    private LlaveSeguridad() {
    }

    /** SHA-256 en streaming del archivo elegido como llave, con progreso y cancelación. */
    public static SecureBytes huella(Path archivoMija, ReporteProgreso progresoMija) throws IOException {
        long tamanoMija = Files.size(archivoMija);
        try (InputStream entradaMija = Files.newInputStream(archivoMija)) {
            return KeyfileDigest.huella(entradaMija, tamanoMija, progresoMija);
        }
    }

    /**
     * Huella (32 B) como arreglo simple, para adaptadores de entrada que no deben usar tipos de {@code core.crypto}.
     * Quien llama la borra con {@code Arrays.fill} (normalmente al cerrar las {@code Credenciales} que la reciben).
     */
    public static byte[] huellaBytes(Path archivoMija, ReporteProgreso progresoMija) throws IOException {
        try (SecureBytes huellaMija = huella(archivoMija, progresoMija)) {
            return huellaMija.bytes().clone();
        }
    }

    /** Genera una llave {@code .kipukey} con la aleatoriedad del sistema. */
    public static Path generar(Path destinoMija) throws IOException {
        return generar(destinoMija, FuenteAleatoria.delSistema());
    }

    /** Genera una llave {@code .kipukey} de 64 bytes aleatorios. No sobrescribe archivos existentes. */
    public static Path generar(Path destinoMija, FuenteAleatoria fuenteMija) throws IOException {
        byte[] llaveMija = KeyfileDigest.generarLlave(fuenteMija);
        try {
            Files.write(destinoMija, llaveMija, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                    StandardOpenOption.SYNC);
            return destinoMija;
        } finally {
            Arrays.fill(llaveMija, (byte) 0);
        }
    }
}
