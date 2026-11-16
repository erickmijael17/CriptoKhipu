#!/usr/bin/env python3
"""Lector independiente de bóvedas .kipu v1 (Fase 9).

Descifra una bóveda usando SOLO docs/FORMAT_SPEC.md (sin código Java del proyecto). Si funciona, la especificación
es completa y correcta. Dependencias: ``cryptography`` y ``argon2-cffi``.

    pip install cryptography argon2-cffi
    python tools/lector_kipu.py boveda.kipu --f1 123456789012 --f2 "Mi Clave 2026" --f3 "mi frase" \
        [--llave boveda.kipukey] [--extraer carpeta]
    python tools/lector_kipu.py boveda.kipu --f1 ... --token CKP-XXXXX-XXXXX-XXXXX-XXXXX-XXXXX-C
    python tools/lector_kipu.py boveda.kipu ... --verificar contenido.sha256

Los secretos se pasan por argumentos SOLO porque es una herramienta de verificación de la especificación; la
aplicación real nunca los acepta así.
"""
import argparse
import hashlib
import hmac
import struct
import sys
import unicodedata
from pathlib import Path

from _argon2_cffi_bindings import ffi, lib  # argon2_ctx: único modo de pasar el "secreto" (K) de Argon2id
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

TAM_HEADER = 16 * 1024
OFFSET_DATOS = 2 * TAM_HEADER
TAM_ZONA_A = 103
TAM_SLOT = 93
TAM_BLOQUE = 1 << 20
TAM_TAG = 16
TAM_FOOTER = 33


class ErrorKipu(Exception):
    pass


# ---------------------------------------------------------------------------------------------- utilidades

def _crc32c_tabla():
    tabla = []
    for i in range(256):
        c = i
        for _ in range(8):
            c = (c >> 1) ^ 0x82F63B78 if c & 1 else c >> 1
        tabla.append(c)
    return tabla


_TABLA_CRC = _crc32c_tabla()


def crc32c(datos: bytes) -> int:
    c = 0xFFFFFFFF
    for b in datos:
        c = _TABLA_CRC[(c ^ b) & 0xFF] ^ (c >> 8)
    return c ^ 0xFFFFFFFF


def argon2id(entrada: bytes, sal: bytes, m_kib: int, t: int, p: int, secreto: bytes | None) -> bytes:
    """Argon2id v1.3, 32 B de salida; ``secreto`` (K) opcional = SHA-256 del archivo de la llave (F4)."""
    salida = ffi.new("uint8_t[32]")
    pwd = ffi.new("uint8_t[]", entrada) if entrada else ffi.NULL
    s = ffi.new("uint8_t[]", sal)
    k = ffi.new("uint8_t[]", secreto) if secreto else ffi.NULL
    ctx = ffi.new("argon2_context *")
    ctx.out, ctx.outlen = salida, 32
    ctx.pwd, ctx.pwdlen = pwd, len(entrada)
    ctx.salt, ctx.saltlen = s, len(sal)
    ctx.secret, ctx.secretlen = k, len(secreto) if secreto else 0
    ctx.ad, ctx.adlen = ffi.NULL, 0
    ctx.t_cost, ctx.m_cost, ctx.lanes, ctx.threads = t, m_kib, p, p
    ctx.version = 0x13
    ctx.allocate_cbk, ctx.free_cbk, ctx.flags = ffi.NULL, ffi.NULL, 0
    rc = lib.argon2_ctx(ctx, 2)  # 2 = Argon2id
    if rc != 0:
        raise ErrorKipu(f"Argon2 falló ({rc})")
    return bytes(ffi.buffer(salida, 32))


def hkdf(material: bytes, info: bytes, sal: bytes | None = None, longitud: int = 32) -> bytes:
    """HKDF-SHA256; sal ausente = sal de ceros de 32 B (RFC 5869)."""
    return HKDF(algorithm=hashes.SHA256(), length=longitud, salt=sal, info=info).derive(material)


def normalizar_f3(frase: str) -> str:
    """Normalización v1: NFD, sin marcas, minúsculas, de nuevo NFD sin marcas, recorte y espacios colapsados."""
    def sin_marcas(texto: str, minusculas: bool) -> str:
        salida = []
        for c in unicodedata.normalize("NFD", texto):
            if unicodedata.category(c) in ("Mn", "Mc", "Me"):
                continue
            salida.append(c.lower() if minusculas else c)
        return "".join(salida)
    return " ".join(sin_marcas(sin_marcas(frase, False), True).split())


def con_longitud(texto: str) -> bytes:
    datos = texto.encode("utf-8")
    return struct.pack(">I", len(datos)) + datos


# ---------------------------------------------------------------------------------------------- header

def leer_header(bloque: bytes) -> dict | None:
    if len(bloque) != TAM_HEADER or crc32c(bloque[:800]) != struct.unpack(">I", bloque[800:804])[0]:
        return None
    if bloque[:5] != b"CKIPU":
        return None
    version, aead, kdf, hash_ = struct.unpack(">HBBB", bloque[5:10])
    politica, norm = struct.unpack(">HB", bloque[10:13])
    if (version, aead, kdf, hash_, politica, norm) != (1, 1, 1, 1, 1, 1):
        raise ErrorKipu(f"versión o algoritmos no soportados: {(version, aead, kdf, hash_, politica, norm)}")
    m, t, p = struct.unpack(">IBB", bloque[13:19])
    tam_bloque = struct.unpack(">I", bloque[99:103])[0]
    if tam_bloque != TAM_BLOQUE:
        raise ErrorKipu("tamaño de bloque distinto de 1 MiB")
    generacion, umbral, requiere_llave, n_slots = struct.unpack(">QIBB", bloque[103:117])
    slots = []
    for i in range(n_slots):
        s = bloque[117 + i * TAM_SLOT: 117 + (i + 1) * TAM_SLOT]
        slots.append({"tipo": s[0], "nonce": s[33:45], "cifrado": s[45:93]})  # MK cifrada (32) ‖ tag (16)
    return {
        "zona_a": bloque[:TAM_ZONA_A], "m": m, "t": t, "p": p, "sal0": bloque[19:51], "sal_tokens": bloque[51:83],
        "vault_id": bloque[83:99], "generacion": generacion, "umbral": umbral, "requiere_llave": requiere_llave,
        "slots": slots, "mac": bloque[768:800], "autenticados": bloque[:768],
    }


def elegir_headers(datos: bytes) -> list[dict]:
    candidatos = []
    for indice, desde in enumerate((0, TAM_HEADER)):
        h = leer_header(datos[desde: desde + TAM_HEADER])
        if h:
            candidatos.append((h["generacion"], -indice, h))
    if not candidatos:
        raise ErrorKipu("ninguna copia del header es válida")
    candidatos.sort(key=lambda c: (c[0], c[1]), reverse=True)  # mayor generación; a igualdad, el primario
    return [c[2] for c in candidatos]


# ---------------------------------------------------------------------------------------------- apertura

def abrir_slot0(h: dict, f1: str, f2: str, f3: str, llave: bytes | None) -> bytes | None:
    entrada = con_longitud(f1) + con_longitud(f2) + con_longitud(normalizar_f3(f3))
    secreto = hashlib.sha256(llave).digest() if llave is not None else None
    kek = argon2id(entrada, h["sal0"], h["m"], h["t"], h["p"], secreto)
    slot = h["slots"][0]
    if slot["tipo"] != 1:
        return None
    aad = h["zona_a"] + bytes([0, 1]) + bytes([h["requiere_llave"]]) + struct.pack(">HI", 1, h["umbral"])
    try:
        return AESGCM(kek).decrypt(slot["nonce"], slot["cifrado"], aad)
    except Exception:
        return None


def abrir_con_token(h: dict, f1: str, token: str) -> bytes | None:
    k_f1 = argon2id(con_longitud(f1), h["sal_tokens"], h["m"], h["t"], h["p"], None)
    # Los 25 símbolos del token (sin prefijo ni guiones); el 26.º es solo la verificación (mod 37).
    simbolos = "".join(c for c in token.upper() if c not in "- \t").removeprefix("CKP")[:25]
    for i, slot in enumerate(h["slots"][1:], start=1):
        if slot["tipo"] != 2:
            continue
        kek = hkdf(k_f1 + simbolos.encode("ascii"), b"ckp/v1/token-slot")
        aad = h["zona_a"] + bytes([i, 2])
        try:
            return AESGCM(kek).decrypt(slot["nonce"], slot["cifrado"], aad)
        except Exception:
            continue
    return None


def leer_footer(datos: bytes) -> dict:
    pos = len(datos) - TAM_FOOTER
    f = datos[pos:]
    off, lon, gen = struct.unpack(">QQQ", f[:24])
    if f[24:29] != b"CKEND" or crc32c(f[:29]) != struct.unpack(">I", f[29:33])[0] or off + lon != pos:
        raise ErrorKipu("footer no válido en los últimos 33 B (este lector no implementa la búsqueda hacia atrás)")
    return {"offset": off, "longitud": lon, "generacion": gen}


def leer_manifest(datos: bytes, mk: bytes, h: dict, footer: dict) -> list[dict]:
    almacenado = datos[footer["offset"]: footer["offset"] + footer["longitud"]]
    clave = hkdf(mk, b"ckp/v1/manifest")
    aad = h["vault_id"] + struct.pack(">Q", footer["generacion"])
    texto = AESGCM(clave).decrypt(almacenado[:12], almacenado[12:], aad)
    if texto[0] != 1:
        raise ErrorKipu("versión de manifest desconocida")
    generacion, n = struct.unpack(">QI", texto[1:13])
    if generacion != footer["generacion"]:
        raise ErrorKipu("la generación del manifest no coincide con la del footer")
    pos, entradas = 13, []
    for _ in range(n):
        file_id = texto[pos: pos + 16]; pos += 16
        (lr,) = struct.unpack(">H", texto[pos: pos + 2]); pos += 2
        ruta = texto[pos: pos + lr].decode("utf-8"); pos += lr
        tamano, modificado = struct.unpack(">QQ", texto[pos: pos + 16]); pos += 16
        (lm,) = struct.unpack(">H", texto[pos: pos + 2]); pos += 2
        mime = texto[pos: pos + lm].decode("utf-8"); pos += lm
        offset, cifrada = struct.unpack(">QQ", texto[pos: pos + 16]); pos += 16
        entradas.append({"file_id": file_id, "ruta": ruta, "tamano": tamano, "modificado": modificado, "mime": mime,
                         "offset": offset, "cifrada": cifrada})
    if pos != len(texto):
        raise ErrorKipu("bytes sobrantes en el manifest")
    return entradas


def descifrar_archivo(datos: bytes, mk: bytes, e: dict) -> bytes:
    clave = hkdf(mk, b"ckp/v1/file", sal=e["file_id"])
    cifrado = datos[e["offset"]: e["offset"] + e["cifrada"]]
    aes, salida, indice, pos = AESGCM(clave), bytearray(), 0, 0
    while True:
        bloque = cifrado[pos: pos + TAM_BLOQUE + TAM_TAG]
        ultimo = pos + len(bloque) >= len(cifrado)
        nonce = b"\0\0\0\0" + struct.pack(">Q", indice)
        aad = e["file_id"] + struct.pack(">Q", indice) + bytes([1 if ultimo else 0])
        salida += aes.decrypt(nonce, bloque, aad)
        pos += len(bloque)
        indice += 1
        if ultimo:
            break
    if len(salida) != e["tamano"]:
        raise ErrorKipu(f"tamaño distinto del declarado en {e['ruta']}")
    return bytes(salida)


# ---------------------------------------------------------------------------------------------- programa

def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("boveda")
    ap.add_argument("--f1", required=True)
    ap.add_argument("--f2")
    ap.add_argument("--f3")
    ap.add_argument("--llave")
    ap.add_argument("--token")
    ap.add_argument("--extraer")
    ap.add_argument("--verificar", help="archivo «sha256  ruta» con los SHA-256 esperados")
    a = ap.parse_args()

    datos = Path(a.boveda).read_bytes()
    mk = None
    for h in elegir_headers(datos):
        if a.token:
            mk = abrir_con_token(h, a.f1, a.token)
        else:
            llave = Path(a.llave).read_bytes() if a.llave else None
            mk = abrir_slot0(h, a.f1, a.f2 or "", a.f3 or "", llave)
        if mk is None:
            continue
        mac = hmac.new(hkdf(mk, b"ckp/v1/header-mac"), h["autenticados"], hashlib.sha256).digest()
        if hmac.compare_digest(mac, h["mac"]):
            break
        mk = None
    if mk is None:
        print("Credenciales incorrectas (o bóveda dañada).", file=sys.stderr)
        return 3
    entradas = leer_manifest(datos, mk, h, leer_footer(datos))
    esperados = {}
    if a.verificar:
        for linea in Path(a.verificar).read_text(encoding="utf-8").splitlines():
            if linea.strip():
                esperados[linea[66:]] = linea[:64]
    fallos = 0
    for e in entradas:
        contenido = descifrar_archivo(datos, mk, e)
        huella = hashlib.sha256(contenido).hexdigest()
        estado = ""
        if esperados:
            correcto = esperados.get(e["ruta"]) == huella
            estado = "  OK" if correcto else "  DISTINTO"
            fallos += 0 if correcto else 1
        print(f"{huella}  {e['ruta']}  ({len(contenido)} B){estado}")
        if a.extraer:
            destino = Path(a.extraer) / e["ruta"]
            destino.parent.mkdir(parents=True, exist_ok=True)
            destino.write_bytes(contenido)
    if esperados and set(esperados) != {e["ruta"] for e in entradas}:
        print("Las rutas de la bóveda no coinciden con las esperadas.", file=sys.stderr)
        fallos += 1
    return 1 if fallos else 0


if __name__ == "__main__":
    sys.exit(main())
