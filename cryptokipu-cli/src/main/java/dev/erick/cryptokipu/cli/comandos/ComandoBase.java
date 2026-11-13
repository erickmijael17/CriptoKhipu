package dev.erick.cryptokipu.cli.comandos;

import dev.erick.cryptokipu.app.casos.DesbloquearBoveda;
import dev.erick.cryptokipu.app.casos.InspeccionarBoveda;
import dev.erick.cryptokipu.app.casos.SesionBoveda;
import dev.erick.cryptokipu.cli.Entorno;
import dev.erick.cryptokipu.cli.ErrorUso;
import dev.erick.cryptokipu.cli.LectorFlujo;
import dev.erick.cryptokipu.cli.LectorSecretos;
import dev.erick.cryptokipu.core.auth.Credenciales;
import dev.erick.cryptokipu.core.auth.TokenRecuperacion;
import dev.erick.cryptokipu.infra.autenticacion.ExportadorTokensTxt;
import dev.erick.cryptokipu.infra.autenticacion.LlaveSeguridad;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Mixin;

/** Utilidades comunes de los subcomandos: secretos, confirmaciones, apertura de sesión y salida de tokens. */
abstract class ComandoBase implements Callable<Integer> {

    protected final Entorno entornoMija;

    @Mixin
    OpcionesComunes comunesMija;

    private LectorSecretos lectorMija;
    private Credenciales vigentesGuardadasMija;

    ComandoBase(Entorno entornoMija) {
        this.entornoMija = Objects.requireNonNull(entornoMija, "entornoMija");
    }

    protected LectorSecretos lector() {
        if (lectorMija == null) {
            if (comunesMija.secretosStdinMija) {
                lectorMija = new LectorFlujo(entornoMija.entradaMija(), comunesMija.charsetMija, entornoMija.erroresMija());
            } else {
                lectorMija = entornoMija.lectorConsolaMija().orElseThrow(() -> new ErrorUso(
                        "No hay una consola interactiva para pedir secretos. Usa --secretos-stdin."));
            }
        }
        return lectorMija;
    }

    /** Pide un secreto nuevo dos veces y comprueba que coincidan (al crear o rotar). */
    protected char[] secretoNuevo(String etiquetaMija) {
        char[] primeroMija = lector().leerSecreto(etiquetaMija);
        char[] segundoMija = lector().leerSecreto(etiquetaMija + " (repetir)");
        try {
            if (!Arrays.equals(primeroMija, segundoMija)) {
                Arrays.fill(primeroMija, '\0');
                throw new ErrorUso("Los valores no coinciden.");
            }
            return primeroMija;
        } finally {
            Arrays.fill(segundoMija, '\0');
        }
    }

    /** Lee F1, F2, F3 vigentes; la llave sale de {@code llaveMija} (o ninguna). */
    protected Credenciales credencialesVigentes(Path llaveMija) throws IOException {
        char[] pinMija = lector().leerSecreto("Contraseña numérica (F1)");
        char[] claveMija = lector().leerSecreto("Contraseña alfanumérica (F2)");
        char[] fraseMija = lector().leerSecreto("Frase de paso (F3)");
        return new Credenciales(pinMija, claveMija, fraseMija, huella(llaveMija));
    }

    /** Lee F1, F2, F3 nuevos, cada uno con confirmación. */
    protected Credenciales credencialesNuevas(Path llaveMija) throws IOException {
        char[] pinMija = secretoNuevo("Nueva contraseña numérica (F1)");
        char[] claveMija = secretoNuevo("Nueva contraseña alfanumérica (F2)");
        char[] fraseMija = secretoNuevo("Nueva frase de paso (F3)");
        return new Credenciales(pinMija, claveMija, fraseMija, huella(llaveMija));
    }

    protected byte[] huella(Path llaveMija) throws IOException {
        if (llaveMija == null) {
            return null;
        }
        return LlaveSeguridad.huellaBytes(llaveMija, entornoMija.cancelacionMija());
    }

    /** Pide las credenciales y desbloquea. Si la bóveda requiere llave y no se indicó, es un error de uso. */
    protected SesionBoveda abrir(Path bovedaMija, Path llaveMija) throws IOException {
        return abrir(bovedaMija, llaveMija, false);
    }

    /**
     * Igual que {@link #abrir(Path, Path)}; con {@code conservarMija} guarda una copia de las credenciales
     * vigentes para re-autenticar en la misma ejecución (rotar, regenerar tokens) sin pedirlas otra vez.
     * Se obtiene con {@link #tomarVigentes()}, que pasa la responsabilidad de borrarla.
     */
    protected SesionBoveda abrir(Path bovedaMija, Path llaveMija, boolean conservarMija) throws IOException {
        InspeccionarBoveda.Resultado infoMija = entornoMija.appMija().inspeccionarBoveda()
                .ejecutar(new InspeccionarBoveda.Comando(bovedaMija));
        if (infoMija.requiereLlaveMija() && llaveMija == null) {
            throw new ErrorUso("Esta bóveda requiere la llave de seguridad: indica --keyfile <ruta>.");
        }
        try (Credenciales credencialesMija = credencialesVigentes(llaveMija)) {
            SesionBoveda sesionMija = entornoMija.appMija().desbloquearBoveda()
                    .ejecutar(new DesbloquearBoveda.Comando(bovedaMija, credencialesMija));
            if (conservarMija) {
                vigentesGuardadasMija = new Credenciales(credencialesMija.pinMija().clone(),
                        credencialesMija.contrasenaMija().clone(), credencialesMija.fraseMija().clone(),
                        credencialesMija.tieneLlave() ? credencialesMija.huellaLlaveMija().clone() : null);
            }
            return sesionMija;
        }
    }

    protected Credenciales tomarVigentes() {
        Credenciales copiaMija = vigentesGuardadasMija;
        vigentesGuardadasMija = null;
        if (copiaMija == null) {
            throw new IllegalStateException("No se conservaron las credenciales vigentes");
        }
        return copiaMija;
    }

    /** Confirmación para acciones destructivas; {@code --yes} la omite. */
    protected void confirmar(String preguntaMija) {
        if (comunesMija.siMija) {
            return;
        }
        char[] respuestaMija = lector().leerLinea(preguntaMija + " [s/N]");
        try {
            String textoMija = new String(respuestaMija).trim().toLowerCase(java.util.Locale.ROOT);
            if (!textoMija.equals("s") && !textoMija.equals("si") && !textoMija.equals("sí")) {
                throw new ErrorUso("Cancelado por el usuario.");
            }
        } finally {
            Arrays.fill(respuestaMija, '\0');
        }
    }

    /**
     * Comprueba <b>antes</b> de actuar adónde irán los tokens: a {@code --tokens-out} (que no debe existir) o a la
     * terminal, solo si stdout es una TTY. En una tubería sin {@code --tokens-out} es un error de uso: los tokens
     * no deben terminar en logs.
     */
    protected void exigirDestinoTokens(Path tokensOutMija) {
        if (tokensOutMija != null) {
            if (Files.exists(tokensOutMija)) {
                throw new ErrorUso("No se sobrescribe " + tokensOutMija + ": elige otro archivo para --tokens-out.");
            }
            return;
        }
        if (!entornoMija.terminalMija().salidaEsTty()) {
            throw new ErrorUso("La salida no es una terminal: usa --tokens-out <archivo> para guardar los tokens.");
        }
    }

    protected void emitirTokens(List<TokenRecuperacion> tokensMija, Path tokensOutMija, Path bovedaMija)
            throws IOException {
        if (tokensOutMija != null) {
            ExportadorTokensTxt.exportar(tokensOutMija, bovedaMija.getFileName().toString(), LocalDateTime.now(),
                    tokensMija);
            entornoMija.erroresMija().println("Tokens guardados en " + tokensOutMija + ". "
                    + ExportadorTokensTxt.AVISO_MIJA);
            return;
        }
        entornoMija.erroresMija().println("Tokens de recuperación (se muestran UNA sola vez):");
        for (TokenRecuperacion tokenMija : tokensMija) {
            char[] formateadoMija = tokenMija.formatear();
            entornoMija.salidaMija().println(formateadoMija);
            Arrays.fill(formateadoMija, '\0');
        }
        entornoMija.salidaMija().flush();
    }
}
