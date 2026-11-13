/** Casos de uso de CryptoKipu, puertos hacia los adaptadores y catálogo de mensajes. */
module dev.erick.cryptokipu.app {
    requires transitive dev.erick.cryptokipu.core;

    exports dev.erick.cryptokipu.app.casos;
    exports dev.erick.cryptokipu.app.mensajes;
    exports dev.erick.cryptokipu.app.puertos;
}
