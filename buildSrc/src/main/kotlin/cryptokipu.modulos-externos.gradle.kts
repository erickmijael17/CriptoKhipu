// Descriptores de módulo para dependencias que solo traen Automatic-Module-Name (o nada).
// jlink y jpackage solo aceptan módulos explícitos; los nombres coinciden con los que exige el module-info de POI.
plugins {
    id("org.gradlex.extra-java-module-info")
}

extraJavaModuleInfo {
    // Falla si aparece un jar sin module-info que no esté listado aquí (nada entra a la imagen sin revisar).
    failOnMissingModuleInfo = true
    // Los jars de los módulos del propio proyecto ya traen su module-info (y aún no existen al resolver).
    skipLocalJars = true

    module("org.apache.pdfbox:pdfbox-io", "org.apache.pdfbox.io") {
        exportAllPackages()
        requires("org.apache.commons.logging")
    }
    module("org.apache.pdfbox:fontbox", "org.apache.fontbox") {
        exportAllPackages()
        requiresTransitive("org.apache.pdfbox.io")
        requires("org.apache.commons.logging")
        requires("java.desktop")
    }
    module("org.apache.pdfbox:pdfbox", "org.apache.pdfbox") {
        exportAllPackages()
        requiresTransitive("org.apache.pdfbox.io")
        requiresTransitive("org.apache.fontbox")
        requires("org.apache.commons.logging")
        requires("java.desktop")
        requires("java.xml")
    }
    module("org.apache.commons:commons-math3", "commons.math3") {
        exportAllPackages()
    }
    module("com.zaxxer:SparseBitSet", "com.zaxxer.sparsebitset") {
        exportAllPackages()
    }
    module("com.github.virtuald:curvesapi", "com.github.virtuald.curvesapi") {
        exportAllPackages()
        requires("java.desktop")
    }
}
