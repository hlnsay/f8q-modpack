"""Build the server-only identity bridge against a local NeoForge development classpath."""
from pathlib import Path
import argparse, json, subprocess, tempfile, zipfile
parser = argparse.ArgumentParser()
parser.add_argument('--classpath-file', required=True)
parser.add_argument('--javac', default='javac')
parser.add_argument('--output', required=True)
a = parser.parse_args()
root = Path(__file__).resolve().parent
classpath = Path(a.classpath_file).read_text(encoding='utf-8-sig').strip()
if classpath.startswith('-cp "'):
    classpath = classpath[5:-1]
with tempfile.TemporaryDirectory(prefix='f8q-identity-') as tmp:
    out = Path(tmp)
    subprocess.run([a.javac, '--release', '21', '-proc:none', '-encoding', 'UTF-8', '-cp', classpath, '-d', tmp, *map(str, (root / 'src').rglob('*.java'))], check=True)
    with zipfile.ZipFile(a.output, 'w', zipfile.ZIP_DEFLATED) as jar:
        for f in out.rglob('*.class'):
            jar.write(f, f.relative_to(out).as_posix())
        jar.writestr('META-INF/neoforge.mods.toml', 'modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="f8qidentity"\nversion="1.0.0"\ndisplayName="F8Q Player Identity"\ndescription="Server-side stable nickname identities"\n[[mixins]]\nconfig="f8qidentity.mixins.json"\n')
        jar.writestr('f8qidentity.mixins.json', json.dumps(dict(required=True, package='dev.f8q.identity.mixin', compatibilityLevel='JAVA_21', mixins=['OfflineIdentityMixin'], injectors=dict(defaultRequire=1))))
