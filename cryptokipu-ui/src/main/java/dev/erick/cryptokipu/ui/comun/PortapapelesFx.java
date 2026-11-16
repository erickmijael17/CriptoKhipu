package dev.erick.cryptokipu.ui.comun;

import dev.erick.cryptokipu.ui.viewmodel.GuardiaPortapapeles;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import javafx.animation.PauseTransition;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.util.Duration;

/**
 * Portapapeles del sistema con JavaFX y, para la {@link GuardiaPortapapeles}, un temporizador de JavaFX.
 *
 * <p>En Windows el historial del portapapeles (Win+V) y el portapapeles en la nube pueden guardar lo copiado.
 * {@link #copiarSinHistorial} agrega los formatos que Windows documenta para excluirlo
 * ({@code ExcludeClipboardContentFromMonitorProcessing}, {@code CanIncludeInClipboardHistory} y
 * {@code CanUploadToCloudClipboard} en 0). JavaFX registra esos nombres como formatos del sistema, así que la petición
 * llega a Windows; es <b>best-effort</b>: no hay garantía en otras plataformas ni en aplicaciones de terceros que
 * vigilen el portapapeles. Por eso la pantalla de tokens sigue recomendando "Guardar TXT".
 */
public final class PortapapelesFx implements GuardiaPortapapeles.Portapapeles, GuardiaPortapapeles.Programador {

    /** Formatos de exclusión del historial de Windows. */
    static final String EXCLUIR_MONITOREO_MIJA = "ExcludeClipboardContentFromMonitorProcessing";
    static final String PERMITIR_HISTORIAL_MIJA = "CanIncludeInClipboardHistory";
    static final String PERMITIR_NUBE_MIJA = "CanUploadToCloudClipboard";

    /** Copia el texto pidiendo al sistema que no lo guarde en el historial ni en la nube. */
    public void copiarSinHistorial(String textoMija) {
        ClipboardContent contenidoMija = new ClipboardContent();
        contenidoMija.putString(textoMija);
        contenidoMija.put(formato(EXCLUIR_MONITOREO_MIJA), dword(0));
        contenidoMija.put(formato(PERMITIR_HISTORIAL_MIJA), dword(0));
        contenidoMija.put(formato(PERMITIR_NUBE_MIJA), dword(0));
        Clipboard.getSystemClipboard().setContent(contenidoMija);
    }

    /** {@code true} si el sistema conserva el formato de exclusión (se comprueba al leer el portapapeles). */
    public boolean excluyeDelHistorial() {
        return Clipboard.getSystemClipboard().hasContent(formato(EXCLUIR_MONITOREO_MIJA));
    }

    @Override
    public Optional<String> texto() {
        Clipboard portapapelesMija = Clipboard.getSystemClipboard();
        return portapapelesMija.hasString() ? Optional.ofNullable(portapapelesMija.getString()) : Optional.empty();
    }

    @Override
    public void limpiar() {
        Clipboard.getSystemClipboard().clear();
    }

    @Override
    public Runnable programar(java.time.Duration retardoMija, Runnable tareaMija) {
        PauseTransition pausaMija = new PauseTransition(Duration.millis(retardoMija.toMillis()));
        pausaMija.setOnFinished(_ -> tareaMija.run());
        pausaMija.play();
        return pausaMija::stop;
    }

    static DataFormat formato(String nombreMija) {
        DataFormat existenteMija = DataFormat.lookupMimeType(nombreMija);
        return existenteMija != null ? existenteMija : new DataFormat(nombreMija);
    }

    private static ByteBuffer dword(int valorMija) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(valorMija).flip();
    }
}
