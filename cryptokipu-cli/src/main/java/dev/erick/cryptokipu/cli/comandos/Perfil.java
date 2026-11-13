package dev.erick.cryptokipu.cli.comandos;

import dev.erick.cryptokipu.core.crypto.ParametrosArgon2;

/** Perfiles de Argon2id elegibles desde la CLI. */
public enum Perfil {
    /** 64 MiB, t=2: equipos con poca memoria (sigue por encima del mínimo de OWASP). */
    ligero(new ParametrosArgon2(64 * 1024, 2, 4)),
    estandar(ParametrosArgon2.ESTANDAR_MIJA),
    alto(ParametrosArgon2.ALTO_MIJA),
    paranoico(ParametrosArgon2.PARANOICO_MIJA);

    private final ParametrosArgon2 parametrosMija;

    Perfil(ParametrosArgon2 parametrosMija) {
        this.parametrosMija = parametrosMija;
    }

    ParametrosArgon2 parametros() {
        return parametrosMija;
    }
}
