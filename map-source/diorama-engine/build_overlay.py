"""Build the embedded renderer against the NeoForge 1.21.1 mapped development classpath."""
from pathlib import Path
import argparse,hashlib,json,shutil,subprocess,urllib.request,uuid,zipfile
cli=argparse.ArgumentParser();cli.add_argument('--base-jar',required=True,type=Path);cli.add_argument('--classpath-file',required=True,type=Path);cli.add_argument('--output',required=True,type=Path);cli.add_argument('--javac',default='javac');args=cli.parse_args()
root=Path(__file__).resolve().parent;info=json.loads((root/'upstream.json').read_text('utf-8'));assert hashlib.sha512(args.base_jar.read_bytes()).hexdigest()==info['base']['sha512'],'Use the unmodified v22 base JAR'
args.output.parent.mkdir(parents=True,exist_ok=True);build=args.output.parent/'f8q-diorama-build'/uuid.uuid4().hex;build.mkdir(parents=True);vendor=args.output.parent/'f8q-diorama-build/vendor';vendor.mkdir(parents=True,exist_ok=True)
def upstream(prefix):
 version=next(v for v in info['upstream'] if v['version'].startswith(prefix));f=next(f for f in version['files'] if f['filename'].endswith('.jar') and '-sources' not in f['filename']);target=vendor/f['filename']
 if not target.exists():target.write_bytes(urllib.request.urlopen(f['url'],timeout=120).read())
 assert hashlib.sha512(target.read_bytes()).hexdigest()==f['hashes']['sha512'];return target
engine=upstream('0.8.1+');clouds=upstream('0.7.0+');cp=args.classpath_file.read_text('utf-8-sig').strip()
if cp.startswith('-cp "') and cp.endswith('"'):cp=cp[5:-1]
source=root.parent/'simplemap/src';classes=build/'classes';classes.mkdir();files=[source/name for name in info['integration_sources']]
arguments=['--release','21','-proc:none','-encoding','UTF-8','-cp',str(args.base_jar.resolve())+';'+str(engine.resolve())+';'+cp,'-d',str(classes),*[str(f) for f in files]]
argfile=build/'javac.args';argfile.write_bytes(('\n'.join('"'+a.replace('\\','/')+'"' for a in arguments)+'\n').encode());subprocess.run([args.javac,'@'+str(argfile)],check=True)
with zipfile.ZipFile(args.base_jar) as z:entries={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
with zipfile.ZipFile(engine) as z:
 for n in z.namelist():
  if not n.endswith('/') and (n.startswith('assets/') or n.startswith('dev/diorama/') and not n.startswith('dev/diorama/neoforge/')):entries[n]=z.read(n)
entries['META-INF/licenses/Diorama-MIT.txt']=(root/'LICENSE-DIORAMA').read_bytes()
for f in classes.rglob('*.class'):entries[f.relative_to(classes).as_posix()]=f.read_bytes()
configs={
 'f8q-diorama-engine.mixins.json':{'required':True,'package':'dev.diorama.mixin','compatibilityLevel':'JAVA_21','client':['BiomeManagerAccessor','SpriteContentsMixin'],'injectors':{'defaultRequire':1}},
 'f8q-diorama-compat.mixins.json':{'required':True,'package':'com.velorise.simplemap.mixin','compatibilityLevel':'JAVA_21','client':['DioramaFogCompatibility','DioramaLiveChunks','DioramaMinimap'],'injectors':{'defaultRequire':1}}
}
toml=entries['META-INF/neoforge.mods.toml'].decode()
for name,data in configs.items():
 entries[name]=json.dumps(data).encode()
 if name not in toml:toml+='\n[[mixins]]\nconfig="'+name+'"\n'
entries['META-INF/neoforge.mods.toml']=toml.encode()
with zipfile.ZipFile(clouds) as z:
 for n in z.namelist():
  if n.endswith('fog.fsh'):entries[n]=z.read(n)
for n in list(entries):
 if n.endswith('.json') and 'shaders/core/diorama_fog' in n:
  data=json.loads(entries[n]);data['samplers']=[s for s in data.get('samplers',[]) if s.get('name')!='Sampler2'];entries[n]=json.dumps(data).encode()
assert 'dev/diorama/neoforge/DioramaNeoForge.class' not in entries
with zipfile.ZipFile(args.output,'w',zipfile.ZIP_DEFLATED) as z:
 for n,b in entries.items():z.writestr(n,b)
print('Built single F8Q/SimpleMap mod:',args.output)
