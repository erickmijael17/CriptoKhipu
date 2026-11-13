package dev.erick.cryptokipu.app.casos;

import dev.erick.cryptokipu.app.puertos.ContenedorAbierto;
import dev.erick.cryptokipu.app.puertos.Origen;
import dev.erick.cryptokipu.core.format.EntradaManifest;
import dev.erick.cryptokipu.core.progreso.ReporteProgreso;
import dev.erick.cryptokipu.core.vault.RutaInterna;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Agrega archivos y carpetas (recursivo) en un solo commit.
 *
 * <ul>
 *   <li>No sigue enlaces simbólicos (evita bucles y escapes de carpeta): se reportan como omitidos.</li>
 *   <li>Rutas internas relativas, con {@code /} y en NFC; nombres no portables se omiten.</li>
 *   <li>Colisiones (sin distinguir mayúsculas) según {@link PoliticaColision}.</li>
 *   <li>Un archivo que cambia de tamaño durante la lectura se descarta.</li>
 *   <li>Cancelar a mitad no escribe el footer: la bóveda queda en el estado anterior.</li>
 * </ul>
 */
public final class AgregarArchivos {

    public AgregarArchivos() {
        // Sin dependencias: opera sobre la sesión del comando.
    }

    public enum Motivo { ENLACE_SIMBOLICO, NO_ES_ARCHIVO_REGULAR, NOMBRE_NO_PORTABLE, YA_EXISTE,
        CAMBIO_DURANTE_LECTURA, ILEGIBLE }

    public record Omitido(Path origenMija, Motivo motivoMija) {
    }

    /**
     * @param origenesMija      archivos o carpetas locales
     * @param carpetaInternaMija carpeta de destino dentro de la bóveda ("" = raíz)
     */
    public record Comando(SesionBoveda sesionMija, List<Path> origenesMija, String carpetaInternaMija,
            PoliticaColision politicaMija, ReporteProgreso progresoMija) {
        public Comando {
            Objects.requireNonNull(sesionMija, "sesionMija");
            origenesMija = List.copyOf(origenesMija);
            Objects.requireNonNull(carpetaInternaMija, "carpetaInternaMija");
            Objects.requireNonNull(politicaMija, "politicaMija");
            Objects.requireNonNull(progresoMija, "progresoMija");
        }
    }

    public record Resultado(List<EntradaManifest> agregadosMija, List<Omitido> omitidosMija) {
    }

    public Resultado ejecutar(Comando comandoMija) throws IOException {
        ContenedorAbierto contenedorMija = comandoMija.sesionMija().contenedor();
        List<Omitido> omitidosMija = new ArrayList<>();
        Map<Path, String> candidatosMija = new LinkedHashMap<>();
        for (Path origenMija : comandoMija.origenesMija()) {
            recolectar(origenMija, comandoMija.carpetaInternaMija(), candidatosMija, omitidosMija);
        }

        Map<String, EntradaManifest> existentesMija = new HashMap<>();
        contenedorMija.manifest().entradasMija()
                .forEach(entradaMija -> existentesMija.put(RutaInterna.claveColision(entradaMija.rutaMija()), entradaMija));
        Map<String, Origen> loteMija = new LinkedHashMap<>();
        List<byte[]> quitarMija = new ArrayList<>();
        for (Map.Entry<Path, String> candidatoMija : candidatosMija.entrySet()) {
            String rutaMija = candidatoMija.getValue();
            String claveMija = RutaInterna.claveColision(rutaMija);
            boolean chocaMija = existentesMija.containsKey(claveMija) || loteMija.containsKey(claveMija);
            if (chocaMija) {
                switch (comandoMija.politicaMija()) {
                    case OMITIR -> {
                        omitidosMija.add(new Omitido(candidatoMija.getKey(), Motivo.YA_EXISTE));
                        continue;
                    }
                    case REEMPLAZAR -> {
                        EntradaManifest viejaMija = existentesMija.remove(claveMija);
                        if (viejaMija != null) {
                            quitarMija.add(viejaMija.fileIdMija());
                        }
                        loteMija.remove(claveMija);
                    }
                    case RENOMBRAR -> {
                        rutaMija = nombreLibre(rutaMija, existentesMija.keySet(), loteMija.keySet());
                        claveMija = RutaInterna.claveColision(rutaMija);
                    }
                }
            }
            loteMija.put(claveMija, new Origen(candidatoMija.getKey(), rutaMija));
        }

        ContenedorAbierto.ResultadoAgregado resultadoMija = contenedorMija.agregar(
                List.copyOf(loteMija.values()), quitarMija, comandoMija.progresoMija());
        resultadoMija.cambiaronMija()
                .forEach(origenMija -> omitidosMija.add(new Omitido(origenMija.archivoMija(), Motivo.CAMBIO_DURANTE_LECTURA)));
        return new Resultado(resultadoMija.agregadasMija(), List.copyOf(omitidosMija));
    }

    private static void recolectar(Path origenMija, String carpetaInternaMija, Map<Path, String> candidatosMija,
            List<Omitido> omitidosMija) throws IOException {
        if (Files.isSymbolicLink(origenMija)) {
            omitidosMija.add(new Omitido(origenMija, Motivo.ENLACE_SIMBOLICO));
            return;
        }
        Path baseMija = origenMija.toAbsolutePath().getParent();
        if (!Files.isDirectory(origenMija, LinkOption.NOFOLLOW_LINKS)) {
            agregarCandidato(origenMija, baseMija, carpetaInternaMija, candidatosMija, omitidosMija);
            return;
        }
        Files.walkFileTree(origenMija, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path archivoMija, BasicFileAttributes atributosMija) {
                        if (atributosMija.isSymbolicLink()) {
                            omitidosMija.add(new Omitido(archivoMija, Motivo.ENLACE_SIMBOLICO));
                        } else {
                            agregarCandidato(archivoMija, baseMija, carpetaInternaMija, candidatosMija, omitidosMija);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path archivoMija, IOException errorMija) {
                        omitidosMija.add(new Omitido(archivoMija, Motivo.ILEGIBLE));
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    private static void agregarCandidato(Path archivoMija, Path baseMija, String carpetaInternaMija,
            Map<Path, String> candidatosMija, List<Omitido> omitidosMija) {
        if (!Files.isRegularFile(archivoMija, LinkOption.NOFOLLOW_LINKS)) {
            omitidosMija.add(new Omitido(archivoMija, Motivo.NO_ES_ARCHIVO_REGULAR));
            return;
        }
        List<String> segmentosMija = new ArrayList<>();
        if (!carpetaInternaMija.isEmpty()) {
            segmentosMija.addAll(List.of(carpetaInternaMija.split("/")));
        }
        baseMija.relativize(archivoMija.toAbsolutePath())
                .forEach(segmentoMija -> segmentosMija.add(segmentoMija.toString()));
        String rutaMija = RutaInterna.unir(segmentosMija);
        if (!RutaInterna.esSegura(rutaMija)) {
            omitidosMija.add(new Omitido(archivoMija, Motivo.NOMBRE_NO_PORTABLE));
            return;
        }
        candidatosMija.put(archivoMija, rutaMija);
    }

    private static String nombreLibre(String rutaMija, Set<String> existentesMija, Set<String> loteMija) {
        Set<String> ocupadosMija = new HashSet<>(existentesMija);
        ocupadosMija.addAll(loteMija);
        int barraMija = rutaMija.lastIndexOf('/');
        String carpetaMija = rutaMija.substring(0, barraMija + 1);
        String nombreMija = rutaMija.substring(barraMija + 1);
        int puntoMija = nombreMija.lastIndexOf('.');
        String baseMija = puntoMija > 0 ? nombreMija.substring(0, puntoMija) : nombreMija;
        String extensionMija = puntoMija > 0 ? nombreMija.substring(puntoMija) : "";
        for (int numeroMija = 2; ; numeroMija++) {
            String candidatoMija = carpetaMija + baseMija + " (" + numeroMija + ")" + extensionMija;
            if (!ocupadosMija.contains(RutaInterna.claveColision(candidatoMija))) {
                return candidatoMija;
            }
        }
    }
}
