package dev.erick.cryptokipu.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Convenciones que Checkstyle no puede ver porque viven en FXML: todo {@code fx:id} termina en {@code Mija} (R2) y
 * todo texto {@code %clave} existe en {@code textos.properties}.
 */
class FxmlConvencionesTest {

    private static final Path VISTAS_MIJA = Path.of("src/main/resources/dev/erick/cryptokipu/ui/vista");
    private static final Path TEXTOS_MIJA = Path.of("src/main/resources/dev/erick/cryptokipu/ui/textos.properties");
    private static final Pattern FX_ID_MIJA = Pattern.compile("fx:id=\"([^\"]+)\"");
    private static final Pattern CLAVE_MIJA = Pattern.compile("=\"%([^\"]+)\"");

    @Test
    void todoFxIdTerminaEnMijaYTodaClaveExiste() throws IOException {
        Properties textosMija = new Properties();
        try (var lectorMija = Files.newBufferedReader(TEXTOS_MIJA)) {
            textosMija.load(lectorMija);
        }
        List<String> problemasMija = new ArrayList<>();
        int vistasMija = 0;
        try (Stream<Path> archivosMija = Files.list(VISTAS_MIJA)) {
            for (Path fxmlMija : archivosMija.filter(rutaMija -> rutaMija.toString().endsWith(".fxml")).toList()) {
                vistasMija++;
                String contenidoMija = Files.readString(fxmlMija);
                Matcher idMija = FX_ID_MIJA.matcher(contenidoMija);
                while (idMija.find()) {
                    if (!idMija.group(1).endsWith("Mija")) {
                        problemasMija.add(fxmlMija.getFileName() + ": fx:id " + idMija.group(1));
                    }
                }
                Matcher claveMija = CLAVE_MIJA.matcher(contenidoMija);
                while (claveMija.find()) {
                    if (!textosMija.containsKey(claveMija.group(1))) {
                        problemasMija.add(fxmlMija.getFileName() + ": falta el texto " + claveMija.group(1));
                    }
                }
            }
        }
        assertEquals(List.of(), problemasMija);
        assertTrue(vistasMija >= 9, "Se esperaban las 9 vistas");
    }

    @Test
    void ningunTextoDeLaInterfazMencionaQueFactorFallo() throws IOException {
        Properties textosMija = new Properties();
        try (var lectorMija = Files.newBufferedReader(TEXTOS_MIJA)) {
            textosMija.load(lectorMija);
        }
        for (String claveMija : textosMija.stringPropertyNames()) {
            if (claveMija.startsWith("desbloqueo.") || claveMija.startsWith("error.")) {
                String valorMija = textosMija.getProperty(claveMija).toLowerCase(java.util.Locale.ROOT);
                assertFalse(valorMija.contains("incorrect"), claveMija + " no debe decir qué factor es incorrecto");
            }
        }
    }
}
