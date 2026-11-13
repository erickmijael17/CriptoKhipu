package dev.erick.cryptokipu.infra.casos;

import dev.erick.cryptokipu.app.casos.CrearBoveda;
import dev.erick.cryptokipu.app.casos.DesbloquearBoveda;
import dev.erick.cryptokipu.app.casos.RecuperarConToken;
import dev.erick.cryptokipu.app.casos.SesionBoveda;
import dev.erick.cryptokipu.core.auth.Credenciales;
import dev.erick.cryptokipu.core.auth.TokenRecuperacion;
import dev.erick.cryptokipu.core.crypto.FuenteAleatoria;
import dev.erick.cryptokipu.core.crypto.ParametrosArgon2;
import dev.erick.cryptokipu.core.crypto.SecureBytes;
import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import dev.erick.cryptokipu.core.format.Header;
import dev.erick.cryptokipu.core.format.HeaderCodec;
import dev.erick.cryptokipu.core.format.LimitesFormato;
import dev.erick.cryptokipu.core.progreso.ReporteProgreso;
import dev.erick.cryptokipu.infra.autenticacion.LlaveSeguridad;
import dev.erick.cryptokipu.infra.configuracion.Contenedor;
import dev.erick.cryptokipu.infra.configuracion.PreferenciasStore;
import dev.erick.cryptokipu.infra.storage.ContenedorKipu;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.CRC32C;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.function.Executable;

/** Utilidades compartidas por las pruebas de casos de uso: una bóveda real en una carpeta temporal. */
final class Banco {

    static final ParametrosArgon2 RAPIDO_MIJA = new ParametrosArgon2(64 * 1024, 2, 1);
    static final FuenteAleatoria FUENTE_MIJA = FuenteAleatoria.delSistema();
    static final String PIN_MIJA = "123456789012";
    static final String CLAVE_MIJA = "Clave Segura 2026";
    static final String FRASE_MIJA = "Mi Pérro Ñato corre feliz";

    final Path carpetaMija;
    final Path bovedaMija;
    final Path llaveMija;
    final Contenedor appMija;

    Banco(Path carpetaMija) throws IOException {
        this.carpetaMija = carpetaMija;
        this.bovedaMija = carpetaMija.resolve("prueba.kipu");
        this.llaveMija = LlaveSeguridad.generar(carpetaMija.resolve("mi.kipukey"), FUENTE_MIJA);
        this.appMija = new Contenedor(ContenedorKipu.Opciones.porDefecto(),
                PreferenciasStore.portatil(carpetaMija.resolve("app")));
    }

    /** Crea la bóveda (con llave) y devuelve copias de los 6 tokens. */
    List<TokenRecuperacion> crear(int umbralMija) throws IOException {
        try (CrearBoveda.Resultado resultadoMija = appMija.crearBoveda().ejecutar(new CrearBoveda.Comando(bovedaMija,
                credenciales(PIN_MIJA, CLAVE_MIJA, FRASE_MIJA, llaveMija), RAPIDO_MIJA, umbralMija))) {
            List<TokenRecuperacion> copiaMija = new ArrayList<>();
            resultadoMija.tokensMija().forEach(tokenMija ->
                    copiaMija.add(new TokenRecuperacion(tokenMija.simbolosMija().clone())));
            return copiaMija;
        }
    }

    List<TokenRecuperacion> crear() throws IOException {
        return crear(0);
    }

    SesionBoveda abrir() throws IOException {
        return abrir(credenciales(PIN_MIJA, CLAVE_MIJA, FRASE_MIJA, llaveMija));
    }

    SesionBoveda abrir(Credenciales credencialesMija) throws IOException {
        return appMija.desbloquearBoveda().ejecutar(new DesbloquearBoveda.Comando(bovedaMija, credencialesMija));
    }

    SesionBoveda recuperar(TokenRecuperacion tokenMija, String pinMija) throws IOException {
        return appMija.recuperarConToken().ejecutar(new RecuperarConToken.Comando(bovedaMija,
                TokenRecuperacion.leer(tokenMija.formatear()), pinMija.toCharArray()));
    }

    Credenciales vigentes() throws IOException {
        return credenciales(PIN_MIJA, CLAVE_MIJA, FRASE_MIJA, llaveMija);
    }

    static Credenciales credenciales(String pinMija, String claveMija, String fraseMija, Path llaveUsadaMija)
            throws IOException {
        return new Credenciales(pinMija.toCharArray(), claveMija.toCharArray(), fraseMija.toCharArray(),
                huella(llaveUsadaMija));
    }

    static byte[] huella(Path llaveMija) throws IOException {
        if (llaveMija == null) {
            return null;
        }
        try (SecureBytes seguraMija = LlaveSeguridad.huella(llaveMija, ReporteProgreso.NINGUNO_MIJA)) {
            return seguraMija.bytes().clone();
        }
    }

    Path archivo(String rutaRelativaMija, int tamanoMija) throws IOException {
        Path rutaMija = carpetaMija.resolve("origen").resolve(rutaRelativaMija);
        Files.createDirectories(rutaMija.getParent());
        Files.write(rutaMija, FUENTE_MIJA.bytes(tamanoMija));
        return rutaMija;
    }

    Header headerEn(long offsetMija) throws IOException {
        return HeaderCodec.decodificar(bloque(offsetMija));
    }

    byte[] bloque(long offsetMija) throws IOException {
        try (RandomAccessFile archivoMija = new RandomAccessFile(bovedaMija.toFile(), "r")) {
            byte[] bloqueMija = new byte[LimitesFormato.TAMANO_HEADER_MIJA];
            archivoMija.seek(offsetMija);
            archivoMija.readFully(bloqueMija);
            return bloqueMija;
        }
    }

    void escribirBloque(long offsetMija, byte[] bloqueMija) throws IOException {
        CRC32C crcMija = new CRC32C();
        crcMija.update(bloqueMija, 0, HeaderCodec.OFFSET_CRC_MIJA);
        java.nio.ByteBuffer.wrap(bloqueMija).putInt(HeaderCodec.OFFSET_CRC_MIJA, (int) crcMija.getValue());
        try (RandomAccessFile archivoMija = new RandomAccessFile(bovedaMija.toFile(), "rw")) {
            archivoMija.seek(offsetMija);
            archivoMija.write(bloqueMija);
        }
    }

    static String sha256(Path archivoMija) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archivoMija)));
        } catch (NoSuchAlgorithmException errorMija) {
            throw new IllegalStateException(errorMija);
        }
    }

    static ExcepcionBoveda esperar(Class<? extends ErrorBoveda> tipoMija, Executable accionMija) {
        ExcepcionBoveda excepcionMija = Assertions.assertThrows(ExcepcionBoveda.class, accionMija);
        Assertions.assertInstanceOf(tipoMija, excepcionMija.error(), excepcionMija.error().toString());
        return excepcionMija;
    }
}
