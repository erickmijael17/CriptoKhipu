package dev.erick.cryptokipu.ui.viewmodel;

import static dev.erick.cryptokipu.ui.viewmodel.BancoUi.F1_MIJA;
import static dev.erick.cryptokipu.ui.viewmodel.BancoUi.F2_MIJA;
import static dev.erick.cryptokipu.ui.viewmodel.BancoUi.F3_MIJA;
import static dev.erick.cryptokipu.ui.viewmodel.BancoUi.c;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erick.cryptokipu.app.casos.SesionBoveda;
import dev.erick.cryptokipu.app.mensajes.CatalogoMensajes;
import dev.erick.cryptokipu.core.auth.Credenciales;
import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import dev.erick.cryptokipu.infra.configuracion.Contenedor;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Reglas de seguridad de la interfaz verificadas en los ViewModels (las mutaciones de la Fase 6 las rompen). */
class SeguridadInterfazTest {

    @TempDir
    Path carpetaMija;

    private Contenedor appMija;
    private Path bovedaMija;
    private AsistenteCreacionViewModel.Creada creadaMija;

    @BeforeEach
    void crearBoveda() throws Exception {
        appMija = BancoUi.contenedor(carpetaMija);
        bovedaMija = carpetaMija.resolve("b.kipu");
        creadaMija = BancoUi.asistenteListo(appMija, bovedaMija, List.of()).crear(ProgresoOperacion.NINGUNO_MIJA);
    }

    private void cerrarCreada() {
        creadaMija.sesionMija().close();
    }

    // ------------------------------------------------------------------ mutación: derivar en el hilo de JavaFX

    @Test
    void lasOperacionesLargasFallanEnElHiloDeJavaFx() throws Exception {
        cerrarCreada();
        AsistenteCreacionViewModel asistenteMija = BancoUi.asistenteListo(appMija, carpetaMija.resolve("otra.kipu"),
                List.of());
        assertInstanceOf(IllegalStateException.class,
                BancoUi.enHiloFx(() -> asistenteMija.crear(ProgresoOperacion.NINGUNO_MIJA)));
        assertFalse(Files.exists(carpetaMija.resolve("otra.kipu")), "No debe haber derivado ni escrito nada");

        DesbloqueoViewModel desbloqueoMija = BancoUi.desbloqueoSinEspera(appMija);
        desbloqueoMija.rutaProperty().set(bovedaMija);
        assertInstanceOf(IllegalStateException.class,
                BancoUi.enHiloFx(() -> desbloqueoMija.desbloquear(c(F1_MIJA), c(F2_MIJA), c(F3_MIJA))));
        assertInstanceOf(IllegalStateException.class, BancoUi.enHiloFx(desbloqueoMija::inspeccionar));
        assertInstanceOf(IllegalStateException.class,
                BancoUi.enHiloFx(() -> desbloqueoMija.recuperar(c("CKP-AAAAA"), c(F1_MIJA))));
        CambioCredencialesViewModel rotacionMija = new CambioCredencialesViewModel(appMija, true);
        assertInstanceOf(IllegalStateException.class, BancoUi.enHiloFx(() -> rotacionMija.rotar(null,
                Optional.empty(), c(F1_MIJA), c(F2_MIJA), c(F3_MIJA))));
    }

    // ------------------------------------------------------------------ mutación: mensaje distinto por factor

    @Test
    void cualquierFactorIncorrectoProduceElMismoMensaje() throws Exception {
        cerrarCreada();
        DesbloqueoViewModel desbloqueoMija = BancoUi.desbloqueoSinEspera(appMija);
        desbloqueoMija.rutaProperty().set(bovedaMija);
        List<String> mensajesMija = new ArrayList<>();
        String[][] intentosMija = {
            {"999999999999", F2_MIJA, F3_MIJA},
            {F1_MIJA, "Otra1Distinta", F3_MIJA},
            {F1_MIJA, F2_MIJA, "otra frase distinta aqui"},
            // Ni siquiera cumple la política: al desbloquear no se valida, se deriva y falla igual.
            {F1_MIJA, "corta", F3_MIJA},
        };
        for (String[] intentoMija : intentosMija) {
            ExcepcionBoveda errorMija = assertThrows(ExcepcionBoveda.class, () -> desbloqueoMija.desbloquear(
                    c(intentoMija[0]), c(intentoMija[1]), c(intentoMija[2])));
            desbloqueoMija.mostrarError(errorMija);
            mensajesMija.add(desbloqueoMija.mensajeErrorProperty().get());
        }
        ExcepcionBoveda tokenMija = assertThrows(ExcepcionBoveda.class,
                () -> desbloqueoMija.recuperar(c("CKP-AAAAA-AAAAA-AAAAA-AAAAA-AAAAA-A"), c(F1_MIJA)));
        if (!(tokenMija.error() instanceof ErrorBoveda.TokenMalEscrito)) {
            desbloqueoMija.mostrarError(tokenMija);
            mensajesMija.add(desbloqueoMija.mensajeErrorProperty().get());
        }
        String esperadoMija = CatalogoMensajes.mensaje(new ErrorBoveda.CredencialesIncorrectas());
        assertTrue(mensajesMija.stream().allMatch(esperadoMija::equals), mensajesMija::toString);
        assertFalse(esperadoMija.contains("F1") || esperadoMija.contains("F2") || esperadoMija.contains("F3"));
    }

    // ------------------------------------------------------------------ mutación: continuar sin confirmar tokens

    @Test
    void tokensExigenConfirmacionYNuncaSobrescribenElTxt() throws Exception {
        TokensViewModel tokensMija = new TokensViewModel();
        tokensMija.cargar(creadaMija.tokensMija());
        assertEquals(6, tokensMija.visibles().size());
        assertFalse(tokensMija.puedeContinuarProperty().get());
        assertFalse(tokensMija.continuar(), "Sin la casilla marcada no se puede continuar");
        assertEquals(6, tokensMija.visibles().size(), "Rechazar no borra los tokens");

        Path txtMija = carpetaMija.resolve("tokens.txt");
        tokensMija.guardarTxt(txtMija, "b.kipu");
        assertTrue(tokensMija.guardadoProperty().get());
        String contenidoMija = Files.readString(txtMija);
        tokensMija.visibles().forEach(visibleMija -> assertTrue(contenidoMija.contains(visibleMija)));
        assertThrows(FileAlreadyExistsException.class, () -> tokensMija.guardarTxt(txtMija, "b.kipu"));
        assertEquals(contenidoMija, Files.readString(txtMija));

        tokensMija.confirmadoProperty().set(true);
        assertTrue(tokensMija.continuar());
        assertTrue(tokensMija.visibles().isEmpty());
        assertFalse(tokensMija.confirmadoProperty().get());
        cerrarCreada();
    }

    // ------------------------------------------------------------------ mutación: saltarse la rotación obligatoria

    @Test
    void laRotacionObligatoriaNoSePuedeSaltarNiHacerAMedias() throws Exception {
        char[] tokenMija = creadaMija.tokensMija().getFirst().formatear();
        char[] copiaTokenMija = tokenMija.clone();
        cerrarCreada();
        DesbloqueoViewModel desbloqueoMija = BancoUi.desbloqueoSinEspera(appMija);
        desbloqueoMija.rutaProperty().set(bovedaMija);
        EstadoApp estadoMija = new EstadoApp(appMija);
        SesionBoveda sesionMija = desbloqueoMija.recuperar(tokenMija, c(F1_MIJA));
        estadoMija.abrir(bovedaMija, sesionMija);
        assertTrue(estadoMija.rotacionObligatoriaProperty().get());

        CambioCredencialesViewModel rotacionMija = new CambioCredencialesViewModel(appMija, true);
        assertFalse(rotacionMija.solicitarCierre());
        assertFalse(rotacionMija.puedeCerrarProperty().get());
        assertTrue(rotacionMija.cambiaF1Property().get() && rotacionMija.cambiaF2Property().get()
                && rotacionMija.cambiaF3Property().get());
        // Falta un factor: rechazado, y la sesión sigue bloqueada.
        assertThrows(IllegalArgumentException.class, () -> rotacionMija.rotar(sesionMija, Optional.empty(),
                c("918273645501"), null, c("la llama sube al cerro")));
        rotacionMija.llaveProperty().set(CambioCredencialesViewModel.DecisionLlave.SIN_CAMBIO);
        assertThrows(IllegalArgumentException.class, () -> rotacionMija.rotar(sesionMija, Optional.empty(),
                c("918273645501"), c("NuevaClave2027"), c("la llama sube al cerro")));
        assertFalse(rotacionMija.solicitarCierre());
        assertThrows(ExcepcionBoveda.class, sesionMija::manifest);
        estadoMija.cerrar();

        // Al reabrir con el token ya usado: rechazado (los tokens son de un solo uso).
        ExcepcionBoveda usadoMija = assertThrows(ExcepcionBoveda.class,
                () -> desbloqueoMija.recuperar(copiaTokenMija, c(F1_MIJA)));
        assertInstanceOf(ErrorBoveda.CredencialesIncorrectas.class, usadoMija.error());
    }

    @Test
    void elAvisoDeIntentosRestantesApareceSoloConAutodestruccion() throws Exception {
        SeguridadViewModel seguridadMija = new SeguridadViewModel(appMija);
        try (Credenciales vigentesMija = new Credenciales(c(F1_MIJA), c(F2_MIJA), c(F3_MIJA), null)) {
            seguridadMija.cambiarUmbral(creadaMija.sesionMija(), vigentesMija, 5, ProgresoOperacion.NINGUNO_MIJA);
        }
        cerrarCreada();
        DesbloqueoViewModel desbloqueoMija = BancoUi.desbloqueoSinEspera(appMija);
        desbloqueoMija.rutaProperty().set(bovedaMija);
        desbloqueoMija.aplicar(desbloqueoMija.inspeccionar());
        assertTrue(desbloqueoMija.avisoIntentosProperty().get().contains("5"),
                desbloqueoMija.avisoIntentosProperty()::get);
        assertThrows(ExcepcionBoveda.class, () -> desbloqueoMija.desbloquear(c("999999999999"), c(F2_MIJA),
                c(F3_MIJA)));
        desbloqueoMija.aplicar(desbloqueoMija.inspeccionar());
        assertTrue(desbloqueoMija.avisoIntentosProperty().get().contains("4"),
                desbloqueoMija.avisoIntentosProperty()::get);
    }
}
