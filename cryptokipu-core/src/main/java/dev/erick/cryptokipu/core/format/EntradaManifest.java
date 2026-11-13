package dev.erick.cryptokipu.core.format;

import dev.erick.cryptokipu.core.crypto.HkdfDeriver;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Un archivo dentro de la bóveda. Todo esto viaja cifrado en el manifest.
 *
 * @param fileIdMija           16 B aleatorios; sal de {@code K_file}
 * @param rutaMija             ruta interna (separador {@code /})
 * @param tamanoMija           tamaño original en bytes
 * @param modificadoMija       fecha de modificación original (ms desde la época)
 * @param tipoMimeMija         tipo MIME detectado
 * @param offsetMija           posición del primer bloque cifrado dentro del .kipu
 * @param longitudCifradaMija  bytes cifrados ocupados (bloques + tags)
 */
public record EntradaManifest(byte[] fileIdMija, String rutaMija, long tamanoMija, long modificadoMija,
        String tipoMimeMija, long offsetMija, long longitudCifradaMija) {

    public EntradaManifest {
        fileIdMija = Objects.requireNonNull(fileIdMija, "fileIdMija").clone();
        if (fileIdMija.length != HkdfDeriver.LONGITUD_FILE_ID_MIJA) {
            throw new IllegalArgumentException("fileIdMija debe medir 16 B");
        }
        Objects.requireNonNull(rutaMija, "rutaMija");
        Objects.requireNonNull(tipoMimeMija, "tipoMimeMija");
        int bytesRutaMija = rutaMija.getBytes(StandardCharsets.UTF_8).length;
        if (rutaMija.isBlank() || bytesRutaMija > LimitesFormato.RUTA_MAX_BYTES_MIJA) {
            throw new IllegalArgumentException("Ruta interna vacía o demasiado larga");
        }
        if (tipoMimeMija.getBytes(StandardCharsets.UTF_8).length > LimitesFormato.MIME_MAX_BYTES_MIJA) {
            throw new IllegalArgumentException("Tipo MIME demasiado largo");
        }
        if (tamanoMija < 0 || offsetMija < LimitesFormato.OFFSET_DATOS_MIJA || longitudCifradaMija < 0) {
            throw new IllegalArgumentException("Tamaño u offset inválido");
        }
    }

    @Override
    public byte[] fileIdMija() {
        return fileIdMija.clone();
    }

    @Override
    public boolean equals(Object otroMija) {
        return otroMija instanceof EntradaManifest otraMija && Arrays.equals(fileIdMija, otraMija.fileIdMija)
                && rutaMija.equals(otraMija.rutaMija) && tamanoMija == otraMija.tamanoMija
                && modificadoMija == otraMija.modificadoMija && tipoMimeMija.equals(otraMija.tipoMimeMija)
                && offsetMija == otraMija.offsetMija && longitudCifradaMija == otraMija.longitudCifradaMija;
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(fileIdMija), rutaMija, tamanoMija, modificadoMija, tipoMimeMija,
                offsetMija, longitudCifradaMija);
    }

    /** No incluye la ruta: el nombre del archivo es dato sensible (política de registro). */
    @Override
    public String toString() {
        return "EntradaManifest[tamano=" + tamanoMija + "]";
    }

    public EntradaManifest conOffset(long nuevoOffsetMija) {
        return new EntradaManifest(fileIdMija, rutaMija, tamanoMija, modificadoMija, tipoMimeMija, nuevoOffsetMija,
                longitudCifradaMija);
    }
}
