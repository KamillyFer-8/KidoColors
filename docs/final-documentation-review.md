# Revisão final da documentação — KidoColors

Data: 07/10/2026. Escopo: README definitivo, conferência de evidências e testes locais. Nenhum commit, push, alteração remota ou nova coleta experimental foi realizado.

## Alterações e estrutura

O README substitui a apresentação por fases e as declarações de estudo pendente por uma descrição do sistema existente e do Run 2 oficial. A abertura diferencia 100 tentativas de 61 análises concluídas e não oculta as 39 falhas. Inclui diagramas, tabelas, comandos com placeholders, links de evidências, score próprio, critérios QUALITY, cobertura e limitações.

Estrutura final: aplicação → funcionamento → arquitetura/tecnologias → Core/score → Scanner V2/segurança → estudo/piloto/evolução → Run 2/categorias/falhas/cobertura → observações → limitações → execução/PostgreSQL/Docker → testes → API → estrutura do repositório → artefatos/integridade → possíveis evoluções → autoria.

## Números e fontes conferidos

Pasta oficial: `results/experimental-study-2026-10-07-run-2-scanner-v2/`.

| Grupo | Valores usados | Conferência |
|---|---|---|
| Execução | 100 tentadas; 61 concluídas; 39 falhas; 61 com score; zero concluídas sem score | `study-statistics.json`, `study.json`, CSV de 100 linhas |
| Coleta | 14.044 observados; 11.498 em concluídas; 2.546 em capturas de falhas; 10.623 avaliados; 875 ignorados em concluídas | Estatísticas, CSV, resumos dos 61 relatórios individuais |
| Achados | 1.158 totais; 1.139 contraste; 6 protanopia; 4 deuteranopia; 9 tritanopia | Estatísticas, CSV e relatórios individuais |
| Páginas com achados | 56/61 = 91,80%; média de achados 18,98 por concluída | Contagens e denominadores das análises concluídas |
| Score | Média 87,44; mediana 90; mínimo 42; máximo 100; faixas 0/3/6/52 | Estatísticas e fórmula original nos 61 relatórios |
| Categorias | 20 por categoria; concluídas 9/15/15/12/10 na ordem da tabela do README | Dataset, linhas dos dois runs e estatísticas por categoria; médias e medianas confirmadas |
| Falhas | 22 INACCESSIBLE; 15 QUALITY; 1 BROWSER; 1 TIMEOUT | Diagnósticos, JSON bruto e CSV; fases/HTTP consultados |
| Motivos QUALITY | 12 CONTENT_NOT_STABLE; 3 NO_VISIBLE_TEXT_AT_READINESS; 1 ZERO_COLLECTED_ELEMENTS; 1 UNIFORM_SCREENSHOT | Diagnósticos estruturados; contagens sobrepostas |
| Cobertura | 58 concluídas truncadas; nove capturas truncadas por altura; duas comparações heurísticas limitadas | Estatísticas, flags das capturas e relatórios |
| Recursos | 1.616 bloqueados/com falha nas capturas disponíveis; 1.035 nas concluídas | Campo `blockedRequests` consolidado |
| Duração | 2.335.670 ms do lote | `study.json` e estatísticas; não substituída pela soma das linhas |

O piloto foi consultado separadamente: 100 tentadas, 53 concluídas, 47 falhas, 50 com score, 8.262 avaliados, 1.184 achados e 18 TIMEOUT. IDs, sites, URLs, categorias e ordem foram confrontados entre os dois runs. Não houve divergência nos números fornecidos para conferência.

## Testes atuais

| Verificação | Resultado nesta revisão |
|---|---|
| Maven verify, perfil `scanner-v2` | BUILD SUCCESS; Core e backend empacotados |
| JUnit Core | 27 testes, zero falhas/erros/ignorados |
| JUnit backend | 77 testes, zero falhas/erros/ignorados |
| Vitest frontend | 24 testes passaram em dois arquivos |
| ESLint | Sem erros; comando finalizado com sucesso |
| TypeScript/Vite | Build finalizado com sucesso |
| Git diff --check | Sem erros de whitespace; avisos de conversão CRLF/LF do Git |

Contagens Java lidas dos XMLs Surefire, não copiadas somente de documentos anteriores. As verificações de scanner usam Chromium e HTTP local; API/persistência de testes utilizam H2 e mocks. Nenhuma das 100 URLs foi solicitada. Não houve novo teste de containers ou declaração de cobertura percentual de código.

O perfil separado gera artefatos em `target-v2`. O JAR reconstruído pelos testes não substitui os JARs congelados nos diretórios experimentais; esses binários foram novamente conferidos pelos manifestos.

## Integridade

Foram comparados os hashes de **1.158 arquivos protegidos** antes e depois desta tarefa: resultados, dataset, fontes Java/React, configurações e builds declarativos. Os inventários do Run 1 e Run 2 foram integralmente conferidos contra os arquivos preservados. Os links locais do README foram verificados.

O registro final do experimento já documentava hashes iguais de 454 arquivos e de 1 estudo, 100 análises e 1.184 achados do piloto no PostgreSQL. Nesta tarefa esses registros foram lidos como evidência histórica, sem executar nova auditoria JDBC ou modificar dados do banco.

Scanner V2, Core, fórmulas, thresholds, dataset, protocolo e todos os arquivos dos runs permanecem iguais. As únicas alterações publicáveis desta tarefa são `README.md` e este documento. Builds, logs e verificações auxiliares ficaram em diretórios já ignorados.

## Inconsistências e pendências identificadas

1. **Documentação histórica:** o README antigo dizia que não existia estudo real. Foi reescrito. `docs/scanner-v2.md`, sua validação, a metodologia e manuais de fases registram estados anteriores, inclusive Run 2 pendente e comportamentos V1. Não foram reescritos para evitar alterar o protocolo e a evidência histórica; o README distingue datas, fonte atual de coleta e matemática preservada.
2. **Natura:** KC-006 terminou BROWSER/CLEANUP sem score/achados. PNG 1280 × 4130 e cobertura declarada 1280 × 4207 foram registrados sem alteração; causa não determinada. As imagens das análises concluídas correspondem às dimensões declaradas.
3. **Comparação da interface:** verifica versão do motor e URL/datas, mas não exige igualdade de protocolo scanner. Como o Core não mudou entre runs, a comparação visual não deve ser interpretada como comparação experimental controlada entre V1 e V2. A limitação foi documentada; código preservado.
4. **Binários grandes:** os dois `backend-used.jar` preservados têm 263.207.855 e 263.221.394 bytes, aproximadamente 251 MiB cada. A preparação posterior para publicação definiu que ficam somente no armazenamento local, ignorados especificamente pelo Git. Identidade, manifestos e evidências permanecem disponíveis para versionamento, sem LFS ou Release. Consulte a [política atual de binários](experimental-binaries.md). Nenhum binário foi apagado e nenhum manifesto foi alterado.
5. **Estado local não publicado:** a V2 e os artefatos já tinham mudanças locais não commitadas. O README não afirma que a revisão atual passou no CI remoto. `scripts/__pycache__/` também já aparecia como não rastreado; não pertence à documentação final e deve ficar fora de uma futura seleção de publicação.

## Conferência de credenciais e afirmações excluídas

`.env.local` está ignorado e não é rastreado; somente os exemplos de ambiente constam nos arquivos versionados. A senha configurada localmente não foi encontrada no README, documentação, resultados, dataset, código ou scripts publicáveis examinados. Valores não foram exibidos. Os exemplos usam placeholders e não incorporam credenciais ao React. Essa conferência dirigida não substitui uma auditoria integral de todo o histórico Git e de binários antes de uma publicação futura.

Não foram incluídos: cobertura de código 100%, precision/recall/F1, eficácia clínica das simulações, certificação WCAG, representatividade estatística, ranking de categorias por acessibilidade, causalidade para todos os erros, melhoria dos websites entre runs, validação atual de Docker ou execução remota da revisão, deploy público, autenticação, PDF ou funcionalidades futuras como existentes. Nenhum contato pessoal ou endereço de portfólio foi inventado.

## Arquivos e Git

Arquivos publicáveis alterados nesta tarefa:

- `README.md` — reescrito.
- `docs/final-documentation-review.md` — criado.

As demais mudanças abaixo já existiam antes da tarefa e foram preservadas. O status não é uma lista de arquivos alterados nesta revisão. Não foram executados `git add`, commit ou push.

```text
## main...origin/main
 M .gitignore
 M README.md
 M backend/src/main/java/dev/kidocolors/backend/analysis/Analysis.java
 M backend/src/main/java/dev/kidocolors/backend/analysis/AnalysisResponse.java
 M backend/src/main/java/dev/kidocolors/backend/analysis/AnalysisService.java
 M backend/src/main/java/dev/kidocolors/backend/scanner/NetworkGuard.java
 M backend/src/main/java/dev/kidocolors/backend/scanner/PageCapture.java
 M backend/src/main/java/dev/kidocolors/backend/scanner/PlaywrightPageScanner.java
 M backend/src/main/java/dev/kidocolors/backend/scanner/ScanException.java
 M backend/src/main/java/dev/kidocolors/backend/scanner/ScannerSettings.java
 M backend/src/main/java/dev/kidocolors/backend/study/StudyService.java
 M backend/src/main/resources/application.yml
 M backend/src/main/resources/scanner/collect-text.js
 M backend/src/test/java/dev/kidocolors/backend/analysis/AnalysisApiTest.java
 M backend/src/test/java/dev/kidocolors/backend/analysis/AnalysisServiceTest.java
 M backend/src/test/java/dev/kidocolors/backend/scanner/PlaywrightPageScannerTest.java
 M backend/src/test/java/dev/kidocolors/backend/study/StudyApiTest.java
 M backend/src/test/java/dev/kidocolors/backend/study/StudyMetricsTest.java
 M datasets/README.md
 M docker-compose.yml
 M pom.xml
 M scripts/start-backend.ps1
?? backend/src/main/java/dev/kidocolors/backend/scanner/ScannerDiagnostics.java
?? datasets/kidocolors-dataset.xlsx
?? datasets/websites.csv
?? docs/final-documentation-review.md
?? docs/scanner-v2-validation.json
?? docs/scanner-v2.md
?? docs/study-failure-audit.csv
?? docs/study-failure-audit.md
?? results/experimental-study-2026-10-07-run-2-scanner-v2/
?? results/experimental-study-2026-10-07/
?? results/study-results.csv
?? results/study-statistics.json
?? results/study-summary.md
?? scripts/__pycache__/
?? scripts/archive-run2-log.py
?? scripts/archive-run2.py
?? scripts/experiment-progress.py
?? scripts/run-experiment.py
?? scripts/run2-experiment.py
?? scripts/seal-run2.py
?? scripts/summarize-experiment.py
```
