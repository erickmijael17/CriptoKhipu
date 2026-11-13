package dev.erick.cryptokipu.ui.viewmodel;

import dev.erick.cryptokipu.app.casos.AbrirEnMemoria;
import dev.erick.cryptokipu.app.casos.AgregarArchivos;
import dev.erick.cryptokipu.app.casos.EliminarArchivo;
import dev.erick.cryptokipu.app.casos.ExtraerArchivo;
import dev.erick.cryptokipu.app.casos.PoliticaColision;
import dev.erick.cryptokipu.app.casos.SesionBoveda;
import dev.erick.cryptokipu.core.format.EntradaManifest;
import dev.erick.cryptokipu.core.registro.Registro;
import dev.erick.cryptokipu.app.puertos.VisorDocumentos;
import dev.erick.cryptokipu.infra.configuracion.Contenedor;
import dev.erick.cryptokipu.ui.medios.ServidorMedios;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.TreeItem;

/**
 * Explorador de la bóveda: árbol (TreeTableView virtualizado; probado con 100 000 entradas), filtro, agregar,
 * extraer, eliminar y vista previa en RAM de imágenes y texto. Las operaciones largas van fuera del hilo de JavaFX.
 */
public final class ExploradorViewModel {

    /** Fila del árbol: una carpeta ({@code entradaMija == null}) o un archivo. */
    public record Nodo(String nombreMija, String rutaMija, EntradaManifest entradaMija, long tamanoMija,
            long modificadoMija, String tipoMija) {
        public boolean esCarpeta() {
            return entradaMija == null;
        }
    }

    /** Resultado de la vista previa: siempre en memoria (o en el servidor local), nunca en disco. */
    public sealed interface VistaPrevia {
        record Imagen(byte[] bytesMija) implements VistaPrevia {
        }

        record Texto(String contenidoMija) implements VistaPrevia {
        }

        /** PDF, Word, Excel o PowerPoint, abierto por el módulo de visores (quien lo muestra lo cierra). */
        record Documento(VisorDocumentos.DocumentoAbierto documentoMija) implements VistaPrevia, AutoCloseable {
            @Override
            public void close() {
                documentoMija.close();
            }
        }

        /** Audio o video publicado en el servidor local; al cambiar de archivo se retira con el token. */
        record Medio(String urlMija, String tokenMija, boolean videoMija) implements VistaPrevia {
            @Override
            public String toString() {
                return "Medio[***]";
            }
        }

        /** Formato que JavaFX no reproduce o no muestra: solo "Abrir con aplicación externa". */
        record Externa(String claveMotivoMija) implements VistaPrevia {
        }
    }

    public static final long MAXIMO_IMAGEN_MIJA = 40L * 1024 * 1024;
    public static final long MAXIMO_TEXTO_MIJA = 10L * 1024 * 1024;
    private static final Set<String> IMAGENES_MIJA = Set.of("png", "jpg", "jpeg", "gif", "bmp");
    private static final Set<String> TEXTOS_MIJA = Set.of("txt", "md", "csv", "json", "xml", "log", "java", "properties",
            "yml", "yaml", "html", "css", "js");
    /** Lo que {@code MediaPlayer} reproduce: MP4 (H.264 + AAC), M4A, MP3, WAV y AIFF. */
    private static final Map<String, String> MEDIOS_MIJA = Map.of("mp4", "video/mp4", "m4v", "video/mp4",
            "m4a", "audio/mp4", "mp3", "audio/mpeg", "wav", "audio/wav", "aif", "audio/aiff", "aiff", "audio/aiff");
    /** Lo que JavaFX no reproduce: MKV, AVI, WebM, FLAC, MOV, WMV, Ogg (HEVC en MP4 se detecta al reproducir). */
    private static final Set<String> SOLO_EXTERNOS_MIJA = Set.of("mkv", "avi", "webm", "flac", "mov", "wmv", "ogg",
            "opus", "3gp", "hevc", "doc", "xls", "ppt");

    private static final System.Logger REGISTRO_MIJA = Registro.para(ExploradorViewModel.class);
    private static final Set<String> DOCUMENTOS_MIJA = Set.of("pdf", "docx", "xlsx", "pptx");

    private final Contenedor appMija;
    private final List<VisorDocumentos> visoresMija;
    private final StringProperty filtroMija = new SimpleStringProperty("");

    public ExploradorViewModel(Contenedor appMija) {
        this(appMija, List.of());
    }

    /** @param visoresMija proveedores de {@link VisorDocumentos} encontrados (vacío si el módulo no está) */
    public ExploradorViewModel(Contenedor appMija, List<VisorDocumentos> visoresMija) {
        this.appMija = Objects.requireNonNull(appMija, "appMija");
        this.visoresMija = List.copyOf(visoresMija);
    }

    public boolean hayVisoresDeDocumentos() {
        return !visoresMija.isEmpty();
    }

    /** Árbol a partir del manifest, solo con las entradas que contienen el filtro (sin distinguir mayúsculas). */
    public TreeItem<Nodo> arbol(List<EntradaManifest> entradasMija) {
        String filtroMinusculasMija = filtroMija.get() == null ? "" : filtroMija.get().toLowerCase(Locale.ROOT);
        TreeItem<Nodo> raizMija = new TreeItem<>(new Nodo("", "", null, 0, 0, ""));
        raizMija.setExpanded(true);
        Map<String, TreeItem<Nodo>> carpetasMija = new HashMap<>();
        carpetasMija.put("", raizMija);
        for (EntradaManifest entradaMija : entradasMija) {
            String rutaMija = entradaMija.rutaMija();
            if (!filtroMinusculasMija.isEmpty() && !rutaMija.toLowerCase(Locale.ROOT).contains(filtroMinusculasMija)) {
                continue;
            }
            int barraMija = rutaMija.lastIndexOf('/');
            TreeItem<Nodo> padreMija = carpeta(carpetasMija, barraMija < 0 ? "" : rutaMija.substring(0, barraMija));
            padreMija.getChildren().add(new TreeItem<>(new Nodo(rutaMija.substring(barraMija + 1), rutaMija, entradaMija,
                    entradaMija.tamanoMija(), entradaMija.modificadoMija(), entradaMija.tipoMimeMija())));
        }
        return raizMija;
    }

    private static TreeItem<Nodo> carpeta(Map<String, TreeItem<Nodo>> carpetasMija, String rutaMija) {
        TreeItem<Nodo> existenteMija = carpetasMija.get(rutaMija);
        if (existenteMija != null) {
            return existenteMija;
        }
        int barraMija = rutaMija.lastIndexOf('/');
        TreeItem<Nodo> padreMija = carpeta(carpetasMija, barraMija < 0 ? "" : rutaMija.substring(0, barraMija));
        TreeItem<Nodo> nuevaMija = new TreeItem<>(new Nodo(rutaMija.substring(barraMija + 1), rutaMija, null, 0, 0, ""));
        padreMija.getChildren().add(nuevaMija);
        carpetasMija.put(rutaMija, nuevaMija);
        return nuevaMija;
    }

    /** Todas las entradas bajo un nodo (un archivo, o todo lo que hay dentro de una carpeta). */
    public static List<EntradaManifest> entradasBajo(TreeItem<Nodo> nodoMija) {
        if (!nodoMija.getValue().esCarpeta()) {
            return List.of(nodoMija.getValue().entradaMija());
        }
        return nodoMija.getChildren().stream().flatMap(hijoMija -> entradasBajo(hijoMija).stream()).toList();
    }

    // ------------------------------------------------------------------ operaciones (fuera del hilo de JavaFX)

    public AgregarArchivos.Resultado agregar(SesionBoveda sesionMija, List<Path> origenesMija,
            PoliticaColision politicaMija, ProgresoOperacion progresoMija) throws IOException {
        HiloFx.exigirFuera();
        progresoMija.etapa("etapa.cifrando", true);
        return appMija.agregarArchivos().ejecutar(new AgregarArchivos.Comando(sesionMija, origenesMija, "", politicaMija,
                progresoMija));
    }

    public ExtraerArchivo.Resultado extraer(SesionBoveda sesionMija, List<EntradaManifest> entradasMija, Path destinoMija,
            PoliticaColision politicaMija, ProgresoOperacion progresoMija) throws IOException {
        HiloFx.exigirFuera();
        progresoMija.etapa("etapa.extrayendo", true);
        return appMija.extraerArchivo().ejecutar(new ExtraerArchivo.Comando(sesionMija,
                entradasMija.stream().map(EntradaManifest::fileIdMija).toList(), destinoMija, politicaMija, progresoMija));
    }

    public void eliminar(SesionBoveda sesionMija, List<EntradaManifest> entradasMija, boolean compactarMija,
            ProgresoOperacion progresoMija) throws IOException {
        HiloFx.exigirFuera();
        progresoMija.etapa(compactarMija ? "etapa.compactando" : "etapa.eliminando", false);
        for (int indiceMija = 0; indiceMija < entradasMija.size(); indiceMija++) {
            boolean ultimoMija = indiceMija == entradasMija.size() - 1;
            appMija.eliminarArchivo().ejecutar(new EliminarArchivo.Comando(sesionMija,
                    entradasMija.get(indiceMija).fileIdMija(), compactarMija && ultimoMija));
        }
    }

    /**
     * Vista previa en RAM. Imágenes: se leen las dimensiones de la cabecera antes de decodificar (máximo
     * {@link DimensionesImagen#PIXELES_MAXIMOS_MIJA}). Texto: hasta 10 MB. PDF y Office: módulo de visores. Audio y
     * video: servidor local. El resto: solo con aplicación externa.
     */
    public VistaPrevia vistaPrevia(SesionBoveda sesionMija, EntradaManifest entradaMija,
            FuenteServidor servidorMija) throws IOException {
        HiloFx.exigirFuera();
        String extensionMija = extension(entradaMija.rutaMija());
        String mimeMija = entradaMija.tipoMimeMija();
        if (MEDIOS_MIJA.containsKey(extensionMija)) {
            ServidorMedios mediosMija = servidorMija.obtener();
            SeekableByteChannel canalMija = abrir(sesionMija, entradaMija);
            ServidorMedios.Publicacion publicadaMija = mediosMija.publicar(canalMija, entradaMija.tamanoMija(),
                    MEDIOS_MIJA.get(extensionMija), "archivo." + extensionMija);
            return new VistaPrevia.Medio(publicadaMija.urlMija(), publicadaMija.tokenMija(),
                    MEDIOS_MIJA.get(extensionMija).startsWith("video/"));
        }
        Optional<VisorDocumentos> visorMija = visoresMija.stream()
                .filter(candidatoMija -> candidatoMija.soporta(extensionMija)).findFirst();
        if (visorMija.isPresent()) {
            if (entradaMija.tamanoMija() > VisorDocumentos.TAMANO_MAXIMO_MIJA) {
                return new VistaPrevia.Externa("previa.demasiadoGrande");
            }
            try (SeekableByteChannel canalMija = abrir(sesionMija, entradaMija)) {
                return new VistaPrevia.Documento(visorMija.get().abrir(extensionMija, canalMija,
                        entradaMija.tamanoMija()));
            } catch (IOException | RuntimeException errorMija) {
                // Documento dañado, protegido o que supera los límites de POI/PDFBox: se ofrece la app externa.
                REGISTRO_MIJA.log(System.Logger.Level.DEBUG, "No se pudo abrir el documento: {0}",
                        errorMija.getClass().getSimpleName());
                return new VistaPrevia.Externa("previa.documentoIlegible");
            }
        }
        if (DOCUMENTOS_MIJA.contains(extensionMija)) {
            return new VistaPrevia.Externa("previa.sinVisores");
        }
        boolean imagenMija = IMAGENES_MIJA.contains(extensionMija) || mimeMija.startsWith("image/");
        boolean textoMija = TEXTOS_MIJA.contains(extensionMija) || mimeMija.startsWith("text/");
        if (!imagenMija && !textoMija) {
            return new VistaPrevia.Externa(SOLO_EXTERNOS_MIJA.contains(extensionMija) ? "previa.formatoExterno"
                    : "previa.noSoportada");
        }
        long maximoMija = imagenMija ? MAXIMO_IMAGEN_MIJA : MAXIMO_TEXTO_MIJA;
        if (entradaMija.tamanoMija() > maximoMija) {
            return new VistaPrevia.Externa("previa.demasiadoGrande");
        }
        byte[] contenidoMija = new byte[(int) entradaMija.tamanoMija()];
        try (SeekableByteChannel canalMija = abrir(sesionMija, entradaMija)) {
            ByteBuffer bufferMija = ByteBuffer.wrap(contenidoMija);
            while (bufferMija.hasRemaining() && canalMija.read(bufferMija) > 0) {
                // leer completo en memoria
            }
        }
        if (!imagenMija) {
            return new VistaPrevia.Texto(new String(contenidoMija, StandardCharsets.UTF_8));
        }
        Optional<DimensionesImagen.Dimensiones> dimensionesMija = DimensionesImagen.leer(contenidoMija);
        if (dimensionesMija.isEmpty()) {
            return new VistaPrevia.Externa("previa.imagenIlegible");
        }
        if (dimensionesMija.get().pixeles() > DimensionesImagen.PIXELES_MAXIMOS_MIJA) {
            return new VistaPrevia.Externa("previa.imagenEnorme");
        }
        return new VistaPrevia.Imagen(contenidoMija);
    }

    /** Servidor de medios de la sesión (se crea al primer uso). */
    @FunctionalInterface
    public interface FuenteServidor {
        ServidorMedios obtener() throws IOException;
    }

    private SeekableByteChannel abrir(SesionBoveda sesionMija, EntradaManifest entradaMija) throws IOException {
        return appMija.abrirEnMemoria().ejecutar(new AbrirEnMemoria.Comando(sesionMija, entradaMija.fileIdMija()));
    }

    /**
     * Copia el archivo a la carpeta temporal privada de la sesión para abrirlo con otra aplicación (la vista
     * advierte antes). Se borra al cerrar la sesión.
     */
    public Path copiaParaAplicacionExterna(SesionBoveda sesionMija, EntradaManifest entradaMija, Path carpetaMija,
            ProgresoOperacion progresoMija) throws IOException {
        HiloFx.exigirFuera();
        progresoMija.etapa("etapa.extrayendo", true);
        ExtraerArchivo.Resultado resultadoMija = appMija.extraerArchivo().ejecutar(new ExtraerArchivo.Comando(
                sesionMija, List.of(entradaMija.fileIdMija()), carpetaMija, PoliticaColision.RENOMBRAR, progresoMija));
        if (resultadoMija.extraidosMija().isEmpty()) {
            throw new IOException("No se pudo preparar la copia temporal");
        }
        return resultadoMija.extraidosMija().getFirst().destinoMija();
    }

    private static String extension(String rutaMija) {
        int puntoMija = rutaMija.lastIndexOf('.');
        return puntoMija < 0 ? "" : rutaMija.substring(puntoMija + 1).toLowerCase(Locale.ROOT);
    }

    public StringProperty filtroProperty() {
        return filtroMija;
    }
}
