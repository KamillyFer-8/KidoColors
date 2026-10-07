# Auditoria técnica do primeiro estudo do KidoColors

Data da auditoria (UTC): 2026-10-07T14:23:21.318133+00:00. Lote: `8411729c-fa79-4356-b6f9-bc0972fab79f`.
Escopo: leitura offline dos dados preservados, logs e implementação utilizada. Nenhuma navegação em websites, consulta à API/banco, nova análise, alteração do dataset, dos resultados, do scanner ou de README foi realizada. Os arquivos desta auditoria são documentos separados.

## Conclusão para decisão

Há limitações corrigíveis de cobertura, prontidão e diagnóstico no scanner V1. Não há evidência de corrupção do score nem de pane global do Chromium. Não é defensável explicar as 47 falhas como websites fora do ar, nem atribuir todas a redirecionamentos. A telemetria preservada é insuficiente para recuperar a causa raiz de 40 falhas e a exceção original da Natura.
Os dois BLOCKED e os quatro LIMIT_EXCEEDED são explicados pela política da V1. A Natura abriu e gerou screenshot antes do BROWSER. Entre os três COMPLETED sem score, Agência Brasil e Conta Azul tinham textos visíveis/coletados, mas todos foram excluídos pelo coletor; Correios produziu screenshot inteiramente vazia.
Antes de tratar os números como caracterização substantiva das páginas, convém decidir sobre melhorias de coleta e observabilidade. Se essas melhorias alterarem o scanner ou protocolo, uma coleta futura deverá ser um novo estudo completo das mesmas 100 entradas, identificado por nova execução/versão. O primeiro estudo permanece integral e não será substituído por reexecuções seletivas. Nenhuma correção ou reexecução está autorizada/realizada nesta auditoria.

## Fontes e limites de evidência

- `results/study-results.csv`, `results/study-statistics.json` e JSON bruto do lote: conferidos entre si para as 47 falhas.
- JSONs individuais e PNGs em `results/experimental-study-2026-10-07/raw/`.
- `.maven-cache/experimental-api.log`: log da API que executou o estudo; não contém stack traces/causas das falhas esperadas. Logs de testes pertencem à verificação técnica e não comprovam causas das falhas dos websites.
- Implementação `PlaywrightPageScanner.java`, `collect-text.js` e `AnalysisService.java`. O coletor JS coincide byte a byte com o JAR experimental preservado.
- `.maven-cache/dataset-preparation/http-evidence.json`: verificação HTTP anterior à execução. Serve de evidência histórica complementar; não prova o status ou redirecionamento durante a coleta e não preenche lacunas experimentais.
- Não foram encontrados HAR/trace/HTML/DOM/CSS computado originais dos erros. Status HTTP, cabeçalho Location, URL/recurso da falha, fase e exceção Playwright não estão nos resultados individuais.

## Quantidades e classificação

| Código persistido | Quantidade | Classificação principal conservadora |
|---|---:|---|
| INACCESSIBLE | 22 | Inconclusiva quanto à causa raiz |
| TIMEOUT | 18 | Inconclusiva quanto à causa raiz |
| LIMIT_EXCEEDED | 4 | Limitação conhecida da V1 |
| BLOCKED | 2 | Limitação conhecida da V1 |
| BROWSER | 1 | Possível bug do scanner; causa raiz inconclusiva |

Grupos principais, sem sobreposição: **6 limitações conhecidas da V1, 1 possível bug e 40 inconclusivas = 47**. Há **12 indícios históricos de negação HTTP 403** dentro das 22 INACCESSIBLE; essa etiqueta complementar não deve ser somada aos grupos principais. Proteção/antibot comprovada como causa do experimento: **0**; indisponibilidade natural externa comprovada como causa: **0**. Zero comprovado não significa que essas causas não ocorreram.

## INACCESSIBLE — 22

Todos os 22 têm a mensagem “Página inacessível ou resposta HTTP de erro.”. No scanner, ela é produzida por `response == null || response.status() >= 400` (linha 65). Portanto, os artefatos identificam esse ramo do código, mas não distinguem resposta nula, 403, 404, 429, 5xx ou outra resposta de erro. Nenhum desses 22 tem a mensagem específica de DNS ou de route.fetch que o código também consegue produzir.
**Redirecionamentos HTTP/HTTPS/www/outro host: 0 comprovados entre os INACCESSIBLE.** Redirecionamentos reconhecidos da navegação principal produzem BLOCKED nesta versão. Os dois redirecionamentos comprovados no estudo estão fora dessa categoria. Sem headers/traces não é possível provar que nenhum dos 22 sofreu uma cadeia ou comportamento dinâmico associado a redirecionamentos, nem reconstruir essa cadeia.
**Websites realmente fora do ar: 0 comprovados; quantidade real indeterminável.** Falha deste cliente não prova indisponibilidade pública. O estudo não preservou evidência suficiente de falha geral do servidor.
Cruzamento histórico por URL exata: **12 retornaram 403 antes do experimento; 5 retornaram 200; 5 não têm status HTTP histórico numérico nesta verificação** (quatro substituições posteriores e LATAM com timeout). Entre os 12 com 403: Magazine Luiza (KC-001), TerabyteShop (KC-004), Leroy Merlin (KC-007), Centauro (KC-011), Drogaria Araujo (KC-016), Panvel (KC-017), Intercept (KC-053), Itaú (KC-063), Santander (KC-065), Vivo (KC-068), Movida (KC-076) e Butantan (KC-099). É indício de negação ao cliente de consulta, não identificação de WAF/antibot ou status da coleta.
Exemplos com HTTP 200 anterior e INACCESSIBLE experimental: USP (KC-021), Senac SP (KC-040), GOL (KC-072), Vale (KC-082) e Siemens (KC-094). Essa mudança não autoriza concluir que o site saiu do ar ou que houve bug específico.

## TIMEOUT — 18

Todas as mensagens são “O carregamento da página excedeu o tempo limite.”. Durações das linhas: mínimo **17.813 s**, máximo **40.373 s**, média **23.1205 s**, mediana **21.4115 s**. Exemplos: Jornal do Comércio (KC-056), 17,813 s; g1 (KC-041), 40,373 s; Poder360 (KC-051), 32,364 s.
A configuração real é **15000 ms por operação**, `waitUntil=LOAD` e espera adicional fixa de **500 ms**. Há timeout em lançamento do Chromium, navegação, route.fetch, operações padrão e screenshot. DNS, inicialização/fechamento, outras operações, persistência e checkpoints não fazem desse valor um prazo rígido para a linha inteira. Uma duração total acima de 15 s não localiza a operação que expirou; 49 das 53 linhas COMPLETED também demoraram mais de 15 s.
O roteador intercepta todas as requisições e utiliza route.fetch antes de fulfill; LOAD depende da conclusão do carregamento, podendo envolver recursos externos. Lentidão de recursos, rede, página dinâmica e prontidão insuficiente são mecanismos possíveis. **Nenhum foi individualmente comprovado nas 18 linhas**, porque fase, recurso pendente e exceção original foram descartados. Histórico anterior: 15 desses sites responderam 200, ESPM respondeu 403 e dois não têm status histórico neste arquivo.
**O limite é razoável como orçamento operacional conservador da V1, mas não está validado como critério de indisponibilidade de websites reais.** Houve timeout em 18% da amostra, incluindo páginas com resposta histórica 200. Isso justifica avaliar protocolo de prontidão e limites por fase antes de uma nova coleta; não prova que elevar para um valor específico resolveria as falhas. Aumentar o tempo sozinho não resolve 403, redirecionamento bloqueado, altura ou exclusão de cores. Qualquer novo valor deve ser escolhido e documentado antes do novo estudo completo, sem calibrar só para melhorar sites que falharam.

## LIMIT_EXCEEDED — 4

Americanas (KC-003), C&A (KC-013), Folha (KC-042) e CartaCapital (KC-055) falharam com “A página excede o limite de altura da screenshot.”. A V1 mede `max(document.documentElement.scrollHeight, window.innerHeight)` e aborta se exceder **12000 px**, antes de coletar textos e gravar a captura (linhas 121–126). Não foi o limite de 2000 elementos. A altura exata não foi persistida, portanto só se pode concluir que o predicado de excesso foi atingido.
São quatro limitações determinísticas conhecidas da V1; página longa não é defeito do website nem falha de acessibilidade. Uma estratégia de captura limitada/segmentada com cobertura explícita seria mudança metodológica a avaliar, não correção executada aqui. O estado de truncamento existente não prova captura parcial nesses quatro casos: a operação abortou antes de produzir captura.

## BLOCKED — 2

UFRGS (KC-025, 2,510 s) e JBS (KC-088, 3,006 s) têm a mensagem explícita “Redirecionamento HTTP bloqueado nesta versão. Informe a URL final diretamente.”. O código usa `maxRedirects=0`, identifica HTTP 3xx diferente de 304 e rejeita a navegação. Não são os textos de rede privada, DNS ou requisição de escrita.
Histórico anterior: UFRGS foi de `https://www.ufrgs.br/` para `https://www.ufrgs.br/site/` (mudança de caminho); JBS foi de `https://jbs.com.br/` para `https://www.jbs.com.br:443/` (www e porta HTTPS explícita). Esses destinos são **históricos**, não cabeçalhos Location registrados no estudo. Nenhuma transição HTTP→HTTPS foi comprovada nesses dois casos; as entradas já são HTTPS. Rejeitar redirecionamentos é uma decisão defensiva conhecida da V1, corrigível somente com validação de rede a cada salto; não se deve simplesmente desativar a proteção.

## BROWSER — 1: Natura (KC-006)

Duração da linha: **8,623 s**. Existe um PNG original **1280×4207** mostrando cabeçalho, produtos e campanha da Natura. É o único caso FAILED com imagem preservada. O JSON de análise não tem captureUrl/reportUrl, metadados ou contagens. Não existe stack trace da exceção original no log da coleta.
Isso comprova que o navegador abriu a página, executou a captura e gravou o PNG. No fluxo do código, depois de store.write ainda ocorre `page.url()` e o fechamento de BrowserContext/Browser/Playwright ao sair do try-with-resources. Uma PlaywrightException nessas etapas pode transformar uma captura feita em BROWSER antes de AnalysisService receber/persistir o PageCapture. **Não é possível escolher a etapa exata ou provar a causa sem a exceção original.**
A mensagem “Não foi possível abrir a página” é insuficiente e contradiz a etapa demonstrada pela imagem. Classificação: **possível bug de ciclo de vida/propagação/classificação do scanner**, e lacuna comprovada de diagnóstico. Não é evidência de Chromium ausente: a imagem e as outras 53 análises concluídas demonstram disponibilidade do navegador. A captura também mostra conteúdo parcialmente carregado, sem permitir atribuir a falha a isso.

## COMPLETED com zero avaliados e score nulo

| ID / site | Textos coletados | Avaliados | Ignorados | Requisições bloqueadas | Evidência |
|---|---:|---:|---:|---:|---|
| KC-046 / Agência Brasil | 242 | 0 | 242 | 12 | {'BACKGROUND_IMAGE_OR_GRADIENT': 242} |
| KC-074 / Correios | 0 | 0 | 0 | 7 | Nenhum texto no coletor |
| KC-080 / Conta Azul | 277 | 0 | 277 | 0 | {'UNSUPPORTED_COLOR_SPACE': 277} |

**Agência Brasil:** screenshot mostra homepage com textos. Todos os 242 foram marcados BACKGROUND_IMAGE_OR_GRADIENT e tiveram cores nulas. O coletor marca o motivo ao encontrar backgroundImage em qualquer ancestral e não remove esse motivo ao encontrar um fundo opaco posterior. Essa política pode excluir textos mesmo quando uma camada opaca elimina a influência de uma imagem ancestral. Há evidência de exclusão total e comportamento conservador amplo; o CSS/ancestral exato não foi preservado, portanto a influência real de cada fundo não pode ser reconstruída.
**Conta Azul:** screenshot mostra homepage com textos; todos os 277 foram marcados UNSUPPORTED_COLOR_SPACE, com cores nulas. O parser aceita apenas rgb()/rgba() com componentes separados por vírgulas; outros formatos de cor ou uma cor de ancestral não reconhecida invalidam o texto. O valor CSS original não foi salvo, logo não é possível identificar qual sintaxe disparou isso. É limitação comprovada de cobertura da V1, não site sem conteúdo nem erro na fórmula do score.
**Correios:** zero textos coletados; o PNG 1662×4391 é uniformemente RGB(250,250,250), com uma única cor nos 7.297.842 pixels. Sete requisições foram bloqueadas, sem URLs/motivos individuais. Isso comprova captura vazia, não ausência de texto no website. Carregamento incompleto, conteúdo oculto, renderização tardia, efeito de recursos bloqueados ou estruturas que o TreeWalker não percorre são hipóteses; nenhuma causa isolada é comprovada. A V1 coleta document.body e não percorre automaticamente documentos de iframes ou árvores shadow DOM.
Classificação dos três casos, separada das 47 falhas: **2 limitações conhecidas de cobertura do coletor (Agência Brasil/Conta Azul)** e **1 possível falha de prontidão/controle de qualidade com causa inconclusiva (Correios)**. Score nulo é o comportamento correto quando não há texto avaliável. O problema é cobertura/qualidade da captura e a interpretação de COMPLETED, não um score que precise ser ajustado.

## Problemas corrigíveis e limitações naturais

| Observação | Evidência / decisão técnica |
|---|---|
| Diagnóstico insuficiente | Comprovado: status/Location/fase/cause e motivos por recurso não persistidos; ScanException é convertida em código/mensagem sem logar cause. Melhorar observabilidade não exige inventar dados retroativos. |
| Redirecionamentos e páginas longas | 6 falhas comprovadas por políticas da V1; requerem protocolo explícito e preservação de segurança/cobertura. |
| Prontidão LOAD + 500 ms | Configuração comprovada; screenshot vazia em Correios e timeout em 18 páginas justificam avaliação. Não há tempos de prontidão/DOM por fase para determinar a solução. |
| Exclusão ampla de backgrounds e cores | 519 textos coletados e todos descartados em dois sites. Há fragilidade sistêmica de cobertura, sem evidência de erro na fórmula. |
| BROWSER após PNG | Evidência de fase posterior à gravação; possível falha de cleanup/propagação; causa raiz ausente. |
| Variabilidade externa | 403/429, WAF/antibot, TLS/DNS, geolocalização, consentimento, conteúdo dinâmico e recursos de terceiros são condições que uma ferramenta deve registrar. Sua existência possível não prova ocorrência neste lote. Não contornar login, CAPTCHA ou proteções. |
Não há base para prometer 100% de sucesso nem para chamar toda falha externa de bug. Há base para considerar uma revisão sistemática da V1 antes de decidir sobre uma nova coleta completa. O primeiro lote mantém status, scores, falhas, hashes e denominadores originais.

## Índice das 47 falhas

Detalhes de evidência e cruzamento histórico estão no [CSV da auditoria](study-failure-audit.csv). Este CSV não substitui `results/study-results.csv`.

| ID | Site | Código | Duração da linha (s) | Grupo principal | JSON original |
|---|---|---|---:|---|---|
| KC-001 | Magazine Luiza | INACCESSIBLE | 12.183 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/4b5fc3c7-9b8a-4046-97bb-c2bec281de9e-analysis.json) |
| KC-003 | Americanas | LIMIT_EXCEEDED | 11.415 | limitação conhecida da V1 | [registro](../results/experimental-study-2026-10-07/raw/be0cc785-17cd-4dfa-be3d-88269d0920e2-analysis.json) |
| KC-004 | TerabyteShop | INACCESSIBLE | 2.898 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/a9af4f22-852e-408a-93fe-b7a8c6ba9b88-analysis.json) |
| KC-005 | Tramontina Store | TIMEOUT | 19.011 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/8c314465-fc68-49f5-9b5f-f1df6e3ea0a0-analysis.json) |
| KC-006 | Natura | BROWSER | 8.623 | possível bug do scanner | [registro](../results/experimental-study-2026-10-07/raw/1d040bec-747f-4d80-9a3b-fe0382581794-analysis.json) |
| KC-007 | Leroy Merlin Brasil | INACCESSIBLE | 3.912 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/30b1c2b1-3d93-4b8f-a001-686426560913-analysis.json) |
| KC-011 | Centauro | INACCESSIBLE | 2.120 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/3fb2c72b-083c-4dba-a883-7c5bd86e207d-analysis.json) |
| KC-013 | C&A Brasil | LIMIT_EXCEEDED | 14.743 | limitação conhecida da V1 | [registro](../results/experimental-study-2026-10-07/raw/a1862cc2-24ba-44a1-b406-2720eb377f48-analysis.json) |
| KC-016 | Drogaria Araujo | INACCESSIBLE | 2.420 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/607516f1-5f4c-46c0-bdb8-8d5645ba08d6-analysis.json) |
| KC-017 | Panvel | INACCESSIBLE | 2.668 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/f14be9d0-bd36-4093-b953-9b251fee6cb7-analysis.json) |
| KC-020 | Wine | TIMEOUT | 26.339 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/9e3ae4b0-4ce3-4054-a527-46ed0f92ea46-analysis.json) |
| KC-021 | Universidade de São Paulo | INACCESSIBLE | 2.472 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/6c89e053-6538-4f3d-b82c-c0f379708c1b-analysis.json) |
| KC-025 | Universidade Federal do Rio Grande do Sul | BLOCKED | 2.510 | limitação conhecida da V1 | [registro](../results/experimental-study-2026-10-07/raw/bf703def-a659-4dfa-9d3f-e72e10e99aae-analysis.json) |
| KC-026 | Universidade Federal da Bahia | TIMEOUT | 20.369 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/0aa64a55-245a-471e-91ae-e247e70b304d-analysis.json) |
| KC-028 | Universidade Federal do Ceará | TIMEOUT | 22.128 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/b3a99962-ddc2-4da8-ac1a-b6f847b96b26-analysis.json) |
| KC-029 | Universidade Federal do Pará | TIMEOUT | 19.780 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/79a452e8-ef21-454f-9706-5bd199982405-analysis.json) |
| KC-032 | Universidade Presbiteriana Mackenzie | TIMEOUT | 21.970 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/435995aa-09e3-4e87-9e70-6caa2bf3547c-analysis.json) |
| KC-034 | Universidade Cruzeiro do Sul | INACCESSIBLE | 3.640 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/741d8e36-a799-41e7-a2c0-a7ae43506ec3-analysis.json) |
| KC-035 | ESPM | TIMEOUT | 18.955 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/30d88436-f92b-4612-8be8-bf11da99450f-analysis.json) |
| KC-040 | Senac São Paulo | INACCESSIBLE | 2.276 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/1c03ee8e-facd-4a62-abfb-a509fce05556-analysis.json) |
| KC-041 | g1 | TIMEOUT | 40.373 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/b4f65473-279d-462b-923a-872b29ddebd6-analysis.json) |
| KC-042 | Folha de S.Paulo | LIMIT_EXCEEDED | 16.498 | limitação conhecida da V1 | [registro](../results/experimental-study-2026-10-07/raw/b5d71b8f-259c-4a17-9fc3-05bca9f958f9-analysis.json) |
| KC-043 | Estadão | TIMEOUT | 20.674 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/2e4bb4b3-6536-48d6-ba3d-9209555acdf5-analysis.json) |
| KC-047 | O TEMPO | TIMEOUT | 23.344 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/31de9281-e4de-4512-9e6f-87b9228f1c3c-analysis.json) |
| KC-048 | Agência Pública | TIMEOUT | 21.510 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/e66e8f41-116a-43ad-b174-2f3c486f9263-analysis.json) |
| KC-051 | Poder360 | TIMEOUT | 32.364 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/627bb654-b153-494d-86fc-2419cc3a94f0-analysis.json) |
| KC-052 | Jornal Opção | INACCESSIBLE | 3.173 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/79bbf897-e654-4632-8dd3-c988dbc174c8-analysis.json) |
| KC-053 | Intercept Brasil | INACCESSIBLE | 3.165 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/dfad1bb8-e27a-48af-b31e-31f8daf8607e-analysis.json) |
| KC-055 | CartaCapital | LIMIT_EXCEEDED | 7.334 | limitação conhecida da V1 | [registro](../results/experimental-study-2026-10-07/raw/b7f40012-33aa-4c1f-ac15-94a0da56ce49-analysis.json) |
| KC-056 | Jornal do Comércio (RS) | TIMEOUT | 17.813 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/3c916916-52d2-4677-b00d-2b69aa9a13b2-analysis.json) |
| KC-058 | Opera Mundi | TIMEOUT | 31.675 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/1dbcdab4-d3d6-4cfb-a7f3-d47d6d629ba7-analysis.json) |
| KC-060 | Correio Braziliense | TIMEOUT | 22.479 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/1f41da4f-4913-4630-adf6-718844437335-analysis.json) |
| KC-063 | Itaú | INACCESSIBLE | 5.607 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/3fc0e6cb-abd6-46f5-97eb-7779e405d4c0-analysis.json) |
| KC-065 | Santander Brasil | INACCESSIBLE | 2.503 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/f37c4fcf-7d0e-42cd-8ce7-ccc68efd901a-analysis.json) |
| KC-068 | Vivo | INACCESSIBLE | 5.599 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/c2a6170d-efe0-41e6-b6a4-6e10667ccf35-analysis.json) |
| KC-071 | LATAM Brasil | INACCESSIBLE | 5.083 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/ed90bd6e-d386-494f-9316-e0e868cbd4be-analysis.json) |
| KC-072 | GOL | INACCESSIBLE | 2.506 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/f9f65310-f91c-4dea-a107-a61e0346e119-analysis.json) |
| KC-073 | Neoenergia | INACCESSIBLE | 2.576 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/492f2871-a608-4c6a-b2cb-2131aa855ea5-analysis.json) |
| KC-076 | Movida | INACCESSIBLE | 3.555 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/527e256c-2628-4615-92e0-f413ce53723e-analysis.json) |
| KC-081 | Petrobras | TIMEOUT | 21.313 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/4201d066-ec2a-4a1e-8274-aaf982e09d5e-analysis.json) |
| KC-082 | Vale | INACCESSIBLE | 3.136 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/14f95238-38ec-4f74-a350-930024ad623b-analysis.json) |
| KC-084 | BNDES | TIMEOUT | 18.239 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/95a6bad5-d9e4-44dc-ad56-40d55a9a9996-analysis.json) |
| KC-085 | Randoncorp | INACCESSIBLE | 2.834 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/e8354a14-f998-42c7-9efb-93ba3b0f9a18-analysis.json) |
| KC-088 | JBS | BLOCKED | 3.006 | limitação conhecida da V1 | [registro](../results/experimental-study-2026-10-07/raw/b4c7f95e-b5fa-44b2-8edb-9cb9cd3b85ef-analysis.json) |
| KC-094 | Siemens Brasil | INACCESSIBLE | 4.199 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/e8f71a87-f7f3-4bcb-9f9a-2ae050a676c6-analysis.json) |
| KC-098 | Embrapa | TIMEOUT | 17.833 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/ab9af877-753e-4ebd-95a9-7f1e9c71d753-analysis.json) |
| KC-099 | Instituto Butantan | INACCESSIBLE | 3.017 | inconclusiva | [registro](../results/experimental-study-2026-10-07/raw/5449682f-7f2b-497a-8115-15051d7fa634-analysis.json) |

## Integridade

Antes e depois desta auditoria foram conferidos os SHA-256 de **444 arquivos** do primeiro estudo, resultados consolidados, dataset e READMEs. Todos permaneceram iguais. Também foi conferido o inventário original de artefatos. Nenhuma nova coleta foi iniciada e nenhum resultado foi reclassificado nos arquivos originais.
