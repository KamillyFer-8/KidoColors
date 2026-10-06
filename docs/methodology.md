# Metodologia do motor — versão 1.0

O KidoColors é uma ferramenta de inspeção automática parcial, não uma certificação WCAG. Esta documentação descreve o algoritmo implementado, não resultados experimentais. O estudo real ainda não foi executado.

## Unidade e cobertura

A unidade de contraste é cada nó de texto visível coletado pelo scanner, apresentado como bloco de texto no relatório. Não é necessariamente um elemento DOM único: um parágrafo com filhos inline pode fornecer vários blocos, sem duplicar o texto dos filhos no pai. elementsCollected conta a coleta; elementsAnalyzed/elementsEvaluated contam apenas blocos cujas cores foram resolvidas. elementsSkipped conta os demais.

Fundos complexos, filtros, opacidade de grupo, efeitos de texto, SVG e espaços de cor não suportados são excluídos da matemática, com motivo registrado na captura. A avaliação não cobre todo o site nem todos os critérios de acessibilidade. Logos, texto incidental ou componentes inativos podem ser exceções ao critério; o scanner ainda não reconhece todas essas situações, portanto os achados precisam de revisão contextual. Oclusão, clipping e elementos sobrepostos também são limitações da coleta.

## Contraste

Usamos cores sRGB opacas resolvidas pelo scanner. Para cada canal normalizado c:

```text
c_linear = c / 12.92                         se c <= 0.04045
c_linear = ((c + 0.055) / 1.055)^2.4         caso contrário
Y = 0.2126 R_linear + 0.7152 G_linear + 0.0722 B_linear
contraste = (Y_maior + 0.05) / (Y_menor + 0.05)
```

Critério utilizado para os achados: WCAG 2.2, 1.4.3, nível AA. O limiar é 4,5:1 para texto normal ou 3:1 para texto grande (24 CSS px, ou 14 pt = 18,666… CSS px com peso >= 700). A razão não é arredondada antes da comparação. O Core também oferece avaliação AAA (7:1 ou 4,5:1), mas o relatório V1 gera achados de AA.

HIGH para razão < 3 e MEDIUM para as demais falhas são níveis de severidade internos do KidoColors, não uma classificação de severidade oficial da WCAG.

Referências: [WCAG 2.2 — luminância](https://www.w3.org/TR/WCAG22/#dfn-relative-luminance), [contraste](https://www.w3.org/TR/WCAG22/#contrast-minimum), [explicação e exceções](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html).

## Simulações

Aplicamos as matrizes pré-calculadas do modelo de Machado, Oliveira e Fernandes, em intensidade 1,0, aos canais RGB linearizados. Depois limitamos cada resultado a [0,1], aplicamos a função inversa sRGB e arredondamos para 8 bits. O processo é igual para uma cor isolada e para os pixels da screenshot. O canal alpha é preservado; a imagem original permanece inalterada.

As três matrizes estão versionadas em Simulation.java. A entrada linear evita o erro de multiplicar diretamente canais sRGB já codificados. Não há ajuste de intensidade na V1.

Protanopia e deuteranopia usam os respectivos extremos do modelo. TRITANOPIA é o nome da opção da aplicação, mas a matriz de tritanomalia em intensidade máxima é uma aproximação ilustrativa, não uma reprodução fisiológica validada de tritanopia. A implementação Colour documenta expressamente essa limitação. Nenhuma imagem simulada reproduz exatamente a percepção de todas as pessoas.

Fontes: [artigo de Machado et al. (2009)](https://pubmed.ncbi.nlm.nih.gov/19834201/), [página dos autores com tabelas e errata](https://www.inf.ufrgs.br/~oliveira/pubs_files/CVD_Simulation/CVD_Simulation.html), [tabelas disponibilizadas pela implementação Colour](https://github.com/colour-science/colour/blob/develop/colour/blindness/datasets/machado2010.py), [documentação do modelo e advertência sobre tritanopia](https://colour.readthedocs.io/en/master/_modules/colour/blindness/machado2009.html).

A página dos autores não pôde ser carregada nesta sessão. Os coeficientes foram conferidos nas tabelas da implementação Colour, que referencia o trabalho original. A implementação Java foi escrita para este projeto; as tabelas numéricas são atribuídas a Machado e à publicação Colour (BSD-3-Clause). Não usamos a biblioteca Colour como dependência.

## Heurística de diferenciação

Distância euclidiana normalizada em sRGB codificado:

```text
d = sqrt((R1-R2)^2 + (G1-G2)^2 + (B1-B2)^2) / (255 * sqrt(3))
similaridade = 1 - d
```

São medidas geométricas simples de [0,1]. Não são Delta E, não são perceptualmente uniformes e não representam probabilidade clínica de distinguir cores.

Comparação de pares de blocos avaliáveis com distância entre suas bounding boxes de até 200 CSS px. Comparamos foreground com foreground e background com background. Para cada simulação, emitimos aviso quando:

- distância original >= 0,20;
- distância simulada <= 0,08;
- distância simulada <= 40% da distância original.

Esses limiares são heurísticos do KidoColors, escolhidos como parâmetros iniciais, e precisam de validação empírica. Não são regras WCAG. O detector não infere se a cor transmite significado, nem se já existem labels, ícones ou outras pistas suficientes. O aviso recomenda revisão humana e pistas além da cor; não propõe uma cor como solução automática para esse caso.

O relatório mantém a primeira ocorrência de cada par não ordenado de cores por papel e simulação. Assim, as contagens de protanopia/deuteranopia/tritanopia representam avisos de combinações únicas, não todos os pares de elementos ou diagnósticos de daltonismo. O limite é 20.000 pares próximos por coleta. Atingi-lo e deixar pares sem comparação registra differentiationTruncated; o cálculo de contraste continua cobrindo todos os blocos avaliáveis coletados.

collectionTruncated indica o limite de elementos do scanner. blockedRequests indica recursos bloqueados ou falhas de requisição durante a captura. Todos esses campos devem acompanhar a interpretação dos resultados; ausência de achados numa coleta parcial não comprova ausência de problemas.

## Sugestões de contraste

Mantemos o fundo e tentamos misturar a cor do texto em direção a preto e branco, em 255 passos por direção. Entre os candidatos que alcançam o limiar AA, escolhemos o mais próximo da cor original na distância RGB usada acima. A razão é recalculada com a cor final de 8 bits. Uma cor que já passa permanece igual.

Essa busca preserva a cor tanto quanto possível dentro dessas duas trajetórias; não promete encontrar a solução perceptual globalmente mais próxima. É uma opção de correção, não a única. Para o relatório AA sempre há ao menos preto ou branco capaz de atingir o limiar em um fundo sRGB opaco. Para limiares genéricos mais altos, a função pode retornar nenhuma sugestão se forem inalcançáveis com o fundo fixo.

A sugestão é uma cor opaca efetiva. Aplicá-la ao CSS exige considerar transparência original, estados do componente e contexto visual e conferir o resultado novamente.

## KidoColors Accessibility Score

```text
score = round(100 * (blocos avaliáveis - falhas AA) / blocos avaliáveis)
```

O resultado está entre 0 e 100; nenhum bloco avaliável resulta em score nulo. Falha operacional também deixa o score nulo. Score zero significa que houve avaliação e todos os blocos avaliados falharam. Score 100 significa apenas que os blocos avaliáveis coletados passaram no contraste AA.

Os avisos heurísticos não reduzem o score V1. Assim evitamos misturar uma medida padronizada de contraste com limiares ainda não validados. O nome pertence ao produto: não há score oficial WCAG e não se pode usar esse número como certificação da página. A interface deve sempre mostrar cobertura, limitações e os avisos separadamente.

totalIssues soma falhas de contraste por bloco e avisos únicos por papel/simulação. É uma contagem de achados heterogêneos; para o estudo, divulgar também contagens por tipo e páginas com pelo menos uma falha de contraste.

## Persistência e reprodução

O serviço usa o mesmo motor para cada solicitação; o processamento em lote também reutiliza esse serviço. Salva versão do motor, metadados do scanner, resumo, achados, posições, cores, razões, sugestões e arquivos PNG original/simulados. durationMs inclui scanner, motor, transformação das imagens, encerramento do navegador e persistência, exceto a atualização final da própria duração. É medido com System.nanoTime, não estimado.

RUNNING significa execução em andamento; COMPLETED significa processamento concluído, mesmo quando não há texto avaliável. O cliente deve verificar score nulo e cobertura. SCANNED/PENDING podem existir em registros das fases anteriores, sem relatório completo. FAILED contém código/mensagem e duração; caso a captura tenha sido concluída antes da falha, ela pode continuar disponível sem o relatório.

Alterar limiares, fórmula, matrizes ou unidade exige atualizar a versão do motor. Não comparar execuções de metodologias diferentes sem explicitar a diferença. A validação manual, seleção do dataset e execução das aproximadamente 100 URLs continuam em fases futuras. Precision/recall/F1 só serão calculados com rótulos reais e unidade de avaliação compatível; avisos heurísticos precisam de protocolo separado do contraste.
