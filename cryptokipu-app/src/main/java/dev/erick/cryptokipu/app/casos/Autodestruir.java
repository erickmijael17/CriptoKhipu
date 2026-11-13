package dev.erick.cryptokipu.app.casos;

import dev.erick.cryptokipu.app.puertos.ContenedorAbierto;
import dev.erick.cryptokipu.core.registro.Registro;
import java.io.IOException;

/**
 * Autodestrucción (interno): sobrescribe todos los slots en ambos headers, respaldo primero, con {@code fsync}.
 * La Zona A queda intacta para poder informar {@code BovedaDestruida}. La clave maestra se vuelve irrecuperable.
 * Solo protege contra intentos dentro de la app (THREAT_MODEL, ADR-006).
 */
final class Autodestruir {

    private static final System.Logger REGISTRO_MIJA = Registro.para(Autodestruir.class);

    private Autodestruir() {
    }

    static void ejecutar(ContenedorAbierto contenedorMija) throws IOException {
        contenedorMija.destruirSlots();
        REGISTRO_MIJA.log(System.Logger.Level.WARNING, "Umbral de intentos alcanzado: bóveda autodestruida");
    }
}
