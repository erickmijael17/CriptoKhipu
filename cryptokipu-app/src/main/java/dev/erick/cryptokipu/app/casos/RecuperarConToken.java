package dev.erick.cryptokipu.app.casos;

import dev.erick.cryptokipu.app.puertos.ContenedorAbierto;
import dev.erick.cryptokipu.app.puertos.RepositorioBoveda;
import dev.erick.cryptokipu.core.auth.TokenRecuperacion;
import dev.erick.cryptokipu.core.crypto.FuenteAleatoria;
import dev.erick.cryptokipu.core.crypto.SecureBytes;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import dev.erick.cryptokipu.core.format.Header;
import dev.erick.cryptokipu.core.format.ZonaA;
import dev.erick.cryptokipu.core.vault.GestorSlots;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Recupera con token + F1. El slot usado se borra en ambos headers (respaldo y luego primario) <b>antes</b>
 * de entregar la sesión, que queda en rotación obligatoria. El token llega ya leído con
 * {@link TokenRecuperacion#leer}, que rechaza los mal escritos antes de derivar.
 */
public final class RecuperarConToken {

    public record Comando(Path rutaMija, TokenRecuperacion tokenMija, char[] pinMija) {
        public Comando {
            Objects.requireNonNull(rutaMija, "rutaMija");
            Objects.requireNonNull(tokenMija, "tokenMija");
            Objects.requireNonNull(pinMija, "pinMija");
        }

        @Override
        public String toString() {
            return "RecuperarConToken.Comando[***]";
        }
    }

    private final RepositorioBoveda repositorioMija;
    private final GestorSlots gestorMija;

    public RecuperarConToken(RepositorioBoveda repositorioMija, FuenteAleatoria fuenteMija) {
        this.repositorioMija = Objects.requireNonNull(repositorioMija, "repositorioMija");
        this.gestorMija = new GestorSlots(fuenteMija);
    }

    public SesionBoveda ejecutar(Comando comandoMija) throws IOException {
        ContenedorAbierto contenedorMija = repositorioMija.abrir(comandoMija.rutaMija());
        Map<ZonaA, SecureBytes> claveF1PorZonaMija = new HashMap<>();
        int[] indiceUsadoMija = {-1};
        try {
            if (contenedorMija.esSoloLectura()) {
                throw new IllegalStateException("En solo lectura no se puede consumir un token");
            }
            Autenticacion.exigirNoDestruida(contenedorMija);
            contenedorMija.desbloquear(headerMija -> {
                SecureBytes claveF1Mija = claveF1PorZonaMija.computeIfAbsent(headerMija.zonaAMija(),
                        zonaMija -> gestorMija.claveF1(zonaMija, comandoMija.pinMija()));
                Optional<GestorSlots.AperturaToken> aperturaMija =
                        GestorSlots.abrirConToken(headerMija, claveF1Mija, comandoMija.tokenMija());
                indiceUsadoMija[0] = aperturaMija.map(GestorSlots.AperturaToken::indiceMija).orElse(-1);
                return aperturaMija.map(GestorSlots.AperturaToken::claveMaestraMija);
            });
            Header headerMija = contenedorMija.header();
            contenedorMija.reescribirHeader(gestorMija.conSlotVacio(headerMija, indiceUsadoMija[0]),
                    headerMija.umbralAutodestruccionMija(), headerMija.requiereLlaveMija());
            if (headerMija.intentosFallidosMija() > 0) {
                contenedorMija.reiniciarIntentos();
            }
            return new SesionBoveda(contenedorMija, true);
        } catch (ExcepcionBoveda errorMija) {
            ExcepcionBoveda finalMija = Autenticacion.registrarFallo(contenedorMija, errorMija);
            contenedorMija.close();
            throw finalMija;
        } catch (IOException | RuntimeException errorMija) {
            contenedorMija.close();
            throw errorMija;
        } finally {
            claveF1PorZonaMija.values().forEach(SecureBytes::close);
        }
    }
}
