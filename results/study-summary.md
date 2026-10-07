# Estudo experimental real do KidoColors

Lote: `8411729c-fa79-4356-b6f9-bc0972fab79f`. Estado persistido: `COMPLETED`.
Coleta UTC: 2026-10-07T13:37:02.487027Z a 2026-10-07T14:10:23.399503Z.
Revisão do código: `9fff8a80a79a3f7d411220f85bbf670eb8b161c5`.

## Verificação antes da coleta

Passaram 27 testes do Core, 73 do backend e 24 do frontend, além de lint e build do frontend. Maven verify gerou o JAR usado. API e PostgreSQL/Supabase responderam UP antes do único POST do lote. Os logs estão no diretório preflight.

## Resultados gerais

| Medida | Resultado |
|---|---|
| URLs previstas e processadas | 100 / 100 |
| Concluídas / falhas | 53 / 47 |
| Taxa de sucesso operacional | 53.00% (denominador: 100 tentadas) |
| Páginas com score / concluídas sem score | 50 / 3 |
| Elementos de texto avaliados | 8262 |
| Achados totais | 1184 |
| Páginas concluídas com pelo menos um achado | 43 / 53 (81.13%) |
| Score médio / mediana | 85.84 / 91.00 |
| Menor / maior score | 35 / 100 |
| Média de achados por página concluída | 22.34 |
| Falhas de contraste | 1175 |
| Avisos de protanopia / deuteranopia / tritanopia | 4 / 2 / 3 |
| Tempo total do lote | 2000.93 s |
| Tempo médio / mínimo / máximo por linha | 19.67 / 2.12 / 69.76 s |

## Distribuição dos scores

| Faixa | Páginas |
|---|---|
| 0-24 | 0 |
| 25-49 | 4 |
| 50-74 | 7 |
| 75-100 | 39 |

## Resultados por categoria

| Categoria | Tentadas | Concluídas | Falhas | Sucesso | Com achados | Elementos | Achados | Score médio | Mediana |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| E-commerce | 20 | 9 | 11 | 45.00% | 8 | 2226 | 418 | 85.00 | 89.00 |
| Educação | 20 | 11 | 9 | 55.00% | 9 | 1383 | 170 | 89.36 | 94.00 |
| Empresas e instituições | 20 | 12 | 8 | 60.00% | 10 | 1518 | 290 | 80.08 | 87.50 |
| Notícias e conteúdo | 20 | 8 | 12 | 40.00% | 7 | 1353 | 154 | 89.43 | 97.00 |
| Serviços | 20 | 13 | 7 | 65.00% | 9 | 1782 | 152 | 87.00 | 98.00 |

## Falhas

| Código | Quantidade |
|---|---:|
| INACCESSIBLE | 22 |
| LIMIT_EXCEEDED | 4 |
| TIMEOUT | 18 |
| BROWSER | 1 |
| BLOCKED | 2 |

Os IDs, URLs, mensagens de erro e durações de todas as falhas estão em `study-results.csv` e no JSON bruto. Nenhuma URL foi substituída ou seletivamente repetida.

## Cobertura e interpretação

Textos ignorados: 1613. Páginas concluídas com coleta truncada: 0; comparação heurística limitada: 0; recursos bloqueados: 48.
COMPLETED indica conclusão técnica, não certificação de acessibilidade. Score nulo não foi convertido em zero. Estatísticas de score usam apenas páginas concluídas com score; achados usam páginas concluídas; tempos por linha incluem falhas.
Agência Brasil (KC-046), Correios (KC-074) e Conta Azul (KC-080) terminaram COMPLETED com zero elementos avaliáveis e score nulo. Esses três casos não comprovam ausência de problemas. Não houve preenchimento dos valores ausentes.
O score mede contraste AA de textos avaliáveis. Os avisos de diferenciação de cores são heurísticos e não entram na fórmula do score. Achados totais somam falhas de contraste e avisos; não equivalem a elementos únicos.
Redirecionamentos HTTP são bloqueados pelo scanner existente. Recursos bloqueados, telas intermediárias, conteúdo dinâmico e páginas truncadas podem afetar a cobertura. Nenhuma medida disponível identifica automaticamente que a captura corresponde a todo o conteúdo principal.
Esta amostra intencional por quotas não representa todos os websites brasileiros. Não foram calculadas precision, recall ou F1: não há rotulagem humana de referência. Tempos são os intervalos medidos pelo sistema, não estimativas.

## Integridade e arquivos

SHA-256 da seleção original de oito colunas: `ce305c5a482ad103501f462d78821694a00061fc2fb8f55fe789bc5f2f8e8900`.
SHA-256 da projeção importada de quatro colunas: `17ba95f31e0431bcde739e65dd35036d0265c0750a7b187d85ba9505f46668a2`.
O dataset original CSV/XLSX e a projeção importada estão preservados em `experimental-study-2026-10-07/`. A exportação original da API está em `application-study-export.csv`, sem mudança de contrato. `study-results.csv` é uma consolidação separada para pesquisa.
Conferência do contrato existente: quatro colunas correspondem à entrada do importador e ao endpoint dataset.csv. O export.csv de resultados do lote já tem 33 colunas no código original; nenhuma coluna ou implementação dessa exportação foi alterada.
O JSON persistido do lote, respostas individuais, relatórios, capturas e PNGs disponíveis estão preservados nesse diretório. `study-statistics.json` contém as métricas gerais e por categoria, ambiente e validações independentes.
Falhas no download de artefatos: 0; detalhes preservados em `study-statistics.json`.
As estatísticas foram recalculadas independentemente e comparadas às métricas persistidas. IDs/URLs/categorias e hashes de entrada foram conferidos. Testes e verificação técnica anteriores não integram o lote experimental.

**Resultados entregues para revisão. O README definitivo não foi reescrito.**
