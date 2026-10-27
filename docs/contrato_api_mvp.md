# Contrato vigente do MVP implementado

Esta especificação substitui os exemplos divergentes dos documentos de levantamento para a implementação das sprints US-01 a US-12. Base: `/api/v1`; JSON UTF-8. O piloto tem uma quadra/câmera por instância do gateway. APIs administrativas pertencem a um administrador global de operação, não a um usuário jogador.

## Decisões consolidadas

- A identidade idempotente é `(dispositivo_id, evento_id UUID)`. A tolerância de cinco segundos foi substituída como mecanismo de idempotência; o cooldown físico continua sendo uma regra separada do gateway.
- O Edge faz upload antes de registrar o lance. É um fluxo de consistência eventual, sem transação atômica entre S3 e banco. O checkpoint local permite retentativa; reconciliação de objetos órfãos não está automatizada no backend.
- O Edge envia `s3_key`, não `video_url` nem uma quadra declarada como autoridade. O backend autentica o dispositivo, deriva sua quadra e exige `replays/{quadra_uuid}/{evento_uuid}.mp4`.
- Timestamp exige offset ou `Z`. Instantes são armazenados em UTC com precisão de microssegundos. Filtros de data/horário usam o fuso IANA da quadra, padrão `America/Sao_Paulo`.
- S3 privado e URLs temporárias emitidas após autorização. A biblioteca do MVP é gratuita; esta entrega não valida venda ou sustentabilidade de retenção permanente. O prazo comercial e a política de privacidade ainda precisam de decisão antes de produção.
- Paginação de listas de lances: `pagina` começa em zero (máximo 1.000.000), `tamanho` padrão 25 (1 a 100). Biblioteca retorna envelope, igual ao catálogo.

## Autenticação

| Uso | Credencial |
|---|---|
| Cadastro/configuração administrativa | `X-Admin-Token`, segredo global de operação |
| Configuração e ingestão Edge | `X-Device-Token`, válido para o `dispositivo_id` informado |
| Biblioteca e acesso privado do jogador | `Authorization: Bearer <JWT>` |
| Grade e detalhe público vigente | Sem autenticação |
| Mídia local | Token assinado no caminho, com expiração |

Empresa, quadra e dispositivo precisam estar ativos para configuração/ingestão. Empresa/quadra inativa também sai da consulta pública. Vídeos salvos continuam acessíveis aos respectivos jogadores ativos. O token do dispositivo é aleatório, armazenado como BCrypt e revelado uma vez na criação. Não há endpoint de rotação nesta entrega.

## Sprint 1 — cadastros e configuração

| Método e rota | Corpo / retorno |
|---|---|
| `POST /empresas` | `{nome, dias_retencao_padrao?}` → 201 `{id,nome,dias_retencao_padrao,ativo,criado_em}` |
| `POST /quadras` | `{empresa_id,nome,duracao_segmento_segundos?,segmentos_concatenar?,fuso_horario?}` → 201 com os dados cadastrados |
| `POST /dispositivos` | `{id,quadra_id,descricao?}` → 201 `{id,quadra_id,descricao,token_acesso,ativo,criado_em}` |
| `GET /dispositivos/{id}/config` | Exige token do próprio dispositivo → 200 `{dispositivo_id,quadra_id,quadra_nome,duracao_segmento_segundos,segmentos_concatenar,fuso_horario}` |
| `PATCH /quadras/{id}` | Administração: `{duracao_segmento_segundos?,segmentos_concatenar?,ativo?}` → 200 |
| `PATCH /empresas/{id}` | Administração: `{ativo}` → 200 |
| `PATCH /dispositivos/{id}` | Administração: `{ativo}` → 200 |

Limites: nome até 100 caracteres; descrição até 150; retenção entre 1 e 30 dias, padrão 2; duração entre 1 e 60 segundos, padrão 60; quantidade entre 1 e 300, padrão 2; produto duração × quantidade até 600 segundos. Sugestão inicial para o piloto: 10 × 12 = 120 segundos, sujeita à validação em hardware. ID de dispositivo: 1 a 50 letras ASCII, números, `_` ou `-`. IDs de quadra/empresa são UUIDs. Configuração não reassocia dispositivos a outra quadra.

Cadastros exigem referências existentes; entrada inválida retorna 400, recurso ausente 404 e identificador duplicado 409. Credencial ausente/inválida retorna 401. Jogador autenticado tentando administrar recebe 403.

## Sprint 2 — ingestão

`POST /replays`, com `X-Device-Token`:

```json
{
  "dispositivo_id": "disp_piloto",
  "evento_id": "58aaa27a-3c6d-4e12-967c-30acc12237cc",
  "timestamp": "2026-10-26T22:14:50-03:00",
  "s3_key": "replays/18dbdc45-02c6-455a-a21b-d58784bd0049/58aaa27a-3c6d-4e12-967c-30acc12237cc.mp4"
}
```

201 para criação; 200 para repetição do mesmo evento/conteúdo. Retorno: `{id,quadra_id,dispositivo_id,evento_id,timestamp_evento,s3_key,tamanho_bytes,status,expira_em,criado_em}`. Não há URL de reprodução nesse retorno.

O timestamp não pode estar mais de dois minutos no futuro. Para evento novo, `expira_em = timestamp_evento + dias_retencao_padrao` precisa ser posterior ao recebimento; evento já expirado retorna 422. Repetir registro existente permanece idempotente após expiração/purga, sem exigir que o objeto ainda exista. Mesmo ID com timestamp/key diferentes retorna 409. Offsets equivalentes e nanos truncados a micros representam o mesmo instante.

Objeto novo deve existir, ser não vazio e respeitar o limite de tamanho (500 MiB por padrão); no S3, HEAD também exige `Content-Type: video/mp4`. Key de outra quadra/evento retorna 400. Outro dispositivo não pode reutilizar a mesma key. A verificação de metadados não equivale a análise ou decodificação completa do conteúdo.

## Sprint 3 — consulta

- `GET /empresas/{id}/quadras` retorna array de quadras ativas com `{id,nome,ativo,fuso_horario,lances_disponiveis_hoje}`. “Hoje” usa o fuso de cada quadra.
- `GET /quadras/{id}/lances?data=2026-10-26&hora_inicio=22:00&hora_fim=23:00&pagina=0&tamanho=25` retorna `{quadra_id,quadra_nome,fuso_horario,data,faixa_horaria,pagina,tamanho,total_lances,lances}`.
- `data` é obrigatória, com ano entre 1900 e 2100. Horários devem vir juntos ou ser ambos omitidos. Omitidos significam o dia local inteiro; horários iguais retornam 400. Se o fim for anterior ao início, a faixa termina no dia seguinte.
- Intervalos são `[inicio,fim)`: um evento às 23:00 não aparece também na faixa 22–23. Ordenação cronológica por instante e UUID.
- Cada lance contém `{id,quadra_id,quadra_nome,timestamp_evento,horario_formatado,fuso_horario,status,expira_em,video_url}`. A key do objeto não é exposta nessa consulta. `video_url` é temporária e pode ser relativa no modo local.
- `GET /lances/{id}` retorna o mesmo detalhe. Público só enquanto disponível/não expirado e com quadra/empresa ativas. Se expirado mas salvo, somente jogador que tem vínculo pode consultar, autenticado. Demais casos retornam 404.
- A disponibilidade pública é verificada no momento da consulta, independentemente da execução do job. Respostas que contêm URLs de reprodução usam `Cache-Control: no-store`.

## Sprint 4 — conta, biblioteca e expiração

- `POST /auth/cadastro`: `{nome,email,senha}` → 201 `{id,nome,email,criado_em}`. E-mail é normalizado para minúsculas e deve ser único. Senha: 10 a 72 caracteres, com máximo adicional de 72 bytes UTF-8 para BCrypt.
- `POST /auth/login`: `{email,senha}` → 200 `{token_tipo:"Bearer",access_token,expira_em_segundos,usuario}`. Credencial incorreta/conta inativa retorna 401; entradas com formato inválido retornam 400. JWT HS256 inclui emissor, público, sujeito e expiração; TTL padrão 24 horas. Usuário inativo perde acesso nas próximas requisições.
- `POST /meus-lances`: `{lance_id}` com JWT → 201 `{id,lance_id,salvo_em}`. Repetição pelo mesmo usuário retorna 200 com o vínculo existente, inclusive depois da expiração. Novo salvamento exige disponibilidade pública e hierarquia ativa; expirado retorna 400.
- `GET /meus-lances?pagina=0&tamanho=25`: JWT → `{pagina,tamanho,total_lances,lances}`. Lances incluem dados de reprodução e `vinculo_id`, `lance_id`, `salvo_em`; ordenação por salvamento descendente. Usuário vê somente seus próprios vínculos.
- Cadastro/login têm limite inicial por processo de 30 requisições/minuto/IP direto; excesso retorna 429 e `Retry-After: 60`. Esse limite precisa ser complementado pelo ambiente de publicação em múltiplas instâncias.

Estados do lance: `DISPONIVEL` → `EXPIRADO_SALVO` quando há vínculo; ou `PURGA_PENDENTE` → `EXPIRADO_PURGADO` quando não há. A rotina também recolhe `EXPIRADO_SALVO` que futuramente perca o último vínculo. A purga registra tentativa e próximo prazo antes da chamada externa e não mantém transação aberta no S3. Falha conserva estado pendente e gera retry com espera progressiva. Não há coluna `video_url` persistida: a existência do objeto é representada pelo estado e sua key é preservada no histórico.

O job é periódico (padrão um minuto), não endpoint público. Não são implementados pagamento, remoção de conta/vínculo ou promessa comercial de armazenamento perpétuo.

## Erros e operação

Erro estruturado: `{codigo,erro}`. 400 validação; 401 autenticação; 403 permissão; 404 ausência/sem acesso público; 409 duplicidade/conflito; 422 evento novo já expirado; 429 excesso de tentativas; 503 storage indisponível/configuração que impede operação segura. Falhas inesperadas retornam mensagem genérica e ficam registradas no servidor.

`GET /actuator/health` informa saúde sem detalhes sensíveis. Não existem endpoints administrativos de SQL, purga manual pública, exploração do filesystem ou emissão anônima de upload. Para execução e configurações, consulte [backend_cloud/README.md](../backend_cloud/README.md).
