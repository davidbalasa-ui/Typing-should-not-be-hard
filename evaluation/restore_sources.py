"""Restore the five upstream checkouts at the exact revisions tested. No remote writes."""
from pathlib import Path
import json
import subprocess

root = Path(__file__).resolve().parent
for record in json.loads((root/'evidence/source-revisions.json').read_text()):
    path = root/'repos'/record['name']
    if not path.exists():
        subprocess.run(['git','clone','--filter=blob:none','--no-checkout',record['remote'],str(path)],check=True)
    if subprocess.check_output(['git','-C',str(path),'status','--porcelain'],text=True).strip() and (path/'.git/index').exists():
        raise SystemExit(f'Refusing to replace changed checkout: {path}')
    subprocess.run(['git','-C',str(path),'fetch','--depth','1','origin',record['sha']],check=True)
    subprocess.run(['git','-C',str(path),'checkout','--detach',record['sha']],check=True)
    print(record['name'],record['sha'])
print('Native submodules are not required for the targeted JVM tests; full builds need separate setup.')
