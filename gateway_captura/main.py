"""Entrada Windows com diretório de trabalho previsível."""
import os
from pathlib import Path


def main():
    os.chdir(Path(__file__).resolve().parent)
    from src.runtime import run
    run()


if __name__ == '__main__':
    main()
