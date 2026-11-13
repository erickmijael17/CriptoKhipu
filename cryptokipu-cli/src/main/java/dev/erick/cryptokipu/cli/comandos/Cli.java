package dev.erick.cryptokipu.cli.comandos;

import dev.erick.cryptokipu.app.mensajes.CatalogoMensajes;
import dev.erick.cryptokipu.cli.CodigosSalida;
import dev.erick.cryptokipu.cli.Entorno;
import dev.erick.cryptokipu.cli.ErrorUso;
import dev.erick.cryptokipu.core.auth.ExcepcionValidacion;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.util.Objects;
import picocli.CommandLine;

/** Arma la línea de comandos con el {@link Entorno} inyectado y traduce errores a mensajes y códigos de salida. */
public final class Cli {

    private Cli() {
    }

    public static CommandLine lineaDeComandos(Entorno entornoMija) {
        Objects.requireNonNull(entornoMija, "entornoMija");
        CommandLine lineaMija = new CommandLine(new Kipu(), new Fabrica(entornoMija));
        lineaMija.setOut(new PrintWriter(entornoMija.salidaMija(), true));
        lineaMija.setErr(new PrintWriter(entornoMija.erroresMija(), true));
        lineaMija.setExecutionExceptionHandler((errorMija, _, _) -> traducir(errorMija, entornoMija));
        return lineaMija;
    }

    /** Ejecuta {@code kipu} y devuelve el código de salida. */
    public static int ejecutar(String[] argumentosMija, Entorno entornoMija) {
        return lineaDeComandos(entornoMija).execute(argumentosMija);
    }

    private static int traducir(Exception errorMija, Entorno entornoMija) {
        return switch (errorMija) {
            case ExcepcionBoveda bovedaMija -> {
                entornoMija.erroresMija().println(CatalogoMensajes.mensaje(bovedaMija.error()));
                yield CodigosSalida.de(bovedaMija.error());
            }
            case ExcepcionValidacion validacionMija -> {
                validacionMija.reglas().forEach(reglaMija ->
                        entornoMija.erroresMija().println("- " + CatalogoMensajes.mensaje(reglaMija)));
                yield CodigosSalida.USO_MIJA;
            }
            case ErrorUso usoMija -> {
                entornoMija.erroresMija().println(usoMija.getMessage());
                yield CodigosSalida.USO_MIJA;
            }
            default -> {
                entornoMija.erroresMija().println("Error: " + errorMija.getClass().getSimpleName()
                        + (errorMija.getMessage() == null ? "" : " — " + errorMija.getMessage()));
                yield CodigosSalida.GENERAL_MIJA;
            }
        };
    }

    /** Crea los subcomandos pasándoles el entorno. */
    private record Fabrica(Entorno entornoMija) implements CommandLine.IFactory {
        @Override
        public <K> K create(Class<K> claseMija) throws Exception {
            if (ComandoBase.class.isAssignableFrom(claseMija)) {
                Constructor<K> constructorMija = claseMija.getDeclaredConstructor(Entorno.class);
                constructorMija.setAccessible(true);
                return constructorMija.newInstance(entornoMija);
            }
            return CommandLine.defaultFactory().create(claseMija);
        }
    }
}
