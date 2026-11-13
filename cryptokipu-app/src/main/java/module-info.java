/** Casos de uso de CryptoKipu y puertos hacia los adaptadores. */
module dev.erick.cryptokipu.app {
    requires transitive dev.erick.cryptokipu.core;

    exports dev.erick.cryptokipu.app.casos;
    exports dev.erick.cryptokipu.app.puertos;
}
