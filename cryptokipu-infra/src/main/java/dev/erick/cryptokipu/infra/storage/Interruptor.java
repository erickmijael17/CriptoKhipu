package dev.erick.cryptokipu.infra.storage;

import java.io.IOException;

/**
 * Punto de inyección de fallos entre los pasos de una escritura. Solo para pruebas: permite
 * simular un corte de energía o un USB desconectado en cada paso y comprobar que la bóveda sigue abriendo.
 */
@FunctionalInterface
public interface Interruptor {

    Interruptor NINGUNO_MIJA = _ -> { };

    /** Pasos observables de una escritura. */
    enum Paso {
        BLOBS_ESCRITOS,
        MANIFEST_ESCRITO,
        FOOTER_ESCRITO,
        RESPALDO_ESCRITO,
        PRIMARIO_ESCRITO,
        COMPACTACION_LISTA
    }

    void en(Paso pasoMija) throws IOException;
}
