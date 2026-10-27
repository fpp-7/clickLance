# Status do projeto — implementação e revisão das sprints

Execução iniciada em 26/10/2026. Este documento descreve o estado implementado, substituindo o checklist anterior de protótipo. O [contrato vigente](contrato_api_mvp.md) e as migrations em `backend_cloud/src/main/resources/db/migration/` são as referências para integração.

## Sprints do backend

| Sprint | Histórias | Entrega | Estado |
|---|---|---|---|
| 1 | US-01 a US-04 | Cadastros protegidos, credencial hash por dispositivo e configuração remota | Implementada; testes e revisão concluídos |
| 2 | US-05 | Ingestão, identidade estável, validação da key e retentativa concorrente | Implementada; testes e revisão concluídos |
| 3 | US-06 a US-08 | Quadras, grade com fuso/paginação, detalhe e reprodução temporária | Implementada; testes e revisão concluídos |
| 4 | US-09 a US-12 | Conta/JWT, biblioteca isolada por usuário, expiração e purga com retry | Implementada; testes e revisão concluídos |

A revisão independente está em [revisao_sprints_2026-10-26.md](revisao_sprints_2026-10-26.md). A implementação usa Spring Boot 4.1.2, Java 21 e JDBC com transações explícitas, em vez do Spring Data JPA sugerido no levantamento. PostgreSQL/Flyway foram mantidos.

## Correções incorporadas do review inicial

- Recorte orientado ao timestamp original, incluindo o segmento do clique quando finalizado.
- Nomes de sessão únicos, proteção de segmentos de eventos pendentes e detecção de lacunas.
- Outbox SQLite, checkpoints, workers separados e preservação do clipe até confirmação.
- Contrato Edge/backend alinhado: dispositivo/token, UUID estável e key determinística.
- Idempotência por `evento_id`, substituindo proximidade de cinco segundos como identidade.
- Objetos privados e links temporários; expiração aplicada também na consulta individual.
- Lock compartilhado entre salvamento e reivindicação de purga; I/O fora da transação.
- Intenção de purga e próximo retry persistidos; lances salvos/falhas antigas não bloqueiam o restante do lote.
- FK composta para coerência dispositivo/quadra e unicidade do objeto.
- Configuração com limites comuns no gateway e no backend, fuso IANA e timestamps com offset.
- Administração fechada por segredo global e conta de jogador sem permissão administrativa.

## Evidência e limites

A suíte foi executada contra PostgreSQL 17.7 real em cluster descartável, além dos testes de storage local/S3 simulado e do gateway com vídeo sintético. Os runners ficam em `backend_cloud/scripts/` e os resultados por classe em `backend_cloud/target/surefire-reports/` após execução.

Resultado final:

| Verificação | Resultado |
|---|---|
| Backend e storage, incluindo PostgreSQL real | 29 testes aprovados; zero falhas/erros/ignorados |
| Gateway isolado, incluindo FFmpeg sintético | 21 testes aprovados |
| Integração Python → API Java → PostgreSQL → MP4 privado | 1 teste aprovado em execução própria do smoke |
| Concorrência de salvar/purgar e avanço de lotes | 2 testes incluídos na suíte PostgreSQL, aprovados |
| Formatação dos diffs | Sem erro de whitespace |

O smoke é opt-in e aparece como ignorado quando a suíte Python isolada roda sem servidor; foi executado separadamente com sucesso pelo runner `backend_cloud/scripts/smoke-gateway.ps1`. Os servidores temporários criados pelos runners foram encerrados.

Nenhuma câmera/serial real, credencial de ambiente ou conta AWS foi usada. O SDK de S3 foi testado com substitutos controlados; não há bucket provisionado, CDN ou publicação externa confirmados. O Dockerfile e Compose foram preparados; o Docker Desktop estava sem engine ativa e a validação de banco foi feita pelo runner PostgreSQL independente.

Não há evidência de SLA de cinco minutos ou gravação sem interrupções em operação comercial. A seleção temporal agora tem teste, mas o alinhamento do relógio ao vídeo da câmera real ainda precisa de calibração.

## Gateway disponível

- Sincronização autenticada e cache da última configuração válida para falha de transporte.
- Instância única por fila SQLite, cooldown monotônico e rejeição de cliques sem buffer/disco.
- Supervisão FFmpeg, logs por sessão e encerramento preservando trabalho.
- Geração de clipe com recorte e nova codificação, formato de cor compatível e faststart.
- Retry independente de upload/registro; erros permanentes bloqueados para correção administrativa.
- CLI para inspecionar/recolocar eventos e fazer backup consistente do SQLite.
- Supervisor PowerShell para Windows, sem instalar tarefa agendada automaticamente.

Escopo: uma câmera/quadra por instância. O firmware permanece sem confirmação de aceite/captura no LCD. Um manifesto de sessão longa ainda cresce durante a sessão e deve ser acompanhado no piloto.

## Próximas entregas

1. **Piloto físico:** câmera/lente/iluminação, pinagem e proteção, calibração temporal, quedas de energia e capacidade de CPU/disco/rede.
2. **Operação:** provisionamento AWS/IAM, backup/restauração, monitoramento remoto, alerta com responsável e atualização/rollback.
3. **Produto:** pagador e hipótese comercial, limites/prazo de conservação, política de privacidade, publicação de imagens e atendimento de retirada.
4. **Interface web:** busca, player, cadastro/login e biblioteca consumindo a API implementada.
5. **Identidade:** recuperação de senha, verificação de e-mail, revogação/rotação e papéis administrativos por empresa.
6. **Storage:** reconciliação de uploads órfãos, estratégia de versões/backup e remoção de conta/vínculos.
7. **Expansão:** vários botões/câmeras por gateway e autenticação/protocolo físico de origem.
8. **Pagamentos:** fora das quatro sprints atuais; requer definir compra, prévia e direitos/reembolso.

A biblioteca gratuita preserva vídeos salvos no MVP; isso não deve ser divulgado como compromisso comercial de armazenamento perpétuo sem a política e o financiamento correspondentes.
