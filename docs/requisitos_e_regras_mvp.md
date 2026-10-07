# ⚽ ClickLance — Especificação de Requisitos e Regras de Negócio (Backend MVP)

> Documento oficial de engenharia de software contendo os **Requisitos Funcionais (RF)**, **Regras de Negócio (RN)**, **Requisitos Não-Funcionais (RNF)** e **Modelo de Dados** para o desenvolvimento do Backend Cloud (Java / Spring Boot) do **ClickLance**.

---

## 📌 1. Visão e Decisões de Arquitetura do MVP

1. **Autonomia sem dependência de operador humano:** Não há abertura ou encerramento manual de partidas. A organização é feita por **quadra, data e faixas de horário fixas de 1 hora** (ex: 20:00–21:00) calculadas automaticamente a partir do timestamp do evento.
2. **Dispositivo = Botão/Entrada Física:** Cada botão físico é modelado como um `Dispositivo` individual com seu próprio `token_acesso` (API Key). Isso permite que um único computador ou central Edge atenda a múltiplas quadras no mesmo local.
3. **Chamada única e atômica pós-upload:** O Gateway Edge processa o corte localmente, realiza o upload no storage (AWS S3) e dispara uma única notificação HTTP consolidada para o backend.
4. **Configuração de captura centralizada por quadra:** Parâmetros operacionais do Edge (duração de segmento e quantidade de segmentos para concatenação) são gerenciados por quadra no backend e consumidos pelo Edge.
5. **Autenticação Zero-Trust e Idempotência:** Requisições do Edge exigem token do dispositivo no header `X-Device-Token`. O backend protege contra duplicidades tratando requisições com tolerância de 5 segundos.
6. **Ciclo de vida híbrido e gestão de custos de Storage:**
   - **Visibilidade pública na quadra:** Curta (política da empresa: 2 a 7 dias).
   - **Biblioteca pessoal do jogador ("Meus Lances"):** Permanente.
   - **Economia no AWS S3:** Lances expirados que ninguém salvou têm seus arquivos de vídeo **deletados fisicamente do S3**, preservando os dados estatísticos no banco como histórico.

---

## 📋 2. Requisitos Funcionais (RF-MVP)

### Módulo 1: Multi-tenancy e Cadastros Base
| ID | Requisito Funcional | Descrição |
|---|---|---|
| **RF-MVP-01** | **Cadastro de Empresa** | O sistema deve permitir o cadastro de empresas operadoras de quadras, incluindo nome, identificador e política padrão de retenção de lances (em dias). *(Ref: RN-01, RN-02)* |
| **RF-MVP-02** | **Cadastro de Quadra** | O sistema deve permitir o cadastro de quadras vinculadas a uma empresa, contendo identificação, configuração de captura e status. *(Ref: RN-02, RN-11)* |
| **RF-MVP-03** | **Cadastro de Dispositivo (Botão)** | O sistema deve permitir o cadastro de dispositivos físicos vinculados a uma quadra, gerando `dispositivo_id` e token seguro (`token_acesso_hash`). *(Ref: RN-03, RN-04, RN-05, RN-07)* |
| **RF-MVP-04** | **Sincronização de Configuração do Edge** | O sistema deve disponibilizar um endpoint para o Gateway Edge consultar os parâmetros de gravação/corte da quadra à qual pertence. *(Ref: RN-11)* |

### Módulo 2: Ingestão de Lances e Edge
| ID | Requisito Funcional | Descrição |
|---|---|---|
| **RF-MVP-05** | **Ingestão Autenticada do Lance** | O backend deve disponibilizar o endpoint `POST /api/v1/replays` para receber `dispositivo_id`, `timestamp` e `video_url`, validando o `X-Device-Token`. *(Ref: RN-05, RN-06, RN-12, RN-13)* |
| **RF-MVP-06** | **Resolução Automática da Quadra** | O backend deve resolver a quadra e a empresa a partir do `dispositivo_id` autenticado. *(Ref: RN-08)* |
| **RF-MVP-07** | **Tratamento de Idempotência** | O backend deve rejeitar ou responder sem duplicar lances que possuam o mesmo dispositivo e timestamp com intervalo menor que 5 segundos. *(Ref: RN-14)* |
| **RF-MVP-08** | **Registro e Cálculo de Expiração** | O backend deve registrar o lance como `DISPONIVEL`, extrair e armazenar a `s3_key`, e calcular `expira_em = timestamp + dias_retencao`. *(Ref: RN-09, RN-15, RN-16)* |

### Módulo 3: Consulta Pública de Lances
| ID | Requisito Funcional | Descrição |
|---|---|---|
| **RF-MVP-09** | **Listagem de Lances por Faixa Horária** | Permitir listar os lances disponíveis filtrando por `quadra_id`, `data` e faixa de horário (slots de 1 hora), exibindo apenas lances com status `DISPONIVEL`. *(Ref: RN-10, RN-17)* |
| **RF-MVP-10** | **Consulta de Lance Individual** | Permitir consultar os detalhes e URL de reprodução de um lance específico pelo seu identificador. |

### Módulo 4: Usuários e Biblioteca Pessoal ("Meus Lances")
| ID | Requisito Funcional | Descrição |
|---|---|---|
| **RF-MVP-11** | **Cadastro e Autenticação de Usuário** | Permitir cadastro e login de jogadores com senha criptografada (BCrypt) e emissão de JWT. |
| **RF-MVP-12** | **Salvar Lance na Biblioteca do Usuário** | Permitir que um usuário autenticado adicione um lance disponível à sua biblioteca pessoal ("Meus Lances"). *(Ref: RN-21, RN-22)* |
| **RF-MVP-13** | **Consulta da Biblioteca do Usuário** | Permitir que o usuário liste todos os seus lances salvos, com data, horário, quadra e link de reprodução, mesmo se o lance já expirou na vitrine pública. *(Ref: RN-19, RN-23)* |

### Módulo 5: Ciclo de Vida e Limpeza
| ID | Requisito Funcional | Descrição |
|---|---|---|
| **RF-MVP-14** | **Rotina Periódica de Expiração e Purga** | Executar rotina agendada (Cron) para processar lances cujo `expira_em` foi atingido: se não houver usuário vinculado, deletar o arquivo do AWS S3 e marcar o registro no banco como `EXPIRADO_PURGADO`. Se houver usuário, marcar apenas como `EXPIRADO_SALVO`. *(Ref: RN-17, RN-18, RN-19, RN-20)* |

---

## ⚖️ 3. Regras de Negócio (RN-MVP)

### Hierarquia e Cadastro
* **RN-MVP-01 — Empresa possui política padrão de retenção:** Cada empresa deve possuir uma política padrão de retenção de lances, definida em quantidade de dias (ex: 2 a 7 dias). Essa política é utilizada para calcular a data de expiração dos lances.
* **RN-MVP-02 — Quadra pertence a uma única empresa:** Cada quadra deve estar vinculada a uma única empresa. Uma empresa pode possuir várias quadras.
* **RN-MVP-03 — Dispositivo pertence a uma única quadra:** Cada dispositivo físico deve estar vinculado a uma única quadra no cadastro do backend (`Dispositivo` $\rightarrow$ `Quadra` $\rightarrow$ `Empresa`).
* **RN-MVP-04 — Dispositivo possui identificação única:** Cada dispositivo possui um identificador único (`dispositivo_id`), utilizado para mapear a origem física dos acionamentos.
* **RN-MVP-05 — Dispositivo possui credencial própria:** Cada dispositivo possui um token de acesso próprio para autenticação das requisições via header HTTP `X-Device-Token`.
* **RN-MVP-06 — Apenas dispositivos ativos realizam ingestão:** Somente dispositivos cadastrados e com status `ativo = true` podem enviar lances. Dispositivos inativos ou tokens inválidos resultam em HTTP `401 Unauthorized`.
* **RN-MVP-07 — Token do dispositivo armazenado como hash:** O token de acesso do dispositivo nunca deve ser gravado em texto puro no banco; armazena-se apenas o seu hash (`token_acesso_hash`).

### Identificação, Resolução e Configuração
* **RN-MVP-08 — A quadra é determinada pelo dispositivo:** O backend determina a quadra associada a partir do `dispositivo_id` autenticado. O Edge não é autoridade para declarar a quadra.
* **RN-MVP-09 — O lance deve possuir o timestamp do acionamento:** Todo lance recebido deve registrar o instante original do clique físico enviado pelo Edge.
* **RN-MVP-10 — Os lances são indexados por data e faixa horária:** Não há abertura ou encerramento manual de partidas. A organização é feita automaticamente pelo timestamp em faixas de 1 hora (ex: `2026-10-06 22:14:50` $\rightarrow$ Data: `2026-10-06`, Faixa: `22:00–23:00`).
* **RN-MVP-11 — A configuração de captura pertence à quadra:** Os parâmetros operacionais (`duracao_segmento_segundos` e `segmentos_concatenar`) residem na Quadra. Todos os dispositivos daquela quadra herdam essa parametrização.

### Ingestão, Processamento e Idempotência
* **RN-MVP-12 — O Edge é responsável pela geração do vídeo:** O Gateway Edge processa o vídeo localmente (corte e concatenação sem re-encoding), faz upload no S3 e envia a referência do vídeo para o backend.
* **RN-MVP-13 — Cada acionamento válido gera um lance:** Toda requisição válida resulta na criação de um único registro de lance associado ao dispositivo e à quadra.
* **RN-MVP-14 — Requisições duplicadas tratadas com idempotência:** Requisições enviadas pelo mesmo dispositivo cujo timestamp esteja dentro de uma tolerância de **5 segundos** são tratadas como o mesmo acionamento. O backend responde com sucesso sem criar registros duplicados.
* **RN-MVP-15 — Lance recebido é criado como disponível:** Lances novos recebidos via ingestão são criados com status `DISPONIVEL`.
* **RN-MVP-16 — A expiração é calculada automaticamente:** Calculada no momento da ingestão por: `expira_em = timestamp_evento + dias_retencao_empresa`.

### Ciclo de Vida, Retenção e Gestão de Storage (S3)
* **RN-MVP-17 — Lances expirados deixam de ser públicos:** Atingido o prazo `expira_em`, o lance deixa de aparecer nas consultas públicas da quadra.
* **RN-MVP-18 — Lance expirado sem vínculo com usuário é purgado do S3:** Se um lance atingir o prazo de expiração e **não** estiver vinculado a nenhum usuário:
  1. O arquivo de vídeo correspondente é **deletado fisicamente do AWS S3** (evitando custos de storage).
  2. O registro no banco de dados é atualizado para `status = EXPIRADO_PURGADO` e `video_url = null` para preservar o histórico e métricas estatísticas da quadra.
* **RN-MVP-19 — Lance expirado vinculado a usuário permanece acessível:** Se o lance estiver salvo na biblioteca de pelo menos um usuário:
  1. O arquivo no AWS S3 **não** é excluído.
  2. O lance deixa de ser público na quadra, mas permanece disponível na área privada dos usuários que o salvaram (`status = EXPIRADO_SALVO`).
* **RN-MVP-20 — Identificação e exclusão segura no S3:** O backend armazena o `s3_key` (caminho relativo do objeto no bucket) para viabilizar a deleção direta via AWS SDK sem ambiguidades.

### Biblioteca Pessoal ("Meus Lances")
* **RN-MVP-21 — Usuário só pode salvar lance disponível e não expirado:** Um usuário autenticado pode vincular um lance à sua biblioteca pessoal somente enquanto o lance estiver com status `DISPONIVEL` e `expira_em > agora`. Lances já expirados não podem ser resgatados.
* **RN-MVP-22 — Vínculo único por usuário:** Um usuário não pode salvar o mesmo lance mais de uma vez (`unique constraint` em `(usuario_id, lance_id)`).
* **RN-MVP-23 — A biblioteca preserva o acesso:** Um lance salvo permanece acessível ao jogador mesmo após o término da visibilidade pública na quadra.

---

## 🔒 4. Requisitos Não Funcionais (RNF-MVP)

| ID | Requisito Não Funcional | Descrição |
|---|---|---|
| **RNF-MVP-01** | **Segurança de Hardware (Edge)** | Ingestão autenticada via header `X-Device-Token` comparado com hash criptográfico. |
| **RNF-MVP-02** | **Segurança de Usuários** | Senhas protegidas com BCrypt e autenticação de rotas via tokens JWT. |
| **RNF-MVP-03** | **Isolamento Multi-tenant** | Dados segregados logicamente por identificadores de empresa. |
| **RNF-MVP-04** | **Performance e Índices** | Índices em `(quadra_id, timestamp_evento)`, `(status, expira_em)` e `(dispositivo_id, timestamp_evento)`. |
| **RNF-MVP-05** | **Idempotência Resiliente** | Resposta consistente (`200 OK` ou `201 Created`) em retentativas de rede do Edge sem gerar múltiplos clipes. |

---

## 🗄️ 5. Modelo de Dados Relacional (PostgreSQL / Spring Data JPA)

```
[Empresa]
 ├── id: UUID (PK)
 ├── nome: String
 ├── dias_retencao_padrao: Integer (ex: 2)
 ├── ativo: Boolean
 └── criado_em: Timestamp

[Quadra]
 ├── id: UUID (PK)
 ├── empresa_id: UUID (FK -> Empresa)
 ├── nome: String ("Quadra 1 - Coberta")
 ├── duracao_segmento_segundos: Integer (default: 60)
 ├── segmentos_concatenar: Integer (default: 2)
 ├── ativo: Boolean
 └── criado_em: Timestamp

[Dispositivo] (Botão Físico)
 ├── id: String (PK: "disp_001")
 ├── quadra_id: UUID (FK -> Quadra)
 ├── token_acesso_hash: String (BCrypt ou SHA-256)
 ├── descricao: String ("Botão Analógico 1")
 ├── ativo: Boolean
 └── criado_em: Timestamp

[Lance]
 ├── id: UUID (PK)
 ├── quadra_id: UUID (FK -> Quadra)
 ├── dispositivo_id: String (FK -> Dispositivo)
 ├── timestamp_evento: Timestamp (momento do clique)
 ├── video_url: String (URL no S3, nulo quando purgado)
 ├── s3_key: String ("replays/quadra_001/lance_20261006_221450.mp4")
 ├── status: Enum (DISPONIVEL, EXPIRADO_SALVO, EXPIRADO_PURGADO)
 ├── expira_em: Timestamp
 └── criado_em: Timestamp

[Usuario] (Jogador)
 ├── id: UUID (PK)
 ├── nome: String
 ├── email: String (Unique)
 ├── senha_hash: String
 ├── ativo: Boolean
 └── criado_em: Timestamp

[UsuarioLance] (Tabela de Junção: Meus Lances)
 ├── id: UUID (PK)
 ├── usuario_id: UUID (FK -> Usuario)
 ├── lance_id: UUID (FK -> Lance)
 ├── salvo_em: Timestamp
 └── UNIQUE(usuario_id, lance_id)
```

---

## 📡 6. Contratos de API (Spring Boot REST)

### 1. Ingestão de Lance (Edge -> Backend)
* **`POST /api/v1/replays`**
* **Headers:** `X-Device-Token: <token_secreto>`
* **Payload:**
  ```json
  {
    "dispositivo_id": "disp_001",
    "timestamp": "2026-10-06T22:14:50",
    "video_url": "https://clicklance-bucket.s3.us-east-1.amazonaws.com/replays/quadra_001/lance_20261006_221450.mp4"
  }
  ```
* **Resposta (201 Created):**
  ```json
  {
    "lance_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "quadra_id": "8b51c8a4-1234-4567-89ab-cdef01234567",
    "status": "DISPONIVEL",
    "expira_em": "2026-10-08T22:14:50"
  }
  ```

### 2. Sincronização de Configurações (Edge -> Backend)
* **`GET /api/v1/dispositivos/{dispositivo_id}/config`**
* **Headers:** `X-Device-Token: <token_secreto>`
* **Resposta (200 OK):**
  ```json
  {
    "dispositivo_id": "disp_001",
    "quadra_id": "8b51c8a4-1234-4567-89ab-cdef01234567",
    "duracao_segmento_segundos": 60,
    "segmentos_concatenar": 2
  }
  ```

### 3. Listagem de Lances por Horário (App / Web)
* **`GET /api/v1/quadras/{quadra_id}/lances?data=2026-10-06&hora_inicio=22:00&hora_fim=23:00`**
* **Resposta (200 OK):**
  ```json
  [
    {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "timestamp_evento": "2026-10-06T22:14:50",
      "video_url": "https://clicklance-bucket.s3.us-east-1.amazonaws.com/replays/quadra_001/lance_20261006_221450.mp4",
      "expira_em": "2026-10-08T22:14:50"
    }
  ]
  ```

### 4. Salvar Lance na Conta do Jogador
* **`POST /api/v1/meus-lances`**
* **Headers:** `Authorization: Bearer <JWT>`
* **Payload:**
  ```json
  {
    "lance_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
  }
  ```
* **Resposta (201 Created):**
  ```json
  {
    "mensagem": "Lance salvo com sucesso na biblioteca.",
    "salvo_em": "2026-10-06T23:30:00"
  }
  ```

### 5. Consultar "Meus Lances"
* **`GET /api/v1/meus-lances`**
* **Headers:** `Authorization: Bearer <JWT>`
* **Resposta (200 OK):**
  ```json
  [
    {
      "lance_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "quadra_nome": "Quadra 1 - Coberta",
      "timestamp_evento": "2026-10-06T22:14:50",
      "video_url": "https://clicklance-bucket.s3.us-east-1.amazonaws.com/replays/quadra_001/lance_20261006_221450.mp4",
      "salvo_em": "2026-10-06T23:30:00"
    }
  ]
  ```
