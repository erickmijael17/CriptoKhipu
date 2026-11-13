/** Adaptadores de salida de CryptoKipu: contenedor .kipu, llaves, temporales, preferencias y composición. */
module dev.erick.cryptokipu.infra {
    requires transitive dev.erick.cryptokipu.app;

    exports dev.erick.cryptokipu.infra.autenticacion;
    exports dev.erick.cryptokipu.infra.configuracion;
    exports dev.erick.cryptokipu.infra.fs;
    exports dev.erick.cryptokipu.infra.storage;
}
