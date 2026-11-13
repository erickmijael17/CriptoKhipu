package dev.erick.cryptokipu.core.auth;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Qué factores cambian en una rotación. Un campo vacío significa "sin cambio".
 * Si viene un PIN (F1), se regeneran los 6 tokens; si no, solo se rehace el slot principal.
 * No se guarda ni se compara nada derivado de F1: el usuario dice explícitamente qué cambia.
 *
 * <p>Toma posesión de los arreglos; {@link #close()} los borra.
 */
public record CambioCredenciales(Optional<char[]> pinMija, Optional<char[]> contrasenaMija,
        Optional<char[]> fraseMija, CambioLlave llaveMija, OptionalInt umbralMija) implements AutoCloseable {

    /** Cambio de la llave de seguridad (F4). */
    public sealed interface CambioLlave {
        record SinCambio() implements CambioLlave {
        }

        /** @param huellaMija SHA-256 de la llave nueva (32 B) */
        record Nueva(byte[] huellaMija) implements CambioLlave {
            public Nueva {
                if (Objects.requireNonNull(huellaMija, "huellaMija").length != 32) {
                    throw new IllegalArgumentException("La huella debe medir 32 B");
                }
            }

            @Override
            public String toString() {
                return "Nueva[***]";
            }
        }

        record Quitar() implements CambioLlave {
        }
    }

    public CambioCredenciales {
        Objects.requireNonNull(pinMija, "pinMija");
        Objects.requireNonNull(contrasenaMija, "contrasenaMija");
        Objects.requireNonNull(fraseMija, "fraseMija");
        Objects.requireNonNull(llaveMija, "llaveMija");
        Objects.requireNonNull(umbralMija, "umbralMija");
    }

    public boolean cambiaPin() {
        return pinMija.isPresent();
    }

    /** Credenciales nuevas combinando los cambios con las vigentes. Devuelve copias: quien llama cierra el resultado. */
    public Credenciales combinar(Credenciales vigentesMija) {
        return new Credenciales(
                pinMija.orElse(vigentesMija.pinMija()).clone(),
                contrasenaMija.orElse(vigentesMija.contrasenaMija()).clone(),
                fraseMija.orElse(vigentesMija.fraseMija()).clone(),
                huellaResultante(vigentesMija.huellaLlaveMija()));
    }

    /**
     * Credenciales nuevas cuando no se conocen las vigentes (tras usar un token): exige F1, F2, F3 y una
     * decisión explícita sobre la llave.
     *
     * @throws ExcepcionValidacion con {@link ReglaIncumplida#FACTORES_INCOMPLETOS}
     */
    public Credenciales completas() {
        if (pinMija.isEmpty() || contrasenaMija.isEmpty() || fraseMija.isEmpty()
                || llaveMija instanceof CambioLlave.SinCambio) {
            throw new ExcepcionValidacion(List.of(ReglaIncumplida.FACTORES_INCOMPLETOS));
        }
        return new Credenciales(pinMija.get().clone(), contrasenaMija.get().clone(), fraseMija.get().clone(),
                huellaResultante(null));
    }

    private byte[] huellaResultante(byte[] vigenteMija) {
        return switch (llaveMija) {
            case CambioLlave.SinCambio _ -> vigenteMija == null ? null : vigenteMija.clone();
            case CambioLlave.Nueva nuevaMija -> nuevaMija.huellaMija().clone();
            case CambioLlave.Quitar _ -> null;
        };
    }

    @Override
    public void close() {
        pinMija.ifPresent(arregloMija -> Arrays.fill(arregloMija, '\0'));
        contrasenaMija.ifPresent(arregloMija -> Arrays.fill(arregloMija, '\0'));
        fraseMija.ifPresent(arregloMija -> Arrays.fill(arregloMija, '\0'));
        if (llaveMija instanceof CambioLlave.Nueva nuevaMija) {
            Arrays.fill(nuevaMija.huellaMija(), (byte) 0);
        }
    }

    @Override
    public boolean equals(Object otroMija) {
        return this == otroMija;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return "***";
    }
}
