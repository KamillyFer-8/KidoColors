# Run 2 oficial — Scanner V2

Lote `338883f9-ec46-40f5-8e49-4bef7529a44e`; 100 tentativas, uma por linha, sem substituições ou repetição seletiva.
Coleta UTC: 2026-10-07T17:07:56.417152Z a 2026-10-07T17:46:52.089047Z.

## Pré-verificação e protocolo

A API e o PostgreSQL/Supabase responderam antes do único POST. Os parâmetros foram lidos do bean efetivo; o Chromium foi aberto e fechado sem criar página ou visitar URL. Dataset, IDs, URLs, sites, categorias, ordem e hashes coincidem com o piloto. Os snapshots da configuração e do JAR aprovado estão nesta pasta.
DOMCONTENTLOADED; navegação 30.000 ms; prontidão 5.000 ms; estabilidade 500 ms; viewport 1280×720; cobertura superior de até 12.000 px; 2.000 textos; cinco redirects. Nenhum parâmetro ou scanner foi ajustado durante o lote.

## Resultados reais

| Medida | Resultado |
|---|---|
| Tentadas / concluídas / falhas | 100 / 61 / 39 |
| Sucesso operacional | 61.00% de 100 tentativas |
| Páginas com score / sem score | 61 / 39 |
| Concluídas sem score | 0 |
| Textos coletados observados / em concluídas / em capturas de falhas | 14044 / 11498 / 2546 |
| Textos avaliados / ignorados em relatórios concluídos | 10623 / 875 |
| Textos com exclusão em capturas de falhas, sem análise pelo Core | 181 |
| Achados / páginas concluídas com achados | 1158 / 56 |
| Concluídas com achados | 91.80% (denominador: 61) |
| Score médio / mediana / mínimo / máximo | 87.44 / 90.00 / 42 / 100 |
| Média de achados por página concluída | 18.98 |
| Falhas de contraste | 1139 |
| Avisos protanopia / deuteranopia / tritanopia | 6 / 4 / 9 |
| Capturas com metadados / sem metadados | 77 / 23 |
| Páginas truncadas observadas / por altura | 74 / 9 |
| Concluídas com coleta truncada / comparação heurística limitada | 58 / 2 |
| Falhas QUALITY | 15 |
| Requisições bloqueadas registradas em capturas / em concluídas | 1616 / 1035 |
| Páginas com bloqueios observados / concluídas com bloqueios | 71 / 55 |
| Tempo total do lote | 2335.67 s |
| Tempo médio / mínimo / máximo por linha, incluindo falhas | 23.00 / 1.90 / 85.89 s |

## Distribuição de scores

| Faixa | Páginas com score |
|---|---:|
| 0-24 | 0 |
| 25-49 | 3 |
| 50-74 | 6 |
| 75-100 | 52 |

## Categorias

| Categoria | Tentadas | Concluídas | Falhas | Sucesso | Com score | Coletados observados | Avaliados | Ignorados concluídas | Achados | Com achados | Score médio | Mediana |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| E-commerce | 20 | 9 | 11 | 45.00% | 9 | 2478 | 1836 | 33 | 188 | 8 | 91.11 | 90.00 |
| Educação | 20 | 15 | 5 | 75.00% | 15 | 2473 | 1772 | 461 | 257 | 14 | 85.87 | 87.00 |
| Empresas e instituições | 20 | 15 | 5 | 75.00% | 15 | 1672 | 1541 | 131 | 262 | 13 | 82.13 | 86.00 |
| Notícias e conteúdo | 20 | 12 | 8 | 60.00% | 12 | 5447 | 3919 | 128 | 274 | 12 | 92.67 | 96.50 |
| Serviços | 20 | 10 | 10 | 50.00% | 10 | 1974 | 1555 | 122 | 177 | 9 | 88.20 | 91.00 |

Todas as demais métricas gerais também estão calculadas por categoria em `study-statistics.json`.

## Falhas e diagnóstico

| Código | Quantidade |
|---|---:|
| INACCESSIBLE | 22 |
| QUALITY | 15 |
| BROWSER | 1 |
| TIMEOUT | 1 |

| Código | Fase | HTTP observado | Tipo de causa | Quantidade | IDs |
|---|---|---|---|---:|---|
| BROWSER | CLEANUP | 200 | com.microsoft.playwright.impl.TargetClosedError | 1 | KC-006 |
| INACCESSIBLE | NAVIGATION | 403 | Sem exceção técnica registrada | 20 | KC-001, KC-004, KC-007, KC-011, KC-016, KC-017, KC-034, KC-040, KC-052, KC-053, KC-063, KC-065, KC-068, KC-071, KC-072, KC-073, KC-076, KC-082, KC-085, KC-099 |
| INACCESSIBLE | NAVIGATION | 405 | Sem exceção técnica registrada | 1 | KC-094 |
| INACCESSIBLE | NAVIGATION | 500 | Sem exceção técnica registrada | 1 | KC-021 |
| QUALITY | QUALITY | 200 | Sem exceção técnica registrada | 15 | KC-003, KC-005, KC-012, KC-014, KC-022, KC-035, KC-043, KC-044, KC-055, KC-056, KC-058, KC-059, KC-066, KC-070, KC-074 |
| TIMEOUT | NAVIGATION | Não disponível | com.microsoft.playwright.TimeoutError | 1 | KC-083 |

Mensagens técnicas e agrupamentos completos estão em `failure-diagnostics.json` e no CSV. HTTP representa a última resposta principal observada; no bloqueio de redirect pode ser a resposta do host de origem, sem requisição ao destino proibido.

### Motivos QUALITY

| Motivo | Páginas |
|---|---:|
| CONTENT_NOT_STABLE | 12 |
| NO_VISIBLE_TEXT_AT_READINESS | 3 |
| ZERO_COLLECTED_ELEMENTS | 1 |
| UNIFORM_SCREENSHOT | 1 |

Uma página pode ter vários motivos QUALITY. HTTP 403/429 indica erro/bloqueio operacional observado; não é evidência de problema de acessibilidade. QUALITY indica insuficiência de qualidade/cobertura da coleta, não ausência de acessibilidade.

## Interpretação e integridade

Scores ausentes permanecem ausentes. Scores e achados usam somente análises concluídas; tempos usam todas as 100 linhas. Textos coletados em capturas de falhas não são apresentados como avaliados pelo Core. Páginas sem captura não são presumidas vazias e não fornecem um total conhecido de recursos bloqueados.
Truncamento delimita a amostra de texto: o score não descreve regiões fora da cobertura. Zero achados não certifica acessibilidade global. Os avisos de diferenciação de cores são heurísticos e não entram na fórmula do score.
O dataset é uma amostra intencional por quotas, sem representatividade estatística de todos os sites brasileiros. Não há referência humana para precision, recall ou F1. Os resultados refletem as condições desta execução e não foram calibrados por URL.
JSONs, PNGs, respostas originais, configuração, JAR, fontes e logs de validação estão separados do Run 1. Os dados foram recalculados independentemente e comparados aos totais da API, aos relatórios individuais e à fórmula existente. O CSV padrão da aplicação não foi modificado; a consolidação de pesquisa é separada.

**Entregue para revisão. README definitivo não reescrito.**

## Ressalva de integridade geométrica

Na verificação final, 454 arquivos protegidos permaneceram byte a byte iguais. Os hashes dos registros do Run 1 no PostgreSQL também permaneceram iguais: 1 estudo, 100 análises e 1.184 achados. A resposta pública do Run 1 foi comparada ao arquivo preservado e permaneceu igual. Dataset, JAR aprovado e fontes do scanner permaneceram congelados; o CSV final contém 100 linhas e 44 colunas verificadas contra as observações originais.

Dos 22 INACCESSIBLE, 20 têm HTTP 403, um HTTP 500 e um HTTP 405, todos na fase NAVIGATION. Essas respostas não demonstram que os sites estavam globalmente indisponíveis. O único TIMEOUT ocorreu em KC-083, na navegação, sem resposta HTTP preservada. Os 15 QUALITY têm HTTP 200. Dois casos registraram redirects principais validados e concluíram: UFRGS (KC-025, dois destinos) e JBS (KC-088, um destino).

A Natura (KC-006), classificada BROWSER na fase CLEANUP, tem cobertura declarada de 1280 × 4207 px e PNG observado de 1280 × 4130 px. Os arquivos e metadados originais permanecem preservados, sem correção retroativa. A causa da diferença não foi determinada; conteúdo dinâmico é uma hipótese, não uma conclusão.

Esse caso não produziu score ou achados. As 61 análises concluídas têm PNGs com dimensões correspondentes à cobertura declarada. A divergência está registrada em `screenshots-verification.json` e em `study-statistics.json`, campo `artifact_errors`.
