"""Diagnóstico físico interativo. Testes automáticos ficam em tests/test_*.py."""
import argparse
import json
import subprocess
import tempfile
import shutil
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
                  'a seleção legada usa somente os dois primeiros. Veja report.json.')
            session = dvr.catalog.create_session(0, 'synthetic')
            session_dir = dvr.catalog.session_directory(session)
            # Reutiliza a lista real de fechamento do FFmpeg, sem inventar intervalos.
            for segment in segments:
                shutil.copy2(segment, session_dir / segment.name)
            shutil.copy2(root / 'segments' / 'segments.csv', session_dir / 'segments.csv')
            dvr.catalog.reconcile(session, finished=True)
            request = dvr.catalog.reserve(session, 5, 2, 0)
            _, records = dvr.wait_replay(request['id'])
            corrected_output = root / 'replay_corrigido_clique_t5.mp4'
            worker._render_replay(request, records, str(corrected_output))
            report['temporal_replay'] = {
                'click_seconds': 5, 'start': request['start'], 'end': request['end'],
                'selected_segments': [Path(r['path']).name for r in records],
                'duration': validate_video(corrected_output), 'clip': str(corrected_output),
            }
            dvr.catalog.release(request['id'])
            print(f'Replay temporal gerado: {corrected_output}')
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
