# Política de registro (logging)

1. Se usa solo `System.Logger`, obtenido con `Registro.para(MiClase.class)`.
2. Nivel por defecto: `INFO`.
3. **Prohibido registrar:** credenciales, tokens, claves, cualquier `byte[]` o `char[]`, nombres o rutas de archivos dentro de la bóveda, y rutas internas del contenedor.
4. **Permitido registrar:** nombre de la operación, duración, tipo de error (`ErrorBoveda`), tamaños agregados.
5. Las excepciones se registran por su tipo (`ExcepcionBoveda` ya lleva como mensaje solo el nombre del error y no guarda traza).
6. Sin telemetría: ningún registro sale del equipo.
7. Los `record` que contengan secretos sobrescriben `toString()` y devuelven `"***"`.
