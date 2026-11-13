package dev.erick.cryptokipu.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Reglas de capas de la arquitectura hexagonal (plan, Fase 0 tarea 5).
 *
 * <p>Las reglas sobre módulos que todavía no tienen clases usan {@code allowEmptyShould(true)};
 * empiezan a aplicarse solas cuando esos módulos reciban código.
 */
class ReglasArquitecturaTest {

    private static final String CORE_MIJA = "dev.erick.cryptokipu.core..";
    private static final String APP_MIJA = "dev.erick.cryptokipu.app..";
    private static final String INFRA_MIJA = "dev.erick.cryptokipu.infra..";
    private static final String UI_MIJA = "dev.erick.cryptokipu.ui..";
    private static final String CLI_MIJA = "dev.erick.cryptokipu.cli..";

    private static JavaClasses clasesMija;

    @BeforeAll
    static void importarClases() {
        clasesMija = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("dev.erick.cryptokipu");
    }

    @Test
    void coreSoloDependeDelJdkYBouncyCastle() {
        ArchRule reglaMija = classes().that().resideInAPackage(CORE_MIJA)
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "javax.crypto..", "org.bouncycastle..", CORE_MIJA)
                .because("regla de oro: core no depende de nada externo salvo Bouncy Castle");
        reglaMija.check(clasesMija);
    }

    @Test
    void coreYAppNoUsanLaRed() {
        ArchRule reglaMija = noClasses().that().resideInAnyPackage(CORE_MIJA, APP_MIJA)
                .should().dependOnClassesThat().resideInAnyPackage("java.net..", "javax.net..")
                .because("R3: la aplicación es 100% offline");
        reglaMija.check(clasesMija);
    }

    @Test
    void coreYAppNoUsanJavaFx() {
        ArchRule reglaMija = noClasses().that().resideInAnyPackage(CORE_MIJA, APP_MIJA)
                .should().dependOnClassesThat().resideInAPackage("javafx..")
                .allowEmptyShould(true);
        reglaMija.check(clasesMija);
    }

    @Test
    void appNoDependeDeAdaptadores() {
        ArchRule reglaMija = noClasses().that().resideInAPackage(APP_MIJA)
                .should().dependOnClassesThat().resideInAnyPackage(INFRA_MIJA, UI_MIJA, CLI_MIJA)
                .allowEmptyShould(true);
        reglaMija.check(clasesMija);
    }

    @Test
    void infraNoDependeDeLaInterfaz() {
        ArchRule reglaMija = noClasses().that().resideInAPackage(INFRA_MIJA)
                .should().dependOnClassesThat().resideInAnyPackage(UI_MIJA, CLI_MIJA)
                .allowEmptyShould(true);
        reglaMija.check(clasesMija);
    }

    @Test
    void laInterfazNoTocaCriptografiaDirectamente() {
        ArchRule reglaMija = noClasses().that().resideInAnyPackage(UI_MIJA, CLI_MIJA)
                .should().dependOnClassesThat()
                .resideInAnyPackage("dev.erick.cryptokipu.core.crypto..", "javax.crypto..", "org.bouncycastle..")
                .because("la UI solo llama a casos de uso")
                .allowEmptyShould(true);
        reglaMija.check(clasesMija);
    }

    @Test
    void sinConsolaFueraDeLaCli() {
        ArchRule reglaMija = noClasses().that().resideInAnyPackage(CORE_MIJA, APP_MIJA, INFRA_MIJA, UI_MIJA)
                .should(GeneralCodingRules.ACCESS_STANDARD_STREAMS)
                .because("política de registro: solo System.Logger vía Registro (la CLI sí imprime)");
        reglaMija.check(clasesMija);
    }

    @Test
    void sinJavaUtilLogging() {
        ArchRule reglaMija = noClasses().that().resideInAPackage("dev.erick.cryptokipu..")
                .should(GeneralCodingRules.USE_JAVA_UTIL_LOGGING)
                .because("política de registro: solo System.Logger vía Registro");
        reglaMija.check(clasesMija);
    }

    @Test
    void elServidorDeMediosSoloViveEnUiOInfra() {
        ArchRule reglaMija = classes().that().haveSimpleNameContaining("ServidorMedios")
                .should().resideInAnyPackage(UI_MIJA, INFRA_MIJA)
                .because("el streaming local (127.0.0.1) nunca puede estar en core ni en app")
                .allowEmptyShould(true);
        reglaMija.check(clasesMija);
    }
}
