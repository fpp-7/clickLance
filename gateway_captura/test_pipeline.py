"""Diagnóstico físico interativo. Testes automáticos ficam em tests/test_*.py."""
import argparse
import json
import subprocess
import tempfile
from pathlib import Path
from queue import Queue

from src.config import Config
from src.diagnostics import capture_sample, validate_video
from src.dvr_manager import DVRManager
from src.video_processor import VideoProcessor


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--synthetic', action='store_true', help='Dispensa câmera e Arduino')
    args = parser.parse_args()
    root = Path(tempfile.mkdtemp(prefix='clicklance-diagnostico-'))
    report = {'directory': str(root), 'synthetic': args.synthetic, 'ok': False}
    dvr = None
    print(f'Artefatos: {root}')
    try:
        if not args.synthetic:
            print(f'Tipo de entrada: {Config.CAMERA_SOURCE} (endereço omitido)')
            input('Enter para gravar 6 segundos; Ctrl+C para cancelar: ')
        dvr = DVRManager()
        dvr.segment_duration = 2
        segments = capture_sample(dvr, root / 'segments', synthetic=args.synthetic)
        worker = VideoProcessor(Queue(), dvr, s3_client=object())
        worker.output_dir = str(root)
        output = root / 'lance_teste.mp4'
        worker._concatenate_segments([str(p) for p in segments[-2:]], str(output))
        duration = validate_video(output)
        report.update(clip=str(output), duration=duration)
        print(f'Captura e concatenação verificadas: {output} ({duration:.2f}s)')
        if args.synthetic:
            dvr.segments_dir = str(root / 'segments')
            selected = dvr.get_recent_segments(2)
            legacy_output = root / 'replay_atual_clique_t5.mp4'
            worker._concatenate_segments(selected, str(legacy_output))
            validate_video(legacy_output)
            report['current_behavior'] = {
                'click_seconds': 5,
                'required_segment': segments[-1].name,
                'selected_segments': [Path(p).name for p in selected],
                'click_segment_missing': str(segments[-1]) not in selected,
                'clip': str(legacy_output),
            }
            print('Demonstração GW-05: clique t=5 está no terceiro segmento; '
                  'a seleção atual usa somente os dois primeiros. Veja report.json.')
        report['ok'] = True
        print('Serial, aceitação durável e integração cloud não foram validadas.')
        return 0
    except (OSError, RuntimeError, ValueError, KeyError, subprocess.SubprocessError) as exc:
        report['error'] = str(exc)
        print(f'Falha no diagnóstico: {exc}')
        return 1
    except KeyboardInterrupt:
        report['error'] = 'Cancelado pelo operador'
        return 130
    finally:
        if dvr is not None:
            dvr.catalog.close()
        (root / 'report.json').write_text(json.dumps(report, indent=2), encoding='utf-8')


if __name__ == '__main__':
    raise SystemExit(main())
