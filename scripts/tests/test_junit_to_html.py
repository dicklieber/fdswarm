"""Run with: python3 -m unittest discover -s scripts/tests"""

from html import unescape
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "junit-to-html.py"


class JunitHtmlTest(unittest.TestCase):
    def test_nested_suites_statuses_and_full_escaped_diagnostics(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "test-report.xml"
            long_message = "details " * 300
            path.write_text(f'''<testsuites><testsuite name="outer"><testsuite name="inner">
              <testcase name="passes" time="0.25"/>
              <testcase name="fails" time="0.5"><failure message="&lt;script&gt;bad&lt;/script&gt;">{long_message}</failure></testcase>
              <testcase name="errors"><error type="Exception">trace</error></testcase>
              <testcase name="skips"><skipped message="reason"/></testcase>
              <system-out>full output &amp; data</system-out>
            </testsuite></testsuite></testsuites>''', encoding="utf-8")
            result = subprocess.run([sys.executable, str(SCRIPT), str(path)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            html = path.with_suffix(".html").read_text()
            for expected in ("Total: 4", "Passed: 1", "Failed: 1", "Error: 1", "Skipped: 1", "0.750 seconds"):
                self.assertIn(expected, html)
            self.assertIn(long_message, html)
            self.assertNotIn("<script>", html)
            self.assertIn("<script>bad</script>", unescape(html))
            self.assertIn("full output &amp; data", html)

    def test_single_suite_and_explicit_output(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "source.xml"
            output = Path(directory) / "custom.html"
            path.write_text('<testsuite name="single"><testcase name="one"/></testsuite>')
            result = subprocess.run([sys.executable, str(SCRIPT), str(path), "--output", str(output)], capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("Total: 1", output.read_text())

    def test_invalid_xml_and_input_overwrite_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "invalid.xml"
            path.write_text("broken XML")
            for arguments in ([str(path)], [str(path), "--output", str(path)]):
                result = subprocess.run([sys.executable, str(SCRIPT), *arguments], capture_output=True)
                self.assertNotEqual(result.returncode, 0)
            self.assertEqual(path.read_text(), "broken XML")
