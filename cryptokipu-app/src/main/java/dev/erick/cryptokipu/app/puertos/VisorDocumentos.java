package dev.erick.cryptokipu.app.puertos;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.util.Objects;

/**
 * Puerto para ver documentos en RAM (PDF, Word, Excel, PowerPoint). Lo implementa el módulo opcional
 * {@code cryptokipu-visores} y la interfaz lo obtiene con {@link java.util.ServiceLoader}: si el módulo no está, no
 * hay vista previa de esos formatos, pero nada más cambia.
 *
 * <p>Reglas para quien implemente el puerto: leer solo del canal recibido (descifrado por bloques, nunca en disco),
 * no crear archivos temporales y limitar tamaños antes de decodificar (bombas de descompresión).
 */
public interface VisorDocumentos {

    /** Tamaño máximo de un documento para verlo en RAM (se lee completo antes de decodificar). */
    long TAMANO_MAXIMO_MIJA = 100L * 1024 * 1024;

    /** Extensiones en minúsculas, sin punto. */
    boolean soporta(String extensionMija);

    /**
     * Abre el documento leyendo del canal (como mucho {@link #TAMANO_MAXIMO_MIJA} bytes). Quien llama cierra el
     * resultado y el canal.
     */
    DocumentoAbierto abrir(String extensionMija, SeekableByteChannel canalMija, long tamanoMija) throws IOException;

    /** Documento abierto en memoria. */
    sealed interface DocumentoAbierto extends AutoCloseable permits Paginado, Textual {
        @Override
        void close();
    }

    /** PDF o presentación: cada página se renderiza solo cuando se pide. */
    non-sealed interface Paginado extends DocumentoAbierto {
        int paginas();

        /** Renderiza la página {@code indiceMija} (desde 0) con un ancho máximo en píxeles. */
        Imagen renderizar(int indiceMija, int anchoMaximoMija) throws IOException;
    }

    /** Word o Excel: el contenido como texto (párrafos, tablas y hojas). */
    record Textual(String contenidoMija) implements DocumentoAbierto {
        public Textual {
            Objects.requireNonNull(contenidoMija, "contenidoMija");
        }

        @Override
        public void close() {
            // Solo texto en memoria.
        }
    }

    /** Píxeles ARGB (un {@code int} por píxel, fila por fila): la interfaz los copia a una imagen de JavaFX. */
    record Imagen(int anchoMija, int altoMija, int[] argbMija) {
        public Imagen {
            Objects.requireNonNull(argbMija, "argbMija");
            if (anchoMija <= 0 || altoMija <= 0 || argbMija.length != anchoMija * altoMija) {
                throw new IllegalArgumentException("Dimensiones inválidas");
            }
        }
    }
}
