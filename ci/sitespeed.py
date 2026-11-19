#!/usr/bin/env python3
"""Compares the sitespeed.io measures of a build with those of main (the baseline).

  sitespeed.py summary <sitespeed output folder>        prints the measures of each page, as JSON
  sitespeed.py compare <baseline summary> <summary>     prints a Markdown table; exits 1 on a regression

The output folder needs the JSON of sitespeed.io: --plugins.add analysisstorer. A measure regresses when it grows
by more than 10% and by more than its slack, which absorbs the noise of CI runners on small values.
"""
import json
import pathlib
import sys

TOLERANCE = 0.10
# name: (unit, slack)
MEASURES = {
    "firstContentfulPaint": ("ms", 100),
    "largestContentfulPaint": ("ms", 100),
    "fullyLoaded": ("ms", 100),
    "requests": ("", 1),
    "transferSize": ("B", 1024),
    "javascriptSize": ("B", 1024),
    "cssSize": ("B", 1024),
}


def summary(folder):
    pages = {}
    for timings_file in sorted(pathlib.Path(folder, "pages").glob("**/data/browsertime.pageSummary.json")):
        browsertime = json.loads(timings_file.read_text())
        pagexray = json.loads((timings_file.parent / "pagexray.pageSummary.json").read_text())
        timings = browsertime["statistics"]["timings"]
        types = pagexray["contentTypes"]
        pages[browsertime["info"]["url"]] = {
            "firstContentfulPaint": timings["paintTiming"]["first-contentful-paint"]["median"],
            "largestContentfulPaint": timings["largestContentfulPaint"]["renderTime"]["median"],
            "fullyLoaded": timings["fullyLoaded"]["median"],
            "requests": pagexray["requests"],
            "transferSize": pagexray["transferSize"],
            "javascriptSize": types.get("javascript", {}).get("transferSize", 0),
            "cssSize": types.get("css", {}).get("transferSize", 0),
        }
    return pages


def compare(baseline, current):
    """Prints the table, and returns the regressions."""
    regressions = []
    print("| Page | Measure | main | this build | change |")
    print("|---|---|---:|---:|---:|")
    for url, measures in current.items():
        before = baseline.get(url)
        if before is None:
            print(f"| {url} | (new page) | | | |")
            continue
        for name, (unit, slack) in MEASURES.items():
            old, new = before.get(name), measures.get(name)
            if old is None or new is None:
                continue
            change = f"{(new - old) / old:+.0%}" if old else ""
            regressed = new > old * (1 + TOLERANCE) and new - old > slack
            mark = " ❌" if regressed else ""
            print(f"| {url} | {name} | {old} {unit} | {new} {unit} | {change}{mark} |")
            if regressed:
                regressions.append(f"{url} {name}: {old} → {new} {unit}")
    return regressions


def main(args):
    if len(args) == 2 and args[0] == "summary":
        print(json.dumps(summary(args[1]), indent=2, sort_keys=True))
        return 0
    if len(args) == 3 and args[0] == "compare":
        baseline = json.loads(pathlib.Path(args[1]).read_text())
        current = json.loads(pathlib.Path(args[2]).read_text())
        regressions = compare(baseline, current)
        if regressions:
            print(f"\n**{len(regressions)} regression(s)** of more than {TOLERANCE:.0%} compared with main:")
            for regression in regressions:
                print(f"- {regression}")
            return 1
        print(f"\nNo regression of more than {TOLERANCE:.0%} compared with main.")
        return 0
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
