package dev.erick.cryptokipu.arquitectura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * ADR-014: lo que ve el usuario dice «CriptoKhipu». «CryptoKipu» solo puede quedar en el código interno
 * (comentarios, nombres de clase). Se revisan los recursos de la interfaz, los textos visibles que viven en el
 * código (ventana, filtros de archivo, TXT de tokens, ayuda de la CLI) y las guías de usuario.
 */
class NombreVisibleTest {

    private static final String VIEJO_MIJA = "CryptoKipu";
    private static final Path RAIZ_MIJA = Path.of("..").toAbsolutePath().normalize();

    @Test
    void losRecursosDeLaInterfazNoUsanElNombreViejo() throws IOException {
        Path recursosMija = RAIZ_MIJA.resolve("cryptokipu-ui/src/main/resources");
        assertTrue(Files.isDirectory(recursosMija), "no se encontró " + recursosMija);
        assertEquals(List.of(), conNombreViejo(archivosDe(recursosMija)));
    }

    @Test
    void lasGuiasDeUsuarioNoUsanElNombreViejo() throws IOException {
        List<Path> guiasMija = List.of(RAIZ_MIJA.resolve("README.md"), RAIZ_MIJA.resolve("NOTICE"),
                RAIZ_MIJA.resolve("docs/GUIA_USB.md"), RAIZ_MIJA.resolve("docs/PRUEBAS_UI.md"));
        guiasMija.forEach(guiaMija -> assertTrue(Files.isRegularFile(guiaMija), "falta " + guiaMija));
        assertEquals(List.of(), conNombreViejo(guiasMija));
    }

    @Test
    void losTextosVisiblesDelCodigoUsanElNombreNuevo() throws IOException {
        List<Path> fuentesMija = List.of(
                RAIZ_MIJA.resolve("cryptokipu-cli/src/main/java/dev/erick/cryptokipu/cli/comandos/Kipu.java"),
                RAIZ_MIJA.resolve("cryptokipu-infra/src/main/java/dev/erick/cryptokipu/infra/autenticacion/"
                        + "ExportadorTokensTxt.java"),
                RAIZ_MIJA.resolve("cryptokipu-ui/src/main/java/dev/erick/cryptokipu/ui/vista/InicioController.java"),
                RAIZ_MIJA.resolve("cryptokipu-ui/src/main/java/dev/erick/cryptokipu/ui/vista/AsistenteController.java"));
        assertEquals(List.of(), conNombreViejo(fuentesMija));
        for (Path pathMija : fuentesMija.subList(0, 2)) {
            assertTrue(Files.readString(pathMija).contains("CriptoKhipu"), pathMija + " debe decir CriptoKhipu");
        }
    }

    @Test
    void elTituloYElDialogoAcercaDeDicenCriptoKhipu() throws IOException {
        Path textosMija = RAIZ_MIJA.resolve("cryptokipu-ui/src/main/resources/dev/erick/cryptokipu/ui/textos.properties");
        String contenidoMija = Files.readString(textosMija);
        assertTrue(contenidoMija.contains("app.titulo=CriptoKhipu"));
        assertTrue(contenidoMija.contains("acercaDe.titulo="), "falta el diálogo «Acerca de»");
    }

    private static List<Path> archivosDe(Path carpetaMija) throws IOException {
        try (Stream<Path> recorridoMija = Files.walk(carpetaMija)) {
            return recorridoMija.filter(Files::isRegularFile).toList();
        }
    }

    private static List<String> conNombreViejo(List<Path> archivosMija) throws IOException {
        List<String> culpablesMija = new ArrayList<>();
        for (Path archivoMija : archivosMija) {
            if (Files.readString(archivoMija).contains(VIEJO_MIJA)) {
                culpablesMija.add(RAIZ_MIJA.relativize(archivoMija).toString());
            }
        }
        return culpablesMija;
    }
}
