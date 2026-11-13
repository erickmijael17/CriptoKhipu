package dev.erick.cryptokipu.app.casos;

import dev.erick.cryptokipu.app.puertos.ContenedorAbierto;
import dev.erick.cryptokipu.core.auth.Credenciales;
import dev.erick.cryptokipu.core.auth.TokenRecuperacion;
import dev.erick.cryptokipu.core.crypto.FuenteAleatoria;
import dev.erick.cryptokipu.core.format.Header;
import dev.erick.cryptokipu.core.vault.GestorSlots;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Reemplaza los 6 tokens por otros nuevos (los anteriores dejan de servir). Exige las credenciales vigentes. */
public final class RegenerarTokens {

    public record Comando(SesionBoveda sesionMija, Credenciales vigentesMija) {
        public Comando {
            Objects.requireNonNull(sesionMija, "sesionMija");
            Objects.requireNonNull(vigentesMija, "vigentesMija");
        }
    }

    public record Resultado(List<TokenRecuperacion> tokensMija) {
    }

    private final GestorSlots gestorMija;

    public RegenerarTokens(FuenteAleatoria fuenteMija) {
        this.gestorMija = new GestorSlots(fuenteMija);
    }

    public Resultado ejecutar(Comando comandoMija) throws IOException {
        ContenedorAbierto contenedorMija = comandoMija.sesionMija().contenedor();
        Header headerMija = contenedorMija.header();
        Autenticacion.exigirCredencialesVigentes(headerMija, gestorMija, comandoMija.vigentesMija());
        GestorSlots.Rotacion rotacionMija = contenedorMija.conClaveMaestra(
                maestraMija -> gestorMija.regenerarTokens(headerMija, maestraMija, comandoMija.vigentesMija().pinMija()));
        contenedorMija.reescribirHeader(rotacionMija.slotsMija(), headerMija.umbralAutodestruccionMija(),
                headerMija.requiereLlaveMija());
        return new Resultado(rotacionMija.tokensNuevosMija());
    }
}
