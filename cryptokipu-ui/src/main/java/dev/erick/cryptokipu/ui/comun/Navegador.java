package dev.erick.cryptokipu.ui.comun;

import javafx.scene.Node;

/** Navegación del shell: vistas principales cacheadas y modales (AtlantaFX ModalPane). */
public interface Navegador {

    enum Vista { INICIO, ASISTENTE, DESBLOQUEO, EXPLORADOR, SEGURIDAD, AJUSTES }

    void mostrar(Vista vistaMija);

    /** @param persistenteMija si es {@code true}, ni Escape ni un clic fuera la cierran (rotación obligatoria, tokens) */
    void abrirModal(Node contenidoMija, boolean persistenteMija);

    void cerrarModal();

    void notificar(String textoMija);
}
