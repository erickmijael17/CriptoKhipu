package dev.erick.cryptokipu.infra.casos;

import java.text.MessageFormat;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@code System.LoggerFinder} de pruebas (se registra con {@code META-INF/services}): acepta TODOS los niveles, como
 * si el registro estuviera en DEBUG/ALL, y guarda cada mensaje ya formateado y la excepción asociada. Sirve a la
 * auditoría de fugas de la Fase 8 ({@link AuditoriaDeFugasTest}).
 */
public final class CapturaDeLogs extends System.LoggerFinder {

    private static final List<String> REGISTROS_MIJA = new CopyOnWriteArrayList<>();

    public CapturaDeLogs() {
        super();
    }

    static List<String> registros() {
        return List.copyOf(REGISTROS_MIJA);
    }

    static void limpiar() {
        REGISTROS_MIJA.clear();
    }

    @Override
    public System.Logger getLogger(String nombreMija, Module moduloMija) {
        return new System.Logger() {
            @Override
            public String getName() {
                return nombreMija;
            }

            @Override
            public boolean isLoggable(Level nivelMija) {
                return nivelMija != Level.OFF;
            }

            @Override
            public void log(Level nivelMija, ResourceBundle paqueteMija, String mensajeMija, Throwable errorMija) {
                REGISTROS_MIJA.add(nombreMija + " " + nivelMija + " " + mensajeMija
                        + (errorMija == null ? "" : " " + errorMija + " " + java.util.Arrays.toString(
                                errorMija.getStackTrace())));
            }

            @Override
            public void log(Level nivelMija, ResourceBundle paqueteMija, String formatoMija, Object... parametrosMija) {
                String textoMija = parametrosMija == null || parametrosMija.length == 0 ? formatoMija
                        : MessageFormat.format(formatoMija, parametrosMija);
                REGISTROS_MIJA.add(nombreMija + " " + nivelMija + " " + textoMija);
            }
        };
    }
}
