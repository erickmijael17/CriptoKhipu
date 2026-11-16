#!/usr/bin/env python3
"""Prueba de builds reproducibles (Fase 9): dos builds LIMPIOS deben producir los mismos SHA-256 en todos los JAR.

    python tools/verificar_reproducible.py

Cada ejecución hace ``gradlew clean jar`` y registra el SHA-256 de ``*/build/libs/*.jar``. Si algún JAR difiere entre
las dos ejecuciones, el programa lo informa y termina con código 1. Los JAR son reproducibles porque la convención de
build fija ``isPreserveFileTimestamps = false`` y ``isReproducibleFileOrder = true`` (buildSrc).
"""
import hashlib
import subprocess
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
GRADLEW = str(RAIZ / ("gradlew.bat" if sys.platform == "win32" else "gradlew"))


def construir() -> dict[str, str]:
    subprocess.run([GRADLEW, "clean", "jar", "--console=plain", "-q"], cwd=RAIZ, check=True)
    huellas = {}
    for jar in sorted(RAIZ.glob("*/build/libs/*.jar")):
        huellas[str(jar.relative_to(RAIZ)).replace("\\", "/")] = hashlib.sha256(jar.read_bytes()).hexdigest()
    return huellas


def main() -> int:
    primera = construir()
    segunda = construir()
    if not primera:
        print("No se encontró ningún JAR.", file=sys.stderr)
        return 1
    distintos = [ruta for ruta in primera if primera[ruta] != segunda.get(ruta)]
    for ruta, huella in primera.items():
        print(f"{huella}  {ruta}{'  DISTINTO' if ruta in distintos else ''}")
    if distintos or set(primera) != set(segunda):
        print(f"\nNO reproducible: {len(distintos)} JAR distintos entre dos builds limpios.", file=sys.stderr)
        return 1
    print(f"\nReproducible: {len(primera)} JAR con el mismo SHA-256 en dos builds limpios.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
