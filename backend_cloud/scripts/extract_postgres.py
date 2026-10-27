"""Extract the pinned test distribution without allowing archive path traversal."""
from pathlib import Path
import io
import sys
import tarfile
import zipfile

archive = Path(sys.argv[1]).resolve()
target = Path(sys.argv[2]).resolve()
target.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(archive) as jar:
    with tarfile.open(fileobj=io.BytesIO(jar.read("postgres-windows-x86_64.txz")), mode="r:xz") as tar:
        members = tar.getmembers()
        for member in members:
            path = (target / member.name).resolve()
            if not path.is_relative_to(target) or not (member.isfile() or member.isdir()):
                raise ValueError("Unsafe path in PostgreSQL distribution")
        if hasattr(tarfile, "data_filter"):
            tar.extractall(target, filter="data")
        else:
            tar.extractall(target)
print(f"PostgreSQL test binaries extracted to {target}")
