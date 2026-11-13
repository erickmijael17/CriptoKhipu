package dev.erick.cryptokipu.app.casos;

import dev.erick.cryptokipu.app.puertos.ContenedorAbierto;
import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import dev.erick.cryptokipu.core.format.EntradaManifest;
import dev.erick.cryptokipu.core.progreso.ReporteProgreso;
import dev.erick.cryptokipu.core.vault.RutaInterna;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Extrae archivos al disco con protección "zip-slip".
 *
 * <ul>
 *   <li>Cada ruta interna se valida <b>al extraer</b> ({@link RutaInterna#problema}) aunque el manifest esté
 *       autenticado: la bóveda pudo crearla otra persona. Además se comprueba que el destino final quede
 *       dentro de la carpeta elegida.</li>
 *   <li>Se escribe en {@code nombre.part} y se renombra solo si <b>todos</b> los bloques se verificaron; si algo
 *       falla, se borra el {@code .part}.</li>
 *   <li>Nunca sobrescribe sin {@link PoliticaColision#REEMPLAZAR}. En Windows y macOS detecta colisiones que solo
 *       difieren en mayúsculas.</li>
 *   <li>Restaura la fecha de modificación; no los permisos.</li>
 * </ul>
 */
public final class ExtraerArchivo {

    public ExtraerArchivo() {
        // Sin dependencias: opera sobre la sesión del comando.
    }

    public enum Motivo { RUTA_PELIGROSA, YA_EXISTE, INTEGRIDAD }

    public record Extraido(String rutaInternaMija, Path destinoMija) {
    }

    public record Rechazado(String rutaInternaMija, Motivo motivoMija) {
    }

    /** @param fileIdsMija archivos a extraer; vacío = todos */
    public record Comando(SesionBoveda sesionMija, List<byte[]> fileIdsMija, Path destinoMija,
            PoliticaColision politicaMija, ReporteProgreso progresoMija) {
        public Comando {
            Objects.requireNonNull(sesionMija, "sesionMija");
            fileIdsMija = List.copyOf(fileIdsMija);
            Objects.requireNonNull(destinoMija, "destinoMija");
            Objects.requireNonNull(politicaMija, "politicaMija");
            Objects.requireNonNull(progresoMija, "progresoMija");
        }
    }

    public record Resultado(List<Extraido> extraidosMija, List<Rechazado> rechazadosMija) {
    }

    private static final boolean SIN_MAYUSCULAS_MIJA = sistemaSinMayusculas();

    public Resultado ejecutar(Comando comandoMija) throws IOException {
        ContenedorAbierto contenedorMija = comandoMija.sesionMija().contenedor();
        Path raizMija = comandoMija.destinoMija().toAbsolutePath().normalize();
        Files.createDirectories(raizMija);
        List<EntradaManifest> entradasMija = seleccionar(contenedorMija, comandoMija.fileIdsMija());
        long totalMija = entradasMija.stream().mapToLong(EntradaManifest::tamanoMija).sum();
        long[] hechoMija = {0};

        List<Extraido> extraidosMija = new ArrayList<>();
        List<Rechazado> rechazadosMija = new ArrayList<>();
        Set<String> ocupadosMija = new HashSet<>();
        for (EntradaManifest entradaMija : entradasMija) {
            comandoMija.progresoMija().verificarCancelacion();
            String rutaMija = entradaMija.rutaMija();
            if (RutaInterna.problema(rutaMija).isPresent()) {
                rechazadosMija.add(new Rechazado(rutaMija, Motivo.RUTA_PELIGROSA));
                continue;
            }
            Path finalMija = resolverDentro(raizMija, rutaMija);
            if (finalMija == null) {
                rechazadosMija.add(new Rechazado(rutaMija, Motivo.RUTA_PELIGROSA));
                continue;
            }
            boolean reemplazarMija = false;
            if (ocupado(finalMija, ocupadosMija)) {
                switch (comandoMija.politicaMija()) {
                    case OMITIR -> {
                        rechazadosMija.add(new Rechazado(rutaMija, Motivo.YA_EXISTE));
                        continue;
                    }
                    case RENOMBRAR -> finalMija = nombreLibre(finalMija, ocupadosMija);
                    case REEMPLAZAR -> reemplazarMija = true;
                }
            }
            ocupadosMija.add(clave(finalMija));
            Files.createDirectories(finalMija.getParent());
            Path parcialMija = crearParcial(finalMija);
            try (OutputStream salidaMija = new Contador(Files.newOutputStream(parcialMija,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING), comandoMija.progresoMija(),
                    hechoMija, totalMija)) {
                contenedorMija.extraer(entradaMija.fileIdMija(), salidaMija);
            } catch (ExcepcionBoveda errorMija) {
                Files.deleteIfExists(parcialMija);
                if (errorMija.error() instanceof ErrorBoveda.IntegridadFallida) {
                    rechazadosMija.add(new Rechazado(rutaMija, Motivo.INTEGRIDAD));
                    continue;
                }
                throw errorMija;
            } catch (IOException | RuntimeException errorMija) {
                Files.deleteIfExists(parcialMija);
                throw errorMija;
            }
            if (reemplazarMija) {
                // Con ATOMIC_MOVE, Windows reemplaza el destino (MoveFileEx + MOVEFILE_REPLACE_EXISTING).
                Files.move(parcialMija, finalMija, StandardCopyOption.ATOMIC_MOVE);
            } else {
                // Sin ATOMIC_MOVE: si alguien creó el archivo entre la comprobación y el renombrado, falla en vez
                // de sobrescribirlo (en Windows, ATOMIC_MOVE siempre reemplaza).
                try {
                    Files.move(parcialMija, finalMija);
                } catch (FileAlreadyExistsException _) {
                    Files.deleteIfExists(parcialMija);
                    rechazadosMija.add(new Rechazado(rutaMija, Motivo.YA_EXISTE));
                    continue;
                }
            }
            Files.setLastModifiedTime(finalMija, FileTime.fromMillis(entradaMija.modificadoMija()));
            extraidosMija.add(new Extraido(rutaMija, finalMija));
        }
        return new Resultado(List.copyOf(extraidosMija), List.copyOf(rechazadosMija));
    }

    /** Ruta final dentro de {@code raizMija}, o {@code null} si escaparía de ella. */
    static Path resolverDentro(Path raizMija, String rutaMija) {
        Path actualMija = raizMija;
        for (String segmentoMija : rutaMija.split("/")) {
            actualMija = actualMija.resolve(segmentoMija);
        }
        Path normalizadaMija = actualMija.normalize();
        return normalizadaMija.startsWith(raizMija) && !normalizadaMija.equals(raizMija) ? normalizadaMija : null;
    }

    private static List<EntradaManifest> seleccionar(ContenedorAbierto contenedorMija, List<byte[]> fileIdsMija) {
        List<EntradaManifest> todasMija = contenedorMija.manifest().entradasMija();
        if (fileIdsMija.isEmpty()) {
            return todasMija;
        }
        return todasMija.stream().filter(entradaMija -> fileIdsMija.stream()
                .anyMatch(idMija -> Arrays.equals(idMija, entradaMija.fileIdMija()))).toList();
    }

    private static Path crearParcial(Path finalMija) throws IOException {
        Path parcialMija = finalMija.resolveSibling(finalMija.getFileName() + ".part");
        try {
            return Files.createFile(parcialMija);
        } catch (FileAlreadyExistsException _) {
            return Files.createTempFile(finalMija.getParent(), finalMija.getFileName() + ".", ".part");
        }
    }

    private static boolean ocupado(Path finalMija, Set<String> ocupadosMija) {
        return Files.exists(finalMija, LinkOption.NOFOLLOW_LINKS) || ocupadosMija.contains(clave(finalMija));
    }

    private static Path nombreLibre(Path finalMija, Set<String> ocupadosMija) {
        String nombreMija = finalMija.getFileName().toString();
        int puntoMija = nombreMija.lastIndexOf('.');
        String baseMija = puntoMija > 0 ? nombreMija.substring(0, puntoMija) : nombreMija;
        String extensionMija = puntoMija > 0 ? nombreMija.substring(puntoMija) : "";
        for (int numeroMija = 2; ; numeroMija++) {
            Path candidatoMija = finalMija.resolveSibling(baseMija + " (" + numeroMija + ")" + extensionMija);
            if (!ocupado(candidatoMija, ocupadosMija)) {
                return candidatoMija;
            }
        }
    }

    private static String clave(Path rutaMija) {
        String textoMija = RutaInterna.normalizar(rutaMija.toString());
        return SIN_MAYUSCULAS_MIJA ? textoMija.toLowerCase(Locale.ROOT) : textoMija;
    }

    private static boolean sistemaSinMayusculas() {
        String sistemaMija = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return sistemaMija.contains("win") || sistemaMija.contains("mac");
    }

    /** Cuenta bytes para el progreso y permite cancelar a mitad de un archivo. */
    private static final class Contador extends FilterOutputStream {
        private final ReporteProgreso progresoMija;
        private final long[] hechoMija;
        private final long totalMija;

        Contador(OutputStream destinoMija, ReporteProgreso progresoMija, long[] hechoMija, long totalMija) {
            super(destinoMija);
            this.progresoMija = progresoMija;
            this.hechoMija = hechoMija;
            this.totalMija = totalMija;
        }

        @Override
        public void write(byte[] datosMija, int desdeMija, int longitudMija) throws IOException {
            progresoMija.verificarCancelacion();
            out.write(datosMija, desdeMija, longitudMija);
            hechoMija[0] += longitudMija;
            progresoMija.avanzar(hechoMija[0], totalMija);
        }
    }
}
