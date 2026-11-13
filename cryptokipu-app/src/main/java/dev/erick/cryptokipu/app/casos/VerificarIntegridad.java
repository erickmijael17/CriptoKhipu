package dev.erick.cryptokipu.app.casos;

import dev.erick.cryptokipu.app.puertos.ContenedorAbierto;
import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import dev.erick.cryptokipu.core.format.EntradaManifest;
import dev.erick.cryptokipu.core.progreso.ReporteProgreso;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Recorre y verifica todos los bloques de todos los archivos, con reporte por archivo, progreso y cancelación. */
public final class VerificarIntegridad {

    public VerificarIntegridad() {
        // Sin dependencias: opera sobre la sesión del comando.
    }

    public enum Estado { CORRECTO, CORRUPTO }

    public record Informe(String rutaInternaMija, Estado estadoMija) {
    }

    public record Comando(SesionBoveda sesionMija, ReporteProgreso progresoMija) {
        public Comando {
            Objects.requireNonNull(sesionMija, "sesionMija");
            Objects.requireNonNull(progresoMija, "progresoMija");
        }
    }

    public record Resultado(List<Informe> informesMija) {
        public boolean todoCorrecto() {
            return informesMija.stream().allMatch(informeMija -> informeMija.estadoMija() == Estado.CORRECTO);
        }
    }

    public Resultado ejecutar(Comando comandoMija) throws IOException {
        ContenedorAbierto contenedorMija = comandoMija.sesionMija().contenedor();
        List<EntradaManifest> entradasMija = contenedorMija.manifest().entradasMija();
        long totalMija = entradasMija.stream().mapToLong(EntradaManifest::tamanoMija).sum();
        long[] hechoMija = {0};
        ReporteProgreso progresoMija = comandoMija.progresoMija();
        List<Informe> informesMija = new ArrayList<>();
        for (EntradaManifest entradaMija : entradasMija) {
            progresoMija.verificarCancelacion();
            OutputStream descarteMija = new OutputStream() {
                @Override
                public void write(int byteMija) {
                    write(new byte[] {(byte) byteMija}, 0, 1);
                }

                @Override
                public void write(byte[] datosMija, int desdeMija, int longitudMija) {
                    progresoMija.verificarCancelacion();
                    hechoMija[0] += longitudMija;
                    progresoMija.avanzar(hechoMija[0], totalMija);
                }
            };
            try {
                contenedorMija.extraer(entradaMija.fileIdMija(), descarteMija);
                informesMija.add(new Informe(entradaMija.rutaMija(), Estado.CORRECTO));
            } catch (ExcepcionBoveda errorMija) {
                if (!(errorMija.error() instanceof ErrorBoveda.IntegridadFallida)) {
                    throw errorMija;
                }
                informesMija.add(new Informe(entradaMija.rutaMija(), Estado.CORRUPTO));
            }
        }
        return new Resultado(List.copyOf(informesMija));
    }
}
