# ADR-012: Rotación explícita y metadatos del slot 0 en la AAD

**Estado:** aceptada (Fase 4, ajustes a la Fase 3)

## Contexto
1. Para saber si F1 cambió al rotar, la sesión guardaba `SHA-256(len‖F1)`. Un SHA-256 es muy rápido: con un volcado de memoria, un F1 de 12 dígitos se recupera en minutos con una GPU, y mucha gente reutiliza su PIN.
2. `requiereLlave` y el umbral de autodestrucción viven en la Zona B, cuyo MAC solo se verifica después de desbloquear. Invertirlos en una copia hacía que la app pidiera (o no) la llave por error: un bloqueo sin acceso a los datos.

## Decisión
- **Rotación explícita:** `RotarCredenciales` recibe un `CambioCredenciales` con un campo opcional por factor (F1, F2, F3, llave: sin cambio / nueva / quitar, umbral). Si trae F1 → slot 0 + 6 tokens nuevos; si no → solo slot 0. No se guarda ni compara nada derivado de F1.
  - Sesión normal: se exigen las credenciales vigentes y se verifican contra el slot 0 (re-autenticación); los factores que no cambian salen de ahí.
  - Rotación obligatoria (tras un token): no hay credenciales vigentes, así que el cambio debe traer F1, F2, F3 y una decisión sobre la llave; como trae F1, los tokens siempre se regeneran.
- **Metadatos en la AAD del slot 0:** `Zona A ‖ 0 ‖ PRINCIPAL ‖ requiereLlave ‖ versiónPolítica ‖ umbral`. Si se manipulan, el slot 0 de esa copia no abre y se usa la otra, que luego repara a la primera. La UI pide la llave si **cualquier** copia la requiere.

## Consecuencias
- Cambiar la llave o el umbral exige rehacer el slot 0, es decir, conocer las credenciales.
- Tras usar un token ya no se conservan los 5 tokens restantes: siempre se emite una hoja nueva.
- La autodestrucción no se puede desactivar editando el header sin hacer fallar el slot 0 (pero una copia vieja del archivo siempre la evita: THREAT_MODEL).
