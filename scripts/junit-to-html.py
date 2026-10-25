#!/usr/bin/env python3
"""Convert a JUnit XML report to a self-contained HTML file (Python 3 only)."""

import argparse
from collections import Counter
from html import escape
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


STYLE = """
body { font: 1rem/1.5 system-ui, sans-serif; margin: 0 auto; padding: 1.5rem;
       max-width: 90rem; color: #18212b; background: #f6f8fa; }
h1, h2, h3, p, summary { overflow-wrap: anywhere; }
.totals { display: flex; flex-wrap: wrap; gap: .75rem; margin: 1rem 0; }
.totals span, article, .output { background: white; border: 1px solid #c5ccd3;
                              border-radius: .4rem; padding: 1rem; }
article { margin: 1rem 0; border-left: .4rem solid #27733c; }
article.failed, article.error { border-left-color: #b42318; }
article.skipped { border-left-color: #8a6100; }
h3 { margin: 0; font-size: 1.05rem; }
.status { font-weight: bold; }
pre { white-space: pre-wrap; overflow-wrap: anywhere; font-size: .95rem; }
summary { cursor: pointer; }
"""


def details(element, heading):
    """Preserve both the diagnostic attributes and complete text, safely escaped."""
    attributes = "\n".join(f"{key}: {value}" for key, value in element.attrib.items())
    body = "".join(element.itertext())
    content = "\n".join(part for part in (attributes, body) if part)
    return f"<details open><summary>{escape(heading)}</summary><pre>{escape(content)}</pre></details>"


def render(root, source):
    counts = Counter()
    duration = 0.0
    sections = []
    for suite in root.iter("testsuite"):
        cases = []
        for case in suite.findall("testcase"):
            failures = case.findall("failure")
            errors = case.findall("error")
            skipped = case.findall("skipped")
            status = "error" if errors else "failed" if failures else "skipped" if skipped else "passed"
            counts[status] += 1
            seconds = case.get("time", "0")
            duration += float(seconds)
            name = case.get("name", "Unnamed test")
            classname = case.get("classname", "")
            diagnostics = "".join(details(node, heading) for nodes, heading in (
                (failures, "Failure"), (errors, "Error"), (skipped, "Skipped"),
                (case.findall("system-out"), "Standard output"),
                (case.findall("system-err"), "Standard error")) for node in nodes)
            cases.append(
                f'<article class="{status}"><h3>{escape(name)}</h3>'
                f'<p>{escape(classname)}</p><p><span class="status">{status.title()}</span>'
                f' · {escape(seconds)} seconds</p>{diagnostics}</article>')
        output = "".join(details(node, heading) for tag, heading in (
            ("system-out", "Suite standard output"), ("system-err", "Suite standard error"))
            for node in suite.findall(tag))
        sections.append(f'<section><h2>{escape(suite.get("name", "Unnamed suite"))}</h2>'
                        + "".join(cases) + output + "</section>")
    total = sum(counts.values())
    totals = f"<span>Total: {total}</span>" + "".join(
        f"<span>{label.title()}: {counts[label]}</span>" for label in ("passed", "failed", "error", "skipped"))
    return (f'<!doctype html><html lang="en"><head><meta charset="utf-8">'
            f'<meta name="viewport" content="width=device-width, initial-scale=1">'
            f'<title>FdSwarm test results</title><style>{STYLE}</style></head><body>'
            f'<h1>FdSwarm test results</h1><p>Source: {escape(str(source))}</p>'
            f'<div class="totals">{totals}<span>Total test time: {duration:.3f} seconds</span></div>'
            + "".join(sections) + '</body></html>')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("xml", type=Path, help="JUnit XML report")
    parser.add_argument("--output", type=Path, help="HTML destination (default: XML path with .html suffix)")
    args = parser.parse_args()
    destination = args.output or args.xml.with_suffix(".html")
    if destination.resolve() == args.xml.resolve():
        parser.error("HTML output must not overwrite the XML input")
    try:
        root = ET.parse(args.xml).getroot()
        if root.tag not in ("testsuite", "testsuites"):
            raise ValueError("Expected a JUnit testsuite or testsuites root element")
        destination.write_text(render(root, args.xml), encoding="utf-8")
    except (OSError, ET.ParseError, ValueError) as error:
        print(f"Cannot generate test report: {error}", file=sys.stderr)
        return 1
    print(destination)
    return 0


if __name__ == "__main__":
    sys.exit(main())
