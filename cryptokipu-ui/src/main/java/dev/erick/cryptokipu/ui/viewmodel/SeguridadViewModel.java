package dev.erick.cryptokipu.ui.viewmodel;

import dev.erick.cryptokipu.app.casos.Compactar;
import dev.erick.cryptokipu.app.casos.RegenerarTokens;
import dev.erick.cryptokipu.app.casos.RotarCredenciales;
import dev.erick.cryptokipu.app.casos.SesionBoveda;
import dev.erick.cryptokipu.app.casos.VerificarIntegridad;
import dev.erick.cryptokipu.core.auth.CambioCredenciales;
import dev.erick.cryptokipu.core.auth.Credenciales;
import dev.erick.cryptokipu.core.auth.TokenRecuperacion;
import dev.erick.cryptokipu.infra.configuracion.Contenedor;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

/** Pantalla de seguridad: regenerar tokens, autodestrucción, verificar integridad y compactar. */
public final class SeguridadViewModel {

    private final Contenedor appMija;
    private final IntegerProperty umbralMija = new SimpleIntegerProperty();

    public SeguridadViewModel(Contenedor appMija) {
        this.appMija = Objects.requireNonNull(appMija, "appMija");
    }

    public List<TokenRecuperacion> regenerarTokens(SesionBoveda sesionMija, Credenciales vigentesMija,
            ProgresoOperacion progresoMija) throws IOException {
        HiloFx.exigirFuera();
        progresoMija.etapa("etapa.derivando", false);
        return appMija.regenerarTokens().ejecutar(new RegenerarTokens.Comando(sesionMija, vigentesMija)).tokensMija();
    }

    /** Cambia el umbral de autodestrucción (0 = desactivada). Rehace el slot principal: exige credenciales. */
    public void cambiarUmbral(SesionBoveda sesionMija, Credenciales vigentesMija, int nuevoUmbralMija,
            ProgresoOperacion progresoMija) throws IOException {
        HiloFx.exigirFuera();
        progresoMija.etapa("etapa.derivando", false);
        try (CambioCredenciales soloUmbralMija = new CambioCredenciales(Optional.empty(), Optional.empty(),
                Optional.empty(), new CambioCredenciales.CambioLlave.SinCambio(), OptionalInt.of(nuevoUmbralMija))) {
            appMija.rotarCredenciales().ejecutar(new RotarCredenciales.Comando(sesionMija, Optional.of(vigentesMija),
                    soloUmbralMija));
        }
    }

    public VerificarIntegridad.Resultado verificar(SesionBoveda sesionMija, ProgresoOperacion progresoMija)
            throws IOException {
        HiloFx.exigirFuera();
        progresoMija.etapa("etapa.verificando", true);
        return appMija.verificarIntegridad().ejecutar(new VerificarIntegridad.Comando(sesionMija, progresoMija));
    }

    public void compactar(SesionBoveda sesionMija, ProgresoOperacion progresoMija) throws IOException {
        HiloFx.exigirFuera();
        progresoMija.etapa("etapa.compactando", false);
        appMija.compactar().ejecutar(new Compactar.Comando(sesionMija));
    }

    public IntegerProperty umbralProperty() {
        return umbralMija;
    }
}
