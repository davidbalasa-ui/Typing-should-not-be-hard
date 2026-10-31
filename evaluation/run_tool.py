"""Run build tools with this environment's proxy and local Android SDK."""
import os
import subprocess
import sys
from pathlib import Path
from urllib.parse import urlsplit

root = Path(__file__).resolve().parent
env = os.environ.copy()
options = ['-Djava.net.preferIPv4Stack=true']
for scheme in ('http', 'https'):
    value = env.get(scheme.upper() + '_PROXY') or env.get(scheme + '_proxy')
    if value:
        url = urlsplit(value)
        if url.hostname:
            options.extend([f'-D{scheme}.proxyHost={url.hostname}',
                            f'-D{scheme}.proxyPort={url.port or 80}'])
env['JAVA_TOOL_OPTIONS'] = ' '.join(options)
env['ANDROID_HOME'] = str(root / 'toolchain/android-sdk')
env['ANDROID_SDK_ROOT'] = env['ANDROID_HOME']
env['GRADLE_USER_HOME'] = str(root / 'toolchain/gradle-cache')
raise SystemExit(subprocess.call(sys.argv[1:], env=env))
