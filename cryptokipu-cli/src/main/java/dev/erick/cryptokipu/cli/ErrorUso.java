package dev.erick.cryptokipu.cli;

import java.io.Serial;

/** Uso incorrecto de la CLI (código de salida 2). El mensaje es para el usuario y nunca contiene secretos. */
public final class ErrorUso extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ErrorUso(String mensajeMija) {
        super(mensajeMija, null, false, false);
    }
}
