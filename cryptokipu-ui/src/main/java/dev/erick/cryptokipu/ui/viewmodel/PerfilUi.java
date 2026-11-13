package dev.erick.cryptokipu.ui.viewmodel;

import dev.erick.cryptokipu.core.crypto.ParametrosArgon2;

/** Perfiles de Argon2id ofrecidos en la interfaz. La RAM se exige en CUALQUIER equipo donde se abra la bóveda. */
public enum PerfilUi {
    LIGERO(new ParametrosArgon2(64 * 1024, 2, 4)),
    ESTANDAR(ParametrosArgon2.ESTANDAR_MIJA),
    ALTO(ParametrosArgon2.ALTO_MIJA),
    PARANOICO(ParametrosArgon2.PARANOICO_MIJA);

    private final ParametrosArgon2 parametrosMija;

    PerfilUi(ParametrosArgon2 parametrosMija) {
        this.parametrosMija = parametrosMija;
    }

    public ParametrosArgon2 parametros() {
        return parametrosMija;
    }

    /** Memoria recomendada libre: la de Argon2 más un margen para la aplicación (~1,5×). */
    public long ramRecomendadaMib() {
        return parametrosMija.memoriaBytes() * 3 / 2 / (1024 * 1024) + 256;
    }
}
