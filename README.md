# api-zelo

API do **Zelo**: o cidadão aponta no mapa problemas da infraestrutura urbana, acompanha o andamento e ajuda a
cuidar do que é de todos. O Zelo **não é canal oficial** do poder público.

Stack: Java 17, Spring Boot 4.2, PostgreSQL + PostGIS, Flyway, JWT (HS256), Cloudflare R2 (ou disco local em dev).

## Como rodar

### 1. Banco

Pré-requisitos: PostgreSQL com **PostGIS** instalado no servidor. No Windows, use o *Application Stack Builder* >
*Spatial Extensions* > **PostGIS 3.7 Bundle for PostgreSQL 19**.

Crie o usuário, o banco e as extensões (como superusuário; troque a senha no script antes):

```powershell
& "C:\Development\PostgreSQL\bin\psql.exe" -U postgres -f scripts/criar-banco.sql
```

As tabelas são criadas pelo Flyway (`src/main/resources/db/migration`) na primeira inicialização.

### 2. Variáveis de ambiente

Copie `.env.example` para `.env` e ajuste. O mínimo para subir: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` e
`ZELO_JWT_SECRET` (32+ caracteres). Com `ZELO_ADMIN_EMAIL`, `ZELO_ADMIN_SENHA` e o endereço (`ZELO_ADMIN_CEP`,
`_RUA`, `_BAIRRO`, `_NUMERO`, `_CIDADE_ID`), o primeiro administrador é criado na inicialização.

### 3. Subir

```powershell
.\mvnw.cmd spring-boot:run
```

Pela IDE, execute a classe `com.zelo.ZeloApplication` (`src/main/java/com/zelo/ZeloApplication.java`), com a raiz do
projeto como diretório de trabalho. O `.env` (UTF-8) é lido automaticamente pela classe `CarregadorDoArquivoEnv`;
variáveis de ambiente do sistema têm prioridade sobre ele.

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI: http://localhost:8080/v3/api-docs
- Saúde: http://localhost:8080/actuator/health

Na primeira subida, com a tabela vazia, os 27 estados e as 5.570 cidades (com polígonos) são importados do IBGE
antes de concluir a inicialização (~15 s), porque o administrador inicial precisa de uma cidade no endereço.
Atualização manual: `POST /api/v1/admin/cidades/importacao`. Para desligar: `ZELO_IMPORTAR_CIDADES=false`.

### Testes

```powershell
.\mvnw.cmd test
```

Testes unitários (`*Test`): domínio e serviços com Mockito. Rodam sem banco.

```powershell
.\mvnw.cmd verify
```

Roda também os testes de integração (`*IT`): aplicação completa com MockMvc, segurança e JWT reais, PostgreSQL
com PostGIS. Eles usam o banco local do `.env` (`DB_USERNAME`/`DB_PASSWORD`), mas num schema próprio, `zelo_teste`,
que é apagado e recriado pelas migrations a cada execução; o schema `public` (dados de desenvolvimento) não é tocado.
As cidades de teste (São Paulo, Rio de Janeiro e Campinas desativada, com limites retangulares) estão em
`src/test/resources/db/dados-teste`. Para usar outro banco: `ZELO_TESTE_DB_URL`.

| Classe | O que cobre |
|---|---|
| `SegurancaIT` | Acesso por perfil a cada grupo de rotas, token adulterado, conta excluída com token válido |
| `AutenticacaoIT` | Cadastro, login, rotação e reuso de refresh token, troca de senha, endereço, exclusão de conta (LGPD) |
| `OcorrenciaIT` | Polígonos das cidades, raio de 25 m, limite diário, filtros, mapa, estatísticas, curtidas, ciclo de vida e notificações |
| `ModeracaoIT` | Denúncias com exatamente um alvo, fila, decisões, mesclagem, suspensão e reativação |
| `RestricoesDoBancoIT` | Constraints (`ck_denuncia_alvo`, `ck_decisao_alvo`…), tabelas imutáveis, FKs e dados das migrations |

## Arquitetura

Monólito modular por funcionalidade, com camadas `api` / `application` / `domain` / `infra` em cada módulo:

| Módulo | Responsabilidade |
| --- | --- |
| `compartilhado` | Segurança (JWT, perfis, rate limit), tratamento de erros, auditoria, paginação |
| `localidade` | Estados e cidades do IBGE (com polígonos), validação de localização |
| `categoria` | Categorias de ocorrência (globais) |
| `usuario` | Cadastro, login, conta, endereço, LGPD, gestão da equipe pelo admin |
| `ocorrencia` | Ciclo de vida, fotos, mapa, duplicidade, curtidas, comentários, painel, estatísticas, rotinas |
| `moderacao` | Denúncias, decisões, mesclagem, suspensão |
| `notificacao` | Notificações no app, preferências, silenciamento, resumo do gestor |

Um módulo não acessa o repositório de outro: chama o serviço público dele ou reage a eventos de domínio.
As transições de status ficam no agregado `Ocorrencia` (sem `setStatus`), testadas sem Spring.

Valores fixos usados pelo código ficam em tabelas de apoio com ids estáveis: `perfil_usuario` (enum `Perfil`)
e `tipo_conteudo` (enum `TipoConteudo`). Denúncias e decisões de moderação apontam para exatamente uma ocorrência
**ou** um comentário (`ocorrencia_id` / `comentario_id`), regra garantida na API (400 `ALVO_INVALIDO`) e no banco
(constraints `ck_denuncia_alvo` e `ck_decisao_alvo`). Moderadores e gestores atuam na cidade do próprio endereço.

Regras que ficam no backend e não em tabela: domínios de e-mail permitidos (`zelo.cadastro.dominios-email-permitidos`),
limite diário, raio de duplicidade, prazos de contestação e arquivamento, limites de fotos (`application.yml`).

## Erros

Todas as respostas de erro seguem a RFC 9457 (`application/problem+json`), com `codigo` estável e mensagem em português:

```json
{
  "type": "about:blank",
  "title": "Regra de negócio violada",
  "status": 422,
  "detail": "A ocorrência deve estar na mesma cidade em que você se encontra.",
  "instance": "/api/v1/ocorrencias",
  "codigo": "CIDADE_DIFERENTE_DO_DISPOSITIVO",
  "timestamp": "2026-11-23T12:00:00Z"
}
```

| Status | Quando |
| --- | --- |
| 400 | Campos inválidos (lista em `erros`), JSON mal formado, parâmetro ausente ou com tipo errado |
| 401 | Sem token, token inválido/expirado, credenciais erradas |
| 403 | Perfil sem permissão, fora da área de atuação, usuário suspenso |
| 404 | Recurso inexistente ou removido |
| 409 | Conflito de estado: transição inválida, e-mail já cadastrado, curtida repetida, ocorrências semelhantes (lista em `semelhantes`) |
| 413 / 415 | Foto grande demais / formato não suportado |
| 422 | Regra de negócio: fora da cidade, categoria indisponível, prazo encerrado, motivo obrigatório |
| 429 | Rate limit ou limite diário de ocorrências (cabeçalho `Retry-After` no rate limit) |
| 503 | Armazenamento de fotos ou IBGE indisponível |
