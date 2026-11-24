# Zelo

> Aponte no mapa o que precisa de reparo na sua cidade e ajude a cuidar do que é de todos.

**Slogans possíveis:** "Cuidar da cidade começa por você."

---

## Sumário

1. [Visão geral](#1-visão-geral)
2. [Funcionamento do aplicativo](#2-funcionamento-do-aplicativo)
3. [Regras de negócio](#3-regras-de-negócio)
4. [Requisitos funcionais](#4-requisitos-funcionais-rf)
5. [Requisitos não funcionais](#5-requisitos-não-funcionais-rnf)
6. [Identidade visual: paleta "Zelo"](#6-identidade-visual-paleta-zelo)
7. [Arquitetura do back-end](#7-arquitetura-do-back-end)
8. [Escopo do MVP](#8-escopo-do-mvp)
9. [Stack e tecnologias](#9-stack-e-tecnologias)
10. [Próximos passos](#10-próximos-passos)

---

## 1. Visão geral

O **Zelo** é um aplicativo que permite ao cidadão abrir o mapa e marcar pontos da infraestrutura pública da sua cidade que precisam de reparo, como buracos na via, lâmpadas de iluminação pública danificadas, calçadas quebradas, bueiros entupidos, entre outros.

**Objetivos**

- Facilitar o registro de problemas urbanos pelo cidadão.
- Reunir as ocorrências em um mapa público e colaborativo.
- Permitir o acompanhamento do andamento de cada ocorrência.
- Dar transparência sobre quais problemas existem e quanto tempo levam para ser resolvidos.

**Perfis de usuário**

| Perfil         | Descrição                                                                                 |
| -------------- | ----------------------------------------------------------------------------------------- |
| Visitante      | Vê o mapa, as ocorrências e as estatísticas, sem conta                                    |
| Cidadão        | Cria ocorrências, curte, comenta, denuncia e confirma ou contesta resoluções              |
| Moderador      | Na sua cidade: avalia denúncias, remove conteúdo, mescla duplicatas, suspende cidadãos    |
| Gestor público | Na sua cidade: acompanha as ocorrências de todas as categorias e atualiza o status        |
| Administrador  | Global: gerencia categorias, cidades, usuários e configurações, com as funções dos demais |

A cidade de atuação do moderador e do gestor é a cidade do seu endereço (ver [3.1](#31-usuários-e-permissões)).

---

## 2. Funcionamento do aplicativo

### 2.1 Fluxo principal do cidadão

1. **Abre o app** e vê o mapa, centralizado na sua localização (se permitir).
2. **Explora** as ocorrências existentes, podendo filtrar por cidade, categoria, status e período.
3. **Interage** com as ocorrências existentes, podendo deixar um comentário, curtida ou denúncia contra a ocorrência.
4. **Registra um problema:**
   1. Toca em "Reportar" e marca o ponto no mapa (ou usa a localização atual).
   2. Escolhe a categoria e escreve a descrição.
   3. Anexa de 1 a 5 fotos e escolhe se a publicação será anônima.
   4. O app envia o ponto marcado e a localização atual do aparelho; os dois precisam estar na mesma cidade.
   5. O sistema verifica se já existe ocorrência semelhante por perto (mesma categoria, raio de 25 m).
   6. Se existir, sugere **curtir** a existente; o cidadão ainda pode confirmar que é outro problema e registrar a nova.
5. **Acompanha** a ocorrência e recebe notificações a cada mudança de status.
6. **Confirma ou contesta** a resolução em até 15 dias. Se contestada, a ocorrência é reaberta.

### 2.2 Ciclo de vida de uma ocorrência

Toda ocorrência nasce **Aberta**. "Contestada" não é um status: é um evento que devolve a ocorrência para Aberta.

```
                                        ┌─→ contestada pelo autor (até 15 dias) → Aberta (reaberta)
Aberta → Em andamento → Resolvida ──────┤
  │          │                          └─→ confirmada pelo autor (encerrada)
  │          └─→ Aberta (atendimento interrompido, motivo obrigatório)
  │
  ├─→ Arquivada (90 dias sem atualização + 30 dias após o aviso; não reabre)
  ├─→ Rejeitada (motivo obrigatório)
  └─→ Duplicada (mesclada a outra)
```

**Quem faz cada transição**

| De           | Para         | Perfil                                   |
| ------------ | ------------ | ---------------------------------------- |
| Aberta       | Em andamento | Gestor                                   |
| Aberta       | Rejeitada    | Gestor ou moderador                      |
| Aberta       | Duplicada    | Moderador (mesclagem)                    |
| Aberta       | Arquivada    | Sistema (rotina diária) ou administrador |
| Em andamento | Resolvida    | Gestor                                   |
| Em andamento | Aberta       | Gestor                                   |
| Em andamento | Rejeitada    | Gestor ou moderador                      |
| Resolvida    | Aberta       | Autor (contestação) ou administrador     |

O administrador pode fazer qualquer transição da tabela. O status **Em análise** existe no modelo para uma futura pré-moderação (Em análise → Aberta, Rejeitada ou Duplicada, pelo moderador), mas não é usado nesta versão.

### 2.3 Fluxo do moderador

1. Recebe a fila de denúncias da sua cidade (spam, ofensa, informação falsa, local errado ou outro), agrupadas por conteúdo.
2. Analisa e decide: manter, remover o conteúdo, mesclar duplicata ou suspender o autor (com ou sem remoção do conteúdo).
3. Registra o motivo da decisão. A decisão é imutável e resolve todas as denúncias pendentes daquele conteúdo.
4. Também pode mesclar ocorrências e suspender ou reativar cidadãos sem denúncia prévia.

### 2.4 Fluxo do gestor público

1. Acessa o painel com a lista de ocorrências da sua cidade, ordenada por prioridade, com filtro opcional por categoria.
2. Atualiza o status e registra observações.
3. Consulta estatísticas de volume e tempo de resolução.
4. Recebe todo dia um resumo das novas ocorrências da sua cidade.

### 2.5 Relação com o poder público

- **Sem parceria com a prefeitura:** o app deixa claro que **não é um canal oficial**. Cada cidade tem um aviso de canal não oficial (com texto padrão) e pode ter o link do canal oficial, configurados pelo administrador. O atalho para gerar um texto ou protocolo para o canal oficial fica para depois (ver [10](#10-próximos-passos)).
- O app não promete prazo de conserto ao cidadão.

---

## 3. Regras de negócio

### 3.1 Usuários e permissões

- A leitura do mapa, das ocorrências, das categorias, das cidades e das estatísticas é pública, sem necessidade de login.
- O cidadão cria a própria conta com e-mail de um dos domínios permitidos (gmail, googlemail, yahoo, hotmail, outlook, live e msn, incluindo as variações `.com.br`), para reduzir spam e trotes. A lista fica na configuração do back-end (`application.yml`), não no banco.
- Contas de moderador, gestor e administrador são criadas pelo administrador, sem restrição de domínio. O primeiro administrador é criado na inicialização a partir das variáveis de ambiente.
- Não há verificação de e-mail nem login social nesta versão.
- O cadastro exige aceite dos Termos de Uso e da Política de Privacidade.
- **Todo usuário tem endereço** (CEP, rua, bairro, número, complemento opcional e cidade). Para moderadores e gestores, a cidade do endereço é a área de atuação; por isso eles não alteram o próprio endereço, só o administrador. O cidadão altera o seu livremente.
- Cada perfil só altera o que lhe cabe. O cidadão não muda o status para "Resolvida" sozinho, por exemplo.
- O usuário suspenso faz login e navega, mas não cria, curte, comenta nem denuncia.

### 3.2 Criação de ocorrência

- A localização é obrigatória. O ponto marcado e a localização atual do aparelho precisam estar dentro da mesma cidade cadastrada, e essa cidade precisa estar ativa.
- A categoria é obrigatória. A lista é única para todas as cidades e configurável pelo administrador (buraco, iluminação, calçada, bueiro, sinalização, entulho, árvore etc.).
- De 1 a 5 fotos, com até 10 MB cada, nos formatos JPEG, PNG ou WEBP.
- A descrição é obrigatória, com 10 a 1000 caracteres.
- Há um limite de 5 ocorrências por usuário por dia civil (fuso de São Paulo), para evitar abuso.
- O autor escolhe, na criação, se a ocorrência será publicada como anônima.
- O autor pode editar ou excluir a própria ocorrência enquanto ela estiver aberta.

### 3.3 Duplicidade

- Ao marcar um ponto, o sistema busca ocorrências ativas da mesma categoria num raio de 25 m.
- Se houver, sugere **"Também vejo esse problema"** (curtir a existente) em vez de criar outra. O cidadão pode confirmar que é outro problema e registrar mesmo assim.
- Moderadores podem mesclar duplicatas da mesma cidade. A duplicada passa a apontar para a principal, e o autor e os apoiadores da duplicada passam a apoiar a principal.

### 3.4 Ciclo de vida e status

- O fluxo segue o descrito em [2.2](#22-ciclo-de-vida-de-uma-ocorrência).
- Cada mudança de status registra autor, data e motivo, formando um histórico imutável (o banco recusa alteração e exclusão).
- Só perfis autorizados alteram o status, e só na sua cidade de atuação.
- Rejeição, interrupção de atendimento e reabertura exigem motivo.
- O autor confirma ou contesta a resolução em até 15 dias. A confirmação encerra a ocorrência; a contestação a reabre. Uma ocorrência arquivada não é reaberta.
- Ocorrências abertas sem atualização por 90 dias recebem aviso e, 30 dias depois do aviso, são arquivadas.
- A prioridade de cada ocorrência é o total de curtidas mais os dias em aberto, recalculada todo dia.

### 3.5 Moderação e qualidade

- Qualquer usuário autenticado pode denunciar uma ocorrência ou um comentário. Cada denúncia aponta para exatamente um dos dois, nunca para ambos.
- Cada usuário denuncia o mesmo conteúdo uma única vez, e não denuncia o próprio conteúdo. O motivo "outro" exige descrição.
- O denunciante pode corrigir a denúncia enquanto ela não for analisada.
- Usuários reincidentes podem ser suspensos temporária ou permanentemente. O moderador só suspende cidadãos; o administrador suspende qualquer perfil, menos outros administradores. Ninguém suspende a si mesmo.

### 3.6 Notificações

- O autor e quem curtiu a ocorrência são avisados a cada mudança de status (o autor não é avisado da própria contestação).
- Também geram aviso: o arquivamento próximo, a mesclagem, a remoção de conteúdo pela moderação e a suspensão da conta.
- O usuário escolhe os canais (push e e-mail; por padrão, só push) e pode silenciar ocorrências.
- O gestor recebe todo dia um resumo das novas ocorrências na sua cidade.

### 3.7 Privacidade e LGPD

- Coletar apenas o necessário e registrar o consentimento explícito, com versão, para os Termos de Uso, a Política de Privacidade e a localização.
- Remover os metadados (EXIF) das fotos antes de publicar: toda foto é regravada.
- Permitir **publicação anônima** por ocorrência na vista pública, mesmo que o sistema conheça o autor. Comentários nunca são anônimos.
- Garantir ao usuário o direito de excluir a conta: os dados pessoais são apagados ou anonimizados, o endereço é apagado se ninguém mais o usa, as sessões são encerradas e o conteúdo deixa de identificar o autor ("Usuário removido").
- Política de retenção: depois de 5 anos, ocorrências e comentários deixam de ser vinculados ao autor.
- Não expor dados pessoais de quem reporta ao gestor. Só a moderação vê o autor, para poder suspendê-lo.

### 3.8 Transparência

- Estatísticas públicas por cidade e categoria, com filtro de período: total, abertas, em andamento, resolvidas, arquivadas e tempo médio de resolução.

### 3.9 Multi-cidade

- Todas as cidades do Brasil estão cadastradas, com estados e limites geográficos importados das APIs de localidades e malhas do IBGE.
- O administrador ativa ou desativa cada cidade e define os textos legais, o aviso de canal não oficial e o link do canal oficial.
- As categorias são as mesmas em todas as cidades. Gestores e moderadores são de uma cidade (a do endereço); o administrador atua em todas.

---

## 4. Requisitos funcionais (RF)

### Conta e acesso

- **RF01:** O sistema deve permitir cadastro (com endereço) e login (e-mail/senha).
- **RF02:** O sistema deve controlar permissões por perfil (cidadão, moderador, gestor, administrador).

### Mapa

- **RF03:** O sistema deve exibir o mapa da cidade com as ocorrências como marcadores.
- **RF04:** O sistema deve centralizar o mapa na localização do usuário, mediante permissão.
- **RF05:** O sistema deve agrupar marcadores próximos (clusterização) conforme o zoom. O agrupamento é feito no app; a API entrega os marcadores da área com limite de itens.
- **RF06:** O sistema deve filtrar ocorrências por cidade, categoria, status e período.
- **RF07:** O sistema deve permitir buscar por endereço ou local (no app, sobre a base do mapa).

### Ocorrências

- **RF08:** O usuário deve poder criar uma ocorrência informando localização, categoria, fotos e descrição.
- **RF09:** O sistema deve validar que o ponto está dentro dos limites da cidade onde o usuário se encontra.
- **RF10:** O sistema deve sugerir ocorrências semelhantes próximas antes de salvar uma nova (duplicidade).
- **RF11:** O usuário deve poder curtir uma ocorrência existente, uma única vez.
- **RF12:** O usuário deve poder comentar, e editar ou excluir a própria ocorrência enquanto ela estiver aberta.
- **RF13:** O sistema deve exibir os detalhes da ocorrência: fotos, status, histórico, curtidas e comentários.

### Status e acompanhamento

- **RF14:** O sistema deve gerenciar o ciclo de vida das ocorrências.
- **RF15:** O sistema deve registrar o histórico de cada mudança de status (autor, data e motivo).
- **RF16:** O autor deve poder confirmar ou contestar a resolução, o que pode reabrir a ocorrência.
- **RF17:** O sistema deve notificar os envolvidos a cada mudança de status.

### Moderação e gestão

- **RF18:** O usuário deve poder denunciar uma ocorrência ou um comentário inadequado.
- **RF19:** O moderador deve poder manter, remover, mesclar duplicatas e suspender ou reativar usuários.
- **RF20:** O gestor deve ter um painel com a lista de ocorrências da sua cidade, com filtro por categoria, e poder atualizar status.
- **RF21:** O administrador deve poder gerenciar categorias, cidades e usuários, além de ter todas as funções dos outros usuários.

### Transparência

- **RF22:** O sistema deve exibir estatísticas públicas (total, resolvidas, tempo médio por cidade e categoria).

---

## 5. Requisitos não funcionais (RNF)

### Desempenho

- **RNF01:** O mapa deve carregar em até 5s em conexão 4G.
- **RNF02:** O sistema deve carregar apenas os marcadores da área visível (consulta por região, resposta enxuta, até 500 itens por padrão e 1000 no máximo, indicando quando há mais).
- **RNF03:** As fotos devem ser comprimidas e redimensionadas no envio (até 1920 px no maior lado, em JPEG).

### Usabilidade e acessibilidade

- **RNF04:** Registrar uma ocorrência deve levar poucos toques (meta: menos de 2 minutos).
- **RNF05:** A interface deve ser responsiva e funcionar bem no celular (mobile first).
- **RNF06:** O sistema deve seguir boas práticas de acessibilidade (WCAG): contraste, leitor de tela e tamanhos de fonte.
- **RNF07:** A interface e as mensagens de erro da API devem estar em português do Brasil.

### Segurança e privacidade

- **RNF08:** Toda comunicação deve usar HTTPS.
- **RNF09:** Senhas devem ser armazenadas com hash seguro (bcrypt).
- **RNF10:** O sistema deve estar em conformidade com a LGPD: consentimento, minimização, exclusão de dados e política de privacidade.
- **RNF11:** Os metadados (EXIF) das fotos devem ser removidos antes da publicação.
- **RNF12:** Deve haver proteção contra abuso: limite de requisições (rate limit: 120 por minuto, 10 por minuto nas rotas de autenticação), validação de entradas e proteção contra injeção e XSS.
- **RNF13:** O upload de arquivos deve validar tipo (pelo conteúdo do arquivo, não pela extensão), tamanho e resolução.

### Disponibilidade e confiabilidade

- **RNF14:** Meta de disponibilidade de 99% ou mais.
- **RNF15:** O sistema deve manter logs e auditoria das ações sensíveis (mudança de status, moderação, gestão de usuários), em registro imutável.

### Escalabilidade e manutenção

- **RNF16:** O código deve ser modular, com testes automatizados (JUnit: unitários nas regras de negócio e de integração com o banco) e documentação da API (OpenAPI/Swagger).
- **RNF17:** O sistema deve usar banco com suporte geoespacial (PostgreSQL com PostGIS) para consultas por raio e área.

### Compatibilidade e portabilidade

- **RNF18:** O sistema deve funcionar nos navegadores modernos (Chrome, Safari, Firefox e Edge) e em Android e iOS, seja como PWA ou app nativo.
- **RNF19:** O mapa deve usar uma base aberta (OpenStreetMap), evitando custos e dependência de licença.

---

## 6. Identidade visual: paleta "Zelo"

Verde-azulado e coral: acolhedora, moderna e próxima do cidadão.

### 6.1 Cores-base

| Função   | Cor       | Uso                                   |
| -------- | --------- | ------------------------------------- |
| Primária | `#0F7B6C` | Marca, botões principais, "Resolvida" |
| Destaque | `#E8664A` | Chamadas de ação, "Aberta"            |
| Apoio    | `#7BC8B8` | Elementos secundários, ilustrações    |
| Fundo    | `#F1F7F5` | Fundo das telas                       |
| Texto    | `#12302B` | Texto principal                       |

### 6.2 Cores de status das ocorrências

| Status       | Cor       | Texto sobre a cor |
| ------------ | --------- | ----------------- |
| Aberta       | `#E8664A` | `#3A1208`         |
| Em andamento | `#E9B44C` | `#412402`         |
| Resolvida    | `#0F7B6C` | `#FFFFFF`         |

### 6.3 Complementos para cobrir toda a interface

| Função                       | Cor                           | Observação                               |
| ---------------------------- | ----------------------------- | ---------------------------------------- |
| Texto secundário             | `#4A635E`                     | Contraste adequado sobre o fundo         |
| Texto discreto / placeholder | `#7C918C`                     | Apenas para dicas e legendas             |
| Borda                        | `#D5E2DE`                     | Campos e divisores                       |
| Superfície (cartões)         | `#FFFFFF`                     | Sobre o fundo `#F1F7F5`                  |
| Primária hover               | `#0B6357`                     | Variação do verde-azulado                |
| Primária pressionada         | `#084F46`                     | Variação do verde-azulado                |
| Primária suave (tint)        | `#D8EFEA`                     | Itens selecionados, selos                |
| Destaque hover               | `#D2523A`                     | Variação do coral                        |
| Erro                         | `#C62F2F`                     | Vermelho mais fechado, separado do coral |
| Aviso                        | `#E9B44C` com texto `#6B4A0A` | Também usado em "Em andamento"           |
| Informação                   | `#2F7DB8`                     | Dicas e mensagens neutras                |

### 6.4 Ajustes de coerência

- Use o coral para "Aberta" no mapa e o vermelho de erro apenas em validações e falhas, para que um marcador de problema nunca pareça uma mensagem de erro.
- O verde-azulado é primária e também "Resolvida", então o botão principal pode parecer um status de sucesso. Dá para aceitar isso ou usar um verde distinto (como `#2F9E5B`) só para "Resolvida".

### 6.5 Boas práticas

- **Status:** use sempre a mesma cor para o mesmo status (marcador, etiqueta e notificação).
- **Acessibilidade:** não dependa só da cor; combine com ícone ou texto, para ajudar quem tem daltonismo.
- **Contraste:** texto e fundo com ao menos 4,5:1 (WCAG AA). Confira no WebAIM Contrast Checker.
- **Modo escuro:** derive uma versão escura da paleta, com fundo grafite e primárias um pouco mais claras.
- **Mapa:** os marcadores precisam contrastar com o mapa-base do OpenStreetMap (verdes e cinzas claros). O coral de destaque funciona bem.

---

## 7. Arquitetura do back-end

Monólito modular, organizado por funcionalidade (package by feature), com camadas dentro de cada módulo. Aplique SOLID sempre, e use ideias da Clean Architecture (inversão de dependência) só onde existe regra de negócio de verdade.

```
com.zelo
├── ocorrencia
│   ├── api          (controllers, DTOs)
│   ├── application  (casos de uso / services)
│   ├── domain       (entidades, regras, portas)
│   └── infra        (repositórios JPA, integrações)
├── localidade       (estados, cidades e importação do IBGE)
├── categoria
├── usuario          (contas, endereços, autenticação)
├── moderacao
├── notificacao
└── compartilhado    (segurança, erros, auditoria, config)
```

### Onde vale ir além das camadas simples (estilo Clean)

- **Ciclo de vida da ocorrência:** transições de status permitidas, quem pode fazer cada uma, reabertura. Coloque isso no domínio (um método `ocorrencia.iniciarAtendimento()`, não um `setStatus` solto), com testes unitários sem Spring nem banco.
- **Detecção de duplicidade e cálculo de prioridade:** são regras que vão mudar. Isolá-las atrás de interfaces (`DetectorDeDuplicidade`, `CalculadoraDePrioridade`) facilita testar e trocar a estratégia.
- **Armazenamento de imagens e notificações:** defina uma interface (`ArmazenamentoDeImagens`, `Notificador`) e implemente na infraestrutura. Assim você troca o disco local pelo Cloudflare R2, ou o log por FCM e e-mail, sem mexer nas regras.

### Onde não vale a cerimônia

- Cadastro de categorias, listagens, filtros e estatísticas são basicamente CRUD e consulta. Um controller chamando um service e um repositório resolve. Criar interface, mapper e caso de uso para cada operação só vai gerar arquivos sem ganho.

### Prática que mantém a modularidade

Um módulo não acessa diretamente o repositório de outro. Ele chama o serviço público do outro módulo, ou escuta um evento (`StatusAlterado`, `OcorrenciaMesclada`, `UsuarioSuspenso`, `ContaExcluida`) para disparar notificações. As notificações rodam depois do commit, em transação própria: uma falha ao notificar nunca desfaz a ação que a gerou.

### Onde fica cada regra

- **Back-end (configuração):** valores que mudam por decisão de produto ficam no `application.yml`, não em tabelas: domínios de e-mail permitidos, limite diário, raio de duplicidade, prazos de contestação e arquivamento, limites de fotos, versões dos termos.
- **Domínio:** regras de transição, permissões por perfil e área de atuação.
- **Banco:** o que nunca pode ser violado, mesmo por SQL direto: chaves estrangeiras, "exatamente um alvo" em denúncias e decisões, endereço obrigatório para contas ativas, uma curtida por usuário e tabelas imutáveis (histórico, decisões, auditoria).

### Modelo de dados

| Tabela                                                            | Observação                                                                                                                                            |
| ----------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| `estado`, `cidade`                                                | Ids são os códigos do IBGE. A cidade guarda o polígono (PostGIS), se está ativa e os textos legais                                                    |
| `perfil_usuario`                                                  | Cidadão, Moderador, Gestor público, Administrador (ids fixos), 1:N com `usuario`                                                                      |
| `endereco`                                                        | CEP, rua, bairro, número, complemento opcional e cidade, 1:N com `usuario`                                                                            |
| `usuario`, `refresh_token`, `consentimento`                       | Conta, sessões (refresh token com rotação) e aceites versionados                                                                                      |
| `categoria`                                                       | Global, sem vínculo com cidade                                                                                                                        |
| `ocorrencia`, `foto_ocorrencia`, `historico_status`               | O ponto geográfico é gerado a partir da latitude e longitude; o histórico é imutável                                                                  |
| `curtida`, `comentario`                                           | Uma curtida por usuário por ocorrência                                                                                                                |
| `tipo_conteudo`                                                   | Ocorrência ou Comentário (ids fixos)                                                                                                                  |
| `denuncia`, `decisao_moderacao`                                   | `ocorrencia_id` e `comentario_id` opcionais, sempre exatamente um preenchido; suspensão e reativação diretas não têm conteúdo; decisões são imutáveis |
| `notificacao`, `preferencia_notificacao`, `ocorrencia_silenciada` | Notificações no app, canais escolhidos e ocorrências silenciadas                                                                                      |
| `registro_auditoria`                                              | Ações sensíveis, imutável                                                                                                                             |

### Erros da API

Todo erro segue o formato Problem Details (RFC 9457), com o status HTTP correspondente, um `codigo` estável (ex.: `CIDADE_DIFERENTE_DO_DISPOSITIVO`) e uma mensagem em português que descreve o problema.

---

## 8. Escopo do MVP

1. Login simples (RF01).
2. Mapa com marcadores (RF03, RF04).
3. Criar ocorrência com localização, categoria e foto (RF08, RF09).
4. Detecção de duplicidade e curtidas (RF10, RF11).
5. Status básico: Aberta, Em andamento, Resolvida e Rejeitada (RF14).
6. Denúncia de conteúdo com moderação manual (RF18, RF19).
7. Aviso de que o app **não é canal oficial**.

Requisitos não funcionais prioritários: RNF05, RNF08 a RNF10 e RNF18.

A API já cobre o documento inteiro, além do MVP, com as exceções listadas em [10](#10-próximos-passos).

---

## 9. Stack e tecnologias

- DB: PostgreSQL com PostGIS.
  - Flyway: migrations.
- Back End: Java 17 com Spring Boot 4.
  - Spring Security com JWT e refresh token, Spring Data JPA, validação.
  - Documentação da API com OpenAPI (Swagger UI).
  - Limites e cidades do IBGE (APIs de localidades e malhas) importados na primeira inicialização.
  - Testes com JUnit, Mockito e MockMvc; os de integração usam o PostgreSQL local num schema próprio.
- Front End: Flutter para o desenvolvimento do aplicativo, MobX para gerência de estado e React para a versão web do painel administrativo (Disponível apenas para moderadores e administradores).
- Armazenamento de Imagens será feito via Cloudflare R2 (API compatível com S3). Em desenvolvimento, as fotos ficam no disco local.

---

## 10. Próximos passos

Itens fora desta versão da API, por decisão ou porque dependem de outra parte do sistema:

- **Verificação de e-mail** no cadastro e **login social**.
- **Pré-moderação** com o status "Em análise" antes de a ocorrência ficar pública.
- **App e painel web:** clusterização dos marcadores (RF05) e busca por endereço (RF07) ficam no cliente.
