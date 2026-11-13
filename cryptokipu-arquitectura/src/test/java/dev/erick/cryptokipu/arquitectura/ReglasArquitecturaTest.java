package dev.erick.cryptokipu.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import dev.erick.cryptokipu.core.crypto.ParametrosArgon2;
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
        // ParametrosArgon2 es un valor de la API de los casos de uso (el perfil elegido al crear), no una primitiva.
        DescribedPredicate<JavaClass> criptografiaMija = JavaClass.Predicates
                .resideInAnyPackage("dev.erick.cryptokipu.core.crypto..", "javax.crypto..", "org.bouncycastle..")
                .and(DescribedPredicate.not(JavaClass.Predicates.equivalentTo(ParametrosArgon2.class)));
        ArchRule reglaMija = noClasses().that().resideInAnyPackage(UI_MIJA, CLI_MIJA)
                .should().dependOnClassesThat(criptografiaMija)
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
    void nadaLeeVariablesDeEntorno() {
        ArchRule reglaMija = noClasses().that().resideInAPackage("dev.erick.cryptokipu..")
                .should().callMethod(System.class, "getenv")
                .orShould().callMethod(System.class, "getenv", String.class)
                .because("los secretos nunca llegan por variables de entorno (lista de procesos, historial)");
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

    @Test
    void laRedYElServidorHttpSoloVivenEnUiMedios() {
        // URI y URL son tipos de valor (la interfaz los usa para cargar CSS/FXML del classpath y abrir archivos
        // con HostServices): no abren conexiones. Todo lo demás de java.net es red.
        DescribedPredicate<JavaClass> redMija = JavaClass.Predicates.resideInAnyPackage("java.net..", "javax.net..",
                "com.sun.net.httpserver..", "jdk.httpserver..")
                .and(DescribedPredicate.not(JavaClass.Predicates.equivalentTo(java.net.URI.class)))
                .and(DescribedPredicate.not(JavaClass.Predicates.equivalentTo(java.net.URL.class)));
        ArchRule reglaMija = noClasses().that().resideOutsideOfPackage("dev.erick.cryptokipu.ui.medios..")
                .should().dependOnClassesThat(redMija)
                .because("el único código con red es el servidor de medios, solo en 127.0.0.1");
        reglaMija.check(clasesMija);
    }

    @Test
    void sinJavaUtilPrefs() {
        ArchRule reglaMija = noClasses().that().resideInAPackage("dev.erick.cryptokipu..")
                .should().dependOnClassesThat().resideInAPackage("java.util.prefs..")
                .because("en Windows java.util.prefs escribe en el registro del anfitrión: las preferencias van a "
                        + "PreferenciasStore (junto a la app en modo portátil)");
        reglaMija.check(clasesMija);
    }

    @Test
    void soloLosVisoresTocanPdfBoxYPoi() {
        ArchRule reglaMija = noClasses().that().resideOutsideOfPackage("dev.erick.cryptokipu.visores..")
                .should().dependOnClassesThat().resideInAnyPackage("org.apache.pdfbox..", "org.apache.poi..")
                .because("el núcleo no carga PDFBox ni POI: son opcionales y viven en cryptokipu-visores");
        reglaMija.check(clasesMija);
    }

    @Test
    void elModuloDeVisoresNoTocaLaRedNiElDisco() {
        ArchRule reglaMija = noClasses().that().resideInAPackage("dev.erick.cryptokipu.visores..")
                .should().dependOnClassesThat().resideInAnyPackage("java.net..", "com.sun.net.httpserver..")
                .orShould().callMethod(java.io.File.class, "createTempFile", String.class, String.class)
                .orShould().callMethod(java.nio.file.Files.class, "createTempFile", String.class, String.class,
                        java.nio.file.attribute.FileAttribute[].class)
                .because("los visores trabajan solo en RAM");
        reglaMija.check(clasesMija);
    }
}
