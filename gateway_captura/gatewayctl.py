"""Operação local. Retry e backup exigem gateway parado para obter o mutex da base."""
import argparse
import json
import os
import shutil
import sqlite3
from pathlib import Path


def main():
    os.chdir(Path(__file__).resolve().parent)
    from src.config import Config
    from src.dvr_manager import DVRManager
    from src.event_store import EventStore
    from src.operations import InstanceLock
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    commands.add_parser('health')
    commands.add_parser('list')
    commands.add_parser('retry').add_argument('event_id')
    commands.add_parser('backup').add_argument('destination')
    args = parser.parse_args()
    root = Path(Config.SEGMENTS_DIR).resolve()
    if args.command == 'health':
        print((root / 'health.json').read_text(encoding='utf-8'))
    elif args.command == 'list':
        connection = sqlite3.connect((root / 'catalog.sqlite3').as_uri() + '?mode=ro', uri=True)
        try:
            connection.row_factory = sqlite3.Row
            print(json.dumps([dict(row) for row in connection.execute(
                'SELECT id,state,attempts,next_attempt,error,created,completed FROM events ORDER BY created')], indent=2))
        finally:
            connection.close()
    else:
        with InstanceLock('base:' + str(root)):
            dvr = DVRManager()
            try:
                store = EventStore(dvr)
                if args.command == 'retry':
                    store.retry(args.event_id)
                    print('Evento recolocado na etapa persistida. Inicie o gateway para retomar.')
                else:
                    destination = Path(args.destination).resolve()
                    sources = [root, Path(Config.OUTPUT_DIR).resolve()]
                    if any(destination.is_relative_to(source) for source in sources):
                        raise ValueError('Backup deve ficar fora dos diretórios de dados.')
                    destination.mkdir(parents=True, exist_ok=False)
                    shutil.copytree(root, destination / 'segments')
                    if sources[1].exists():
                        shutil.copytree(sources[1], destination / 'clips')
                    backup = sqlite3.connect(destination / 'segments' / 'catalog.sqlite3')
                    try:
                        store.db.backup(backup)
                    finally:
                        backup.close()
                    print(f'Backup criado em {destination}')
            finally:
                dvr.catalog.close()


if __name__ == '__main__':
    main()
