"""Reject Resend credentials in tracked source without printing their values."""
import pathlib
import re
import subprocess
import sys

PATTERN = re.compile(rb'(?<![A-Za-z0-9_])re_[A-Za-z0-9_\-]{20,100}(?![A-Za-z0-9_])')


def findings(data):
    return [data[:match.start()].count(b'\n') + 1 for match in PATTERN.finditer(data)]


def main():
    root = pathlib.Path(__file__).resolve().parent.parent
    paths = subprocess.check_output(['git', 'ls-files', '-z'], cwd=root).decode('utf-8').split('\0')
    failures = []
    for name in paths:
        path = root / name
        if not name or not path.is_file():
            continue
        for line in findings(path.read_bytes()):
            failures.append(f'{name}:{line}: possible Resend credential (value redacted)')
    for failure in failures:
        print(failure)
    if failures:
        print('Remove the credential from source and revoke it with the provider.')
        return 1
    print('No Resend credential patterns found in tracked files.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
