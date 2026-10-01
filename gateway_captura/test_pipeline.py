"""
test_pipeline.py — Teste local do pipeline de vídeo (DVR + Corte).

Este script testa CADA ETAPA isoladamente:
  1. Verifica se o FFmpeg está instalado.
  2. Verifica se a câmera é detectável.
  3. Grava 3 segmentos curtos de 15 segundos (simula o DVR).
  4. Concatena os 2 últimos segmentos num lance (simula o Worker).
  5. Escuta o Arduino e, ao clicar o botão, refaz o corte em tempo real.

Uso:
  python test_pipeline.py
"""

import glob
import os
import subprocess
import sys
import time

# Carrega .env antes de importar os módulos do projeto
from dotenv import load_dotenv
load_dotenv()

# =========================================================================
# CONFIGURAÇÕES DO TESTE
# =========================================================================
CAMERA_NAME = os.getenv("CAMERA_DEVICE_NAME", "Microsoft® LifeCam VX-2000")
SERIAL_PORT = os.getenv("SERIAL_PORT", "COM6")
BAUD_RATE = int(os.getenv("BAUD_RATE", "9600"))

TEST_SEGMENTS_DIR = "test_segments"
TEST_CLIPS_DIR = "test_clips"
TEST_SEGMENT_DURATION = 15  # segundos (curto para testar rápido)
TEST_NUM_SEGMENTS = 3       # gravar 3 segmentos de 15s = 45s total

DEBOUNCE_SECONDS = 5  # Ignora bounces do botão dentro desta janela


def separator(title: str):
    print(f"\n{'='*60}")
    print(f"  {title}")
    print(f"{'='*60}")


# =========================================================================
# ETAPA 1: FFmpeg instalado?
# =========================================================================
def test_ffmpeg():
    separator("ETAPA 1: Verificando FFmpeg")
    try:
        result = subprocess.run(
            ["ffmpeg", "-version"],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
        )
        version_line = result.stdout.split("\n")[0]
        print(f"  ✅ FFmpeg encontrado: {version_line}")
        return True
    except FileNotFoundError:
        print("  ❌ FFmpeg NÃO encontrado no PATH!")
        print("     Instale: https://ffmpeg.org/download.html")
        return False


# =========================================================================
# ETAPA 2: Câmera detectável?
# =========================================================================
def test_camera():
    separator(f"ETAPA 2: Verificando câmera '{CAMERA_NAME}'")

    # Tenta abrir a câmera por 3 segundos
    command = [
        "ffmpeg", "-y",
        "-f", "dshow",
        "-i", f"video={CAMERA_NAME}",
        "-t", "2",          # grava só 2 segundos
        "-f", "null", "-",   # descarta a saída
    ]

    result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

    if result.returncode == 0 or "frame=" in result.stderr:
        print(f"  ✅ Câmera '{CAMERA_NAME}' aberta com sucesso!")
        return True
    else:
        print(f"  ❌ Não conseguiu abrir a câmera '{CAMERA_NAME}'")
        print(f"     Stderr (últimas 5 linhas):")
        for line in result.stderr.strip().split("\n")[-5:]:
            print(f"       {line}")
        return False


# =========================================================================
# ETAPA 3: Gravação segmentada (simula o DVR por ~45 segundos)
# =========================================================================
def test_dvr_recording():
    separator(f"ETAPA 3: Gravando {TEST_NUM_SEGMENTS} segmentos de {TEST_SEGMENT_DURATION}s")
    os.makedirs(TEST_SEGMENTS_DIR, exist_ok=True)

    segment_pattern = os.path.join(TEST_SEGMENTS_DIR, "seg_%04d.mp4")
    total_time = TEST_NUM_SEGMENTS * TEST_SEGMENT_DURATION

    command = [
        "ffmpeg", "-y",
        "-f", "dshow",
        "-i", f"video={CAMERA_NAME}",
        "-c:v", "libx264",
        "-preset", "ultrafast",
        "-tune", "zerolatency",
        "-f", "segment",
        "-segment_time", str(TEST_SEGMENT_DURATION),
        "-reset_timestamps", "1",
        "-t", str(total_time),  # Limita o tempo total (não grava infinito)
        segment_pattern,
    ]

    print(f"  Gravando {total_time}s de vídeo da câmera...")
    print(f"  ⏳ Aguarde ~{total_time} segundos...\n")

    process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

    # Mostra progresso enquanto grava
    start = time.time()
    while process.poll() is None:
        elapsed = int(time.time() - start)
        segments_done = len(glob.glob(os.path.join(TEST_SEGMENTS_DIR, "seg_*.mp4")))
        print(f"\r  ⏱️  {elapsed}s / {total_time}s  |  Segmentos criados: {segments_done}", end="", flush=True)
        time.sleep(1)

    print()  # nova linha

    # Lista segmentos gerados
    segments = sorted(glob.glob(os.path.join(TEST_SEGMENTS_DIR, "seg_*.mp4")))
    if segments:
        print(f"\n  ✅ {len(segments)} segmento(s) gerado(s):")
        for seg in segments:
            size_kb = os.path.getsize(seg) / 1024
            print(f"     📁 {os.path.basename(seg)} ({size_kb:.0f} KB)")
        return True
    else:
        print("  ❌ Nenhum segmento foi gerado!")
        _, stderr = process.communicate()
        print(f"     Stderr: {stderr.decode('utf-8', errors='replace')[-500:]}")
        return False


# =========================================================================
# ETAPA 4: Concatenação (simula o Worker cortando o lance)
# =========================================================================
def test_concatenation():
    separator("ETAPA 4: Concatenando segmentos num lance")
    os.makedirs(TEST_CLIPS_DIR, exist_ok=True)

    segments = sorted(glob.glob(os.path.join(TEST_SEGMENTS_DIR, "seg_*.mp4")))

    if len(segments) < 2:
        print("  ⚠️  Menos de 2 segmentos disponíveis, usando o que tiver.")
        selected = segments
    else:
        # Pega os 2 últimos (simula o comportamento real)
        selected = segments[-2:]

    print(f"  Segmentos selecionados para concat: {[os.path.basename(s) for s in selected]}")

    # Cria arquivo concat.txt
    concat_path = os.path.join(TEST_CLIPS_DIR, "concat_list.txt")
    with open(concat_path, "w", encoding="utf-8") as f:
        for seg in selected:
            abs_path = os.path.abspath(seg).replace("\\", "/")
            f.write(f"file '{abs_path}'\n")

    output_path = os.path.join(TEST_CLIPS_DIR, "lance_teste.mp4")

    command = [
        "ffmpeg", "-y",
        "-f", "concat",
        "-safe", "0",
        "-i", concat_path,
        "-c", "copy",
        output_path,
    ]

    print("  Executando FFmpeg concat...")
    result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

    # Limpa concat.txt
    try:
        os.remove(concat_path)
    except OSError:
        pass

    if result.returncode == 0 and os.path.exists(output_path):
        size_mb = os.path.getsize(output_path) / (1024 * 1024)
        print(f"\n  ✅ LANCE GERADO COM SUCESSO!")
        print(f"     📹 Arquivo: {os.path.abspath(output_path)}")
        print(f"     📦 Tamanho: {size_mb:.2f} MB")
        print(f"\n  👉 Abra o arquivo acima no seu player de vídeo para confirmar!")
        return True
    else:
        print("  ❌ Falha na concatenação!")
        print(f"     Stderr: {result.stderr[-500:]}")
        return False


# =========================================================================
# ETAPA 5: Teste com Arduino real (escuta serial + corte ao vivo)
# =========================================================================
def test_with_arduino():
    separator("ETAPA 5: Teste ao vivo com Arduino")
    print(f"  Iniciando gravação contínua + escuta serial em {SERIAL_PORT}...")
    print(f"  Pressione o botão do Arduino para gerar um lance.")
    print(f"  ⚠️  Debounce de {DEBOUNCE_SECONDS}s ativado (bounces serão ignorados).")
    print(f"  Pressione Ctrl+C para encerrar.\n")

    import serial

    os.makedirs(TEST_SEGMENTS_DIR, exist_ok=True)
    os.makedirs(TEST_CLIPS_DIR, exist_ok=True)

    # Limpa segmentos antigos de testes anteriores
    for old_seg in glob.glob(os.path.join(TEST_SEGMENTS_DIR, "seg_*.mp4")):
        try:
            os.remove(old_seg)
        except OSError:
            pass

    # Inicia DVR em background
    segment_pattern = os.path.join(TEST_SEGMENTS_DIR, "seg_%04d.mp4")
    dvr_command = [
        "ffmpeg", "-y",
        "-f", "dshow",
        "-i", f"video={CAMERA_NAME}",
        "-c:v", "libx264",
        "-preset", "ultrafast",
        "-tune", "zerolatency",
        "-f", "segment",
        "-segment_time", str(TEST_SEGMENT_DURATION),
        "-reset_timestamps", "1",
        segment_pattern,
    ]

    dvr_process = subprocess.Popen(dvr_command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    print(f"  📹 DVR iniciado (PID {dvr_process.pid}). Gravando segmentos de {TEST_SEGMENT_DURATION}s...")
    print(f"  ⏳ Aguarde pelo menos {TEST_SEGMENT_DURATION * 2}s antes de clicar o botão.\n")

    last_trigger_time = 0.0
    lance_count = 0

    try:
        connection = serial.Serial(SERIAL_PORT, BAUD_RATE, timeout=1)
        time.sleep(2)
        print(f"  🔌 Serial {SERIAL_PORT} conectada. Aguardando botão...\n")

        while True:
            # Mostra status do DVR em tempo real
            num_segs = len(glob.glob(os.path.join(TEST_SEGMENTS_DIR, "seg_*.mp4")))
            elapsed = int(time.time() - last_trigger_time) if last_trigger_time > 0 else 999
            status = "🟢 PRONTO" if num_segs >= 2 and elapsed >= DEBOUNCE_SECONDS else "⏳ Aguarde"
            print(f"\r  [{status}] Segmentos: {num_segs} | Lances gerados: {lance_count}    ", end="", flush=True)

            if connection.in_waiting > 0:
                line = connection.readline().decode("utf-8", errors="ignore").strip()

                if line == "Botao foi Pressionado!":
                    now = time.time()

                    # DEBOUNCE: ignora bounces
                    if (now - last_trigger_time) < DEBOUNCE_SECONDS:
                        continue

                    last_trigger_time = now
                    print(f"\n\n  🎯 BOTÃO PRESSIONADO! Gerando lance...\n")

                    # Pega segmentos completos (exclui o último que está sendo escrito)
                    all_segs = sorted(glob.glob(os.path.join(TEST_SEGMENTS_DIR, "seg_*.mp4")))
                    if len(all_segs) <= 1:
                        print("  ⚠️  Sem segmentos completos ainda. Aguarde mais tempo e tente novamente.\n")
                        continue

                    completed = all_segs[:-1]  # Exclui o que está sendo gravado agora
                    selected = completed[-2:]   # Pega os 2 mais recentes

                    print(f"  Segmentos selecionados: {[os.path.basename(s) for s in selected]}")

                    # Concatena
                    timestamp_str = time.strftime("%Y%m%d_%H%M%S")
                    output_path = os.path.join(TEST_CLIPS_DIR, f"lance_{timestamp_str}.mp4")
                    concat_path = os.path.join(TEST_CLIPS_DIR, "concat_list.txt")

                    with open(concat_path, "w", encoding="utf-8") as f:
                        for seg in selected:
                            f.write(f"file '{os.path.abspath(seg).replace(chr(92), '/')}'\n")

                    concat_cmd = [
                        "ffmpeg", "-y", "-f", "concat", "-safe", "0",
                        "-i", concat_path, "-c", "copy", output_path,
                    ]

                    res = subprocess.run(concat_cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
                    try:
                        os.remove(concat_path)
                    except OSError:
                        pass

                    if res.returncode == 0 and os.path.exists(output_path):
                        lance_count += 1
                        size_mb = os.path.getsize(output_path) / (1024 * 1024)
                        print(f"  ✅ LANCE #{lance_count} GERADO: {output_path} ({size_mb:.2f} MB)")
                        print(f"  👉 Abra no player para verificar!\n")
                        print(f"  Pressione o botão novamente para mais um lance, ou Ctrl+C para sair.\n")
                    else:
                        print(f"  ❌ Falha na concat: {res.stderr[-300:]}")

            time.sleep(0.05)  # Pequeno sleep para não consumir 100% CPU

    except KeyboardInterrupt:
        print("\n\n  Encerrando teste...")
    finally:
        dvr_process.terminate()
        try:
            dvr_process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            dvr_process.kill()
        if 'connection' in locals() and connection.is_open:
            connection.close()
        print("  DVR e serial encerrados.")


# =========================================================================
# MAIN
# =========================================================================
def main():
    print("\n" + "=" * 60)
    print("  🧪 TESTE DO PIPELINE DE VÍDEO — ClickLance Edge")
    print("=" * 60)
    print(f"  Câmera: {CAMERA_NAME}")
    print(f"  Arduino: {SERIAL_PORT}")

    # Etapa 1
    if not test_ffmpeg():
        sys.exit(1)

    # Etapa 2
    if not test_camera():
        sys.exit(1)

    # Perguntar ao usuário o que quer testar
    print("\n" + "-" * 60)
    print("  Escolha o modo de teste:")
    print("    [1] Teste rápido offline (grava 45s + concatena automaticamente)")
    print("    [2] Teste ao vivo com Arduino (grava contínuo + espera botão)")
    print("-" * 60)

    choice = input("  Opção (1 ou 2): ").strip()

    if choice == "2":
        test_with_arduino()
    else:
        # Etapa 3
        if not test_dvr_recording():
            sys.exit(1)
        # Etapa 4
        if not test_concatenation():
            sys.exit(1)

    separator("RESULTADO FINAL")
    print("  ✅ Pipeline de vídeo validado com sucesso!")
    print("  Os próximos passos são configurar o AWS S3 e a API Spring Boot.\n")


if __name__ == "__main__":
    main()
