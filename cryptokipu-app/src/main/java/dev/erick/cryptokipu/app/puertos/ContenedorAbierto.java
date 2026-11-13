package dev.erick.cryptokipu.app.puertos;

import dev.erick.cryptokipu.core.crypto.SecureBytes;
import dev.erick.cryptokipu.core.format.EntradaManifest;
import dev.erick.cryptokipu.core.format.Header;
import dev.erick.cryptokipu.core.format.Manifest;
import dev.erick.cryptokipu.core.format.Slot;
import dev.erick.cryptokipu.core.progreso.ReporteProgreso;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.util.List;
import java.util.function.Function;

/**
 * Puerto de salida: una bóveda abierta y bloqueada. Todo el acceso pasa por un único canal (ADR-010).
 * Tras {@link #close()} cualquier operación lanza {@link IllegalStateException}.
 */
public interface ContenedorAbierto extends AutoCloseable {

    /** Resultado de agregar: entradas nuevas y orígenes descartados porque cambiaron durante la lectura. */
    record ResultadoAgregado(List<EntradaManifest> agregadasMija, List<Origen> cambiaronMija) {
        public ResultadoAgregado {
            agregadasMija = List.copyOf(agregadasMija);
            cambiaronMija = List.copyOf(cambiaronMija);
        }
    }

    /** Header preferido (antes de desbloquear: el de mayor generación con CRC válido). */
    Header header();

    /** Copias del header estructuralmente válidas, en orden de preferencia. */
    List<Header> candidatosHeader();

    /**
     * Prueba los headers candidatos con {@code abridorMija} y verifica el MAC. Antes de que una copia pase
     * el MAC no se escribe nada en las zonas A ni B.
     */
    void desbloquear(AbridorSlots abridorMija) throws IOException;

    Manifest manifest();

    /** Acceso controlado a la clave maestra (solo con la bóveda desbloqueada). */
    <T> T conClaveMaestra(Function<SecureBytes, T> usoMija);

    /** Reescribe la zona B en ambas copias del header: respaldo, fsync, primario, fsync. */
    void reescribirHeader(List<Slot> slotsMija, int umbralAutodestruccionMija, boolean requiereLlaveMija)
            throws IOException;

    /**
     * Cifra y agrega {@code origenesMija} y quita {@code quitarMija} en un solo commit. Un archivo cuyo tamaño
     * cambia durante la lectura se descarta. Si se cancela, no se escribe el footer.
     */
    ResultadoAgregado agregar(List<Origen> origenesMija, List<byte[]> quitarMija, ReporteProgreso progresoMija)
            throws IOException;

    /** Descifra un archivo hacia {@code salidaMija}; cada bloque se verifica antes de escribirlo. */
    void extraer(byte[] fileIdMija, OutputStream salidaMija) throws IOException;

    /** Canal de solo lectura con acceso aleatorio que descifra por bloque. */
    SeekableByteChannel abrirLectura(byte[] fileIdMija) throws IOException;

    void eliminar(byte[] fileIdMija) throws IOException;

    void compactar() throws IOException;

    boolean esSoloLectura();

    int registrarIntentoFallido() throws IOException;

    void reiniciarIntentos() throws IOException;

    /** Autodestrucción: sobrescribe todos los slots en ambos headers (respaldo primero). No necesita la clave. */
    void destruirSlots() throws IOException;

    @Override
    void close();
}
