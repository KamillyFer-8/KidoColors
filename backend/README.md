# Back-End — Fases 4 e 7

Java 21, Spring Boot 3.5.16, Web, Data JPA, Validation, PostgreSQL e Playwright Java 1.63.0. O motor kidocolors-core está integrado à coleta e à geração de imagens simuladas.

## Comportamento atual

Fase 8: `dev.kidocolors.backend.validation.ValidationCli` calcula contagens TP/FP/FN/TN, precision, recall, F1 e cobertura a partir de CSV rotulado, sem servidor, banco ou navegador. O template vazio é rejeitado e saídas existentes não são sobrescritas. [Protocolo, formato e execução](../docs/validation.md). Não foram produzidas métricas reais de validação.

POST valida a URL, persiste RUNNING e executa scanner, Core e geração de simulações, de forma síncrona. Ao terminar, salva COMPLETED ou FAILED, finishedAt e durationMs medido. COMPLETED indica processamento concluído, sem certificar acessibilidade. Sem texto avaliável, score é nulo, mesmo em COMPLETED. elementsCollected conta blocos coletados; elementsAnalyzed conta blocos avaliáveis; totalIssues soma falhas de contraste e avisos heurísticos. Registros antigos PENDING/SCANNED não recebem processamento retroativo; envie a URL novamente.

| Endpoint | Resposta |
|---|---|
| POST /api/analyses | 201, JSON da solicitação e Location |
| GET /api/analyses/{id} | 200 ou 404 |
| GET /api/analyses?page=0&size=20 | Histórico paginado, mais recentes primeiro |
| GET /api/analyses/by-url?url=... | Histórico da URL normalizada, mesma paginação |
| GET /api/analyses/{id}/capture | Metadados e elementos coletados; 404 se indisponível |
| GET /api/analyses/{id}/screenshot | PNG real da coleta; 404 se indisponível |
| GET /api/analyses/{id}/report | Resumo, achados, sugestões e links das imagens; 404 se indisponível |
| GET /api/analyses/{id}/screenshot?simulation=PROTANOPIA | PNG simulado; aceita também DEUTERANOPIA e TRITANOPIA |

POST responde 201 também quando a coleta falha, porque o registro FAILED foi criado e pode ser consultado. O cliente precisa verificar status e errorCode. Entrada inválida continua sendo 400 e não cria registro.

O histórico limita size a 1–100; page começa em zero. Erros usam application/problem+json: 400 para entrada inválida, 404 para ID inexistente e 500 com mensagem genérica para falhas internas. O DTO não aceita propriedades desconhecidas, como um score fornecido pelo cliente.

URLs precisam de HTTP/HTTPS, host válido e ausência de credenciais. A origem é normalizada, o fragmento é removido e caminho/query escapados são preservados. IPs locais, privados e faixas reservadas comuns são bloqueados. NetworkGuard resolve os nomes antes da navegação e de cada requisição HTTP, rejeitando qualquer resultado privado. GET/HEAD são permitidos; métodos de escrita, WebSockets, service workers e WebRTC são bloqueados. O navegador não recebe cookies/autenticação de sessões do usuário.

Redirecionamentos HTTP são rejeitados nesta versão: route.fetch usa maxRedirects=0 e o scanner não entrega a resposta 3xx ao navegador. Informe a URL final. Recursos secundários bloqueados incrementam blockedRequests e podem alterar a aparência da página. Mudanças de URL por JavaScript passam pela mesma política de rede.

Limitação de segurança: a validação DNS e a conexão do Playwright são operações separadas; não existe fixação do IP validado. Isso não elimina DNS rebinding nem constitui isolamento completo de rede. A aplicação permanece destinada ao uso local, sem deploy público. Essa limitação deverá ser revista antes de abrir o serviço a usuários externos.

## Executar no terminal PowerShell do VS Code

O desenvolvimento usa Spring Boot/Core local e PostgreSQL hospedado, preferencialmente no Supabase. Docker é opcional. A configuração existente `application.yml` utiliza `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`; JPA, entidades e repositories foram preservados.

Siga [configuração do Supabase, preparação do Chromium e testes](../docs/local-development.md). Depois de preparar o JAR e preencher `.env.local`, execute na raiz:

```powershell
.\scripts\start-backend.ps1
```

O script carrega apenas configurações conhecidas no processo atual, sem imprimir credenciais. Spring não lê `.env` automaticamente. A API fica em `localhost:8080`; `/api/health` executa `SELECT 1` no PostgreSQL. Ctrl+C encerra a API. Para recompilar, use os comandos Maven do guia e reinicie o JAR.

Use Session pooler na porta 5432 e SSL para o PostgreSQL hospedado. A conexão real só pode ser verificada após configurar o projeto no Supabase localmente. Não envie senhas ao chat. Screenshots continuam em `storage/captures`, sem Supabase Storage.

A [alternativa Docker](../docs/docker.md) reúne os três serviços e usa um PostgreSQL próprio, independente do Supabase. `compose.db.yml` permanece disponível para quem escolher um banco em container com Java/Vite locais; esse arquivo não é necessário para o desenvolvimento padrão.

Hibernate mantém `ddl-auto=update` para o projeto de desenvolvimento. Não há migrations versionadas configuradas; esse comportamento não substitui uma estratégia de produção.

## Testes

mvn verify na raiz executa Core e Backend. UrlValidatorTest e NetworkGuardTest verificam entradas e bloqueios. AnalysisServiceTest isola repositório/scanner e verifica persistência, falhas e tempo real. AnalysisApiTest sobe Spring, valida JSON/HTTP e persiste por JPA num H2 em memória, com scanner mockado. PlaywrightPageScannerTest usa Chromium real, a fixture HTML offline e um servidor HTTP local autorizado exclusivamente no teste; valida estilos, PNG, coleta, limites, redirects, erro HTTP e timeout. H2 é apenas de teste; nenhum registro destes testes representa o estudo. Os testes do scanner não são ignorados quando o navegador está ausente: falham e exigem instalação.

Os testes H2 não comprovam execução em PostgreSQL. A conexão ao PostgreSQL hospedado será verificada após a configuração local do Supabase. Não existe fallback H2 ao executar a aplicação normalmente. Os testes não carregam .env.local nem dependem do banco real.

Não há lint Java separado configurado nesta fase. O Compose do banco não foi executado neste ambiente.

Verificação da Fase 3 em 06/10/2026: BUILD SUCCESS com 48 testes do Backend e 12 do Core, sem falhas ou testes ignorados. O scanner foi executado com Chromium real sobre fixtures locais; o JAR executável foi gerado. Esta verificação não é uma coleta do estudo e não valida PostgreSQL real.

No Windows, o perfil Maven windows-test-sockets configura a JVM dos testes para que os pipes internos de selectors usem fallback TCP, evitando a falha AF_UNIX observada no diretório temporário deste ambiente. Não altera Java/Windows globalmente. A configuração aponta jdk.net.unixdomain.tmpdir para um diretório que não deve ser criado. Referências: [propriedade de rede do JDK](https://docs.oracle.com/en/java/javase/16/core/networking-properties.html) e [fallback da implementação Windows do OpenJDK 21](https://github.com/openjdk/jdk21u/blob/master/src/java.base/windows/classes/sun/nio/ch/PipeImpl.java).

## Arquivos e conceitos

- KidoColorsApplication inicia Spring e descobre controllers/services/repositories.
- Analysis é a entidade persistida, com metadados e contagens; resultados ficam nulos em falhas ou registros anteriores sem motor completo.
- AnalysisRepository fornece operações JPA; não precisamos escrever SQL para CRUD básico.
- AnalysisService concentra o caso de uso; evita manter transação de banco aberta durante a navegação, usando transações próprias dos salvamentos do repositório.
- AnalysisController adapta HTTP para chamadas do serviço.
- CreateAnalysisRequest, AnalysisResponse e HistoryResponse são DTOs, separando o contrato da API do modelo do banco.
- ApiExceptionHandler concentra os erros, evitando tratamentos duplicados em cada endpoint.
- UrlValidator é a primeira barreira de validação; NetworkGuard verifica a rede durante a coleta.
- AnalysisEngine adapta CollectedText para TextSample e chama ColorAnalyzer, sem duplicar fórmulas.
- SimulationImageService aplica o mesmo Simulation do Core aos pixels e gera PNGs.
- AccessibilityIssue persiste cada achado; IssueDetail é o DTO de cores, posição, contexto e sugestões.

A tabela accessibility_issue referencia analysis e armazena tipo, severidade, simulação e ordem em colunas, com os detalhes estruturados do achado em detail_json (text). Isso mantém dados de contexto variável sem dezenas de colunas; o DTO continua tipado em Java. analysis tem contagens por tipo para exportação e report_json para versão/resumo do motor. capture_json contém a coleta; PNGs ficam fora do banco. StudyRun registra o lote; analysis.study_run_id associa análises a ele. AnalysisService evita uma transação de banco aberta durante a navegação; cada saveAndFlush termina sua própria transação, com cascade para salvar os achados junto ao resultado final.

## Lotes e exportação — Fase 7

| Endpoint administrativo local | Resposta |
|---|---|
| POST /api/studies (multipart: name, file) | 201 + Location, executa CSV sequencialmente e salva o lote |
| GET /api/studies/{id} | JSON com metadados, métricas, categorias e linhas; 404 se inexistente |
| GET /api/studies/{id}/export.csv | CSV de resultados para planilhas |
| GET /api/studies/{id}/dataset.csv | CSV original para rastreabilidade |

Leia [execução de lotes, denominadores e limitações](../docs/studies.md). O parser Apache Commons CSV evita separar campos incorretamente por vírgulas simples. O lote reutiliza AnalysisService e o mesmo scanner/Core. Não há motor separado, dashboard de estudo ou processamento concorrente das linhas. O estudo real ainda não foi executado.

Verificação da Fase 7 em 06/10/2026: Maven verify passou, com 27 testes do Core e 65 do Backend (14 novos testes de lote), sem falhas ou testes ignorados. O JAR executável foi gerado. Persistência e API de lotes foram verificadas com H2 e fixtures; execução com PostgreSQL e dataset aprovado continua pendente.

## Coleta e duração

O tempo inicia imediatamente antes de chamar o scanner e inclui criação/fechamento do navegador, carregamento, coleta, screenshot, motor de análise, geração de simulações e persistência. A atualização final de durationMs fica fora do intervalo, assim como validação inicial e inserção RUNNING. Instant registra início/fim em UTC; System.nanoTime mede tempo decorrido sem depender de ajustes do relógio do sistema.

Metadados incluem navegador, URL final, viewport 1280×720, escala 1, timeout de 15 segundos por operação e espera adicional de 500 ms após load. O timeout não é um limite rígido do processo inteiro. Limites: 2.000 blocos de texto e altura de screenshot de 12.000 px; atingir o limite de elementos marca truncated, exceder a altura resulta em LIMIT_EXCEEDED. Cada texto é limitado a 1.000 caracteres no relatório.

collect-text.js percorre nós de texto do documento principal, usa computed styles e Range para posições e evita repetir o texto de filhos no pai. Retorna cores HEX opacas após composição de transparência simples sobre fundos de ancestrais, adotando canvas branco. Posições estão em CSS pixels em relação ao documento. selector + textNodeIndex identificam a origem; o seletor não é garantido depois que a página muda.

Gradientes, imagens de fundo, filtros, opacidade de grupo e espaços de cor não suportados resultam em unsupportedReason e cores nulas. Elementos display:none, visibility:hidden e opacity:0 são excluídos. Não cobre texto em canvas, imagens, pseudo-elementos, iframes, shadow DOM ou valores/placeholder de inputs. Ainda não detecta totalmente oclusão, clipping e fundos de elementos sobrepostos. Não rola a página para ativar lazy loading nem garante estabilidade de conteúdo dinâmico. Estes limites precisam ser considerados na validação empírica.

Erros persistidos: TIMEOUT, INACCESSIBLE, BLOCKED, LIMIT_EXCEEDED, STORAGE, BROWSER ou UNEXPECTED. A interrupção do processo pode deixar RUNNING no histórico; não há fila ou recuperação automática nesta V1. Faça nova solicitação após reiniciar. Se o banco falhar ao persistir o estado, a API responderá erro interno; um PNG já gravado pode ficar órfão.

Referências de implementação: [Playwright Java](https://playwright.dev/java/docs/intro), [interceptação de rede](https://playwright.dev/java/docs/network), [Route.fetch e redirects](https://playwright.dev/java/docs/api/class-route#route-fetch).

## Motor e interpretação dos resultados

Leia [metodologia do motor](../docs/methodology.md) para matrizes, fórmulas, limiares, unidade, deduplicação, sugestões e limitações.

O score próprio é round(100 × blocos que passam em AA / blocos avaliáveis). Não é score oficial WCAG. Os avisos de cores não reduzem o score. O relatório registra elementos excluídos, coleta truncada, recursos bloqueados e limite de comparação de pares; esses campos devem acompanhar a apresentação do resultado.

Avisos de protanopia/deuteranopia/tritanopia são combinações únicas de cores por papel/simulação, detectadas com uma heurística não validada empiricamente. A opção TRITANOPIA usa uma aproximação do modelo Machado, explicitamente documentada. Não descreva os avisos como falhas normativas ou diagnósticos.

Testes acrescentados: AnalysisMathTest cobre vetores das matrizes, extremos, score, similaridade, sugestões, cobertura e orçamento de pares; SimulationImageServiceTest valida transformação PNG, alpha e preservação do original. O teste de scanner agora conecta a coleta real ao motor e às três imagens. AnalysisApiTest verifica persistência e recuperação de achados e sugestões com JPA/H2. Mocks continuam exclusivamente em testes.

Verificação da Fase 4 em 06/10/2026: BUILD SUCCESS, 27 testes do Core e 51 do Backend passaram, sem falhas ou testes ignorados. Um caso de precisão no extremo preto/branco foi corrigido limitando a distância normalizada ao máximo matemático 1, com regressão automatizada. O JAR executável foi gerado. Nenhum dataset real foi processado.
