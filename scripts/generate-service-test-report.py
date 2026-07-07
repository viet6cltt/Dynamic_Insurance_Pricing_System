#!/usr/bin/env python3
from __future__ import annotations

import datetime as dt
import html
import pathlib
import xml.etree.ElementTree as ET


ROOT = pathlib.Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "test-report" / "index.html"
SERVICES = [
    ("user-service", "User Service"),
    ("product-service", "Product Service"),
    ("payment-service", "Payment Service"),
    ("pricing-service", "Pricing Service"),
    ("application-policy-service", "Application Policy Service"),
    ("notification-service", "Notification Service"),
]
THRESHOLD = 70.0


def pct(covered: int, missed: int) -> float:
    total = covered + missed
    return 100.0 if total == 0 else covered * 100.0 / total


def read_counter(node: ET.Element, counter_type: str) -> tuple[int, int]:
    for counter in node.findall("counter"):
        if counter.attrib.get("type") == counter_type:
            return int(counter.attrib.get("covered", 0)), int(counter.attrib.get("missed", 0))
    return 0, 0


def add_counter(left: tuple[int, int], right: tuple[int, int]) -> tuple[int, int]:
    return left[0] + right[0], left[1] + right[1]


def read_jacoco(service_dir: pathlib.Path) -> dict:
    report = service_dir / "target/site/jacoco/jacoco.xml"
    empty = {
        "exists": False,
        "module_line": (0, 0),
        "module_instruction": (0, 0),
        "module_branch": (0, 0),
        "service_line": (0, 0),
        "service_instruction": (0, 0),
        "classes": [],
    }
    if not report.exists():
        return empty

    root = ET.parse(report).getroot()
    data = {
        "exists": True,
        "module_line": read_counter(root, "LINE"),
        "module_instruction": read_counter(root, "INSTRUCTION"),
        "module_branch": read_counter(root, "BRANCH"),
        "service_line": (0, 0),
        "service_instruction": (0, 0),
        "classes": [],
    }

    for package in root.findall("package"):
        package_name = package.attrib.get("name", "")
        if "/service" not in package_name:
            continue

        data["service_line"] = add_counter(data["service_line"], read_counter(package, "LINE"))
        data["service_instruction"] = add_counter(
            data["service_instruction"], read_counter(package, "INSTRUCTION")
        )

        for class_node in package.findall("class"):
            line = read_counter(class_node, "LINE")
            instruction = read_counter(class_node, "INSTRUCTION")
            if line == (0, 0) and instruction == (0, 0):
                continue
            data["classes"].append(
                {
                    "name": class_node.attrib.get("name", "").replace("/", "."),
                    "line": line,
                    "instruction": instruction,
                }
            )

    data["classes"].sort(key=lambda item: pct(*item["line"]))
    return data


def read_surefire(service_dir: pathlib.Path) -> dict:
    reports_dir = service_dir / "target/surefire-reports"
    totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0, "time": 0.0, "suites": []}
    if not reports_dir.exists():
        return totals

    for report in sorted(reports_dir.glob("TEST-*.xml")):
        root = ET.parse(report).getroot()
        suites = root.findall("testsuite") if root.tag == "testsuites" else [root]
        for suite in suites:
            tests = int(float(suite.attrib.get("tests", 0)))
            failures = int(float(suite.attrib.get("failures", 0)))
            errors = int(float(suite.attrib.get("errors", 0)))
            skipped = int(float(suite.attrib.get("skipped", 0)))
            elapsed = float(suite.attrib.get("time", 0.0))
            totals["tests"] += tests
            totals["failures"] += failures
            totals["errors"] += errors
            totals["skipped"] += skipped
            totals["time"] += elapsed
            totals["suites"].append(
                {
                    "name": suite.attrib.get("name", report.stem.removeprefix("TEST-")),
                    "tests": tests,
                    "failures": failures,
                    "errors": errors,
                    "skipped": skipped,
                    "time": elapsed,
                }
            )
    return totals


def fmt_pct(counter: tuple[int, int]) -> str:
    return f"{pct(*counter):.1f}%"


def coverage_class(counter: tuple[int, int]) -> str:
    value = pct(*counter)
    if value >= 85:
        return "strong"
    if value >= THRESHOLD:
        return "ok"
    return "low"


def status_for(service: dict) -> tuple[str, str]:
    failed_tests = service["surefire"]["failures"] + service["surefire"]["errors"]
    service_coverage = pct(*service["jacoco"]["service_line"])
    if failed_tests:
        return "Failing", "bad"
    if not service["jacoco"]["exists"]:
        return "Missing report", "warn"
    if service_coverage < THRESHOLD:
        return "Below target", "warn"
    return "Passing", "good"


def bar(counter: tuple[int, int]) -> str:
    value = pct(*counter)
    return (
        f'<div class="bar {coverage_class(counter)}">'
        f'<span style="width: {value:.1f}%"></span>'
        f"</div>"
    )


def render_services(services: list[dict]) -> str:
    rows = []
    for service in services:
        status, tone = status_for(service)
        jacoco = service["jacoco"]
        surefire = service["surefire"]
        jacoco_link = f"../{service['id']}/target/site/jacoco/index.html"
        rows.append(
            f"""
            <section class="service-card">
              <div class="service-head">
                <div>
                  <h2>{html.escape(service["title"])}</h2>
                  <p>{html.escape(service["id"])}</p>
                </div>
                <span class="badge {tone}">{status}</span>
              </div>
              <div class="metrics">
                <div><span>{surefire["tests"]}</span><label>Tests</label></div>
                <div><span>{surefire["failures"] + surefire["errors"]}</span><label>Failed</label></div>
                <div><span>{surefire["skipped"]}</span><label>Skipped</label></div>
                <div><span>{surefire["time"]:.1f}s</span><label>Time</label></div>
              </div>
              <div class="coverage-row">
                <div>
                  <strong>Service line coverage</strong>
                  <span>{fmt_pct(jacoco["service_line"])}</span>
                </div>
                {bar(jacoco["service_line"])}
              </div>
              <div class="coverage-row">
                <div>
                  <strong>Module line coverage</strong>
                  <span>{fmt_pct(jacoco["module_line"])}</span>
                </div>
                {bar(jacoco["module_line"])}
              </div>
              <a class="report-link" href="{jacoco_link}">Open detailed Jacoco report</a>
            </section>
            """
        )
    return "\n".join(rows)


def render_class_rows(services: list[dict]) -> str:
    rows = []
    for service in services:
        for item in service["jacoco"]["classes"]:
            rows.append(
                f"""
                <tr>
                  <td>{html.escape(service["id"])}</td>
                  <td>{html.escape(item["name"])}</td>
                  <td>{fmt_pct(item["line"])}</td>
                  <td>{item["line"][0]}/{item["line"][0] + item["line"][1]}</td>
                  <td>{bar(item["line"])}</td>
                </tr>
                """
            )
    return "\n".join(rows)


def render_suite_rows(services: list[dict]) -> str:
    rows = []
    for service in services:
        for suite in sorted(service["surefire"]["suites"], key=lambda item: item["name"]):
            failed = suite["failures"] + suite["errors"]
            tone = "bad" if failed else "good"
            rows.append(
                f"""
                <tr>
                  <td>{html.escape(service["id"])}</td>
                  <td>{html.escape(suite["name"])}</td>
                  <td>{suite["tests"]}</td>
                  <td><span class="mini-badge {tone}">{failed}</span></td>
                  <td>{suite["skipped"]}</td>
                  <td>{suite["time"]:.2f}s</td>
                </tr>
                """
            )
    return "\n".join(rows)


def render_html(services: list[dict]) -> str:
    generated_at = dt.datetime.now().astimezone().strftime("%Y-%m-%d %H:%M:%S %Z")
    total_tests = sum(item["surefire"]["tests"] for item in services)
    total_failed = sum(item["surefire"]["failures"] + item["surefire"]["errors"] for item in services)
    total_skipped = sum(item["surefire"]["skipped"] for item in services)
    total_time = sum(item["surefire"]["time"] for item in services)
    service_line = (0, 0)
    module_line = (0, 0)
    for service in services:
        service_line = add_counter(service_line, service["jacoco"]["service_line"])
        module_line = add_counter(module_line, service["jacoco"]["module_line"])

    return f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Service Test Report</title>
  <style>
    :root {{
      color-scheme: light;
      --bg: #f6f7f9;
      --panel: #ffffff;
      --text: #1f2937;
      --muted: #667085;
      --line: #d8dee8;
      --green: #16845b;
      --green-soft: #dff5ec;
      --yellow: #a15c00;
      --yellow-soft: #fff1d6;
      --red: #b42318;
      --red-soft: #fee4e2;
      --blue: #2563eb;
      font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
    }}
    * {{ box-sizing: border-box; }}
    body {{ margin: 0; background: var(--bg); color: var(--text); }}
    main {{ max-width: 1180px; margin: 0 auto; padding: 32px 20px 48px; }}
    header {{ display: flex; justify-content: space-between; gap: 24px; align-items: flex-end; margin-bottom: 24px; }}
    h1, h2, h3 {{ margin: 0; letter-spacing: 0; }}
    h1 {{ font-size: 34px; line-height: 1.15; }}
    h2 {{ font-size: 18px; }}
    h3 {{ font-size: 20px; margin: 28px 0 12px; }}
    p {{ margin: 6px 0 0; color: var(--muted); }}
    .summary {{ display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; margin-bottom: 18px; }}
    .summary-card, .service-card, .table-wrap {{ background: var(--panel); border: 1px solid var(--line); border-radius: 8px; }}
    .summary-card {{ padding: 16px; }}
    .summary-card span {{ display: block; font-size: 26px; font-weight: 750; }}
    .summary-card label {{ display: block; margin-top: 4px; color: var(--muted); font-size: 13px; }}
    .grid {{ display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }}
    .service-card {{ padding: 18px; }}
    .service-head {{ display: flex; justify-content: space-between; gap: 12px; align-items: flex-start; margin-bottom: 16px; }}
    .badge, .mini-badge {{ display: inline-flex; align-items: center; border-radius: 999px; font-size: 12px; font-weight: 700; }}
    .badge {{ padding: 6px 10px; }}
    .mini-badge {{ padding: 3px 8px; }}
    .good {{ background: var(--green-soft); color: var(--green); }}
    .warn {{ background: var(--yellow-soft); color: var(--yellow); }}
    .bad {{ background: var(--red-soft); color: var(--red); }}
    .metrics {{ display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; margin-bottom: 16px; }}
    .metrics div {{ border: 1px solid var(--line); border-radius: 8px; padding: 10px; }}
    .metrics span {{ display: block; font-size: 20px; font-weight: 750; }}
    .metrics label {{ display: block; color: var(--muted); font-size: 12px; margin-top: 2px; }}
    .coverage-row {{ margin-top: 12px; }}
    .coverage-row > div:first-child {{ display: flex; justify-content: space-between; gap: 12px; margin-bottom: 6px; font-size: 13px; }}
    .bar {{ height: 9px; background: #eef2f7; border-radius: 999px; overflow: hidden; }}
    .bar span {{ display: block; height: 100%; border-radius: inherit; }}
    .bar.strong span {{ background: var(--green); }}
    .bar.ok span {{ background: var(--blue); }}
    .bar.low span {{ background: var(--yellow); }}
    .report-link {{ display: inline-flex; margin-top: 14px; color: var(--blue); font-weight: 700; text-decoration: none; }}
    .report-link:hover {{ text-decoration: underline; }}
    .table-wrap {{ overflow: auto; }}
    table {{ width: 100%; border-collapse: collapse; min-width: 820px; }}
    th, td {{ padding: 11px 12px; border-bottom: 1px solid var(--line); text-align: left; font-size: 13px; vertical-align: middle; }}
    th {{ color: var(--muted); font-weight: 750; background: #fbfcfe; }}
    tr:last-child td {{ border-bottom: 0; }}
    code {{ background: #eef2f7; padding: 2px 5px; border-radius: 5px; }}
    .note {{ margin-top: 16px; color: var(--muted); font-size: 13px; }}
    @media (max-width: 900px) {{
      header {{ display: block; }}
      .summary {{ grid-template-columns: repeat(2, minmax(0, 1fr)); }}
      .grid {{ grid-template-columns: 1fr; }}
    }}
  </style>
</head>
<body>
  <main>
    <header>
      <div>
        <h1>Service Test Report</h1>
        <p>Aggregated from Maven Surefire and Jacoco reports across backend services.</p>
      </div>
      <p>Generated: {html.escape(generated_at)}</p>
    </header>

    <section class="summary">
      <div class="summary-card"><span>{total_tests}</span><label>Total tests</label></div>
      <div class="summary-card"><span>{total_failed}</span><label>Failed tests</label></div>
      <div class="summary-card"><span>{total_skipped}</span><label>Skipped tests</label></div>
      <div class="summary-card"><span>{total_time:.1f}s</span><label>Total time</label></div>
      <div class="summary-card"><span>{fmt_pct(service_line)}</span><label>Service coverage</label></div>
    </section>

    <div class="coverage-row">
      <div><strong>Overall service line coverage target: {THRESHOLD:.0f}%</strong><span>{fmt_pct(service_line)}</span></div>
      {bar(service_line)}
    </div>
    <div class="coverage-row">
      <div><strong>Overall module line coverage</strong><span>{fmt_pct(module_line)}</span></div>
      {bar(module_line)}
    </div>

    <h3>Service Summary</h3>
    <section class="grid">
      {render_services(services)}
    </section>

    <h3>Service Class Coverage</h3>
    <section class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Service</th>
            <th>Class</th>
            <th>Line coverage</th>
            <th>Covered lines</th>
            <th>Progress</th>
          </tr>
        </thead>
        <tbody>
          {render_class_rows(services)}
        </tbody>
      </table>
    </section>

    <h3>Test Suites</h3>
    <section class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Service</th>
            <th>Suite</th>
            <th>Tests</th>
            <th>Failed</th>
            <th>Skipped</th>
            <th>Time</th>
          </tr>
        </thead>
        <tbody>
          {render_suite_rows(services)}
        </tbody>
      </table>
    </section>

    <p class="note">
      Refresh data by rerunning each service with Jacoco, then run
      <code>python3 scripts/generate-service-test-report.py</code>.
    </p>
  </main>
</body>
</html>
"""


def main() -> None:
    services = []
    for service_id, title in SERVICES:
        service_dir = ROOT / service_id
        services.append(
            {
                "id": service_id,
                "title": title,
                "jacoco": read_jacoco(service_dir),
                "surefire": read_surefire(service_dir),
            }
        )

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(render_html(services), encoding="utf-8")
    print(f"Wrote {OUTPUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
