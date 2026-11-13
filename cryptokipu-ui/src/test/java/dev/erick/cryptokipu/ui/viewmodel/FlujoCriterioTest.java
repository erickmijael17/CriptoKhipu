package dev.erick.cryptokipu.ui.viewmodel;

import static dev.erick.cryptokipu.ui.viewmodel.BancoUi.F1_MIJA;
import static dev.erick.cryptokipu.ui.viewmodel.BancoUi.F2_MIJA;
import static dev.erick.cryptokipu.ui.viewmodel.BancoUi.F3_MIJA;
import static dev.erick.cryptokipu.ui.viewmodel.BancoUi.c;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erick.cryptokipu.app.casos.ExtraerArchivo;
import dev.erick.cryptokipu.app.casos.PoliticaColision;
import dev.erick.cryptokipu.app.casos.SesionBoveda;
import dev.erick.cryptokipu.core.auth.TokenRecuperacion;
import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import dev.erick.cryptokipu.core.format.EntradaManifest;
import dev.erick.cryptokipu.infra.configuracion.Contenedor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Criterio de salida de la Fase 6, recorrido por los ViewModels (lo mismo que hace la interfaz): crear una bóveda
 * con un video, una foto, un Word y un Excel; cerrarla; reabrirla; recuperarla con un token; rotar (obligatorio).
 */
class FlujoCriterioTest {

    @TempDir
    Path carpetaMija;

    @Test
    void crearCerrarReabrirRecuperarYRotar() throws Exception {
        Contenedor appMija = BancoUi.contenedor(carpetaMija);
        EstadoApp estadoMija = new EstadoApp(appMija);
        List<Path> archivosMija = BancoUi.cuatroArchivos(carpetaMija.resolve("originales"));
        Path bovedaMija = carpetaMija.resolve("criterio.kipu");

        // 1. Crear (asistente) y mostrar tokens: sin confirmar no se puede continuar.
        AsistenteCreacionViewModel asistenteMija = BancoUi.asistenteListo(appMija, bovedaMija, archivosMija);
        asistenteMija.evaluarDestino();
        assertTrue(asistenteMija.avisosDestino().stream().noneMatch(Aviso::bloqueanteMija));
        AsistenteCreacionViewModel.Creada creadaMija = asistenteMija.crear(ProgresoOperacion.NINGUNO_MIJA);
        assertEquals(4, creadaMija.archivosMija().agregadosMija().size());
        assertEquals(6, creadaMija.tokensMija().size());
        char[] tokenEscritoMija = creadaMija.tokensMija().get(2).formatear();
        TokensViewModel tokensMija = new TokensViewModel();
        tokensMija.cargar(creadaMija.tokensMija());
        assertFalse(tokensMija.continuar());
        tokensMija.confirmadoProperty().set(true);
        assertTrue(tokensMija.continuar());
        assertTrue(tokensMija.visibles().isEmpty());

        // 2. Abrir en la interfaz y cerrar.
        estadoMija.abrir(creadaMija.rutaMija(), creadaMija.sesionMija());
        assertTrue(estadoMija.abiertaProperty().get());
        estadoMija.cerrar();
        assertTrue(creadaMija.sesionMija().cerrada());

        // 3. Reabrir con las credenciales y extraer el video: idéntico al original.
        DesbloqueoViewModel desbloqueoMija = new DesbloqueoViewModel(appMija);
        desbloqueoMija.rutaProperty().set(bovedaMija);
        desbloqueoMija.aplicar(desbloqueoMija.inspeccionar());
        assertFalse(desbloqueoMija.requiereLlaveProperty().get());
        estadoMija.abrir(bovedaMija, desbloqueoMija.desbloquear(c(F1_MIJA), c(F2_MIJA), c(F3_MIJA)));
        List<EntradaManifest> entradasMija = estadoMija.sesion().manifest().entradasMija();
        assertEquals(List.of("foto.jpg", "informe.docx", "presupuesto.xlsx", "viaje.mp4"),
                entradasMija.stream().map(EntradaManifest::rutaMija).sorted().toList());
        ExploradorViewModel exploradorMija = new ExploradorViewModel(appMija);
        Path extraidosMija = carpetaMija.resolve("extraidos");
        ExtraerArchivo.Resultado extraccionMija = exploradorMija.extraer(estadoMija.sesion(), entradasMija,
                extraidosMija, PoliticaColision.OMITIR, ProgresoOperacion.NINGUNO_MIJA);
        assertEquals(4, extraccionMija.extraidosMija().size());
        for (Path originalMija : archivosMija) {
            assertArrayEquals(Files.readAllBytes(originalMija),
                    Files.readAllBytes(extraidosMija.resolve(originalMija.getFileName())));
        }
        estadoMija.cerrar();

        // 4. Recuperar con un token: la sesión queda en rotación obligatoria y no permite operar.
        SesionBoveda recuperadaMija = desbloqueoMija.recuperar(tokenEscritoMija, c(F1_MIJA));
        estadoMija.abrir(bovedaMija, recuperadaMija);
        assertTrue(estadoMija.rotacionObligatoriaProperty().get());
        ExcepcionBoveda pendienteMija = assertThrows(ExcepcionBoveda.class, () -> estadoMija.sesion().manifest());
        assertInstanceOf(ErrorBoveda.RotacionPendiente.class, pendienteMija.error());

        // 5. Rotación obligatoria: no se puede cerrar hasta terminar; entrega 6 tokens nuevos.
        CambioCredencialesViewModel rotacionMija = new CambioCredencialesViewModel(appMija, true);
        assertFalse(rotacionMija.solicitarCierre());
        List<TokenRecuperacion> nuevosMija = rotacionMija.rotar(estadoMija.sesion(), Optional.empty(),
                c("918273645501"), c("NuevaClave2027"), c("la llama sube al cerro"));
        assertEquals(6, nuevosMija.size());
        rotacionMija.rotacionTerminada();
        estadoMija.rotacionTerminada();
        assertTrue(rotacionMija.solicitarCierre());
        assertEquals(4, estadoMija.sesion().manifest().entradasMija().size());
        estadoMija.cerrar();
        nuevosMija.forEach(TokenRecuperacion::close);

        // 6. Las credenciales viejas ya no abren; las nuevas sí.
        ExcepcionBoveda viejasMija = assertThrows(ExcepcionBoveda.class,
                () -> desbloqueoMija.desbloquear(c(F1_MIJA), c(F2_MIJA), c(F3_MIJA)));
        assertInstanceOf(ErrorBoveda.CredencialesIncorrectas.class, viejasMija.error());
        try (SesionBoveda nuevaMija = desbloqueoMija.desbloquear(c("918273645501"), c("NuevaClave2027"),
                c("la llama sube al cerro"))) {
            assertFalse(nuevaMija.rotacionObligatoria());
        }
    }
}
