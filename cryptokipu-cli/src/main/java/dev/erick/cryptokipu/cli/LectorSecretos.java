package dev.erick.cryptokipu.cli;

/**
 * Puerto de entrada de secretos. Los secretos <b>nunca</b> llegan como argumentos ni variables de entorno
 * (quedarían en el historial de la shell y en la lista de procesos): se piden aquí.
 */
public interface LectorSecretos {

    /** Lee un secreto sin eco. Quien llama borra el arreglo. */
    char[] leerSecreto(String etiquetaMija);

    /** Lee una respuesta no secreta (por ejemplo, una confirmación). */
    char[] leerLinea(String etiquetaMija);
}
