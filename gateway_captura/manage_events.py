"""Inspect durable events or resume one after correcting its configuration."""
import argparse
import json

from src.config import Config
from src.event_store import EventStore


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--requeue", metavar="EVENTO_UUID")
    parser.add_argument("--backup", metavar="NOVO_ARQUIVO_SQLITE")
    arguments = parser.parse_args()
    store = EventStore(Config.STATE_DB, Config.MAX_PENDING_EVENTS)
    if arguments.requeue:
        store.requeue(arguments.requeue)
    if arguments.backup:
        print("Backup consistente criado:", store.backup(arguments.backup))
    print(json.dumps([{key: event.get(key) for key in ("evento_id", "timestamp", "state", "attempts", "error", "clip_path")}
                      for event in store.pending()], ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
