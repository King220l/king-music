"""Isolated offline production test: actual remapped JARs, no player profile or account."""
from pathlib import Path
import json, os, hashlib, urllib.request, shutil, subprocess
r=Path(__file__).resolve().parents[1];cache=Path(os.environ.get('GRADLE_USER_HOME', str(Path.home()/'.gradle')))/'caches';dest=r/'build/production-libraries';dest.mkdir(parents=True,exist_ok=True)
def download(url,path,sha1=None):
 if not path.exists():
  with urllib.request.urlopen(url,timeout=60) as response: path.write_bytes(response.read())
 if sha1 and hashlib.sha1(path.read_bytes()).hexdigest()!=sha1: raise ValueError('Hash mismatch: '+str(path))
 return path
def maven(group,artifact,version,filename,url,sha1=None):
 candidates=list((cache/'modules-2/files-2.1'/group/artifact/version).glob('*/'+filename))
 if candidates:
  path=candidates[0]
  if sha1 and hashlib.sha1(path.read_bytes()).hexdigest()!=sha1: raise ValueError('Cached hash mismatch')
  return path
 return download(url,dest/filename,sha1)
version_cache=cache/'fabric-loom/1.21.3'
metadata=next(path for path in [version_cache/'mojang_minecraft_info.json',version_cache/'minecraft-info.json'] if path.is_file())
meta=json.loads(metadata.read_text())
libraries={}
for lib in meta['libraries']:
 allow=True
 if 'rules' in lib:
  allow=False
  for rule in lib['rules']:
   if 'features' in rule: continue
   system=rule.get('os',{})
   if system.get('name','windows')!='windows': continue
   if system.get('arch','x86_64') not in ['x86_64','amd64']: continue
   allow=rule['action']=='allow'
 if not allow:continue
 group,artifact,version,*_=lib['name'].split(':'); item=lib['downloads']['artifact'];filename=Path(item['path']).name
 libraries[(group,artifact,*_)]=maven(group,artifact,version,filename,item['url'],item.get('sha1'))
profile=json.loads(urllib.request.urlopen('https://meta.fabricmc.net/v2/versions/loader/1.21.3/0.16.14/profile/json',timeout=30).read())
for lib in profile['libraries']:
 group,artifact,version=lib['name'].split(':');filename=f'{artifact}-{version}.jar';path=f'{group.replace(".","/")}/{artifact}/{version}/{filename}'
 libraries[(group,artifact)]=maven(group,artifact,version,filename,lib['url']+path)
libs=list(libraries.values())
client=cache/'fabric-loom/1.21.3/minecraft-client.jar'
assert hashlib.sha1(client.read_bytes()).hexdigest()==meta['downloads']['client']['sha1']
libs.append(client)
game=r/'run-production-core-lyrics';(game/'mods').mkdir(parents=True,exist_ok=True)
for folder in ['build/libs','lyrics/build/libs']:
 candidates=list((r/folder).glob('*-Fabric-1.21.3.jar'));assert len(candidates)==1
 shutil.copy2(candidates[0],game/'mods'/candidates[0].name)
api=maven('net.fabricmc.fabric-api','fabric-api','0.114.1+1.21.3','fabric-api-0.114.1+1.21.3.jar','https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.114.1+1.21.3/fabric-api-0.114.1+1.21.3.jar')
shutil.copy2(api,game/'mods'/api.name)
smoke=list((r/'smoke-tests/build/libs').glob('*-Fabric-1.21.3.jar'));assert len(smoke)==1
shutil.copy2(smoke[0],game/'mods'/smoke[0].name)
(game/'config/yamusic').mkdir(parents=True,exist_ok=True)
shutil.copy2(r/'run/config/yamusic/settings.json',game/'config/yamusic/settings.json')
shutil.copy2(r/'official/1.21.3/manifest.txt',game/'config/yamusic/official-manifest.txt')
shutil.copy2(r/'official/1.21.3/manifest.sig',game/'config/yamusic/official-manifest.sig')
shutil.copy2(r/'run/options.txt',game/'options.txt')
args=['-Xmx2G','-cp',os.pathsep.join(map(str,libs)),profile['mainClass'],'--username','KingMusicTest','--version','1.21.3','--gameDir',str(game),'--assetsDir',str(cache/'fabric-loom/assets'),'--assetIndex',meta['id']+'-'+meta['assetIndex']['id'],'--uuid','00000000000000000000000000000001','--accessToken','0','--userType','legacy','--versionType','release','--width','1280','--height','720']
if '--world' in __import__('sys').argv: args.insert(0,'-Dkingmusic.smoke.world=true')
argfile=r/'build/production.args';argfile.write_text('\n'.join('"'+x.replace('\\','/')+'"' for x in args),'utf-8')
(game/'smoke-result.txt').unlink(missing_ok=True)
print('Launching offline production 1.21.3 with Core and Lyrics JARs and the test-only smoke mod',flush=True)
raise SystemExit(subprocess.call([str(Path(os.environ['JAVA_HOME']) / 'bin/java') if 'JAVA_HOME' in os.environ else 'java','@'+str(argfile)],cwd=game))
