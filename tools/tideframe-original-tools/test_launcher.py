"""Real -File regressions; false/missing EULA prevents Java, downloads or server starts."""
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT.parents[1] / 'work'


class LauncherCases:
    engine = ''

    def invoke(self, launcher, cwd, *args):
        executable = shutil.which(self.engine)
        if executable is None:
            self.skipTest(self.engine + ' is unavailable')
        return subprocess.run(
            [executable, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(launcher), *args],
            cwd=cwd, capture_output=True, timeout=15)

    def fixture(self, folder, eula=True):
        folder.mkdir()
        (folder / '.tideframe-disposable').write_text('TIDEFRAME_TEST_ONLY\n', encoding='ascii')
        if eula:
            (folder / 'eula.txt').write_text('eula=false\n', encoding='ascii')
        return folder

    def test_omitted_directory_uses_script_folder_from_another_cwd(self):
        with tempfile.TemporaryDirectory(prefix='launcher-test-', dir=WORK) as temp:
            base = Path(temp).resolve()
            self.assertEqual(base.parent, WORK.resolve())
            demo = self.fixture(base / 'demo with spaces')
            launcher = demo / 'INICIAR_PRUEBA.ps1'
            shutil.copy2(ROOT / 'tools/start-demo.ps1', launcher)
            result = self.invoke(launcher, base)
            self.assertEqual(result.returncode, 0, repr(result.stderr))
            self.assertIn(b'EULA sin aceptar', result.stdout)
            self.assertFalse((demo / 'paper.jar').exists())
            self.assertFalse((base / 'eula.txt').exists())

    def test_omitted_directory_creates_false_eula_beside_script(self):
        with tempfile.TemporaryDirectory(prefix='launcher-test-', dir=WORK) as temp:
            base = Path(temp).resolve()
            self.assertEqual(base.parent, WORK.resolve())
            demo = self.fixture(base / 'demo with spaces', eula=False)
            launcher = demo / 'INICIAR_PRUEBA.ps1'
            shutil.copy2(ROOT / 'tools/start-demo.ps1', launcher)
            result = self.invoke(launcher, base)
            self.assertEqual(result.returncode, 0, repr(result.stderr))
            self.assertIn('eula=false', (demo / 'eula.txt').read_text())
            self.assertFalse((demo / 'paper.jar').exists())
            self.assertFalse((base / 'eula.txt').exists())

    def test_explicit_directory_overrides_script_folder(self):
        with tempfile.TemporaryDirectory(prefix='launcher-test-', dir=WORK) as temp:
            base = Path(temp).resolve()
            self.assertEqual(base.parent, WORK.resolve())
            demo = self.fixture(base / 'explicit demo')
            launcher = base / 'INICIAR_PRUEBA.ps1'
            shutil.copy2(ROOT / 'tools/start-demo.ps1', launcher)
            result = self.invoke(launcher, base, '-DemoDirectory', str(demo))
            self.assertEqual(result.returncode, 0, repr(result.stderr))
            self.assertIn(b'EULA sin aceptar', result.stdout)
            self.assertFalse((base / 'eula.txt').exists())

    def test_quality_ports_keep_false_eula_without_starting_or_download(self):
        with tempfile.TemporaryDirectory(prefix='launcher-test-', dir=WORK) as temp:
            base = Path(temp).resolve()
            demo = self.fixture(base / 'quality demo')
            launcher = demo / 'INICIAR_PRUEBA.ps1'
            shutil.copy2(ROOT / 'tools/start-demo.ps1', launcher)
            result = self.invoke(launcher, base, '-MinecraftPort', '25588', '-PackPort', '8138')
            self.assertEqual(result.returncode, 0, repr(result.stderr))
            self.assertIn(b'EULA sin aceptar', result.stdout)
            self.assertFalse((demo / 'paper.jar').exists())

    def test_non_test_ports_are_rejected_before_starting_or_download(self):
        with tempfile.TemporaryDirectory(prefix='launcher-test-', dir=WORK) as temp:
            base = Path(temp).resolve()
            demo = self.fixture(base / 'wrong ports')
            launcher = demo / 'INICIAR_PRUEBA.ps1'
            shutil.copy2(ROOT / 'tools/start-demo.ps1', launcher)
            result = self.invoke(launcher, base, '-MinecraftPort', '25565')
            self.assertNotEqual(result.returncode, 0)
            self.assertFalse((demo / 'paper.jar').exists())


class WindowsPowerShellTests(LauncherCases, unittest.TestCase):
    engine = 'powershell.exe'


class PowerShellSevenTests(LauncherCases, unittest.TestCase):
    engine = 'pwsh.exe'


if __name__ == '__main__':
    sys.stdout.reconfigure(errors='backslashreplace')
    sys.stderr.reconfigure(errors='backslashreplace')
    unittest.main(verbosity=2)
