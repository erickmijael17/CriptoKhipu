package dev.erick.cryptokipu.app.casos;

import dev.erick.cryptokipu.app.puertos.ContenedorAbierto;
import dev.erick.cryptokipu.core.auth.CambioCredenciales;
import dev.erick.cryptokipu.core.auth.Credenciales;
import dev.erick.cryptokipu.core.auth.ExcepcionValidacion;
import dev.erick.cryptokipu.core.auth.ReglaIncumplida;
import dev.erick.cryptokipu.core.auth.TokenRecuperacion;
import dev.erick.cryptokipu.core.crypto.FuenteAleatoria;
import dev.erick.cryptokipu.core.format.Header;
import dev.erick.cryptokipu.core.vault.GestorSlots;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Cambia credenciales sin recifrar datos. El {@link CambioCredenciales} dice explícitamente qué cambia:
 * si trae F1 → slot 0 + 6 tokens nuevos; si no → solo slot 0. No se guarda nada derivado de F1.
 *
 * <ul>
 *   <li>Sesión normal: {@code vigentesMija} es obligatorio y se verifica contra el slot 0 (re-autenticación);
 *       los factores que no cambian se toman de ahí.</li>
 *   <li>Rotación obligatoria (tras un token): no hay credenciales vigentes conocidas, así que el cambio debe
 *       traer F1, F2, F3 y una decisión sobre la llave; como trae F1, los tokens siempre se regeneran.</li>
 * </ul>
 */
public final class RotarCredenciales {

    public record Comando(SesionBoveda sesionMija, Optional<Credenciales> vigentesMija, CambioCredenciales cambioMija) {
        public Comando {
            Objects.requireNonNull(sesionMija, "sesionMija");
            Objects.requireNonNull(vigentesMija, "vigentesMija");
            Objects.requireNonNull(cambioMija, "cambioMija");
        }
    }

    /** @param tokensNuevosMija los 6 tokens nuevos si cambió F1; vacía si no */
    public record Resultado(List<TokenRecuperacion> tokensNuevosMija) {
    }

    private final GestorSlots gestorMija;

    public RotarCredenciales(FuenteAleatoria fuenteMija) {
        this.gestorMija = new GestorSlots(fuenteMija);
    }

    public Resultado ejecutar(Comando comandoMija) throws IOException {
        SesionBoveda sesionMija = comandoMija.sesionMija();
        ContenedorAbierto contenedorMija = sesionMija.contenedorParaRotar();
        Header headerMija = contenedorMija.header();
        CambioCredenciales cambioMija = comandoMija.cambioMija();

        Credenciales nuevasMija;
        boolean regenerarMija;
        if (sesionMija.rotacionObligatoria()) {
            nuevasMija = cambioMija.completas();
            regenerarMija = true;
        } else {
            Credenciales vigentesMija = comandoMija.vigentesMija().orElseThrow(
                    () -> new ExcepcionValidacion(List.of(ReglaIncumplida.FACTORES_INCOMPLETOS)));
            Autenticacion.exigirCredencialesVigentes(headerMija, gestorMija, vigentesMija);
            nuevasMija = cambioMija.combinar(vigentesMija);
            regenerarMija = cambioMija.cambiaPin();
        }
        try (Credenciales credencialesMija = nuevasMija) {
            Autenticacion.validar(credencialesMija);
            int umbralMija = cambioMija.umbralMija().orElse(headerMija.umbralAutodestruccionMija());
            GestorSlots.Rotacion rotacionMija = contenedorMija.conClaveMaestra(maestraMija ->
                    gestorMija.rotar(headerMija, maestraMija, credencialesMija, umbralMija, regenerarMija));
            contenedorMija.reescribirHeader(rotacionMija.slotsMija(), umbralMija, credencialesMija.tieneLlave());
            sesionMija.rotacionCompletada();
            return new Resultado(rotacionMija.tokensNuevosMija());
        }
    }
}
