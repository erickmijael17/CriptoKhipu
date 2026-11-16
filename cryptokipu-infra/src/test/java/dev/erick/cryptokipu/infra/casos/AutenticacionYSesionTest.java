package dev.erick.cryptokipu.infra.casos;

import static dev.erick.cryptokipu.infra.casos.Banco.CLAVE_MIJA;
import static dev.erick.cryptokipu.infra.casos.Banco.FRASE_MIJA;
import static dev.erick.cryptokipu.infra.casos.Banco.PIN_MIJA;
import static dev.erick.cryptokipu.infra.casos.Banco.credenciales;
import static dev.erick.cryptokipu.infra.casos.Banco.esperar;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erick.cryptokipu.app.casos.Compactar;
import dev.erick.cryptokipu.app.casos.InspeccionarBoveda;
import dev.erick.cryptokipu.app.casos.RegenerarTokens;
import dev.erick.cryptokipu.app.casos.RotarCredenciales;
import dev.erick.cryptokipu.app.casos.SesionBoveda;
import dev.erick.cryptokipu.core.auth.CambioCredenciales;
import dev.erick.cryptokipu.core.auth.CambioCredenciales.CambioLlave;
import dev.erick.cryptokipu.core.auth.ExcepcionValidacion;
import dev.erick.cryptokipu.core.auth.ReglaIncumplida;
import dev.erick.cryptokipu.core.auth.TokenRecuperacion;
import dev.erick.cryptokipu.core.crypto.SecureBytes;
import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.format.Header;
import dev.erick.cryptokipu.core.format.HeaderCodec;
import dev.erick.cryptokipu.core.format.LimitesFormato;
import dev.erick.cryptokipu.core.format.TipoSlot;
import dev.erick.cryptokipu.core.format.ZonaA;
import dev.erick.cryptokipu.infra.autenticacion.LlaveSeguridad;
import dev.erick.cryptokipu.infra.storage.ContenedorKipu;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Autenticación, rotación explícita, metadatos del slot 0, sesión y autodestrucción (Fase 4, puntos 0, 2 y 7). */
class AutenticacionYSesionTest {

    @TempDir
    Path carpetaMija;

    private Banco bancoMija;

    @BeforeEach
    void preparar() throws IOException {
        bancoMija = new Banco(carpetaMija);
    }

    private static CambioCredenciales cambio(String pinMija, String claveMija, String fraseMija, CambioLlave llaveMija) {
        return new CambioCredenciales(Optional.ofNullable(pinMija).map(String::toCharArray),
                Optional.ofNullable(claveMija).map(String::toCharArray),
                Optional.ofNullable(fraseMija).map(String::toCharArray), llaveMija, OptionalInt.empty());
    }

    private List<TokenRecuperacion> rotar(SesionBoveda sesionMija, boolean conVigentesMija, CambioCredenciales cambioMija)
            throws IOException {
        return bancoMija.appMija.rotarCredenciales().ejecutar(new RotarCredenciales.Comando(sesionMija,
                conVigentesMija ? Optional.of(bancoMija.vigentes()) : Optional.empty(), cambioMija)).tokensNuevosMija();
    }

    // ------------------------------------------------------------------ desbloqueo

    @ParameterizedTest(name = "factor equivocado: F{0}")
    @ValueSource(ints = {1, 2, 3, 4})
    void cualquierFactorEquivocadoDaElMismoErrorYSumaUnIntento(int factorMija) throws IOException {
        bancoMija.crear();
        Path otraMija = LlaveSeguridad.generar(carpetaMija.resolve("otra.kipukey"), Banco.FUENTE_MIJA);
        var malasMija = credenciales(factorMija == 1 ? "123456789013" : PIN_MIJA,
                factorMija == 2 ? "Clave Segura 2027" : CLAVE_MIJA,
                factorMija == 3 ? "mi gato nato corre feliz" : FRASE_MIJA,
                factorMija == 4 ? otraMija : bancoMija.llaveMija);

        assertEquals("CredencialesIncorrectas",
                esperar(ErrorBoveda.CredencialesIncorrectas.class, () -> bancoMija.abrir(malasMija)).getMessage());
        assertEquals(1, bancoMija.headerEn(0).intentosFallidosMija());
    }

    // ------------------------------------------------------------------ 0a: rotación explícita

    @Test
    void cambiarSoloF2ConservaLosTokens() throws IOException {
        List<TokenRecuperacion> tokensMija = bancoMija.crear();
        try (SesionBoveda sesionMija = bancoMija.abrir()) {
            assertTrue(rotar(sesionMija, true, cambio(null, "Otra Clave 2027", null, new CambioLlave.SinCambio()))
                    .isEmpty());
        }
        bancoMija.abrir(credenciales(PIN_MIJA, "Otra Clave 2027", FRASE_MIJA, bancoMija.llaveMija)).close();
        bancoMija.recuperar(tokensMija.getFirst(), PIN_MIJA).close();
    }

    @Test
    void siVieneF1SeRegeneranLosSeisTokensAunqueSeaElMismoValor() throws IOException {
        List<TokenRecuperacion> viejosMija = bancoMija.crear();
        List<TokenRecuperacion> nuevosMija;
        try (SesionBoveda sesionMija = bancoMija.abrir()) {
            nuevosMija = rotar(sesionMija, true, cambio("987654321098", null, null, new CambioLlave.SinCambio()));
        }
        assertEquals(6, nuevosMija.size());
        esperar(ErrorBoveda.CredencialesIncorrectas.class, () -> bancoMija.recuperar(viejosMija.get(2), "987654321098"));
        bancoMija.recuperar(nuevosMija.get(2), "987654321098").close();
    }

    @Test
    void rotarExigeCredencialesVigentesCorrectas() throws IOException {
        bancoMija.crear();
        try (SesionBoveda sesionMija = bancoMija.abrir()) {
            esperar(ErrorBoveda.CredencialesIncorrectas.class, () -> bancoMija.appMija.rotarCredenciales().ejecutar(
                    new RotarCredenciales.Comando(sesionMija, Optional.of(credenciales(PIN_MIJA, "Clave Mala 2026",
                            FRASE_MIJA, bancoMija.llaveMija)), cambio(null, "Otra Clave 2027", null,
                            new CambioLlave.SinCambio()))));
            ExcepcionValidacion sinVigentesMija = assertThrows(ExcepcionValidacion.class, () -> bancoMija.appMija
                    .rotarCredenciales().ejecutar(new RotarCredenciales.Comando(sesionMija, Optional.empty(),
                            cambio(null, "Otra Clave 2027", null, new CambioLlave.SinCambio()))));
            assertEquals(List.of(ReglaIncumplida.FACTORES_INCOMPLETOS), sinVigentesMija.reglas());
        }
    }

    @Test
    void quitarYAgregarLaLlaveCambiaLaMarcaDelHeader() throws IOException {
        bancoMija.crear();
        try (SesionBoveda sesionMija = bancoMija.abrir()) {
            rotar(sesionMija, true, cambio(null, null, null, new CambioLlave.Quitar()));
        }
        assertFalse(bancoMija.headerEn(0).requiereLlaveMija());
        bancoMija.abrir(credenciales(PIN_MIJA, CLAVE_MIJA, FRASE_MIJA, null)).close();
    }

    // ------------------------------------------------------------------ 0b: metadatos en la AAD del slot 0

    @Test
    void invertirRequiereLlaveEnElPrimarioAbreConElRespaldoYLoRepara() throws IOException {
        bancoMija.crear();
        byte[] primarioMija = bancoMija.bloque(LimitesFormato.OFFSET_HEADER_PRIMARIO_MIJA);
        primarioMija[ZonaA.TAMANO_MIJA + 12] = 0; // requiereLlave: 1 → 0, CRC recalculado
        bancoMija.escribirBloque(LimitesFormato.OFFSET_HEADER_PRIMARIO_MIJA, primarioMija);
        assertFalse(bancoMija.headerEn(LimitesFormato.OFFSET_HEADER_PRIMARIO_MIJA).requiereLlaveMija());

        InspeccionarBoveda.Resultado infoMija = bancoMija.appMija.inspeccionarBoveda()
                .ejecutar(new InspeccionarBoveda.Comando(bancoMija.bovedaMija));
        assertTrue(infoMija.requiereLlaveMija(), "la UI pide la llave si cualquier copia la requiere");

        bancoMija.abrir().close();
        assertTrue(bancoMija.headerEn(LimitesFormato.OFFSET_HEADER_PRIMARIO_MIJA).requiereLlaveMija(), "reparado");
    }

    @Test
    void cambiarElUmbralEnUnaCopiaHaceFallarSuSlotPrincipal() throws IOException {
        bancoMija.crear(5);
        for (long offsetMija : new long[] {LimitesFormato.OFFSET_HEADER_PRIMARIO_MIJA,
                LimitesFormato.OFFSET_HEADER_RESPALDO_MIJA}) {
            byte[] bloqueMija = bancoMija.bloque(offsetMija);
            java.nio.ByteBuffer.wrap(bloqueMija).putInt(ZonaA.TAMANO_MIJA + 8, 0); // desactivar autodestrucción
            bancoMija.escribirBloque(offsetMija, bloqueMija);
        }
        esperar(ErrorBoveda.CredencialesIncorrectas.class, () -> bancoMija.abrir());
    }

    // ------------------------------------------------------------------ 2: sesión

    @Test
    void trasCerrarLaSesionLaClaveMaestraQuedaEnCeroYTodoSeRechaza() throws IOException {
        bancoMija.crear();
        SesionBoveda sesionMija = bancoMija.abrir();
        // La clave vive fuera del heap (Fase 8): se guarda la referencia y, tras cerrar, ya no se puede leer.
        SecureBytes maestraMija = ((ContenedorKipu) sesionMija.contenedor()).conClaveMaestra(claveMija -> claveMija);
        assertFalse(maestraMija.estaCerrado());
        assertFalse(java.util.Arrays.equals(new byte[32], maestraMija.bytes()));

        sesionMija.close();

        assertTrue(maestraMija.estaCerrado());
        assertThrows(IllegalStateException.class, maestraMija::bytes);
        assertTrue(sesionMija.cerrada());
        assertThrows(IllegalStateException.class, sesionMija::manifest);
        assertThrows(IllegalStateException.class,
                () -> bancoMija.appMija.compactar().ejecutar(new Compactar.Comando(sesionMija)));
        sesionMija.close(); // idempotente
    }

    @Test
    void conRotacionObligatoriaSoloSePuedeRotarOCerrar() throws IOException {
        List<TokenRecuperacion> tokensMija = bancoMija.crear();
        try (SesionBoveda sesionMija = bancoMija.recuperar(tokensMija.getFirst(), PIN_MIJA)) {
            assertTrue(sesionMija.rotacionObligatoria());
            esperar(ErrorBoveda.RotacionPendiente.class, sesionMija::manifest);
            esperar(ErrorBoveda.RotacionPendiente.class,
                    () -> bancoMija.appMija.compactar().ejecutar(new Compactar.Comando(sesionMija)));
            esperar(ErrorBoveda.RotacionPendiente.class, () -> bancoMija.appMija.regenerarTokens()
                    .ejecutar(new RegenerarTokens.Comando(sesionMija, bancoMija.vigentes())));

            ExcepcionValidacion incompletoMija = assertThrows(ExcepcionValidacion.class, () -> rotar(sesionMija, false,
                    cambio(null, "Clave Nueva 2028", "frase nueva y distinta", new CambioLlave.Quitar())));
            assertEquals(List.of(ReglaIncumplida.FACTORES_INCOMPLETOS), incompletoMija.reglas());

            List<TokenRecuperacion> nuevosMija = rotar(sesionMija, false,
                    cambio(PIN_MIJA, "Clave Nueva 2028", "frase nueva y distinta", new CambioLlave.Quitar()));
            assertEquals(6, nuevosMija.size(), "tras un token siempre se regeneran");
            assertFalse(sesionMija.rotacionObligatoria());
            assertEquals(0, sesionMija.manifest().entradasMija().size());
        }
        bancoMija.abrir(credenciales(PIN_MIJA, "Clave Nueva 2028", "frase nueva y distinta", null)).close();
    }

    @Test
    void elSlotDelTokenUsadoSeBorraEnAmbosHeadersYNoSirveDosVeces() throws IOException {
        List<TokenRecuperacion> tokensMija = bancoMija.crear();
        bancoMija.recuperar(tokensMija.get(2), PIN_MIJA).close();
        for (long offsetMija : new long[] {LimitesFormato.OFFSET_HEADER_RESPALDO_MIJA,
                LimitesFormato.OFFSET_HEADER_PRIMARIO_MIJA}) {
            Header headerMija = bancoMija.headerEn(offsetMija);
            assertEquals(TipoSlot.VACIO, headerMija.slotsMija().get(3).tipoMija());
        }
        esperar(ErrorBoveda.CredencialesIncorrectas.class, () -> bancoMija.recuperar(tokensMija.get(2), PIN_MIJA));
    }

    // ------------------------------------------------------------------ 7: autodestrucción

    @Test
    void alcanzarElUmbralDestruyeAmbosHeaders() throws IOException {
        bancoMija.crear(3);
        var malasMija = credenciales(PIN_MIJA, CLAVE_MIJA, "frase equivocada aqui", bancoMija.llaveMija);
        esperar(ErrorBoveda.CredencialesIncorrectas.class, () -> bancoMija.abrir(malasMija));
        esperar(ErrorBoveda.CredencialesIncorrectas.class, () -> bancoMija.abrir(malasMija));
        esperar(ErrorBoveda.BovedaDestruida.class, () -> bancoMija.abrir(malasMija));

        for (long offsetMija : new long[] {LimitesFormato.OFFSET_HEADER_RESPALDO_MIJA,
                LimitesFormato.OFFSET_HEADER_PRIMARIO_MIJA}) {
            Header headerMija = bancoMija.headerEn(offsetMija);
            assertTrue(headerMija.slotsMija().stream().allMatch(slotMija -> slotMija.tipoMija() == TipoSlot.VACIO));
        }
        esperar(ErrorBoveda.BovedaDestruida.class, () -> bancoMija.abrir());
        assertTrue(bancoMija.appMija.inspeccionarBoveda()
                .ejecutar(new InspeccionarBoveda.Comando(bancoMija.bovedaMija)).destruidaMija());
    }

    @Test
    void unAccesoCorrectoReiniciaElContador() throws IOException {
        bancoMija.crear(3);
        var malasMija = credenciales(PIN_MIJA, CLAVE_MIJA, "frase equivocada aqui", bancoMija.llaveMija);
        esperar(ErrorBoveda.CredencialesIncorrectas.class, () -> bancoMija.abrir(malasMija));
        esperar(ErrorBoveda.CredencialesIncorrectas.class, () -> bancoMija.abrir(malasMija));
        bancoMija.abrir().close();
        esperar(ErrorBoveda.CredencialesIncorrectas.class, () -> bancoMija.abrir(malasMija));
        bancoMija.abrir().close();
        assertEquals(0, HeaderCodec.decodificar(bancoMija.bloque(0)).intentosFallidosMija());
    }
}
