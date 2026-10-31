"""Compile unmodified upstream Kotlin components and run selected JUnit tests offline.

This is a targeted JVM baseline, not an Android build or the complete project suite.
"""
from pathlib import Path
import json
import subprocess

root = Path(__file__).resolve().parent
libs = root / 'toolchain/gradle-9.4.1/lib'
test_jars = root / 'toolchain/test-jars'
classpath = ':'.join(str(p) for p in [libs/'kotlin-stdlib-2.3.0.jar', *test_jars.glob('*.jar')])
repos = root/'repos'
selections = {
    'LeanType': [
        'app/src/main/java/helium314/keyboard/latin/voice/AudioUtils.kt',
        'app/src/test/java/helium314/keyboard/latin/voice/AudioUtilsTest.kt',
    ],
    'DictateKeyboard': [
        'app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/WordRun.kt',
        'app/src/test/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/WordRunTest.kt',
        'app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/AutoCorrectStrength.kt',
        'app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/AutoCommitGate.kt',
        'lib/dictate-core/src/main/kotlin/dev/patrickgold/florisboard/dictate/symbols/CustomSymbols.kt',
        'lib/dictate-core/src/main/kotlin/dev/patrickgold/florisboard/dictate/symbols/SymbolDefaults.kt',
        'lib/dictate-core/src/main/kotlin/dev/patrickgold/florisboard/dictate/field/FieldText.kt',
        'lib/dictate-core/src/main/kotlin/dev/patrickgold/florisboard/dictate/field/WordChars.kt',
        'lib/dictate-core/src/main/kotlin/dev/patrickgold/florisboard/dictate/field/TranscriptHandover.kt',
    ],
    'WMKeyboard': [
        'core/voice/src/main/java/com/wasimaster/wmkeyboard/core/voice/VoiceSpacing.kt',
        'app/src/test/java/com/wasimaster/wmkeyboard/core/voice/VoiceSpacingTest.kt',
        'core/voice/src/main/java/com/wasimaster/wmkeyboard/core/voice/VoiceClipGate.kt',
        'app/src/test/java/com/wasimaster/wmkeyboard/core/voice/VoiceClipGateTest.kt',
    ],
}
classes = {
    'LeanType': ['helium314.keyboard.latin.voice.AudioUtilsTest'],
    'DictateKeyboard': ['dev.patrickgold.florisboard.ime.nlp.latin.WordRunTest', 'KeyboardBehaviorTest'],
    'WMKeyboard': ['com.wasimaster.wmkeyboard.core.voice.VoiceSpacingTest', 'com.wasimaster.wmkeyboard.core.voice.VoiceClipGateTest'],
}
results = []
for repo, files in selections.items():
    output = root/'component-build'/repo
    output.mkdir(parents=True, exist_ok=True)
    source_files = [str(repos/repo/f) for f in files]
    if repo == 'DictateKeyboard':
        source_files.append(str(root/'tests/KeyboardBehaviorTest.kt'))
    compile_args = ['java', '-cp', str(libs/'*'), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
                    '-no-stdlib', '-no-reflect', '-jvm-target', '17', '-cp', classpath,
                    '-d', str(output), *source_files]
    log = root/'evidence'/f'{repo}-components.log'
    with log.open('w') as stream:
        code = subprocess.call(compile_args, stdout=stream, stderr=subprocess.STDOUT)
        compiled = code == 0
        if compiled:
            code = subprocess.call(['java', '-cp', str(output)+':'+classpath,
                                    'org.junit.runner.JUnitCore', *classes[repo]],
                                   stdout=stream, stderr=subprocess.STDOUT)
    results.append({'repo':repo,'compiled':compiled,'exit_code':code,'log':str(log.relative_to(root)),
                    'source_files':files,'test_classes':classes[repo]})
    print(repo, 'PASS' if code == 0 else 'FAILED', flush=True)
(root/'evidence/component-results.json').write_text(json.dumps(results, indent=2)+'\n')
raise SystemExit(any(r['exit_code'] for r in results))
