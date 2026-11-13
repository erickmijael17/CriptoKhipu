package dev.erick.cryptokipu.infra.configuracion;

import dev.erick.cryptokipu.app.casos.AbrirEnMemoria;
import dev.erick.cryptokipu.app.casos.AgregarArchivos;
import dev.erick.cryptokipu.app.casos.Compactar;
import dev.erick.cryptokipu.app.casos.CrearBoveda;
import dev.erick.cryptokipu.app.casos.DesbloquearBoveda;
import dev.erick.cryptokipu.app.casos.EliminarArchivo;
import dev.erick.cryptokipu.app.casos.ExtraerArchivo;
import dev.erick.cryptokipu.app.casos.InspeccionarBoveda;
import dev.erick.cryptokipu.app.casos.RecuperarConToken;
import dev.erick.cryptokipu.app.casos.RegenerarTokens;
import dev.erick.cryptokipu.app.casos.RotarCredenciales;
import dev.erick.cryptokipu.app.casos.VerificarIntegridad;
import dev.erick.cryptokipu.app.puertos.RepositorioBoveda;
import dev.erick.cryptokipu.core.crypto.FuenteAleatoria;
import dev.erick.cryptokipu.infra.fs.ColocadorSeguro;
import dev.erick.cryptokipu.infra.storage.ContenedorKipu;
import dev.erick.cryptokipu.infra.storage.RepositorioKipu;
import java.util.Objects;

/**
 * Raíz de composición manual (sin Spring): construye todas las dependencias una vez. La UI y la CLI
 * obtienen los casos de uso desde aquí.
 */
public final class Contenedor {

    private final FuenteAleatoria fuenteMija;
    private final RepositorioBoveda repositorioMija;
    private final PreferenciasStore preferenciasMija;

    public Contenedor(ContenedorKipu.Opciones opcionesMija, PreferenciasStore preferenciasMija) {
        Objects.requireNonNull(opcionesMija, "opcionesMija");
        this.fuenteMija = opcionesMija.fuenteMija();
        this.repositorioMija = new RepositorioKipu(opcionesMija);
        this.preferenciasMija = Objects.requireNonNull(preferenciasMija, "preferenciasMija");
    }

    public static Contenedor porDefecto() {
        return new Contenedor(ContenedorKipu.Opciones.porDefecto(), PreferenciasStore.delUsuario());
    }

    public PreferenciasStore preferencias() {
        return preferenciasMija;
    }

    public CrearBoveda crearBoveda() {
        return new CrearBoveda(repositorioMija, fuenteMija);
    }

    public InspeccionarBoveda inspeccionarBoveda() {
        return new InspeccionarBoveda(repositorioMija);
    }

    public DesbloquearBoveda desbloquearBoveda() {
        return new DesbloquearBoveda(repositorioMija, fuenteMija);
    }

    public RecuperarConToken recuperarConToken() {
        return new RecuperarConToken(repositorioMija, fuenteMija);
    }

    public RotarCredenciales rotarCredenciales() {
        return new RotarCredenciales(fuenteMija);
    }

    public RegenerarTokens regenerarTokens() {
        return new RegenerarTokens(fuenteMija);
    }

    public AgregarArchivos agregarArchivos() {
        return new AgregarArchivos();
    }

    public ExtraerArchivo extraerArchivo() {
        return new ExtraerArchivo(new ColocadorSeguro());
    }

    public AbrirEnMemoria abrirEnMemoria() {
        return new AbrirEnMemoria();
    }

    public EliminarArchivo eliminarArchivo() {
        return new EliminarArchivo();
    }

    public VerificarIntegridad verificarIntegridad() {
        return new VerificarIntegridad();
    }

    public Compactar compactar() {
        return new Compactar();
    }
}
