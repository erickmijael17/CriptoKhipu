#!/usr/bin/env python3
"""Falla si OSV-Scanner encontró una vulnerabilidad de severidad ALTA o CRÍTICA (CVSS >= 7.0) (Fase 9, solo CI).

    osv-scanner scan source --recursive --format json --output osv.json . || true
    python tools/osv_umbral.py osv.json

El escaneo necesita red (consulta la base de OSV); la aplicación en sí sigue siendo 100% offline. Las vulnerabilidades
de severidad media o baja se listan como aviso, sin romper el build. Para suprimir un falso positivo, agregarlo a
``osv-scanner.toml`` (``[[IgnoredVulns]]``) con su justificación escrita.
"""
import json
import sys
from pathlib import Path

UMBRAL = 7.0


def puntaje(texto) -> float:
    try:
        return float(texto)
    except (TypeError, ValueError):
        return {"CRITICAL": 9.5, "HIGH": 8.0, "MODERATE": 5.0, "MEDIUM": 5.0, "LOW": 2.0}.get(str(texto).upper(), 0.0)


def main() -> int:
    datos = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    altas, otras = [], []
    for resultado in datos.get("results", []):
        for paquete in resultado.get("packages", []):
            nombre = f"{paquete['package']['name']}@{paquete['package']['version']}"
            maximos = {}
            for grupo in paquete.get("groups", []):
                for ident in grupo.get("ids", []):
                    maximos[ident] = puntaje(grupo.get("max_severity"))
            for vuln in paquete.get("vulnerabilities", []):
                nota = maximos.get(vuln["id"], puntaje(vuln.get("database_specific", {}).get("severity")))
                (altas if nota >= UMBRAL else otras).append((nota, vuln["id"], nombre, vuln.get("summary", "")))
    for nota, ident, nombre, resumen in sorted(otras, reverse=True):
        print(f"aviso  {nota:4.1f}  {ident}  {nombre}  {resumen}")
    for nota, ident, nombre, resumen in sorted(altas, reverse=True):
        print(f"ALTA   {nota:4.1f}  {ident}  {nombre}  {resumen}", file=sys.stderr)
    if altas:
        print(f"\n{len(altas)} vulnerabilidad(es) con CVSS >= {UMBRAL}: el build falla.", file=sys.stderr)
        return 1
    print(f"Sin vulnerabilidades de severidad alta o crítica ({len(otras)} de menor severidad).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
