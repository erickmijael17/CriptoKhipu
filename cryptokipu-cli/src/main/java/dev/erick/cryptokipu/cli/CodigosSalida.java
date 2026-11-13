package dev.erick.cryptokipu.cli;

import dev.erick.cryptokipu.core.error.ErrorBoveda;

/** Códigos de salida de {@code kipu} (plan, Fase 5 punto 4). El {@code switch} es exhaustivo. */
public final class CodigosSalida {

    public static final int OK_MIJA = 0;
    public static final int GENERAL_MIJA = 1;
    public static final int USO_MIJA = 2;
    public static final int CREDENCIALES_MIJA = 3;
    public static final int INTEGRIDAD_MIJA = 4;
    public static final int EN_USO_MIJA = 5;
    public static final int DESTRUIDA_MIJA = 6;
    public static final int ESPACIO_MIJA = 7;
    public static final int TOKEN_MIJA = 8;
    public static final int ROTACION_MIJA = 9;
    public static final int CANCELADO_MIJA = 130;

    private CodigosSalida() {
    }

    public static int de(ErrorBoveda errorMija) {
        return switch (errorMija) {
            case ErrorBoveda.CredencialesIncorrectas _ -> CREDENCIALES_MIJA;
            case ErrorBoveda.IntegridadFallida _, ErrorBoveda.FormatoNoSoportado _,
                    ErrorBoveda.ParametrosFueraDeRango _ -> INTEGRIDAD_MIJA;
            case ErrorBoveda.BovedaEnUso _ -> EN_USO_MIJA;
            case ErrorBoveda.BovedaDestruida _ -> DESTRUIDA_MIJA;
            case ErrorBoveda.EspacioInsuficiente _, ErrorBoveda.LimiteSistemaArchivos _ -> ESPACIO_MIJA;
            case ErrorBoveda.TokenMalEscrito _ -> TOKEN_MIJA;
            case ErrorBoveda.RotacionPendiente _ -> ROTACION_MIJA;
            case ErrorBoveda.OperacionCancelada _ -> CANCELADO_MIJA;
            case ErrorBoveda.MemoriaInsuficiente _ -> GENERAL_MIJA;
        };
    }
}
