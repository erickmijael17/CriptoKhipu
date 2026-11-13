package dev.erick.cryptokipu.core.format;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Índice cifrado de la bóveda.
 *
 * @param generacionMija igual a la generación del footer que lo apunta
 * @param entradasMija   archivos vivos
 */
public record Manifest(long generacionMija, List<EntradaManifest> entradasMija) {

    public Manifest {
        entradasMija = List.copyOf(Objects.requireNonNull(entradasMija, "entradasMija"));
        if (generacionMija < 1) {
            throw new IllegalArgumentException("generacionMija debe ser >= 1");
        }
        if (entradasMija.size() > LimitesFormato.MANIFEST_MAX_ENTRADAS_MIJA) {
            throw new IllegalArgumentException("Demasiadas entradas");
        }
    }

    public static Manifest vacio() {
        return new Manifest(1, List.of());
    }

    public Optional<EntradaManifest> buscar(byte[] fileIdMija) {
        return entradasMija.stream().filter(entradaMija -> Arrays.equals(entradaMija.fileIdMija(), fileIdMija))
                .findFirst();
    }

    /** Nueva versión (generación + 1) con entradas agregadas. */
    public Manifest conAgregadas(List<EntradaManifest> nuevasMija) {
        List<EntradaManifest> todasMija = new ArrayList<>(entradasMija);
        todasMija.addAll(nuevasMija);
        return new Manifest(generacionMija + 1, todasMija);
    }

    /** Nueva versión (generación + 1) sin la entrada indicada. */
    public Manifest sinEntrada(byte[] fileIdMija) {
        return new Manifest(generacionMija + 1, entradasMija.stream()
                .filter(entradaMija -> !Arrays.equals(entradaMija.fileIdMija(), fileIdMija)).toList());
    }

    /** Nueva versión (generación + 1): quita las entradas indicadas y agrega las nuevas, en un solo commit. */
    public Manifest conCambios(List<byte[]> quitarMija, List<EntradaManifest> agregarMija) {
        List<EntradaManifest> todasMija = new ArrayList<>(entradasMija.stream()
                .filter(entradaMija -> quitarMija.stream()
                        .noneMatch(idMija -> Arrays.equals(idMija, entradaMija.fileIdMija())))
                .toList());
        todasMija.addAll(agregarMija);
        return new Manifest(generacionMija + 1, todasMija);
    }

    /** Nueva versión (generación + 1) con otras entradas (compactación). */
    public Manifest conEntradas(List<EntradaManifest> otrasMija) {
        return new Manifest(generacionMija + 1, otrasMija);
    }
}
