# Comparação técnica — Run 1 / V1 × Run 2 / V2

Piloto `8411729c-fa79-4356-b6f9-bc0972fab79f`; Run 2 `338883f9-ec46-40f5-8e49-4bef7529a44e`. Os mesmos 100 IDs/URLs/sites/categorias e a mesma ordem foram confirmados.
A fórmula do score não mudou; prontidão, cobertura, suporte de cores/redirects e critérios de validade mudaram. Esta comparação mostra capacidade operacional e diagnóstico, não melhora/piora de acessibilidade dos websites. As execuções ocorreram em momentos diferentes e não isolam causalmente todas as diferenças de rede/conteúdo.

| Medida | Run 1 / V1 | Run 2 / V2 |
|---|---:|---:|
| Tentadas | 100 | 100 |
| Concluídas | 53 | 61 |
| Falhas | 47 | 39 |
| Sucesso operacional | 53.00% | 61.00% |
| Com score | 50 | 61 |
| Concluídas sem score | 3 | 0 |
| Textos avaliados | 8262 | 10623 |
| Capturas com metadados, incluindo falhas | 53 | 77 |
| Execuções com novo diagnóstico estruturado | 0 | 100 |
| Falhas com status HTTP preservado | 0 | 38 |
| Truncamento registrado em capturas | 0 | 74 |
| Tempo total | 2000.93 s | 2335.67 s |
| Tempo médio por linha, incluindo falhas | 19.67 s | 23.00 s |
| Score médio, separado por protocolo/coorte | 85.84 (50 páginas) | 87.44 (61 páginas) |
| Mediana do score, separada por protocolo | 91.00 | 90.00 |

## Distribuição de falhas

| Código | Run 1 | Run 2 |
|---|---:|---:|
| BLOCKED | 2 | 0 |
| BROWSER | 1 | 1 |
| INACCESSIBLE | 22 | 22 |
| LIMIT_EXCEEDED | 4 | 0 |
| QUALITY | 0 | 15 |
| TIMEOUT | 18 | 1 |

## Sucesso operacional por categoria

| Categoria | Run 1 concluídas/20 | Run 2 concluídas/20 |
|---|---:|---:|
| E-commerce | 9 | 9 |
| Educação | 11 | 15 |
| Empresas e instituições | 12 | 15 |
| Notícias e conteúdo | 8 | 12 |
| Serviços | 13 | 10 |

## Transições de conclusão técnica nas mesmas URLs

| Situação | Páginas |
|---|---:|
| Concluídas nos dois Runs | 44 |
| Concluídas somente no Run 1 | 9 |
| Concluídas somente no Run 2 | 17 |
| Falharam nos dois Runs | 30 |

Essas transições são descritivas: critérios de conclusão e condições externas diferem. Não atribuímos cada transição exclusivamente à mudança do scanner.

## Cobertura e diagnóstico

V1 bloqueava redirects HTTP e abortava documentos acima de 12.000 px antes de coletar. V2 valida destinos e usa uma região superior limitada de coleta/captura. Mais páginas com truncamento não significam piora: a V1 frequentemente não produzia uma captura nesses casos. Não existe medição suficiente para comparar o percentual de todo o DOM coberto em ambos os Runs.
V1 usava LOAD + 500 ms e timeout de 15 s. V2 usa DOMCONTENTLOADED, orçamento de navegação de 30 s e prontidão limitada a 5 s com 500 ms de estabilidade. Tempos também incluem rede, renderização, limpeza, persistência e conteúdo variável; não são benchmarks isolados do algoritmo.
V1 permitiu três COMPLETED sem score (Agência Brasil, Correios e Conta Azul). V2 rejeita coleta sem texto avaliável/qualidade suficiente. Por isso os denominadores de conclusão têm critérios diferentes. Os scores são apresentados separadamente, sem média combinada e sem atribuir suas diferenças a mudanças na acessibilidade dos sites.
V1 não preservava status HTTP, fase ou causa técnica nos erros. V2 os registra quando disponíveis e preserva capturas de falhas após coleta. Falhas anteriores à resposta continuam sem status HTTP, pois o dado não existe.

## Casos de interesse do piloto

| ID | Site | Run 1 | Run 2 | Fase V2 | HTTP V2 |
|---|---|---|---|---|
| KC-006 | Natura | BROWSER | BROWSER | CLEANUP | 200 |
| KC-025 | Universidade Federal do Rio Grande do Sul | BLOCKED | COMPLETED | COMPLETE | 200 |
| KC-046 | Agência Brasil | COMPLETED | COMPLETED | COMPLETE | 200 |
| KC-074 | Correios | COMPLETED | QUALITY | QUALITY | 200 |
| KC-080 | Conta Azul | COMPLETED | COMPLETED | COMPLETE | 200 |
| KC-088 | JBS | BLOCKED | COMPLETED | COMPLETE | 200 |

Os detalhes desses casos são observações desta execução; mudanças não demonstram uma causa isolada nem justificam substituição de resultados do piloto.

Run 1 permanece separado e preservado. Run 2 foi executado integralmente, sem reexecução seletiva ou ajustes durante o lote. README definitivo aguarda revisão.

Ressalva: na falha BROWSER da Natura, a cobertura declarada é 4207 px e a altura do PNG é 4130 px. Não há score/achados nesse caso. A divergência foi preservada e registrada; todas as capturas de análises concluídas correspondem às dimensões declaradas. Não foi identificada evidência de invalidade sistêmica dos scores do Run 2 nesta verificação, embora o problema de cleanup da Natura continue pendente.

Observações verificáveis: UFRGS concluiu após dois redirects registrados e JBS após um, ambos HTTP 200. Agência Brasil passou a ter 121 textos avaliados e score 100; Conta Azul, 217 avaliados e score 84. Correios recebeu QUALITY e permaneceu sem score. Esses números descrevem a cobertura e os critérios do V2 nesta execução; não certificam acessibilidade total nem substituem os resultados do piloto.

Integridade final confirmada em `integrity-verification.json`: 454 arquivos protegidos e os hashes dos 1.285 registros do piloto no PostgreSQL permaneceram iguais. Fontes, JAR e dataset congelados também foram verificados. Os 100 IDs de análises do Run 2 são distintos, com uma única submissão do lote e nenhuma reexecução seletiva.
