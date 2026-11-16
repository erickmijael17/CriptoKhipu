#!/usr/bin/env python3
"""Resumen de calidad (Fase 9): cobertura (JaCoCo), mutaciones (PIT) y análisis estático (SpotBugs).

    ./gradlew clean check :cryptokipu-core:pitest [:cryptokipu-infra:pitest]
    python tools/resumen_calidad.py --readme README.md

Lee los reportes de ``*/build/reports`` y reemplaza el bloque entre ``<!-- calidad:inicio -->`` y
``<!-- calidad:fin -->`` del README con una tabla. Sin ``--readme`` solo imprime la tabla.
"""
import argparse
import re
import sys
from pathlib import Path
from xml.etree import ElementTree as ET

RAIZ = Path(__file__).resolve().parent.parent
MODULOS = ["core", "app", "infra", "cli", "visores", "ui"]


def jacoco(modulo: str):
    ruta = RAIZ / f"cryptokipu-{modulo}/build/reports/jacoco/test/jacocoTestReport.xml"
    if not ruta.exists():
        return None
    raiz = ET.parse(ruta).getroot()
    contadores = {}
    for c in raiz.findall("counter"):
        contadores[c.get("type")] = (int(c.get("covered")), int(c.get("missed")))
    return contadores


def porcentaje(par) -> str:
    if not par or sum(par) == 0:
        return "—"
    return f"{100 * par[0] / sum(par):.0f} %"


def pit(modulo: str):
    ruta = RAIZ / f"cryptokipu-{modulo}/build/reports/pitest/mutations.xml"
    if not ruta.exists():
        return None
    por_paquete = {}
    for m in ET.parse(ruta).getroot().iter("mutation"):
        paquete = ".".join(m.find("mutatedClass").text.split(".")[:-1]).replace("dev.erick.cryptokipu.", "")
        muertos, total = por_paquete.get(paquete, (0, 0))
        detectado = m.get("status") in ("KILLED", "TIMED_OUT", "MEMORY_ERROR", "RUN_ERROR")
        por_paquete[paquete] = (muertos + (1 if detectado else 0), total + 1)
    return por_paquete


def spotbugs(modulo: str):
    ruta = RAIZ / f"cryptokipu-{modulo}/build/reports/spotbugs/main.xml"
    if not ruta.exists():
        return None
    altas = medias = 0
    for b in ET.parse(ruta).getroot().iter("BugInstance"):
        if b.get("priority") == "1":
            altas += 1
        elif b.get("priority") == "2":
            medias += 1
    return altas, medias


def tabla() -> str:
    filas = ["| Módulo | Líneas | Ramas | Instrucciones |", "|---|---|---|---|"]
    tot = {"LINE": [0, 0], "BRANCH": [0, 0], "INSTRUCTION": [0, 0]}
    for m in MODULOS:
        c = jacoco(m)
        if not c:
            continue
        for k in tot:
            if k in c:
                tot[k][0] += c[k][0]
                tot[k][1] += c[k][1]
        filas.append(f"| `{m}` | {porcentaje(c.get('LINE'))} | {porcentaje(c.get('BRANCH'))} | {porcentaje(c.get('INSTRUCTION'))} |")
    filas.append(f"| **Total** | **{porcentaje(tot['LINE'])}** | **{porcentaje(tot['BRANCH'])}** | **{porcentaje(tot['INSTRUCTION'])}** |")
    salida = ["**Cobertura de pruebas (JaCoCo):**", "", *filas, ""]
    mut = ["| Paquete | Mutantes detectados |", "|---|---|"]
    hay = False
    gm = gt = 0
    for m in MODULOS:
        r = pit(m)
        if not r:
            continue
        for paquete, (muertos, total) in sorted(r.items()):
            hay = True
            gm += muertos
            gt += total
            mut.append(f"| `{paquete}` | {100 * muertos / total:.0f} % ({muertos}/{total}) |")
    if hay:
        mut.append(f"| **Total** | **{100 * gm / gt:.0f} % ({gm}/{gt})** |")
        salida += ["**Mutaciones (PIT):**", "", *mut, ""]
    altas = medias = 0
    visto = False
    for m in MODULOS:
        r = spotbugs(m)
        if r:
            visto = True
            altas += r[0]
            medias += r[1]
    if visto:
        salida += [f"**Análisis estático (SpotBugs + FindSecBugs):** {altas} hallazgos de prioridad alta"
                   f" (el build falla con cualquiera) y {medias} de prioridad media (informativos).", ""]
    return "\n".join(salida)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--readme")
    a = ap.parse_args()
    texto = tabla()
    if not a.readme:
        print(texto)
        return 0
    ruta = Path(a.readme)
    contenido = ruta.read_text(encoding="utf-8")
    nuevo, n = re.subn(r"(<!-- calidad:inicio -->).*?(<!-- calidad:fin -->)",
                       lambda m: f"{m.group(1)}\n{texto}\n{m.group(2)}", contenido, flags=re.S)
    if n != 1:
        print("No se encontró el bloque <!-- calidad:inicio --> … <!-- calidad:fin --> en el README.", file=sys.stderr)
        return 1
    ruta.write_text(nuevo, encoding="utf-8", newline="")
    print(f"README actualizado ({len(texto.splitlines())} líneas).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
