# Rendimiento de CriptoKhipu

Mediciones con JMH 1.37 (`./gradlew :cryptokipu-core:jmh :cryptokipu-infra:jmh`), un *fork* por benchmark. Los resultados en
bruto quedan en `*/build/results/jmh/results.json`. Son una referencia de **este** equipo, no una garantía: repetir en el
equipo del usuario antes de sacar conclusiones.

## Hardware y software

| | |
|---|---|
| CPU | AMD Ryzen 7 7730U (8 núcleos / 16 hilos, portátil) |
| RAM | 23,4 GB |
| Disco | SSD (NVMe) |
| SO | Windows 11 Pro |
| JVM | Eclipse Temurin 25.0.5 (HotSpot, G1) con las opciones de `OpcionesJvm` |
| Fecha | 2026-11-16 |

> Las barras de error de JMH son grandes (pocas iteraciones, equipo portátil con ahorro de energía y térmica variable). Los
> valores son órdenes de magnitud fiables, no cifras al milisegundo.

## 1. Cifrado y descifrado (AES-256-GCM por bloques de 1 MiB)

`CifradoBenchmark`: 16 MiB por operación, `ChunkCipher` (un `Cipher` nuevo por bloque, clave fuera del heap), modo
`AverageTime` con calentamiento.

| Operación | Tiempo por 16 MiB | Velocidad |
|---|---|---|
| Cifrar | ≈ 32 ms | ≈ 500 MiB/s (≈ 530 MB/s) |
| Descifrar | ≈ 42 ms | ≈ 380 MiB/s (≈ 400 MB/s) |

A esa velocidad el disco o el USB limitan antes que el cifrado: un USB 3 típico escribe 30–150 MB/s.

## 2. Argon2id por perfil

`Argon2Benchmark`: una derivación (`SingleShotTime`, que incluye el costo de reservar y rellenar la memoria). Bouncy Castle
calcula los carriles **de forma secuencial**: `p = 4` no es más rápido que `p = 1` (se mantiene `p = 4` por la política del
formato, no por velocidad).

| Perfil | m | t | p | Tiempo |
|---|---|---|---|---|
| Ligero (pruebas) | 64 MiB | 2 | 1 | ≈ 0,2–0,3 s (no medido aparte; usado en las pruebas) |
| **Estándar** | 256 MiB | 2 | 4 | **≈ 0,9 s** |
| Alto | 512 MiB | 2 | 4 | ≈ 1,9 s |
| Paranoico | 1 GiB | 2 | 4 | ≈ 3,9 s |
| Estándar con t = 3 | 256 MiB | 3 | 4 | ≈ 1,3 s |
| Alto con t = 3 | 512 MiB | 3 | 4 | ≈ 2,7 s |
| Paranoico con t = 3 | 1 GiB | 3 | 4 | ≈ 5 s |

Meta del plan: ~1 s por derivación en el equipo del usuario → el perfil **Estándar** la cumple aquí. Un intento fallido cuesta
lo mismo que uno correcto: ése es el costo que paga un atacante por cada combinación.

## 3. Apertura de una bóveda con muchas entradas

`ManifestBenchmark`: descifrar y parsear el manifest (`ManifestCodec.descifrar`), que es lo que domina al abrir una bóveda
grande.

| Entradas | Tiempo |
|---|---|
| 10 000 | ≈ 6 ms |
| 100 000 | ≈ 150 ms |

Una bóveda de 100 000 archivos abre en una fracción de segundo una vez derivada la clave (el costo es Argon2id, no el
manifest). La prueba pesada `cienMilArchivosPequenosSeAgregaYSeReabre` (`./gradlew :cryptokipu-infra:pruebasPesadas`)
mide el ciclo completo con archivos reales.

## 4. Latencia de lectura aleatoria (seek)

`LecturaAleatoriaBenchmark`: canal de `AbrirEnMemoria` sobre un archivo de 64 MiB; cada lectura posiciona el canal y lee
4 KiB. Cada posición cae en un bloque de 1 MiB que hay que **descifrar entero** (la caché guarda un bloque).

| Escenario | Latencia |
|---|---|
| Lectura aleatoria de 4 KiB | ≈ 1,1 ms |
| Lectura en un bloque distinto (peor caso) | ≈ 1,15 ms |

≈ 1 ms por salto basta para que `MediaPlayer` adelante y retroceda sin congelarse (ver `MediaPlayerFxTest`). Lecturas
secuenciales dentro del mismo bloque reutilizan el bloque ya descifrado.

## 5. Presupuestos como pruebas

| Presupuesto | Prueba | Resultado en este equipo |
|---|---|---|
| Arranque en frío de la interfaz < 2 s sobre la imagen `jlink` | `ArranqueEnFrioTest` (tarea `pruebasToolkit`, necesita pantalla) | ver abajo |
| Ningún bloqueo del hilo de JavaFX > 100 ms en un flujo automatizado | `PresupuestoHiloFxTest` (`pruebasToolkit`) | ver abajo |
| Memoria: Argon2id avisa si el equipo no tiene la RAM del perfil | `DesbloqueoViewModel` (aviso previo) | cubierto en `ComponentesViewModelTest` |

## 6. Cómo repetir

```bash
./gradlew :cryptokipu-core:jmh :cryptokipu-infra:jmh      # benchmarks (≈ 10 min)
./gradlew :cryptokipu-ui:pruebasToolkit                   # presupuestos de arranque y de hilo de JavaFX
./gradlew :cryptokipu-infra:pruebasPesadas                # 5 GB, 100 000 archivos, compactar 5 GB, FAT32 (≈ 15 GB de disco)
```
