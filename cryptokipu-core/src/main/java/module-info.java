/**
 * Núcleo de CryptoKipu: dominio y criptografía.
 * No depende de JavaFX, de la red ni de los demás módulos; solo del JDK y Bouncy Castle.
 */
module dev.erick.cryptokipu.core {
    requires org.bouncycastle.provider;

    exports dev.erick.cryptokipu.core.auth;
    exports dev.erick.cryptokipu.core.crypto;
    exports dev.erick.cryptokipu.core.error;
    exports dev.erick.cryptokipu.core.format;
    exports dev.erick.cryptokipu.core.progreso;
    exports dev.erick.cryptokipu.core.registro;
    exports dev.erick.cryptokipu.core.vault;
}
