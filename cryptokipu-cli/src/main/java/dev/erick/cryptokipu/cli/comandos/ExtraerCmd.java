package dev.erick.cryptokipu.cli.comandos;

import dev.erick.cryptokipu.app.casos.ExtraerArchivo;
import dev.erick.cryptokipu.app.casos.SesionBoveda;
import dev.erick.cryptokipu.cli.CodigosSalida;
import dev.erick.cryptokipu.cli.Entorno;
import dev.erick.cryptokipu.cli.ErrorUso;
import dev.erick.cryptokipu.core.format.EntradaManifest;
import dev.erick.cryptokipu.core.vault.RutaInterna;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "extract", description = "Extrae archivos (verifica cada bloque; nunca escribe fuera de --to).")
final class ExtraerCmd extends ComandoBase {

    static final class Seleccion {
        @Option(names = "--ruta", paramLabel = "<interna>", required = true,
                description = "Ruta dentro de la bóveda (se puede repetir).")
        List<String> rutasMija;

        @Option(names = "--todo", required = true, description = "Extraer todos los archivos.")
        boolean todoMija;
    }

    @Parameters(index = "0", paramLabel = "<bóveda>")
    Path bovedaMija;

    @ArgGroup(exclusive = true, multiplicity = "1")
    Seleccion seleccionMija;

    @Option(names = "--to", required = true, paramLabel = "<carpeta>", description = "Carpeta de destino.")
    Path destinoMija;

    @Option(names = "--keyfile", paramLabel = "<ruta>", description = "Llave de seguridad, si la bóveda la pide.")
    Path llaveMija;

    @Option(names = "--si-existe", defaultValue = "omitir",
            description = "Si el archivo ya existe en el destino: ${COMPLETION-CANDIDATES} (por defecto: ${DEFAULT-VALUE}).")
    AgregarCmd.SiExiste siExisteMija;

    ExtraerCmd(Entorno entornoMija) {
        super(entornoMija);
    }

    @Override
    public Integer call() throws Exception {
        if (siExisteMija == AgregarCmd.SiExiste.reemplazar) {
            confirmar("Se reemplazarán archivos existentes en " + destinoMija + ". ¿Continuar?");
        }
        try (SesionBoveda sesionMija = abrir(bovedaMija, llaveMija)) {
            List<byte[]> idsMija = new ArrayList<>();
            if (!seleccionMija.todoMija) {
                for (String rutaMija : seleccionMija.rutasMija) {
                    String normalizadaMija = RutaInterna.normalizar(rutaMija);
                    idsMija.add(sesionMija.manifest().entradasMija().stream()
                            .filter(entradaMija -> entradaMija.rutaMija().equals(normalizadaMija))
                            .map(EntradaManifest::fileIdMija).findFirst()
                            .orElseThrow(() -> new ErrorUso("No existe en la bóveda: " + rutaMija)));
                }
            }
            ExtraerArchivo.Resultado resultadoMija = entornoMija.appMija().extraerArchivo().ejecutar(
                    new ExtraerArchivo.Comando(sesionMija, idsMija, destinoMija, siExisteMija.politica(),
                            entornoMija.cancelacionMija()));
            resultadoMija.extraidosMija().forEach(extraidoMija -> entornoMija.salidaMija().println(extraidoMija.destinoMija()));
            resultadoMija.rechazadosMija().forEach(rechazoMija -> entornoMija.erroresMija()
                    .println("No extraído (" + rechazoMija.motivoMija() + "): " + rechazoMija.rutaInternaMija()));
            boolean integridadMija = resultadoMija.rechazadosMija().stream()
                    .anyMatch(rechazoMija -> rechazoMija.motivoMija() == ExtraerArchivo.Motivo.INTEGRIDAD);
            return integridadMija ? CodigosSalida.INTEGRIDAD_MIJA : CodigosSalida.OK_MIJA;
        }
    }
}
