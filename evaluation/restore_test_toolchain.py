"""Download the exact compiler/JUnit artifacts used for the targeted baseline."""
from pathlib import Path
import hashlib
import json
import subprocess
import zipfile

root = Path(__file__).resolve().parent
for record in json.loads((root/'evidence/test-toolchain.json').read_text()):
    path = root/record['path']
    path.parent.mkdir(parents=True,exist_ok=True)
    if not path.exists():
        subprocess.run(['curl','-fL','--retry','2','--max-time','180',record['url'],'-o',str(path)],check=True)
    actual = hashlib.sha256(path.read_bytes()).hexdigest()
    if actual != record['sha256']:
        raise SystemExit(f'Checksum mismatch: {path}')
archive=root/'toolchain/gradle-9.4.1.zip'
if not (root/'toolchain/gradle-9.4.1/lib').exists():
    with zipfile.ZipFile(archive) as z:z.extractall(root/'toolchain')
print('Offline test toolchain ready. Requires Java 17 or later.')
