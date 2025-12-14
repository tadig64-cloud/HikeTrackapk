#!/usr/bin/env python3
import argparse, os, re
from pathlib import Path
import xml.etree.ElementTree as ET

def iter_xml_files(folder: Path):
    for p in folder.rglob("*.xml"):
        # Only resource XML in values* folders
        if p.parent.name.startswith("values"):
            yield p

def extract_names(xml_path: Path):
    try:
        root = ET.parse(xml_path).getroot()
    except Exception:
        return []
    names = []
    # <string name="...">
    for e in root.findall("string"):
        n = e.attrib.get("name")
        if n: names.append(n)
    # <string-array name="...">
    for arr in root.findall("string-array"):
        n = arr.attrib.get("name")
        if n: names.append(n)
    # <plurals name="...">
    for pl in root.findall("plurals"):
        n = pl.attrib.get("name")
        if n: names.append(n)
    return names

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--res", required=True, help="Path to app/src/main/res")
    args = ap.parse_args()

    res = Path(args.res)
    values_folders = [p for p in res.iterdir() if p.is_dir() and p.name.startswith("values")]
    any_dup = False

    for vf in sorted(values_folders, key=lambda p: p.name):
        seen = {}
        dups = []
        for xml in iter_xml_files(vf):
            for name in extract_names(xml):
                if name in seen:
                    dups.append((name, seen[name], xml))
                else:
                    seen[name] = xml
        if dups:
            any_dup = True
            print(f"\n=== Duplicates in {vf.name} ===")
            for name, first, second in dups:
                print(f"- {name}\n  first:  {first}\n  second: {second}")
    if not any_dup:
        print("OK: Aucun duplicate detecte dans les dossiers values*.")

if __name__ == "__main__":
    main()
