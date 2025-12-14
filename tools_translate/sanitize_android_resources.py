# -*- coding: utf-8 -*-
from __future__ import annotations

import re
import shutil
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

# This file is intentionally written WITHOUT docstrings containing "\u" to avoid
# Python unicodeescape parsing issues on Windows.

@dataclass
class Stats:
    files_scanned: int = 0
    files_fixed: int = 0
    occurrences: int = 0
    files_moved: int = 0
    io_errors: int = 0

# Match language resource folders like: values-en, values-fr, values-es, values-de, values-en-rUS...
# Avoid qualifiers like values-night, values-land, values-sw600dp, etc.
_LANG_RE = re.compile(r"^values-([a-z]{2,3})(?:$|-r[A-Z]{2}$)")

# Files that should NOT be localized (they break or are ignored by AAPT when duplicated under values-xx)
_NON_LOCALIZABLE_GLOBS = [
    "attrs*.xml",
    "colors*.xml",
    "dimens*.xml",
    "themes*.xml",
    "styles*.xml",
    "ids*.xml",
    "ht_colors*.xml",
    "ic_launcher_background*.xml",
    "secrets*.xml",
    "ids_and_strings_profile*.xml",
]

# Allowed Android string escapes after a single backslash.
# Keep: \\ \n \t \r \b \' \" and also \@ \? to escape resource refs.
_ALLOWED_ESCAPES = set(list("ntrb'\"\\@?"))

_HEX4 = re.compile(r"^[0-9a-fA-F]{4}$")


def _is_language_values_dir(p: Path) -> bool:
    return p.is_dir() and bool(_LANG_RE.match(p.name))


def _make_backup_dir(script_dir: Path) -> Path:
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup = script_dir / "_i18n_backup" / ts
    backup.mkdir(parents=True, exist_ok=True)
    return backup


def _matches_any_glob(path: Path, globs: list[str]) -> bool:
    for g in globs:
        if path.match(g):
            return True
    return False


def _fix_invalid_backslashes(s: str) -> tuple[str, int]:
    # Fix invalid backslash escapes for Android string resources.
    # Keep allowed escapes and valid unicode \uXXXX, escape everything else.

    if "\\" not in s:
        return s, 0

    out: list[str] = []
    i = 0
    occ = 0
    n = len(s)

    while i < n:
        ch = s[i]
        if ch != "\\":
            out.append(ch)
            i += 1
            continue

        # Count consecutive backslashes
        j = i
        while j < n and s[j] == "\\":
            j += 1
        run_len = j - i

        # If already escaped (\\ or more), keep as-is
        if run_len >= 2:
            out.append("\\" * run_len)
            i = j
            continue

        # Single backslash
        if j >= n:
            out.append("\\\\")
            occ += 1
            i = j
            continue

        nxt = s[j]

        # Valid simple escapes
        if nxt in _ALLOWED_ESCAPES:
            out.append("\\")
            out.append(nxt)
            i = j + 1
            continue

        # Unicode escape \uXXXX
        if nxt == "u":
            hex_part = s[j + 1 : j + 5] if (j + 5) <= n else ""
            if len(hex_part) == 4 and _HEX4.match(hex_part):
                out.append("\\u")
                out.append(hex_part)
                i = j + 5
                continue
            # Invalid \u -> escape backslash
            out.append("\\\\u")
            occ += 1
            i = j + 1
            continue

        # Uppercase \U (common in Windows paths like C:\Users)
        if nxt == "U":
            out.append("\\\\U")
            occ += 1
            i = j + 1
            continue

        # Unknown escape -> escape backslash
        out.append("\\\\")
        out.append(nxt)
        occ += 1
        i = j + 1

    return "".join(out), occ


def _sanitize_one_xml_file(path: Path) -> tuple[bool, int]:
    # Text-level sanitization (no XML parsing), keeps formatting intact.
    raw = path.read_bytes()
    text = raw.decode("utf-8-sig")  # accept BOM
    fixed_text, occ = _fix_invalid_backslashes(text)
    if occ > 0 and fixed_text != text:
        path.write_text(fixed_text, encoding="utf-8", newline="\n")
        return True, occ
    return False, 0


def main() -> int:
    script_dir = Path(__file__).resolve().parent
    project_root = script_dir.parent
    res_dir = project_root / "app" / "src" / "main" / "res"

    print("==================================================")
    print("HikeTrack - i18n quick FIX (v9)")
    print("- Fix invalid backslash/unicode escapes in translated strings")
    print("- Move non-localizable resources out of values-xx folders")
    print("==================================================")
    print(f"[INFO] Script: {Path(__file__).resolve()}")
    print(f"[INFO] Project root: {project_root}")
    print(f"[INFO] res dir: {res_dir}")
    print("")

    if not res_dir.exists():
        print("[ERREUR] Dossier res introuvable. Attendu:", res_dir)
        return 2

    backup_root = _make_backup_dir(script_dir)
    stats = Stats()

    values_dirs = sorted([p for p in res_dir.iterdir() if _is_language_values_dir(p)])
    if not values_dirs:
        print("[WARN] Aucun dossier values-xx detecte.")
        return 0

    print("[INFO] Dossiers langues detectes:", ", ".join([p.name for p in values_dirs]))

    for vdir in values_dirs:
        for xml in sorted(vdir.glob("*.xml")):
            stats.files_scanned += 1

            if _matches_any_glob(xml, _NON_LOCALIZABLE_GLOBS):
                rel = xml.relative_to(res_dir)
                dest = backup_root / rel
                dest.parent.mkdir(parents=True, exist_ok=True)
                try:
                    shutil.move(str(xml), str(dest))
                    stats.files_moved += 1
                    print(f"[MOVE] {rel} -> {dest.relative_to(script_dir)}")
                except Exception as e:
                    stats.io_errors += 1
                    print(f"[WARN] Impossible de deplacer {rel}: {e}")
                continue

            try:
                changed, occ = _sanitize_one_xml_file(xml)
                if changed:
                    stats.files_fixed += 1
                    stats.occurrences += occ
                    rel = xml.relative_to(res_dir)
                    print(f"[FIX]  {rel}  (+{occ} corrections)")
            except Exception as e:
                stats.io_errors += 1
                rel = xml.relative_to(res_dir)
                print(f"[WARN] Echec lecture/ecriture {rel}: {e}")

    print("--------------------------------------------------")
    print(f"[DONE] Fichiers scannes: {stats.files_scanned}")
    print(f"[DONE] Fichiers corriges: {stats.files_fixed} (occurrences: {stats.occurrences})")
    print(f"[DONE] Fichiers deplaces: {stats.files_moved}")
    if stats.io_errors:
        print(f"[WARN] Problemes I/O: {stats.io_errors}")
    print(f"[INFO] Backup non-destructif: {backup_root}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
