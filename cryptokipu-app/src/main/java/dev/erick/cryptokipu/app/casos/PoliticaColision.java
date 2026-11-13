package dev.erick.cryptokipu.app.casos;

/** Qué hacer si un nombre ya existe (dentro de la bóveda al agregar, o en el destino al extraer). */
public enum PoliticaColision {
    /** Sustituir el existente. Es la única forma de sobrescribir. */
    REEMPLAZAR,
    /** Usar "nombre (2).ext", "nombre (3).ext"… */
    RENOMBRAR,
    /** Dejar el existente y saltar el nuevo. */
    OMITIR
}
