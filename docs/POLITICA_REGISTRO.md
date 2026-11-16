# Política de registro (logging)

1. Se usa solo `System.Logger`, obtenido con `Registro.para(MiClase.class)`.
2. Nivel por defecto: `INFO`.
3. **Prohibido registrar:** credenciales, tokens, claves, cualquier `byte[]` o `char[]`, nombres o rutas de archivos dentro de la bóveda, y rutas internas del contenedor.
4. **Permitido registrar:** nombre de la operación, duración, tipo de error (`ErrorBoveda`), tamaños agregados.
5. Las excepciones se registran por su tipo (`ExcepcionBoveda` ya lleva como mensaje solo el nombre del error y no guarda traza).
6. Sin telemetría: ningún registro sale del equipo.
7. Los `record` que contengan secretos sobrescriben `toString()` y devuelven `"***"`.
8. **Auditoría (Fase 8):** `AuditoriaDeFugasTest` ejecuta el flujo completo (crear, abrir, agregar, extraer, token, rotar,
   regenerar, autodestrucción y los caminos de error) con un `System.LoggerFinder` que acepta **todos** los niveles
   (equivalente a DEBUG) y comprueba que lo registrado, lo impreso en stdout y stderr y los mensajes de las excepciones no
   contienen ninguna contraseña de prueba, ningún token, ningún nombre de archivo interno ni volcados de bytes
   (hexadecimal, listas de números, `byte[]@…`, base64). Todo registro nuevo debe pasar esa prueba.
9. Los avisos de bajo nivel (`BloqueoMemoria`, bloqueo de páginas en RAM) se registran **una sola vez** y sin datos.
