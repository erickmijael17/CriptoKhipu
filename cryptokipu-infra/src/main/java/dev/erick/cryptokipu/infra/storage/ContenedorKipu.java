package dev.erick.cryptokipu.infra.storage;

import static dev.erick.cryptokipu.core.format.LimitesFormato.LIMITE_FAT32_MIJA;
import static dev.erick.cryptokipu.core.format.LimitesFormato.OFFSET_DATOS_MIJA;
import static dev.erick.cryptokipu.core.format.LimitesFormato.OFFSET_HEADER_PRIMARIO_MIJA;
import static dev.erick.cryptokipu.core.format.LimitesFormato.OFFSET_HEADER_RESPALDO_MIJA;
import static dev.erick.cryptokipu.core.format.LimitesFormato.TAMANO_HEADER_MIJA;

import dev.erick.cryptokipu.app.puertos.AbridorSlots;
import dev.erick.cryptokipu.app.puertos.ContenedorAbierto;
import dev.erick.cryptokipu.app.puertos.Origen;
import dev.erick.cryptokipu.core.progreso.ReporteProgreso;
import dev.erick.cryptokipu.core.crypto.ChunkCipher;
import dev.erick.cryptokipu.core.crypto.FuenteAleatoria;
import dev.erick.cryptokipu.core.crypto.HkdfDeriver;
import dev.erick.cryptokipu.core.crypto.ProveedorNonce;
import dev.erick.cryptokipu.core.crypto.SecureBytes;
import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.error.ErrorBoveda.IntegridadFallida.Zona;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import dev.erick.cryptokipu.core.format.EntradaManifest;
import dev.erick.cryptokipu.core.format.Footer;
import dev.erick.cryptokipu.core.format.Header;
import dev.erick.cryptokipu.core.format.HeaderCodec;
import dev.erick.cryptokipu.core.format.LimitesFormato;
import dev.erick.cryptokipu.core.format.Manifest;
import dev.erick.cryptokipu.core.format.ManifestCodec;
import dev.erick.cryptokipu.core.format.Slot;
import dev.erick.cryptokipu.core.registro.Registro;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contenedor {@code .kipu} en disco (plan, Fase 2).
 *
 * <p>Ciclo de vida: {@link #crear} o {@link #abrir} → {@link #desbloquear} con la clave maestra →
 * operaciones → {@link #close()}. La bóveda queda bloqueada con {@code FileChannel.tryLock()} mientras
 * está abierta.
 *
 * <p>Escritura segura de un commit: blobs → manifest → footer → {@code fsync}. Un corte antes del footer
 * deja válido el footer anterior; al abrir se busca el último footer válido y la siguiente escritura
 * trunca la basura. Los headers (primario y respaldo) se reescriben en su lugar: respaldo, {@code fsync},
 * primario, {@code fsync}.
 *
 * <p><b>Canal único (ADR-010):</b> todo acceso al {@code .kipu} abierto pasa por un solo
 * {@link FileChannel} con lecturas y escrituras posicionales ({@code read/write(buffer, posición)}),
 * que son seguras entre hilos. En Windows el bloqueo es obligatorio y ni siquiera otro descriptor del
 * mismo proceso puede leer el archivo, así que el visor y el servidor de medios (Fase 7) leen siempre a
 * través de este contenedor ({@link #extraer}, {@code Canales.Rango}), nunca reabriendo la ruta.
 */
public final class ContenedorKipu implements ContenedorAbierto {

    private static final System.Logger REGISTRO_MIJA = Registro.para(ContenedorKipu.class);
    private static final int VENTANA_BUSQUEDA_MIJA = 1 << 20;
    private static final double MARGEN_ESPACIO_MIJA = 1.05;
    /** Estimación por entrada del manifest: fileId + longitudes + campos fijos (sin ruta ni MIME). */
    private static final int BYTES_FIJOS_ENTRADA_MIJA = 16 + 2 + 8 + 8 + 2 + 8 + 8;

    private final Path rutaMija;
    private final Opciones opcionesMija;
    private final ProveedorNonce nonceMija;
    private FileChannel canalMija;
    private FileLock bloqueoMija;
    private boolean soloLecturaMija;

    private List<Candidato> candidatosMija;
    private Candidato elegidoMija;
    private Footer footerMija;
    private long finValidoMija;

    private SecureBytes claveMaestraMija;
    private SecureBytes claveHeaderMija;
    private SecureBytes claveManifestMija;
    private Manifest manifestMija;
    private final Set<CanalDescifrado> lectoresMija = ConcurrentHashMap.newKeySet();
    private volatile boolean cerradoMija;

    /** Dependencias sustituibles en pruebas. */
    public record Opciones(FuenteAleatoria fuenteMija, PoliticaDestino destinoMija, Interruptor interruptorMija) {

        public Opciones {
            Objects.requireNonNull(fuenteMija, "fuenteMija");
            Objects.requireNonNull(destinoMija, "destinoMija");
            Objects.requireNonNull(interruptorMija, "interruptorMija");
        }

        public static Opciones porDefecto() {
            return new Opciones(FuenteAleatoria.delSistema(), PoliticaDestino.delSistema(), Interruptor.NINGUNO_MIJA);
        }
    }

    private record Candidato(Header headerMija, byte[] bloqueMija, boolean esPrimarioMija) {
    }

    private ContenedorKipu(Path rutaMija, Opciones opcionesMija) {
        this.rutaMija = rutaMija.toAbsolutePath();
        this.opcionesMija = opcionesMija;
        this.nonceMija = new ProveedorNonce(opcionesMija.fuenteMija());
    }

    // ------------------------------------------------------------------ creación y apertura

    /**
     * Crea una bóveda nueva. {@code headerMija} trae la zona A y los slots ya envueltos; aquí se calcula
     * el MAC y se escribe un manifest vacío. Devuelve el contenedor ya desbloqueado.
     */
    public static ContenedorKipu crear(Path rutaMija, Header headerMija, SecureBytes claveMaestraMija,
            Opciones opcionesMija) throws IOException {
        ContenedorKipu contenedorMija = new ContenedorKipu(rutaMija, opcionesMija);
        InfoDestino destinoMija = opcionesMija.destinoMija().describir(directorio(contenedorMija.rutaMija));
        long necesarioMija = OFFSET_DATOS_MIJA + 64 * 1024;
        if (destinoMija.espacioUsableMija() < necesarioMija) {
            throw new ExcepcionBoveda(new ErrorBoveda.EspacioInsuficiente(necesarioMija,
                    destinoMija.espacioUsableMija()));
        }
        contenedorMija.canalMija = FileChannel.open(contenedorMija.rutaMija, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.READ, StandardOpenOption.WRITE);
        try {
            contenedorMija.bloquear(false);
            contenedorMija.prepararClaves(claveMaestraMija);
            Header conMacMija = headerMija.conMac(HeaderCodec.calcularMac(headerMija, contenedorMija.claveHeaderMija));
            byte[] bloqueMija = HeaderCodec.codificar(conMacMija, opcionesMija.fuenteMija());
            Canales.escribir(contenedorMija.canalMija, OFFSET_HEADER_RESPALDO_MIJA, bloqueMija);
            Canales.escribir(contenedorMija.canalMija, OFFSET_HEADER_PRIMARIO_MIJA, bloqueMija);
            contenedorMija.canalMija.force(true);
            contenedorMija.elegidoMija = new Candidato(conMacMija, bloqueMija, true);
            contenedorMija.candidatosMija = List.of(contenedorMija.elegidoMija);
            contenedorMija.finValidoMija = OFFSET_DATOS_MIJA;
            contenedorMija.confirmar(Manifest.vacio(), OFFSET_DATOS_MIJA);
            return contenedorMija;
        } catch (IOException | RuntimeException errorMija) {
            contenedorMija.close();
            Files.deleteIfExists(contenedorMija.rutaMija);
            throw errorMija;
        }
    }

    /** Abre y bloquea la bóveda. Lee ambos headers y localiza el último footer válido; no necesita claves. */
    public static ContenedorKipu abrir(Path rutaMija, Opciones opcionesMija) throws IOException {
        ContenedorKipu contenedorMija = new ContenedorKipu(rutaMija, opcionesMija);
        exigirSinCompactacionEnCurso(contenedorMija.rutaMija);
        try {
            contenedorMija.canalMija = FileChannel.open(contenedorMija.rutaMija, StandardOpenOption.READ,
                    StandardOpenOption.WRITE);
        } catch (AccessDeniedException | UnsupportedOperationException _) {
            contenedorMija.canalMija = FileChannel.open(contenedorMija.rutaMija, StandardOpenOption.READ);
            contenedorMija.soloLecturaMija = true;
        }
        try {
            contenedorMija.bloquear(contenedorMija.soloLecturaMija);
            contenedorMija.leerHeaders();
            contenedorMija.localizarFooter();
            return contenedorMija;
        } catch (IOException | RuntimeException errorMija) {
            contenedorMija.close();
            throw errorMija;
        }
    }

    /** Desbloqueo con una clave maestra ya conocida (pruebas y migraciones). */
    public void desbloquear(SecureBytes claveMaestraEntradaMija) throws IOException {
        desbloquear(_ -> Optional.of(copiaSegura(claveMaestraEntradaMija)));
    }

    /**
     * Prueba los headers candidatos en orden (mayor generación con CRC válido primero). Para cada uno,
     * abre sus slots con {@code abridorMija} y verifica el MAC con la clave obtenida. La primera copia
     * que pasa el MAC queda elegida y <b>solo entonces</b> se repara la otra. Antes de este punto no se
     * escribe nada en las zonas A ni B de ninguna copia.
     *
     * @throws ExcepcionBoveda {@code CredencialesIncorrectas} si ningún slot abre;
     *                         {@code IntegridadFallida(HEADER)} si algún slot abrió pero ningún MAC es válido
     */
    @Override
    public void desbloquear(AbridorSlots abridorMija) throws IOException {
        Candidato validoMija = null;
        boolean algunSlotAbrioMija = false;
        for (Candidato candidatoMija : candidatosMija) {
            Optional<SecureBytes> maestraMija = abridorMija.abrir(candidatoMija.headerMija());
            if (maestraMija.isEmpty()) {
                continue;
            }
            algunSlotAbrioMija = true;
            try (SecureBytes claveMija = maestraMija.get()) {
                prepararClaves(claveMija);
            }
            if (HeaderCodec.macValido(candidatoMija.headerMija(), claveHeaderMija)) {
                validoMija = candidatoMija;
                break;
            }
            cerrarClaves();
        }
        if (validoMija == null) {
            cerrarClaves();
            throw new ExcepcionBoveda(algunSlotAbrioMija
                    ? new ErrorBoveda.IntegridadFallida(Zona.HEADER)
                    : new ErrorBoveda.CredencialesIncorrectas());
        }
        elegidoMija = validoMija;
        repararHeaders();
        byte[] almacenadoMija = Canales.leer(canalMija, footerMija.offsetManifestMija(),
                (int) footerMija.longitudManifestMija(), Zona.MANIFEST);
        Manifest leidoMija = ManifestCodec.descifrar(almacenadoMija, claveManifestMija,
                elegidoMija.headerMija().zonaAMija().vaultIdMija(), footerMija.generacionMija());
        for (EntradaManifest entradaMija : leidoMija.entradasMija()) {
            if (entradaMija.offsetMija() + entradaMija.longitudCifradaMija() > footerMija.offsetManifestMija()) {
                throw new ExcepcionBoveda(new ErrorBoveda.IntegridadFallida(Zona.MANIFEST));
            }
        }
        manifestMija = leidoMija;
    }

    // ------------------------------------------------------------------ consultas

    /** Header elegido (antes de desbloquear: el de mayor generación con CRC válido). */
    @Override
    public Header header() {
        exigirAbierto();
        return elegidoMija.headerMija();
    }

    /** Headers estructuralmente válidos, en orden de preferencia. */
    @Override
    public List<Header> candidatosHeader() {
        exigirAbierto();
        return candidatosMija.stream().map(Candidato::headerMija).toList();
    }

    @Override
    public Manifest manifest() {
        exigirDesbloqueado();
        return manifestMija;
    }

    @Override
    public <T> T conClaveMaestra(java.util.function.Function<SecureBytes, T> usoMija) {
        exigirDesbloqueado();
        return usoMija.apply(claveMaestraMija);
    }

    @Override
    public boolean esSoloLectura() {
        return soloLecturaMija;
    }

    public long tamanoEnDisco() throws IOException {
        return canalMija.size();
    }

    // ------------------------------------------------------------------ operaciones

    /** Cifra y agrega los archivos en un solo commit (sin quitar nada ni reportar progreso). */
    public List<EntradaManifest> agregar(List<Origen> origenesMija) throws IOException {
        return agregar(origenesMija, List.of(), ReporteProgreso.NINGUNO_MIJA).agregadasMija();
    }

    /**
     * Cifra y agrega {@code origenesMija} y quita {@code quitarMija} en un solo commit. Verifica espacio y límite
     * FAT32 antes de escribir. Un archivo cuyo tamaño cambia durante la lectura se descarta (su espacio se
     * reutiliza). Si se cancela o falla la E/S, se descarta lo escrito y no se escribe el footer.
     */
    @Override
    public ResultadoAgregado agregar(List<Origen> origenesMija, List<byte[]> quitarMija, ReporteProgreso progresoMija)
            throws IOException {
        exigirEscribible();
        long cifradoTotalMija = 0;
        long originalTotalMija = 0;
        long manifestExtraMija = 0;
        for (Origen origenMija : origenesMija) {
            long tamanoMija = Files.size(origenMija.archivoMija());
            originalTotalMija += tamanoMija;
            cifradoTotalMija += ChunkCipher.tamanoCifrado(tamanoMija);
            manifestExtraMija += BYTES_FIJOS_ENTRADA_MIJA + LimitesFormato.RUTA_MAX_BYTES_MIJA
                    + LimitesFormato.MIME_MAX_BYTES_MIJA;
        }
        long tamanoFinalMija = finValidoMija + cifradoTotalMija + footerMija.longitudManifestMija()
                + manifestExtraMija + Footer.TAMANO_MIJA;
        verificarDestino(tamanoFinalMija, (long) ((tamanoFinalMija - finValidoMija) * MARGEN_ESPACIO_MIJA));

        long finAnteriorMija = finValidoMija;
        try {
            truncarBasura();
            long posicionMija = finValidoMija;
            long[] leidosMija = {0};
            List<EntradaManifest> nuevasMija = new ArrayList<>();
            List<Origen> cambiaronMija = new ArrayList<>();
            for (Origen origenMija : origenesMija) {
                progresoMija.verificarCancelacion();
                long esperadoMija = Files.size(origenMija.archivoMija());
                byte[] fileIdMija = opcionesMija.fuenteMija().bytes(HkdfDeriver.LONGITUD_FILE_ID_MIJA);
                Canales.Salida salidaMija = new Canales.Salida(canalMija, posicionMija);
                try (SecureBytes claveArchivoMija = HkdfDeriver.claveArchivo(claveMaestraMija, fileIdMija);
                        InputStream entradaMija = new LecturaConProgreso(Files.newInputStream(origenMija.archivoMija()),
                                progresoMija, leidosMija, originalTotalMija)) {
                    ChunkCipher.cifrar(claveArchivoMija, fileIdMija, entradaMija, salidaMija);
                }
                long tamanoOriginalMija = salidaMija.escritos() - tags(salidaMija.escritos());
                if (tamanoOriginalMija != esperadoMija || Files.size(origenMija.archivoMija()) != esperadoMija) {
                    cambiaronMija.add(origenMija); // no se avanza: el siguiente archivo reutiliza este espacio
                    continue;
                }
                nuevasMija.add(new EntradaManifest(fileIdMija, origenMija.rutaInternaMija(), tamanoOriginalMija,
                        Files.getLastModifiedTime(origenMija.archivoMija()).toMillis(),
                        tipoMime(origenMija.archivoMija()), posicionMija, salidaMija.escritos()));
                posicionMija += salidaMija.escritos();
            }
            progresoMija.verificarCancelacion();
            opcionesMija.interruptorMija().en(Interruptor.Paso.BLOBS_ESCRITOS);
            if (nuevasMija.isEmpty() && quitarMija.isEmpty()) {
                deshacerHasta(finAnteriorMija);
                return new ResultadoAgregado(List.of(), cambiaronMija);
            }
            confirmar(manifestMija.conCambios(quitarMija, nuevasMija), posicionMija);
            return new ResultadoAgregado(nuevasMija, cambiaronMija);
        } catch (IOException | ExcepcionBoveda errorMija) {
            deshacerHasta(finAnteriorMija);
            throw errorMija;
        }
    }

    /** Descifra un archivo hacia {@code salidaMija}, verificando cada bloque. */
    @Override
    public void extraer(byte[] fileIdMija, OutputStream salidaMija) throws IOException {
        exigirDesbloqueado();
        EntradaManifest entradaMija = manifestMija.buscar(fileIdMija)
                .orElseThrow(() -> new IllegalArgumentException("fileId inexistente"));
        try (SecureBytes claveArchivoMija = HkdfDeriver.claveArchivo(claveMaestraMija, fileIdMija)) {
            ChunkCipher.descifrar(claveArchivoMija, fileIdMija,
                    new Canales.Rango(canalMija, entradaMija.offsetMija(), entradaMija.longitudCifradaMija()),
                    salidaMija);
        }
    }

    /**
     * Canal de solo lectura con acceso aleatorio que descifra por bloque (ADR-010: lee por el canal único).
     * Se cierra automáticamente al cerrar el contenedor.
     */
    @Override
    public SeekableByteChannel abrirLectura(byte[] fileIdMija) throws IOException {
        exigirDesbloqueado();
        EntradaManifest entradaMija = manifestMija.buscar(fileIdMija)
                .orElseThrow(() -> new IllegalArgumentException("fileId inexistente"));
        CanalDescifrado lectorMija = new CanalDescifrado(canalMija, entradaMija,
                HkdfDeriver.claveArchivo(claveMaestraMija, fileIdMija), lectoresMija::remove);
        lectoresMija.add(lectorMija);
        return lectorMija;
    }

    /**
     * Quita el archivo del manifest. Sus bloques <b>siguen cifrados dentro</b> del archivo hasta
     * {@link #compactar()}.
     */
    @Override
    public void eliminar(byte[] fileIdMija) throws IOException {
        exigirEscribible();
        if (manifestMija.buscar(fileIdMija).isEmpty()) {
            throw new IllegalArgumentException("fileId inexistente");
        }
        verificarDestino(finValidoMija + footerMija.longitudManifestMija() + Footer.TAMANO_MIJA,
                footerMija.longitudManifestMija() + Footer.TAMANO_MIJA);
        long finAnteriorMija = finValidoMija;
        try {
            truncarBasura();
            confirmar(manifestMija.sinEntrada(fileIdMija), finValidoMija);
        } catch (IOException errorMija) {
            deshacerHasta(finAnteriorMija);
            throw errorMija;
        }
    }

    /** "Eliminar y compactar": quita el archivo y elimina físicamente sus bloques. */
    public void eliminarYCompactar(byte[] fileIdMija) throws IOException {
        eliminar(fileIdMija);
        compactar();
    }

    /**
     * Reescribe la bóveda solo con los archivos vivos en {@code <ruta>.tmp} y la reemplaza con un
     * movimiento atómico. Los bloques se copian cifrados (la clave depende del fileId, no del offset).
     */
    @Override
    public void compactar() throws IOException {
        exigirEscribible();
        cerrarLectores();
        verificarDestino(0, canalMija.size());
        // Fase 8: <ruta>.lock se mantiene durante TODO el proceso, también en el reemplazo atómico, cuando el
        // archivo principal no está bloqueado por nadie. Se borra al terminar (BloqueoAuxiliar.close).
        try (BloqueoAuxiliar _ = BloqueoAuxiliar.adquirir(rutaBloqueo(rutaMija))) {
            compactarConBloqueo();
        }
    }

    private void compactarConBloqueo() throws IOException {
        Path temporalMija = rutaMija.resolveSibling(rutaMija.getFileName() + ".tmp");
        Files.deleteIfExists(temporalMija);
        Manifest nuevoMija;
        Footer nuevoFooterMija;
        try (FileChannel destinoMija = FileChannel.open(temporalMija, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            Canales.escribir(destinoMija, OFFSET_HEADER_RESPALDO_MIJA, elegidoMija.bloqueMija());
            Canales.escribir(destinoMija, OFFSET_HEADER_PRIMARIO_MIJA, elegidoMija.bloqueMija());
            long posicionMija = OFFSET_DATOS_MIJA;
            List<EntradaManifest> movidasMija = new ArrayList<>();
            for (EntradaManifest entradaMija : manifestMija.entradasMija()) {
                copiar(canalMija, entradaMija.offsetMija(), entradaMija.longitudCifradaMija(), destinoMija,
                        posicionMija);
                movidasMija.add(entradaMija.conOffset(posicionMija));
                posicionMija += entradaMija.longitudCifradaMija();
            }
            nuevoMija = manifestMija.conEntradas(movidasMija);
            byte[] cifradoMija = cifrarManifest(nuevoMija);
            Canales.escribir(destinoMija, posicionMija, cifradoMija);
            nuevoFooterMija = new Footer(posicionMija, cifradoMija.length, nuevoMija.generacionMija());
            Canales.escribir(destinoMija, posicionMija + cifradoMija.length, nuevoFooterMija.codificar());
            destinoMija.force(true);
        } catch (IOException | RuntimeException errorMija) {
            Files.deleteIfExists(temporalMija);
            throw errorMija;
        }
        opcionesMija.interruptorMija().en(Interruptor.Paso.COMPACTACION_LISTA);
        liberarCanal();
        Files.move(temporalMija, rutaMija, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        sincronizarDirectorio(directorio(rutaMija));
        opcionesMija.interruptorMija().en(Interruptor.Paso.ARCHIVO_REEMPLAZADO);
        canalMija = FileChannel.open(rutaMija, StandardOpenOption.READ, StandardOpenOption.WRITE);
        bloquear(false);
        manifestMija = nuevoMija;
        footerMija = nuevoFooterMija;
        finValidoMija = nuevoFooterMija.offsetManifestMija() + nuevoFooterMija.longitudManifestMija()
                + Footer.TAMANO_MIJA;
        escribirZonaC(elegidoMija.headerMija().intentosFallidosMija(), finValidoMija - Footer.TAMANO_MIJA);
        REGISTRO_MIJA.log(System.Logger.Level.INFO, "Bóveda compactada");
    }

    /**
     * Reescribe la zona B (slots y umbral) con generación + 1: respaldo, {@code fsync}, primario,
     * {@code fsync}. Los slots anteriores quedan sobrescritos en ambas copias.
     */
    public void reescribirHeader(List<Slot> slotsMija, int umbralAutodestruccionMija) throws IOException {
        reescribirHeader(slotsMija, umbralAutodestruccionMija, elegidoMija.headerMija().requiereLlaveMija());
    }

    @Override
    public void reescribirHeader(List<Slot> slotsMija, int umbralAutodestruccionMija, boolean requiereLlaveMija)
            throws IOException {
        exigirEscribible();
        Header nuevoMija = elegidoMija.headerMija().conZonaB(slotsMija, umbralAutodestruccionMija, requiereLlaveMija);
        nuevoMija = nuevoMija.conMac(HeaderCodec.calcularMac(nuevoMija, claveHeaderMija));
        byte[] bloqueMija = HeaderCodec.codificar(nuevoMija, opcionesMija.fuenteMija());
        Canales.escribir(canalMija, OFFSET_HEADER_RESPALDO_MIJA, bloqueMija);
        canalMija.force(true);
        opcionesMija.interruptorMija().en(Interruptor.Paso.RESPALDO_ESCRITO);
        Canales.escribir(canalMija, OFFSET_HEADER_PRIMARIO_MIJA, bloqueMija);
        canalMija.force(true);
        opcionesMija.interruptorMija().en(Interruptor.Paso.PRIMARIO_ESCRITO);
        elegidoMija = new Candidato(nuevoMija, bloqueMija, true);
        candidatosMija = List.of(elegidoMija);
    }

    /**
     * Zona C: suma un intento fallido. Lee el contador actual del header primario en disco (es donde vive,
     * aunque el header preferido sea el respaldo). No requiere la clave maestra; no toca las zonas A ni B.
     */
    @Override
    public int registrarIntentoFallido() throws IOException {
        exigirNoSoloLectura();
        ByteBuffer zonaCMija = ByteBuffer.wrap(Canales.leer(canalMija,
                OFFSET_HEADER_PRIMARIO_MIJA + HeaderCodec.OFFSET_ZONA_C_MIJA, HeaderCodec.TAMANO_ZONA_C_MIJA, Zona.HEADER));
        int intentosMija = Math.max(0, zonaCMija.getInt()) + 1;
        escribirZonaC(intentosMija, elegidoMija.headerMija().pistaFooterMija());
        return intentosMija;
    }

    @Override
    public void reiniciarIntentos() throws IOException {
        exigirNoSoloLectura();
        escribirZonaC(0, elegidoMija.headerMija().pistaFooterMija());
    }

    /**
     * Autodestrucción: reemplaza todos los slots por slots vacíos en ambos headers (respaldo, fsync, primario,
     * fsync). No necesita la clave: el MAC queda inválido, pero el CRC se recalcula para que el header se siga
     * leyendo y la app pueda informar {@code BovedaDestruida} (Zona A intacta).
     */
    @Override
    public void destruirSlots() throws IOException {
        exigirNoSoloLectura();
        cerrarLectores();
        Header actualMija = elegidoMija.headerMija();
        List<Slot> vaciosMija = new ArrayList<>();
        for (int indiceMija = 0; indiceMija < actualMija.slotsMija().size(); indiceMija++) {
            vaciosMija.add(Slot.vacio(opcionesMija.fuenteMija()));
        }
        Header destruidoMija = actualMija.conZonaB(vaciosMija, actualMija.umbralAutodestruccionMija());
        byte[] bloqueMija = HeaderCodec.codificar(destruidoMija, opcionesMija.fuenteMija());
        Canales.escribir(canalMija, OFFSET_HEADER_RESPALDO_MIJA, bloqueMija);
        canalMija.force(true);
        Canales.escribir(canalMija, OFFSET_HEADER_PRIMARIO_MIJA, bloqueMija);
        canalMija.force(true);
        cerrarClaves();
        elegidoMija = new Candidato(destruidoMija, bloqueMija, true);
        candidatosMija = List.of(elegidoMija);
    }

    /** Borra las claves, cierra los canales de lectura, suelta el bloqueo y cierra el canal. Idempotente. */
    @Override
    public void close() {
        cerradoMija = true;
        cerrarLectores();
        cerrarClaves();
        try {
            liberarCanal();
        } catch (IOException errorMija) {
            REGISTRO_MIJA.log(System.Logger.Level.WARNING, "No se pudo cerrar el canal de la bóveda");
        }
    }

    // ------------------------------------------------------------------ bloqueo auxiliar de la compactación

    /** {@code <ruta>.lock}: lo mantiene la instancia que compacta mientras reemplaza el archivo (Fase 8). */
    static Path rutaBloqueo(Path rutaMija) {
        return rutaMija.resolveSibling(rutaMija.getFileName() + ".lock");
    }

    /**
     * Si hay un {@code .lock}: con una instancia viva compactando es {@code BovedaEnUso}; si nadie lo bloquea quedó
     * huérfano (la instancia murió a medias) y se borra. Sin {@code .lock}, o sin permiso para mirarlo (medio de
     * solo lectura), no hace nada.
     */
    private static void exigirSinCompactacionEnCurso(Path rutaMija) {
        Path bloqueoMija = rutaBloqueo(rutaMija);
        if (!Files.exists(bloqueoMija)) {
            return;
        }
        try (BloqueoAuxiliar _ = BloqueoAuxiliar.adquirir(bloqueoMija)) {
            REGISTRO_MIJA.log(System.Logger.Level.INFO, "Se limpió un archivo de bloqueo de compactación huérfano");
        } catch (IOException _) {
            // Medio de solo lectura o sin permiso: no hay compactación posible desde aquí.
        }
    }

    /** Archivo de bloqueo exclusivo con su propio ciclo de vida: se crea al adquirirlo y se borra al cerrarlo. */
    private static final class BloqueoAuxiliar implements AutoCloseable {
        private final Path archivoMija;
        private final FileChannel canalMija;
        private final FileLock bloqueoMija;

        private BloqueoAuxiliar(Path archivoMija, FileChannel canalMija, FileLock bloqueoMija) {
            this.archivoMija = archivoMija;
            this.canalMija = canalMija;
            this.bloqueoMija = bloqueoMija;
        }

        static BloqueoAuxiliar adquirir(Path archivoMija) throws IOException {
            FileChannel canalMija = FileChannel.open(archivoMija, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock bloqueoMija = null;
            try {
                bloqueoMija = canalMija.tryLock();
            } catch (OverlappingFileLockException _) {
                // Otra instancia de este mismo proceso lo tiene.
            } catch (IOException _) {
                // En Windows, un bloqueo de otro proceso hace fallar el intento: también es "en uso".
            }
            if (bloqueoMija == null) {
                canalMija.close();
                throw new ExcepcionBoveda(new ErrorBoveda.BovedaEnUso());
            }
            return new BloqueoAuxiliar(archivoMija, canalMija, bloqueoMija);
        }

        @Override
        public void close() {
            try {
                bloqueoMija.release();
                canalMija.close();
                Files.deleteIfExists(archivoMija);
            } catch (IOException _) {
                REGISTRO_MIJA.log(System.Logger.Level.WARNING,
                        "No se pudo borrar el archivo de bloqueo de compactación; se limpiará al abrir la bóveda");
            }
        }
    }

    // ------------------------------------------------------------------ internos

    private void bloquear(boolean compartidoMija) throws IOException {
        try {
            bloqueoMija = canalMija.tryLock(0, Long.MAX_VALUE, compartidoMija);
        } catch (OverlappingFileLockException _) {
            bloqueoMija = null;
        }
        if (bloqueoMija == null) {
            throw new ExcepcionBoveda(new ErrorBoveda.BovedaEnUso());
        }
    }

    private void leerHeaders() throws IOException {
        if (canalMija.size() < OFFSET_DATOS_MIJA) {
            throw new ExcepcionBoveda(new ErrorBoveda.IntegridadFallida(Zona.HEADER));
        }
        List<Candidato> validosMija = new ArrayList<>(2);
        ExcepcionBoveda primerErrorMija = null;
        for (boolean esPrimarioMija : new boolean[] {true, false}) {
            long offsetMija = esPrimarioMija ? OFFSET_HEADER_PRIMARIO_MIJA : OFFSET_HEADER_RESPALDO_MIJA;
            byte[] bloqueMija = Canales.leer(canalMija, offsetMija, TAMANO_HEADER_MIJA, Zona.HEADER);
            try {
                validosMija.add(new Candidato(HeaderCodec.decodificar(bloqueMija), bloqueMija, esPrimarioMija));
            } catch (ExcepcionBoveda errorMija) {
                if (primerErrorMija == null) {
                    primerErrorMija = errorMija;
                }
            }
        }
        if (validosMija.isEmpty()) {
            throw primerErrorMija;
        }
        validosMija.sort(Comparator.comparingLong((Candidato candidatoMija) -> candidatoMija.headerMija().generacionMija())
                .reversed().thenComparing(candidatoMija -> !candidatoMija.esPrimarioMija()));
        candidatosMija = List.copyOf(validosMija);
        elegidoMija = candidatosMija.getFirst();
    }

    /**
     * Footer al final → pista de la zona C → búsqueda hacia atrás.
     *
     * <p>La pista no está autenticada: después de seguirla se busca entre la pista y el final del
     * archivo un footer válido de generación mayor, para que una pista manipulada no cargue un estado
     * viejo. La generación del footer además forma parte de la AAD del manifest.
     */
    private void localizarFooter() throws IOException {
        long tamanoMija = canalMija.size();
        long posicionMija = tamanoMija - Footer.TAMANO_MIJA;
        Optional<Footer> encontradoMija = footerEn(posicionMija);
        if (encontradoMija.isEmpty()) {
            long pistaMija = elegidoMija.headerMija().pistaFooterMija();
            encontradoMija = pistaMija > 0 ? footerEn(pistaMija) : Optional.empty();
            posicionMija = pistaMija;
            if (encontradoMija.isPresent()) {
                long posteriorMija = buscarFooterHaciaAtras(tamanoMija, pistaMija + Footer.TAMANO_MIJA);
                Optional<Footer> nuevoMija = posteriorMija < 0 ? Optional.empty() : footerEn(posteriorMija);
                if (nuevoMija.isPresent()
                        && nuevoMija.get().generacionMija() > encontradoMija.get().generacionMija()) {
                    encontradoMija = nuevoMija;
                    posicionMija = posteriorMija;
                }
            }
        }
        if (encontradoMija.isEmpty()) {
            posicionMija = buscarFooterHaciaAtras(tamanoMija, OFFSET_DATOS_MIJA);
            encontradoMija = posicionMija < 0 ? Optional.empty() : footerEn(posicionMija);
        }
        footerMija = encontradoMija.orElseThrow(
                () -> new ExcepcionBoveda(new ErrorBoveda.IntegridadFallida(Zona.FOOTER)));
        finValidoMija = posicionMija + Footer.TAMANO_MIJA;
        if (finValidoMija < tamanoMija) {
            REGISTRO_MIJA.log(System.Logger.Level.WARNING,
                    "Datos incompletos tras el último footer válido; se descartarán en la próxima escritura");
        }
    }

    private Optional<Footer> footerEn(long posicionMija) throws IOException {
        // Sin sumar a la posición: la pista de la Zona C no está autenticada y puede valer Long.MAX_VALUE (la suma
        // desbordaría a un negativo y la lectura fallaría con IOException en lugar de rechazarse). Hallazgo de Jazzer.
        if (posicionMija < OFFSET_DATOS_MIJA || posicionMija > canalMija.size() - Footer.TAMANO_MIJA) {
            return Optional.empty();
        }
        return Footer.leer(Canales.leer(canalMija, posicionMija, Footer.TAMANO_MIJA, Zona.FOOTER), posicionMija);
    }

    /** Último footer válido (el más cercano al final) cuyo inicio sea {@code >= limiteInferiorMija}; -1 si no hay. */
    private long buscarFooterHaciaAtras(long tamanoMija, long limiteInferiorMija) throws IOException {
        byte[] magicMija = LimitesFormato.MAGIC_FOOTER_MIJA;
        long pisoMija = Math.max(OFFSET_DATOS_MIJA, limiteInferiorMija);
        long finVentanaMija = tamanoMija;
        while (finVentanaMija > pisoMija) {
            long inicioMija = Math.max(pisoMija, finVentanaMija - VENTANA_BUSQUEDA_MIJA);
            // Solapa magic − 1 bytes para no perder un magic partido entre ventanas.
            long hastaMija = Math.min(tamanoMija, finVentanaMija + magicMija.length - 1);
            byte[] ventanaMija = Canales.leer(canalMija, inicioMija, (int) (hastaMija - inicioMija), Zona.FOOTER);
            for (int indiceMija = ventanaMija.length - magicMija.length; indiceMija >= 0; indiceMija--) {
                if (ventanaMija[indiceMija] == magicMija[0] && Arrays.equals(ventanaMija, indiceMija,
                        indiceMija + magicMija.length, magicMija, 0, magicMija.length)) {
                    long candidatoMija = inicioMija + indiceMija - Footer.OFFSET_MAGIC_MIJA;
                    if (candidatoMija >= pisoMija && footerEn(candidatoMija).isPresent()) {
                        return candidatoMija;
                    }
                }
            }
            finVentanaMija = inicioMija;
        }
        return -1;
    }

    private void repararHeaders() throws IOException {
        if (soloLecturaMija) {
            return;
        }
        byte[] bloqueMija = elegidoMija.bloqueMija();
        boolean reparadoMija = false;
        for (long offsetMija : new long[] {OFFSET_HEADER_RESPALDO_MIJA, OFFSET_HEADER_PRIMARIO_MIJA}) {
            byte[] actualMija = Canales.leer(canalMija, offsetMija, TAMANO_HEADER_MIJA, Zona.HEADER);
            if (!Arrays.equals(actualMija, 0, HeaderCodec.OFFSET_ZONA_C_MIJA, bloqueMija, 0,
                    HeaderCodec.OFFSET_ZONA_C_MIJA)) {
                Canales.escribir(canalMija, offsetMija, bloqueMija);
                canalMija.force(true);
                reparadoMija = true;
            }
        }
        if (reparadoMija) {
            REGISTRO_MIJA.log(System.Logger.Level.WARNING, "Copia del header reparada");
        }
        candidatosMija = List.of(elegidoMija);
    }

    /** Escribe manifest + footer en {@code posicionMija}, hace {@code fsync} y actualiza el estado. */
    private void confirmar(Manifest nuevoMija, long posicionMija) throws IOException {
        byte[] cifradoMija = cifrarManifest(nuevoMija);
        Canales.escribir(canalMija, posicionMija, cifradoMija);
        opcionesMija.interruptorMija().en(Interruptor.Paso.MANIFEST_ESCRITO);
        Footer nuevoFooterMija = new Footer(posicionMija, cifradoMija.length, nuevoMija.generacionMija());
        long posicionFooterMija = posicionMija + cifradoMija.length;
        Canales.escribir(canalMija, posicionFooterMija, nuevoFooterMija.codificar());
        // Restos de un archivo descartado o de una escritura previa no deben quedar tras el footer.
        canalMija.truncate(posicionFooterMija + Footer.TAMANO_MIJA);
        opcionesMija.interruptorMija().en(Interruptor.Paso.FOOTER_ESCRITO);
        canalMija.force(true);
        manifestMija = nuevoMija;
        footerMija = nuevoFooterMija;
        finValidoMija = posicionFooterMija + Footer.TAMANO_MIJA;
        escribirZonaC(elegidoMija.headerMija().intentosFallidosMija(), posicionFooterMija);
    }

    private byte[] cifrarManifest(Manifest nuevoMija) {
        return ManifestCodec.cifrar(nuevoMija, claveManifestMija, elegidoMija.headerMija().zonaAMija().vaultIdMija(),
                nonceMija);
    }

    private void escribirZonaC(int intentosMija, long pistaMija) throws IOException {
        Canales.escribir(canalMija, OFFSET_HEADER_PRIMARIO_MIJA + HeaderCodec.OFFSET_ZONA_C_MIJA,
                HeaderCodec.codificarZonaC(intentosMija, pistaMija));
        canalMija.force(true);
        elegidoMija = new Candidato(elegidoMija.headerMija().conZonaC(intentosMija, pistaMija),
                elegidoMija.bloqueMija(), elegidoMija.esPrimarioMija());
    }

    private void verificarDestino(long tamanoFinalMija, long bytesNuevosMija) throws IOException {
        InfoDestino destinoMija = opcionesMija.destinoMija().describir(directorio(rutaMija));
        if (destinoMija.esFat() && tamanoFinalMija > LIMITE_FAT32_MIJA) {
            throw new ExcepcionBoveda(new ErrorBoveda.LimiteSistemaArchivos(destinoMija.tipoSistemaMija(),
                    LIMITE_FAT32_MIJA));
        }
        if (destinoMija.espacioUsableMija() < bytesNuevosMija) {
            throw new ExcepcionBoveda(new ErrorBoveda.EspacioInsuficiente(bytesNuevosMija,
                    destinoMija.espacioUsableMija()));
        }
    }

    private void truncarBasura() throws IOException {
        if (canalMija.size() > finValidoMija) {
            canalMija.truncate(finValidoMija);
        }
    }

    private void deshacerHasta(long finAnteriorMija) {
        try {
            canalMija.truncate(finAnteriorMija);
            canalMija.force(true);
        } catch (IOException _) {
            REGISTRO_MIJA.log(System.Logger.Level.WARNING,
                    "No se pudo descartar la escritura incompleta; se descartará al reabrir");
        }
    }

    private void prepararClaves(SecureBytes claveMaestraEntradaMija) {
        cerrarClaves();
        claveMaestraMija = copiaSegura(claveMaestraEntradaMija);
        claveHeaderMija = HkdfDeriver.claveHeaderMac(claveMaestraMija);
        claveManifestMija = HkdfDeriver.claveManifest(claveMaestraMija);
    }

    /** Duplica una clave fuera del heap; la copia temporal en el heap se borra dentro de {@code envolver}. */
    private static SecureBytes copiaSegura(SecureBytes origenMija) {
        return SecureBytes.envolver(origenMija.bytes());
    }

    private void cerrarClaves() {
        for (SecureBytes claveMija : new SecureBytes[] {claveMaestraMija, claveHeaderMija, claveManifestMija}) {
            if (claveMija != null) {
                claveMija.close();
            }
        }
        claveMaestraMija = null;
        claveHeaderMija = null;
        claveManifestMija = null;
        manifestMija = null;
    }

    private void liberarCanal() throws IOException {
        if (bloqueoMija != null && bloqueoMija.isValid()) {
            bloqueoMija.release();
        }
        bloqueoMija = null;
        if (canalMija != null && canalMija.isOpen()) {
            canalMija.close();
        }
    }

    private void exigirAbierto() {
        if (cerradoMija) {
            throw new IllegalStateException("La bóveda está cerrada");
        }
    }

    private void cerrarLectores() {
        List.copyOf(lectoresMija).forEach(CanalDescifrado::close);
        lectoresMija.clear();
    }

    private void exigirDesbloqueado() {
        exigirAbierto();
        if (manifestMija == null) {
            throw new IllegalStateException("La bóveda no está desbloqueada");
        }
    }

    private void exigirNoSoloLectura() {
        exigirAbierto();
        if (soloLecturaMija) {
            throw new IllegalStateException("La bóveda está abierta en solo lectura");
        }
    }

    private void exigirEscribible() {
        exigirDesbloqueado();
        exigirNoSoloLectura();
    }

    /** Cuenta los bytes leídos de los orígenes para el progreso y permite cancelar a mitad de un archivo. */
    private static final class LecturaConProgreso extends FilterInputStream {
        private final ReporteProgreso progresoMija;
        private final long[] leidosMija;
        private final long totalMija;

        LecturaConProgreso(InputStream entradaMija, ReporteProgreso progresoMija, long[] leidosMija, long totalMija) {
            super(entradaMija);
            this.progresoMija = progresoMija;
            this.leidosMija = leidosMija;
            this.totalMija = totalMija;
        }

        @Override
        public int read(byte[] destinoMija, int desdeMija, int longitudMija) throws IOException {
            progresoMija.verificarCancelacion();
            int leidoMija = super.read(destinoMija, desdeMija, longitudMija);
            if (leidoMija > 0) {
                leidosMija[0] += leidoMija;
                progresoMija.avanzar(leidosMija[0], totalMija);
            }
            return leidoMija;
        }
    }

    private static long tags(long cifradoMija) {
        long bloquesMija = Math.max(1, (cifradoMija + ChunkCipher.TAMANO_BLOQUE_CIFRADO_MIJA - 1)
                / ChunkCipher.TAMANO_BLOQUE_CIFRADO_MIJA);
        return bloquesMija * 16;
    }

    private static String tipoMime(Path archivoMija) {
        try {
            String tipoMija = Files.probeContentType(archivoMija);
            return tipoMija == null ? "application/octet-stream" : tipoMija;
        } catch (IOException _) {
            return "application/octet-stream";
        }
    }

    /**
     * {@code fsync} del directorio para que el renombrado sobreviva a un apagón (Linux/macOS).
     * Windows no permite abrir un directorio como canal; allí {@code MoveFileEx} ya es durable.
     */
    static boolean sincronizarDirectorio(Path directorioMija) {
        try (FileChannel canalDirectorioMija = FileChannel.open(directorioMija, StandardOpenOption.READ)) {
            canalDirectorioMija.force(true);
            return true;
        } catch (IOException _) {
            return false;
        }
    }

    private static Path directorio(Path rutaMija) {
        Path padreMija = rutaMija.toAbsolutePath().getParent();
        return padreMija == null ? rutaMija.toAbsolutePath() : padreMija;
    }

    private static void copiar(FileChannel origenMija, long desdeMija, long longitudMija, FileChannel destinoMija,
            long haciaMija) throws IOException {
        long copiadosMija = 0;
        while (copiadosMija < longitudMija) {
            int trozoMija = (int) Math.min(ChunkCipher.TAMANO_BLOQUE_CIFRADO_MIJA, longitudMija - copiadosMija);
            byte[] datosMija = Canales.leer(origenMija, desdeMija + copiadosMija, trozoMija, Zona.BLOQUE);
            Canales.escribir(destinoMija, haciaMija + copiadosMija, datosMija);
            copiadosMija += trozoMija;
        }
    }
}
