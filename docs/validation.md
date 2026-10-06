# Validação manual — protocolo inicial v1

Esta fase entrega o protocolo, um template vazio e o cálculo offline das métricas. **A validação empírica ainda não foi realizada.** Não há rótulos reais, precision/recall/F1 ou conclusões sobre eficácia. O estudo de aproximadamente 100 URLs continua condicionado à validação e aprovação do dataset.

## Amostra e unidade

Proposta inicial: 12 páginas públicas, quatro categorias com três páginas cada. É um plano, não uma lista de URLs ou amostra representativa da web. Selecione e aprove a amostra antes de observar scores/achados; registre critérios, categorias e exclusões. Não escolha páginas depois de descobrir resultados favoráveis.

Para limitar o trabalho manual, fixe o recorte do documento principal na primeira viewport de 1280 × 720 px, sem rolagem. Inclua cada nó de texto visível cuja bounding box intersecte essa região. Desconsidere previsões fora dela. Isso avalia um recorte, não a captura inteira. Mudanças do recorte exigem nova versão do protocolo, sem selecionar trechos após ver resultados. Registre conteúdo fora da unidade suportada (iframe, canvas, pseudo-elementos, shadow DOM) como limitação de cobertura.

Preserve screenshot original, JSON de `/capture` e `/report`, analysis_id, URL solicitada/final, data, navegador, viewport, espera, timeout, versão do motor, revisão Git e condições de consentimento/rede. Evidência manual deve corresponder ao mesmo conteúdo e estado da captura: uma página que mudou posteriormente não é ground truth dessa análise. Se não conseguir conferir a sessão observada, descarte a sessão com justificativa e refaça a coleta.

## Referência independente

Faça um inventário manual sem consultar score ou lista de achados. Não copie a lista do scanner como inventário: isso esconderia textos não detectados e inflaria recall. A unidade é um nó de texto visível, não um elemento agregado ou uma página. Atribua unit_id único dentro de cada análise e registre texto, seletor, índice do nó no elemento, bounding box e arquivo de evidência. Não conte o texto agregado de um parágrafo e seus filhos inline novamente.

Registre cores efetivas, tamanho/peso, transparência e fundo. Determine o contraste com cálculo independente do KidoColors, sem arredondar a razão para decidir. AA exige 4,5:1 para texto normal e 3:1 para texto grande (24 px ou 14 pt com peso >= 700). Consulte [critérios e referências](methodology.md), mas não use a classificação do motor como referência humana.

- `reference=FAIL`: falha AA confirmada no contexto.
- `reference=PASS`: contraste AA confirmado no contexto.
- `reference=EXCLUDED`: exceção normativa ou referência incerta; motivo obrigatório. Não converter dúvida em PASS.

Considere exceções como logos, texto incidental e componentes inativos. Não exclua automaticamente fundos complexos: se uma revisão independente confirma a falha, use FAIL, mesmo que o motor ignore o texto. Quando não houver referência confiável, exclua e explique.

Guarde a referência inicial com hash, data e revisor, antes de anexar previsões. Preferencialmente faça uma segunda revisão independente e adjudique divergências; se houver só um revisor, declare essa limitação. Não use IA para fabricar rótulos ou evidência. Correções do inventário após observar previsões exigem registro da alteração, preservação dos hashes anteriores e revisão independente.

## Associação das previsões

Depois de finalizar a referência, associe cada unidade da mesma sessão por texto, seletor, textNodeIndex e posição. Seletor sozinho pode ser ambíguo. Não transforme um achado duplicado em dois TP. Resolva associações ambíguas antes de calcular métricas; exclusões devem ter motivo explícito.

- `detector=FAIL`: coletada com achado CONTRAST.
- `detector=PASS`: coletada com cores avaliáveis, sem achado CONTRAST.
- `detector=SKIPPED`: coletada com motivo de não avaliação.
- `detector=MISSING`: no inventário humano, ausente da coleta.

Ausência na lista de problemas não significa PASS. Consulte `/capture` para distinguir avaliação, exclusão e perda. Confira também todos os achados CONTRAST dentro do recorte contra o inventário para não omitir falsos positivos. Avisos COLOR_DIFFERENTIATION não são previsões de falha de contraste.

## CSV e comando offline

Copie `datasets/validation/labels-template.csv` para `labels-real.csv`. O template contém somente cabeçalho; o avaliador o rejeita enquanto estiver vazio.

| Coluna | Conteúdo obrigatório |
|---|---|
| analysis_id | UUID da sessão analisada |
| unit_id | Identificador humano único nessa sessão |
| reference | FAIL, PASS ou EXCLUDED |
| detector | FAIL, PASS, SKIPPED ou MISSING |
| reviewer | Identificação do revisor |
| evidence | Arquivo/identificador da evidência e justificativa |

CSV UTF-8 com vírgulas, aspas CSV para campos compostos e BOM opcional. Use exatamente essas colunas em qualquer ordem. Todos os campos são obrigatórios, até 4.000 caracteres; limites de 1 MiB e 10.000 unidades. Duplicatas são rejeitadas. O arquivo preenchido não comprova por si só autenticidade ou completude dos rótulos.

Após revisar rótulos reais, no terminal PowerShell do VS Code, na raiz:

```powershell
$mavenCache = Join-Path (Get-Location).Path '.maven-cache'
mvn.cmd -B -ntp "-Dmaven.repo.local=$mavenCache" '-DskipTests' install
mvn.cmd -B -ntp -f backend/pom.xml "-Dmaven.repo.local=$mavenCache" exec:java '-Dexec.mainClass=dev.kidocolors.backend.validation.ValidationCli' '-Dexec.args=datasets/validation/labels-real.csv results/validation-real.json'
```

O comando não inicia Spring, navegador ou banco. Escreve JSON com hash do CSV, contagens, cobertura e métricas, sem sobrescrever arquivo existente. Preserve protocolo, revisão Git, evidências e decisões de revisão. O programa verifica formato e consistência básica, não a existência de analysis_id no banco ou a correção das previsões transcritas: essa conferência faz parte da revisão humana.

## Denominadores e métricas

O alvo positivo é **falha de contraste AA**.

| Referência | Detector | Detecção no conjunto rotulado |
|---|---|---|
| FAIL | FAIL | TP |
| PASS | FAIL | FP |
| FAIL | PASS / MISSING / SKIPPED | FN |
| PASS | PASS / MISSING / SKIPPED | TN |
| EXCLUDED | Qualquer | Fora da matriz, exclusão informada |

`endToEnd` mede alertas de falha no conjunto rotulado. Ausência de alerta sobre referência PASS é TN para essa tarefa; **não significa que o motor avaliou ou aprovou o texto**. Perdas e textos ignorados são informados separadamente. Uma falha confirmada manualmente que não foi coletada é FN.

`classifiedOnly` considera apenas detector FAIL/PASS. Examina a classificação nos casos resolvidos, sem substituir a medida que inclui perdas. `classifiedCoverage` = unidades FAIL/PASS do detector entre referências elegíveis / referências FAIL/PASS. Sem referências elegíveis, cobertura nula.

```text
precision = TP / (TP + FP)
recall = TP / (TP + FN)
F1 = 2 TP / (2 TP + FP + FN)
```

Denominador zero produz nulo. F1 pode ser zero com FN/FP mesmo quando precision é indefinida; usamos a fórmula direta. Valores vão de 0 a 1. Sem positivos de referência, recall é indefinido. Publique também TP/FP/FN/TN, escopo, número de análises, exclusões, perdas, cobertura e exemplos de erros.

Unidades da mesma página são correlacionadas; esses números não demonstram representatividade da web ou precisão por website. Não há meta arbitrária de aprovação. Antes do estudo maior, revise erros sistemáticos, divergências humanas e exclusões. Se ajustar o motor usando essa amostra, avalie a versão alterada numa amostra independente, separada da amostra de ajuste.

## Heurística de diferenciação

Exige protocolo separado: contraste usa nós de texto; a heurística usa pares únicos de cores por papel/simulação. Não reutilize rótulos de contraste para calcular métricas da heurística. Defina independentemente par, contexto, informação transmitida pela cor, pistas alternativas e julgamento humano.

Revisar imagens pode indicar utilidade exploratória, mas não comprova percepção clínica. Inferências sobre dificuldade real de pessoas com deficiência de visão de cores requerem outro protocolo, com participantes e critérios apropriados. Na V1, registre casos qualitativos e limitações; não fabrique F1 usando como referência as próprias distâncias RGB do detector.

## Estado

Protocolo e ferramenta prontos. Amostra aprovada, rótulos humanos, métricas reais e decisão de executar o estudo continuam pendentes. Fixtures dos testes verificam o cálculo, não a eficácia do detector.

Verificação em 06/10/2026: os 7 testes novos de cálculo/comando offline passaram, sem falhas ou testes ignorados; Maven verify gerou o JAR com BUILD SUCCESS. A execução dos testes usou somente rótulos sintéticos em memória ou diretório temporário, sem gerar métricas reais em results/.
