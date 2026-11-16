# ADR-014: Nombre comercial (CriptoKhipu) frente a identificadores técnicos

**Estado:** aceptada (Fase 8)

## Contexto
El producto se presenta como **CriptoKhipu** (con «h», por *khipu*, el sistema de cuerdas anudadas andino). Durante las
fases 0–7 el código, los módulos y el formato nacieron con el nombre provisional «CryptoKipu». Cambiar todo a la vez
rompería bóvedas ya creadas (el *magic* está dentro de cada archivo), el nombre de los módulos Java y los comandos.

## Decisión
**Lo que ve el usuario dice «CriptoKhipu»**: título de la ventana, `textos.properties`, el diálogo «Acerca de»,
el README, las guías de usuario (`GUIA_USB`, `PRUEBAS_UI`), el encabezado del TXT de tokens, la ayuda y la versión de la
CLI, el nombre que `jpackage` da a la carpeta portátil, al `.exe` y a los instaladores (`--name CriptoKhipu`).

**Lo que NO cambia** (identificadores técnicos, estables a propósito):

| Identificador | Valor | Por qué no cambia |
|---|---|---|
| Paquete y módulos Java | `dev.erick.cryptokipu.*` | Renombrar módulos rompe `--enable-native-access`, ServiceLoader y la compatibilidad de código |
| *Magic* del formato | `CKIPU` | Está dentro de cada `.kipu`: cambiarlo invalida todas las bóvedas existentes |
| Extensiones | `.kipu`, `.kipukey` | Los archivos del usuario ya las usan |
| Comando de la CLI | `kipu` | Scripts y costumbre; el encabezado de ayuda sí dice CriptoKhipu |
| Carpeta de preferencias, prefijo de temporales, propiedades `-Dcryptokipu.*` | `.cryptokipu`, `cryptokipu-tmp-`, … | Compatibilidad con instalaciones y limpieza de restos de versiones previas |

**Identidad de paquete fijada desde ahora** (para que las versiones futuras *actualicen* en vez de duplicarse):

| Plataforma | Opción de `jpackage` | Valor |
|---|---|---|
| Todas | `--name` | `CriptoKhipu` |
| Windows | `--win-upgrade-uuid` | `7fd13518-ad1c-4405-8186-0957ebbe6a35` (**no cambiar nunca**) |
| macOS | `--mac-package-identifier` | `dev.erick.criptokhipu` (con «h», igual que el nombre visible; corregido en la Fase 9, antes de publicar ninguna versión) |

Viven en `buildSrc/.../OpcionesJvm.kt` (`IdentidadPaquete`) y `./gradlew :cryptokipu-ui:opcionesJpackage` las imprime.

## Consecuencias
- Una prueba (`NombreVisibleTest`) falla si «CryptoKipu» aparece en los recursos de la interfaz o en las guías de usuario.
  En el código interno (comentarios, nombres de clase como `CryptoKipuApp`) puede seguir apareciendo.
- Cambiar el UUID de actualización de Windows o el identificador de macOS haría que el instalador nuevo conviva con el
  viejo en lugar de reemplazarlo.
