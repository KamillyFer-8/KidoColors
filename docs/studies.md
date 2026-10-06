# Processamento em lote — Fase 7

Esta fase implementa a ferramenta de processamento; não executa o estudo real. A coleta das aproximadamente 100 URLs depende de validação do detector, metodologia de amostragem e aprovação do dataset, nas fases seguintes.

## Entrada e execução

Use CSV UTF-8, separado por vírgulas, com exatamente as colunas `id,site,url,categoria`. A ordem das colunas pode mudar. BOM UTF-8 é aceito. Campos com vírgulas, aspas ou quebras de linha devem usar as regras CSV: envolver em aspas e duplicar aspas internas. O parser e a escrita usam [Apache Commons CSV, formato RFC4180](https://commons.apache.org/proper/commons-csv/apidocs/org/apache/commons/csv/CSVFormat.html).

`id`, `site` e `url` são obrigatórios; categoria vazia vira nula. Limites: 100 caracteres para id, 200 para site, 2048 para URL e 120 para categoria; arquivo até 1 MiB e no máximo 500 linhas de dados. IDs duplicados, cabeçalho incorreto, colunas extras, linhas vazias e UTF-8 inválido rejeitam o arquivo inteiro, antes de criar análises. URLs repetidas com IDs diferentes são processadas separadamente, sem deduplicação silenciosa. Erros de URL/protocolo/rede são falhas daquela linha, com continuidade das seguintes.

O endpoint administrativo local é síncrono. Use diretamente a API na porta 8080, sem passar pelo proxy Vite, cujo limite de espera é de 3 minutos. Requer a API, PostgreSQL e Chromium configurados conforme `backend/README.md`.

Os comandos abaixo são instruções para uma execução futura, após aprovação; não foram usados para executar o estudo nesta fase. No terminal PowerShell do VS Code, na raiz, com o CSV aprovado em `datasets/amostra-aprovada.csv`:

```powershell
# Esta operação inicia a coleta das URLs do arquivo.
curl.exe --fail-with-body --max-time 7200 -X POST http://localhost:8080/api/studies -F 'name=Estudo aprovado' -F 'file=@datasets/amostra-aprovada.csv;type=text/csv' -o results/lote.json

# Execute estas linhas somente se o POST terminou com sucesso.
$studyResult = Get-Content -Raw -Encoding UTF8 results/lote.json | ConvertFrom-Json
$studyId = $studyResult.id
curl.exe --fail-with-body "http://localhost:8080/api/studies/$studyId" -o results/lote.json
curl.exe --fail-with-body "http://localhost:8080/api/studies/$studyId/export.csv" -o results/paginas.csv
curl.exe --fail-with-body "http://localhost:8080/api/studies/$studyId/dataset.csv" -o results/dataset-original.csv
```

POST retorna 201 com `Location` e o JSON do lote salvo. `status=COMPLETED` significa que todas as linhas foram tentadas, mesmo quando há falhas de página. Confira `metrics.completed`, `metrics.failed` e `metrics.scored`. O GET por UUID também funciona durante a execução, caso o ID seja conhecido. Não há nova execução ao consultar ou exportar um lote.

Não reenviamos automaticamente o POST. Uma interrupção do cliente pode deixar o servidor processando, e reenviar o arquivo inicia outro lote. Não há fila, retomada automática ou chave de idempotência nesta V1. Registros RUNNING após queda do processo exigem conferência dos checkpoints antes de uma nova execução. Cada checkpoint salva os resultados já processados, mas não é uma transação única com a última análise: uma queda entre esses dois salvamentos pode deixar uma análise associada ao lote fora do snapshot de linhas. Uma falha ao persistir o próprio lote pode interromper a execução e resultar em erro HTTP.

## Persistência e rastreabilidade

`study_run` guarda UUID, nome, SHA-256 dos bytes originais do CSV, texto original, datas UTC, duração, status, ambiente e snapshots das linhas em JSON textual. O limite de 500 linhas mantém essa representação simples e limitada em tamanho. Os achados e capturas continuam nas tabelas/arquivos da análise individual; não duplicamos todos os textos ou imagens dentro do lote.

Cada análise criada pelo lote tem uma relação JPA opcional `analysis.study_run_id` com o lote. Linhas com URL rejeitada antes de criar a análise não têm analysis_id. Cada snapshot inclui id/site do dataset, URL solicitada, categoria, análise/links, resumo do motor, metadados da captura, duração e erro. A associação identifica também análises criadas antes de uma interrupção do checkpoint.

O ambiente registra Java, sistema operacional, versão Playwright, motor e parâmetros da coleta. Cada captura disponível registra o navegador efetivamente usado, URL final, viewport, altura, timeout, espera, recursos bloqueados e truncamento. Para reproduzir o estudo, mantenha o JSON, o dataset original e a revisão do código utilizada. O hash identifica exatamente os bytes importados, incluindo BOM e estilo de quebra de linha.

`dataset.csv` devolve o CSV original, sem normalização ou neutralização de células, para preservar sua identificação. `export.csv` é a versão de resultados destinada a planilhas: usa aspas CSV e prefixa apóstrofo em textos com possíveis fórmulas (`=`, `+`, `-`, `@`, inclusive após espaços) ou caracteres de controle. Essa neutralização altera somente a exportação; o JSON preserva os dados. O CSV de resultados inclui BOM UTF-8 para facilitar o reconhecimento de acentos.

## Duração e denominadores

O lote mede `durationMs` com `System.nanoTime()` após validar o CSV, antes de registrar ambiente e RUNNING, até salvar o estado final. Inclui processamento sequencial, serialização, checkpoints e persistência do estado final. A última atualização que grava a própria duração fica fora do intervalo. Não inclui envio do arquivo, validação do CSV ou geração posterior das exportações.

Cada linha mede seu próprio intervalo envolvendo validação de URL, chamada do serviço e recuperação do resumo/metadados. Não inclui o checkpoint do lote. `analysis.durationMs` mantém a duração da análise individual, com o mesmo significado usado na interface. Assim, `duracao_linha_ms` e `duracao_analise_ms` são distintos e explícitos. O tempo total do lote é medido separadamente, não calculado como soma das páginas.

- `totalRows`: linhas planejadas no CSV; `metrics.processed`: snapshots registrados. Em interrupção, não presumir que todas foram processadas.
- `completed`/`failed`: resultado do processamento da linha; sucesso exige análise COMPLETED sem erro da linha. Uma captura concluída sem texto avaliável pode ser sucesso operacional com score nulo.
- `successRate`: completed / processed, entre 0 e 1; nulo sem linhas processadas.
- `scored`: somente linhas concluídas com score não nulo. Score zero é um resultado válido.
- média, mediana, mínimo, máximo e distribuição de score: apenas `scored`; estatísticas nulas sem scores. Distribuição usa faixas 0–24, 25–49, 50–74 e 75–100.
- totais de textos, achados e contagens por tipo: linhas concluídas; média de achados usa completed como denominador.
- média/mínimo/máximo de duração: todas as linhas processadas, incluindo falhas; valores nulos sem linhas.
- `categories`: os mesmos cálculos, com denominadores por categoria. A chave vazia representa categoria ausente, evitando colisão com nomes fornecidos no dataset.

CSV contém uma linha por URL processada; JSON contém metadados, métricas gerais, métricas por categoria e snapshots. Valores ausentes ficam vazios no CSV e nulos no JSON; falhas não recebem score zero. Métricas de linhas rejeitadas ficam ausentes. Uma falha ao recuperar o relatório pode deixar uma análise COMPLETED no histórico e uma linha FAILED no lote; o snapshot conserva a análise e o erro para investigação.

## Limites de interpretação

Os avisos de diferenciação continuam sendo heurísticos, separados do score AA. Examine cobertura, textos ignorados, truncamento, recursos bloqueados e a versão do motor. As estatísticas não validam o detector nem demonstram representatividade da amostra. Fixtures nos testes não são dados experimentais e não são exportadas para `results/`.

## Testes

Testes verificam CSV com aspas, vírgulas, quebras de linha, BOM, limites, IDs repetidos, UTF-8 inválido e hash; métricas com scores zero/nulos, medianas e categorias; HTTP multipart, continuidade depois de URL inválida e timeout, mesma chamada de scanner, persistência JPA/H2, vínculo com análises, consulta e exportação. Scanner e imagens são simulados somente nos testes do lote; os testes existentes do scanner continuam usando páginas locais e Chromium real. A integração completa com PostgreSQL real permanece para a fase de execução integrada.
