# ADR-003: Todos los factores en una sola derivación Argon2id

**Estado:** aceptada

## Contexto
Validar cada factor por separado permite atacarlos uno por uno.

## Decisión
F1, F2 y F3 (con prefijo de longitud) son la entrada de Argon2id y el SHA-256 de F4 es su parámetro `secret`. Un único resultado: abre o "Credenciales incorrectas".

## Consecuencias
- El atacante debe acertar todos los factores a la vez.
- La app no puede decir qué factor está mal (es intencional).
