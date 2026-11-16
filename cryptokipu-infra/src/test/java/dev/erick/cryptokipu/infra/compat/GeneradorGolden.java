package dev.erick.cryptokipu.infra.compat;

import dev.erick.cryptokipu.app.casos.AgregarArchivos;
import dev.erick.cryptokipu.app.casos.CrearBoveda;
import dev.erick.cryptokipu.app.casos.PoliticaColision;
import dev.erick.cryptokipu.core.auth.Credenciales;
import dev.erick.cryptokipu.core.auth.TokenRecuperacion;
import dev.erick.cryptokipu.core.crypto.FuenteAleatoria;
import dev.erick.cryptokipu.core.crypto.ParametrosArgon2;
import dev.erick.cryptokipu.core.progreso.ReporteProgreso;
import dev.erick.cryptokipu.infra.autenticacion.LlaveSeguridad;
import dev.erick.cryptokipu.infra.configuracion.Contenedor;
import dev.erick.cryptokipu.infra.configuracion.PreferenciasStore;
import dev.erick.cryptokipu.infra.storage.ContenedorKipu;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Genera la bóveda «golden» v1 (Fase 9): una bóveda real, con credenciales y llave CONOCIDAS, que se guarda en
 * {@code src/test/resources/golden/v1}. {@link GoldenV1Test} la abre en cada build: si un cambio futuro rompe la
 * compatibilidad con las bóvedas existentes, el build falla.
 *
 * <p>NO se regenera salvo que se decida, a propósito, cambiar de versión de formato (entonces nace {@code golden/v2} y
 * {@code v1} se conserva). {@code ./gradlew :cryptokipu-infra:generarGolden}.
 */
public final class GeneradorGolden {

    static final String PIN_MIJA = "482915736028";
    static final String CLAVE_MIJA = "Golden Vault 2026";
    static final String FRASE_MIJA = "El Cóndor pasa el Río";

    private GeneradorGolden() {
    }

    /** Contenido determinista de cada archivo (se recalcula en la prueba; solo los SHA-256 viajan en el recurso). */
    static Map<String, byte[]> contenidos() {
        Map<String, byte[]> archivosMija = new TreeMap<>();
        archivosMija.put("leeme.txt", "CriptoKhipu golden v1: ñandú, año 2026, 日本語.\n".getBytes(StandardCharsets.UTF_8));
        archivosMija.put("vacio.bin", new byte[0]);
        archivosMija.put("carpeta/año 2026/ñandú.dat", patron(70_000, 7));
        archivosMija.put("carpeta/exacto-un-bloque.bin", patron(1 << 20, 11));
        archivosMija.put("carpeta/bloque-mas-uno.bin", patron((1 << 20) + 1, 13));
        return archivosMija;
    }

    private static byte[] patron(int tamanoMija, int pasoMija) {
        byte[] bytesMija = new byte[tamanoMija];
        for (int indiceMija = 0; indiceMija < tamanoMija; indiceMija++) {
            bytesMija[indiceMija] = (byte) (indiceMija * pasoMija + (indiceMija >>> 8) + 1);
        }
        return bytesMija;
    }

    static String sha256(byte[] bytesMija) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytesMija));
        } catch (java.security.NoSuchAlgorithmException errorMija) {
            throw new IllegalStateException(errorMija);
        }
    }

    public static void main(String[] argumentosMija) throws IOException {
        Path destinoMija = Path.of(argumentosMija[0]).resolve("golden/v1");
        Files.createDirectories(destinoMija);
        Path temporalMija = Files.createTempDirectory("golden");
        FuenteAleatoria fuenteMija = FuenteAleatoria.delSistema();
        Path llaveMija = LlaveSeguridad.generar(temporalMija.resolve("golden.kipukey"), fuenteMija);
        Path bovedaMija = temporalMija.resolve("golden.kipu");
        Contenedor appMija = new Contenedor(ContenedorKipu.Opciones.porDefecto(),
                PreferenciasStore.portatil(temporalMija.resolve("app")));
        List<Path> origenesMija = new ArrayList<>();
        Path carpetaOrigenMija = Files.createDirectories(temporalMija.resolve("origen"));
        for (Map.Entry<String, byte[]> archivoMija : contenidos().entrySet()) {
            Path rutaMija = carpetaOrigenMija.resolve(archivoMija.getKey());
            Files.createDirectories(rutaMija.getParent());
            Files.write(rutaMija, archivoMija.getValue());
            Files.setLastModifiedTime(rutaMija, java.nio.file.attribute.FileTime.fromMillis(1_700_000_000_000L));
        }
        // Se agregan los elementos de primer nivel (no la carpeta contenedora): las rutas internas quedan sin prefijo.
        try (var primerNivelMija = Files.list(carpetaOrigenMija)) {
            primerNivelMija.sorted().forEach(origenesMija::add);
        }
        Credenciales credencialesMija = new Credenciales(PIN_MIJA.toCharArray(), CLAVE_MIJA.toCharArray(),
                FRASE_MIJA.toCharArray(), LlaveSeguridad.huellaBytes(llaveMija, ReporteProgreso.NINGUNO_MIJA));
        // Perfil mínimo permitido (64 MiB, t=2, p=1): la prueba corre en cada build.
        try (CrearBoveda.Resultado resultadoMija = appMija.crearBoveda().ejecutar(new CrearBoveda.Comando(bovedaMija,
                credencialesMija, new ParametrosArgon2(64 * 1024, 2, 1), 0))) {
            appMija.agregarArchivos().ejecutar(new AgregarArchivos.Comando(resultadoMija.sesionMija(), origenesMija, "",
                    PoliticaColision.OMITIR, ReporteProgreso.NINGUNO_MIJA));
            StringBuilder tokensMija = new StringBuilder();
            for (TokenRecuperacion tokenMija : resultadoMija.tokensMija()) {
                tokensMija.append(new String(tokenMija.formatear())).append('\n');
            }
            Files.writeString(destinoMija.resolve("tokens.txt"), tokensMija);
        }
        Files.copy(bovedaMija, destinoMija.resolve("golden.kipu"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.copy(llaveMija, destinoMija.resolve("golden.kipukey"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        StringBuilder huellasMija = new StringBuilder();
        for (Map.Entry<String, byte[]> archivoMija : contenidos().entrySet()) {
            huellasMija.append(sha256(archivoMija.getValue())).append("  ").append(archivoMija.getKey()).append('\n');
        }
        Files.writeString(destinoMija.resolve("contenido.sha256"), huellasMija, StandardCharsets.UTF_8);
        Files.writeString(destinoMija.resolve("credenciales.txt"), "F1=" + PIN_MIJA + "\nF2=" + CLAVE_MIJA + "\nF3=" + FRASE_MIJA
                + "\nllave=golden.kipukey\nformato=v1\n", StandardCharsets.UTF_8);
        System.out.println("golden v1 generada en " + destinoMija);
    }
}
