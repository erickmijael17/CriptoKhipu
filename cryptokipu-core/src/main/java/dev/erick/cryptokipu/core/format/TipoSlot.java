package dev.erick.cryptokipu.core.format;

import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;

/** Tipo de un key slot. Un slot {@link #VACIO} contiene bytes aleatorios (token usado o autodestrucción). */
public enum TipoSlot {
    VACIO(0),
    PRINCIPAL(1),
    TOKEN(2);

    private final int codigoMija;

    TipoSlot(int codigoMija) {
        this.codigoMija = codigoMija;
    }

    public int codigo() {
        return codigoMija;
    }

    public static TipoSlot desdeCodigo(int codigoMija) {
        for (TipoSlot tipoMija : values()) {
            if (tipoMija.codigoMija == codigoMija) {
                return tipoMija;
            }
        }
        throw new ExcepcionBoveda(new ErrorBoveda.FormatoNoSoportado(codigoMija));
    }
}
