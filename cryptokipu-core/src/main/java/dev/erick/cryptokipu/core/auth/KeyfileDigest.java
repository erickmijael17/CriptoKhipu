package dev.erick.cryptokipu.core.auth;

import dev.erick.cryptokipu.core.crypto.FuenteAleatoria;
import dev.erick.cryptokipu.core.crypto.SecureBytes;
import dev.erick.cryptokipu.core.progreso.ReporteProgreso;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

/**
 * F4: SHA-256 de los bytes exactos de la llave de seguridad, calculado en streaming con un buffer
 * de 1 MiB (una llave puede ser un video de varios GB). Abrir el archivo es tarea de {@code infra}.
 */
public final class KeyfileDigest {

    private static final int BUFFER_MIJA = 1 << 20;

    private KeyfileDigest() {
    }

    /**
     * @param totalMija tamaño esperado (para el progreso), o -1 si no se conoce
     * @throws dev.erick.cryptokipu.core.error.ExcepcionBoveda {@code OperacionCancelada} si el usuario cancela
     */
    public static SecureBytes huella(InputStream entradaMija, long totalMija, ReporteProgreso progresoMija)
            throws IOException {
        Objects.requireNonNull(entradaMija, "entradaMija");
        Objects.requireNonNull(progresoMija, "progresoMija");
        MessageDigest resumenMija;
        try {
            resumenMija = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException errorMija) {
            throw new IllegalStateException("SHA-256 no disponible", errorMija);
        }
        byte[] bufferMija = new byte[BUFFER_MIJA];
        long leidosTotalMija = 0;
        try {
            int leidosMija;
            while ((leidosMija = entradaMija.read(bufferMija)) != -1) {
                progresoMija.verificarCancelacion();
                resumenMija.update(bufferMija, 0, leidosMija);
                leidosTotalMija += leidosMija;
                progresoMija.avanzar(leidosTotalMija, totalMija);
            }
            return SecureBytes.envolver(resumenMija.digest());
        } finally {
            Arrays.fill(bufferMija, (byte) 0);
        }
    }

    /** Contenido de una llave {@code .kipukey}: 64 bytes aleatorios. Quien llama los borra tras escribirlos. */
    public static byte[] generarLlave(FuenteAleatoria fuenteMija) {
        return fuenteMija.bytes(AuthPolicy.LONGITUD_LLAVE_GENERADA_MIJA);
    }
}
