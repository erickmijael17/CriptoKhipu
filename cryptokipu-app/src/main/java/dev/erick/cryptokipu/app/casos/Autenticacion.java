package dev.erick.cryptokipu.app.casos;

import dev.erick.cryptokipu.app.puertos.ContenedorAbierto;
import dev.erick.cryptokipu.core.auth.Credenciales;
import dev.erick.cryptokipu.core.auth.ExcepcionValidacion;
import dev.erick.cryptokipu.core.auth.ReglaIncumplida;
import dev.erick.cryptokipu.core.auth.TokenRecuperacion;
import dev.erick.cryptokipu.core.auth.ValidadorFactores;
import dev.erick.cryptokipu.core.crypto.SecureBytes;
import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import dev.erick.cryptokipu.core.format.Header;
import dev.erick.cryptokipu.core.format.ZonaA;
import dev.erick.cryptokipu.core.registro.Registro;
import dev.erick.cryptokipu.core.vault.GestorSlots;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Utilidades compartidas por los casos de uso de autenticación (paquete privado). */
final class Autenticacion {

    private static final System.Logger REGISTRO_MIJA = Registro.para(Autenticacion.class);

    private Autenticacion() {
    }

    static void validar(Credenciales credencialesMija) {
        List<ReglaIncumplida> reglasMija = ValidadorFactores.validar(credencialesMija.pinMija(),
                credencialesMija.contrasenaMija(), credencialesMija.fraseMija());
        if (!reglasMija.isEmpty()) {
            throw new ExcepcionValidacion(reglasMija);
        }
    }

    /** Si todas las copias del header tienen el slot principal vacío, la bóveda fue autodestruida. */
    static void exigirNoDestruida(ContenedorAbierto contenedorMija) {
        if (contenedorMija.candidatosHeader().stream().allMatch(Header::estaDestruido)) {
            throw new ExcepcionBoveda(new ErrorBoveda.BovedaDestruida());
        }
    }

    /** Desbloquea con F1–F4; la KEK se calcula una vez por Zona A distinta. */
    static void desbloquearConCredenciales(ContenedorAbierto contenedorMija, GestorSlots gestorMija,
            Credenciales credencialesMija) throws IOException {
        Map<ZonaA, SecureBytes> kekPorZonaMija = new HashMap<>();
        try {
            contenedorMija.desbloquear(headerMija -> GestorSlots.abrirPrincipal(headerMija,
                    kekPorZonaMija.computeIfAbsent(headerMija.zonaAMija(),
                            zonaMija -> gestorMija.kekPrincipal(zonaMija, credencialesMija))));
        } finally {
            kekPorZonaMija.values().forEach(SecureBytes::close);
        }
    }

    /** Comprueba que {@code credencialesMija} abren el slot principal del header vigente (re-autenticación). */
    static void exigirCredencialesVigentes(Header headerMija, GestorSlots gestorMija, Credenciales credencialesMija) {
        try (SecureBytes kekMija = gestorMija.kekPrincipal(headerMija.zonaAMija(), credencialesMija)) {
            GestorSlots.abrirPrincipal(headerMija, kekMija).orElseThrow(
                    () -> new ExcepcionBoveda(new ErrorBoveda.CredencialesIncorrectas())).close();
        }
    }

    /**
     * Ante credenciales incorrectas: suma un intento en la Zona C y, si se alcanza el umbral del header,
     * ejecuta la autodestrucción y convierte el error en {@code BovedaDestruida}.
     */
    static ExcepcionBoveda registrarFallo(ContenedorAbierto contenedorMija, ExcepcionBoveda errorMija) {
        if (!(errorMija.error() instanceof ErrorBoveda.CredencialesIncorrectas) || contenedorMija.esSoloLectura()) {
            return errorMija;
        }
        try {
            int intentosMija = contenedorMija.registrarIntentoFallido();
            int umbralMija = contenedorMija.header().umbralAutodestruccionMija();
            if (umbralMija > 0 && intentosMija >= umbralMija) {
                Autodestruir.ejecutar(contenedorMija);
                return new ExcepcionBoveda(new ErrorBoveda.BovedaDestruida());
            }
        } catch (IOException _) {
            REGISTRO_MIJA.log(System.Logger.Level.WARNING, "No se pudo registrar el intento fallido");
        }
        return errorMija;
    }

    static List<TokenRecuperacion> copiar(List<TokenRecuperacion> tokensMija) {
        return tokensMija.stream().map(tokenMija -> new TokenRecuperacion(tokenMija.simbolosMija().clone())).toList();
    }
}
