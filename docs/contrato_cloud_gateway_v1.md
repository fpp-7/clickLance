# Contrato cloud do gateway — proposta v1

15/10/2026. Cliente implementado e verificado com simuladores; backend real e AWS
ainda não homologados. Este documento alinha GW-08/GW-09 e substitui, para o novo
cliente, a identidade baseada apenas em proximidade de cinco segundos.

## Ingestão

`POST /api/v1/replays`, `X-Device-Token` e `Idempotency-Key` igual ao `evento_id`.
O timestamp representa o instante original e contém offset UTC.

```json
{
  "contrato_versao": 1,
  "evento_id": "d70fd979d77d4e33a587f01bb05a58c5",
  "dispositivo_id": "mega01",
  "quadra_id": "quadra_001",
  "timestamp": "2026-10-15T15:47:57+00:00",
  "objeto": {
    "bucket": "bucket-privado",
    "key": "replays/mega01/d70fd979d77d4e33a587f01bb05a58c5.mp4",
    "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
  }
}
```

Resposta 200/201, inclusive para reenvio idêntico:

```json
{
  "evento_id": "d70fd979d77d4e33a587f01bb05a58c5",
  "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
}
```

O backend precisa impor unicidade de evento, validar propriedade da referência
do objeto e retornar 409 se a mesma identidade trouxer conteúdo divergente.
O cliente só confirma registro ao receber a mesma identidade e checksum.
Erros 400/401/403/404/409/410/422 entram em intervenção; 429/5xx e falhas de
conexão são retentáveis. Redirecionamentos não são seguidos com o token.

Objetos são privados; o cliente não publica URL de reprodução. O upload usa
`PutObject IfNoneMatch=*`, chave estável, metadata SHA-256 e verificação de
checksum/tamanho via HEAD. Em resposta incerta, novo envio não sobrescreve
conteúdo: 412 é reconciliado contra o objeto existente. Ver a
[documentação oficial do PutObject](https://docs.aws.amazon.com/boto3/latest/reference/services/s3/client/put_object.html).
O bucket deve ter acesso público bloqueado e a identidade IAM deve poder gravar
e consultar os próprios objetos. Tokens/chaves de sessão ficam no ambiente.

## Configuração

`CONFIG_URL` define a URL autenticada de configuração do dispositivo. O cliente
consulta no início e a cada `CONFIG_REFRESH_SECONDS` (padrão 300 s).

```json
{
  "version": "quadra-001-v1",
  "active": true,
  "settings": {
    "CAMERA_SOURCE": "rtsp",
    "CAMERA_RTSP_URL": "rtsp://192.0.2.10/stream-confirmado",
    "CAMERA_RTSP_TRANSPORT": "tcp",
    "SEGMENT_DURATION_SECONDS": 60,
    "KEYFRAME_INTERVAL": 30,
    "REPLAY_BEFORE_SECONDS": 120,
    "REPLAY_AFTER_SECONDS": 0
  }
}
```

O endereço do exemplo é ilustrativo, sem configuração específica de Intelbras.
Limites numéricos e chaves permitidas estão em `src/remote_config.py`. Atualizações
parciais são mescladas com a última configuração válida e a configuração efetiva
é armazenada em SQLite. Resposta inválida não a substitui.

Mudanças de captura invalidam prontidão e abrem nova sessão; eventos antigos
mantêm janela, fontes, bucket e quadra. 401/403/410 persistem inativação; inicialização
offline posterior mantém essa condição. Falha de rede permite fallback ao cache.
Sem cache válido na primeira inicialização com `CONFIG_URL`, o gateway falha com
erro acionável. Com `CONFIG_URL` vazio, `.env` é provisionamento local explícito.

Alterar credenciais não muda a identidade dos eventos. Bucket e dispositivo de
eventos já aceitos não são reescritos silenciosamente para uma nova instalação.
