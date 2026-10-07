# Scanner V2 — protocolo proposto para revisão

Data: 2026-10-07. Estado: implementação e validação local; Run 2 não executado.

O estudo de 2026-10-07 é o **Run 1 / piloto**. Seus CSVs, JSONs, PNGs,
dataset, manifestos e JAR preservado permanecem como foram coletados. Esta
revisão não corrige retrospectivamente seus resultados. O README definitivo
não foi reescrito. A decisão de iniciar uma nova coleta completa depende da
revisão deste protocolo.

## Fundamento das mudanças

| Evidência da auditoria | Mudança V2 |
|---|---|
| Status e causa técnica ausentes nas falhas | Diagnóstico persistido, com status HTTP e fase |
| Dois redirects explicitamente bloqueados | Navegação por destinos validados, até cinco saltos |
| Quatro páginas abortadas por altura | Captura e coleta limitadas à mesma região superior |
| Dezoito timeouts sem fase identificável | DOMCONTENTLOADED, prontidão limitada e orçamento uniforme |
| Agência Brasil: 242 textos excluídos por background | Fundo opaco descendente pode cobrir uma imagem ancestral |
| Conta Azul: 277 textos excluídos por espaço de cor | Conversão dos formatos aceitos pelo Chromium para sRGB |
| Correios: PNG uniforme e zero textos | Falha de qualidade, preservando a captura para inspeção |
| Natura: PNG existente, erro BROWSER genérico | Cleanup explícito, fase e causa preservadas junto da captura |

Essas evidências justificam corrigir comportamentos gerais; não demonstram que
todos os INACCESSIBLE ou TIMEOUT do piloto sejam bugs. Nenhuma das 100 URLs
foi acessada para ajustar esta implementação. Os testes usam documentos locais,
servidores locais com allowlist exclusiva de testes e H2.

## Configuração uniforme

| Parâmetro | V2 padrão |
|---|---|
| Navegação | DOMCONTENTLOADED |
| Orçamento de navegação, incluindo redirects HTTP | 30.000 ms |
| Prontidão após navegação | Até 5.000 ms |
| Estabilidade exigida | 500 ms, amostragem a cada 100 ms |
| Critério de estabilidade | Primeiros 2.000 caracteres do texto visível do body + altura do documento |
| Fontes | `document.fonts.status` deve estar `loaded` para a prontidão |
| Viewport | 1280 × 720, escala 1, locale pt-BR, timezone UTC |
| Cobertura da captura | Região superior de 1280 px de largura, até 12.000 px de altura |
| Máximo de textos coletados | 2.000 |
| Máximo de redirects | Cinco por cadeia |

O timeout de 30 s evita que o teto de 15 s seja a única oportunidade de uma
página real responder. É uma decisão operacional anterior à nova coleta,
não um valor ótimo demonstrado para os sites do dataset. Esperar LOAD pode
depender de recursos secundários; DOMCONTENTLOADED com texto visível e
estabilidade limitada fornece um critério mais explícito. Não usamos
NETWORKIDLE, que pode nunca ocorrer em páginas com tráfego contínuo.

O tempo total da análise pode superar 35 s: inclui início do Chromium,
coleta, screenshot, armazenamento, simulações e cleanup. Operações Playwright
fora de navegação/prontidão têm timeout padrão de 30 s. As requisições
interceptadas respeitam o orçamento restante da fase quando aplicável.
Resolução DNS e processamento síncrono não constituem um deadline global
garantido. Se o texto não aparecer, as fontes não terminarem ou o conteúdo
amostrado não estabilizar em 5 s, a captura é feita para inspeção, mas a
análise falha com `QUALITY`.

Os parâmetros são configuráveis para uso normal. Para o Run 2, os valores
acima devem ser aplicados igualmente às 100 URLs e preservados no manifesto;
nenhum ajuste por site é permitido. `SCANNER_TIMEOUT_MS`,
`SCANNER_READINESS_TIMEOUT_MS` e `SCANNER_SETTLE_MS` controlam os tempos.
O Compose e o script local incluem os padrões V2. Overrides de ambiente
existentes precisam ser conferidos antes da coleta; não alteramos `.env.local`.

## Redirects e proteção de rede

Somente HTTP/HTTPS e GET/HEAD continuam permitidos. Cada URL inicial, destino
de redirect e recurso é verificado pelo NetworkGuard/UrlValidator, incluindo
resolução DNS e rejeição de localhost, IPs privados/reservados e credenciais
embutidas na URL. WebSockets, WebRTC, service workers e requisições de escrita
continuam bloqueados.

`route.fetch` usa `maxRedirects=0`: não pode seguir um destino antes da
validação. Location relativo é resolvido contra a URL da resposta. Na
navegação principal, um documento neutro transitório conclui a requisição
interceptada e o scanner inicia uma nova navegação no destino validado. Esse
documento nunca é analisado. Isso evita a corrida com a página de erro do
Chromium provocada por abortar a navegação e mantém a origem, a URL final e
a resolução de caminhos relativos do HTML final corretas.

Recursos seguem uma cadeia manual também validada; headers de autenticação e
cookies da requisição original não são encaminhados a outra origem. Headers
da origem original não são registrados nos diagnósticos. Loops e excesso de
saltos produzem `LIMIT_EXCEEDED`; destino proibido produz `BLOCKED`; ausência
ou formato inválido de Location produz `INACCESSIBLE`.

## Páginas longas e cobertura

A altura acima de 12.000 px deixa de ser, por si só, uma falha. O PNG é
recortado à região superior definida e somente textos com retângulo
inteiramente dentro dessa região são coletados. Texto que cruza o limite ou
fica fora da largura de 1280 px também não entra no score. Não há rolagem
automática, paginação ou tentativa de coletar infinite scroll.

`pageHeight` preserva a altura observada do documento. O diagnóstico registra
`coverageHeight` e `heightTruncated`; `truncated` também sinaliza perda de
cobertura por altura, posição ou limite de elementos. Portanto, um score de
página truncada descreve apenas a amostra de texto capturada, não toda a
página. O limite de 2.000 elementos continua explícito e não foi ampliado.

## Cores e backgrounds

A composição alfa e a fórmula de contraste continuam as mesmas. Uma imagem
ou gradient de um ancestral torna o fundo incerto até que um descendente com
cor de fundo completamente opaca o cubra. Uma imagem no próprio elemento
continua acima da sua cor de fundo e continua impedindo a avaliação; fundos
translúcidos não eliminam a incerteza. Opacidade, filtros, efeitos de texto e
SVG continuam com suas exclusões. Background clip no texto também é excluído.

O parser anterior de RGB/RGBA separado por vírgulas permanece. Como fallback,
o Chromium converte cores CSS modernas aceitas em um canvas sRGB de 1 pixel.
Dois valores sentinela verificam que a atribuição da cor foi aceita: uma cor
recusada não pode reutilizar acidentalmente a cor anterior. A conversão gera
RGB de 8 bits e alfa de 8 bits, compatíveis com o modelo sRGB existente. Cores
fora do gamut podem ser convertidas/recortadas pelo navegador; não se trata
de uma métrica perceptual de contraste em wide gamut. Sintaxe não resolvida
ou background incerto continua excluído, com `unsupportedReason` preservado.
Não estimamos contraste a partir de pixels de imagens ou gradients.

## Qualidade e observabilidade

PNG inteiramente uniforme, zero textos coletados, zero textos avaliáveis ou
prontidão não atendida produzem `FAILED / QUALITY`, sem score, relatório ou
contagem fictícia de problemas. A captura e os motivos permanecem disponíveis
quando a screenshot foi salva. A API também rejeita captura sem texto
avaliável, mesmo se uma implementação de PageScanner a devolver normalmente.
Isso é diferente de uma página com textos avaliados e nenhuma falha encontrada.
Conteúdo composto exclusivamente por “carregando”, “loading”, “aguarde” ou
“please wait”, com espaços/pontos/reticências, recebe `LOADING_ONLY_CONTENT`.
É uma verificação limitada de placeholder; não interpreta semanticamente sites.

O diagnóstico contém protocolo `scanner-v2`, URL inicial/final sanitizada,
status HTTP quando há resposta, duração observada, cobertura, cadeia de
redirects, motivos de qualidade, fase, código, mensagem e tipo/mensagem
técnica da exceção. As fases incluem VALIDATION, BROWSER_START, NAVIGATION,
LOADING, REDIRECT_VALIDATION, READINESS, COLLECTION, SCREENSHOT, STORAGE,
QUALITY e CLEANUP. A API acrescenta CAPTURE_SERIALIZATION, ANALYSIS,
SIMULATION_SCREENSHOT e REPORT_SERIALIZATION para falhas posteriores.

O JSON de captura usa `diagnostics`; a resposta de análise e os checkpoints
de lote têm `scannerDiagnosticsJson`, uma string JSON para leitura mesmo
quando não existe captura. A entidade tem a coluna nullable
`scanner_diagnostics_json`. JSONs legados sem esses campos continuam legíveis;
não há preenchimento retroativo dos registros do piloto. Novos lotes registram
o protocolo e configuração no ambiente. O CSV padrão conserva suas colunas;
diagnósticos detalhados devem ser preservados nos JSONs da nova execução.

Não registramos headers, cookies ou stack traces brutos. URLs diagnósticas
omitem userinfo, query e fragmento; mensagens técnicas omitem URLs e valores
de credenciais. Os logs usam ID, código e fase, sem imprimir a exceção bruta.
Falha de cleanup após captura mantém a captura e sua causa. Cada recurso é
fechado separadamente; falhas adicionais de cleanup não substituem a falha
principal e têm seus tipos registrados como avisos.

## Impacto no estudo e limites restantes

O **score, pesos, limiares WCAG e simulações do Core não mudaram**. Mudaram
o protocolo de coleta, a cobertura, a normalização de cores e a elegibilidade
de uma página como análise concluída. Isso pode alterar quais textos são
avaliados, scores observados e denominadores de sucesso. Run 1 e Run 2 devem
ser identificados separadamente e não mesclados como se fossem o mesmo
protocolo. Uma coleta aprovada deve executar todas as mesmas 100 URLs; não
substituir seletivamente falhas do piloto.

Continuam possíveis WAF/CAPTCHA, HTTP 403/429, DNS/TLS instável, indisponibilidade,
recursos bloqueados, navegação JavaScript durante a captura, fontes tardias,
conteúdo dinâmico fora da amostra de estabilidade e conteúdo carregado só
após rolagem/interação. A coleta não atravessa iframe/shadow DOM e não
reconstrói overlays, pseudo-elementos ou fundos fotográficos. CSS redirecionado
pode depender da resolução de caminhos de recursos na URL original.
Não há garantia de que a screenshot uniforme detecte todos os estados vazios,
nem de que texto estável prove que todo o site terminou de carregar. A
validação DNS preserva a proteção existente, mas não fixa o IP da conexão:
DNS rebinding entre verificação e conexão continua um limite conhecido.

## Validação e artefatos locais

As fixtures verificam redirect relativo válido com URL final correta, destino
privado que não recebe nenhuma requisição, loop, HTTP 503, timeout com causa,
texto que aparece após carregamento, conteúdo instável, screenshot uniforme,
altura/captura de 12.000 px, texto fora da cobertura, fundo opaco sobre gradient,
fundo translúcido incerto, cores modernas, falha de cleanup após captura,
persistência de diagnóstico/qualidade e leitura de captura legada.

O perfil Maven `scanner-v2` usa `target-v2` em cada módulo para construir sem
substituir o JAR mantido aberto pela API piloto. Com Maven/Chromium já instalados:

```powershell
$taskMavenCache = Join-Path (Get-Location).Path '.maven-cache'
$env:PLAYWRIGHT_BROWSERS_PATH = Join-Path (Get-Location).Path '.playwright'
$env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = '1'
mvn -o -B -ntp -Pscanner-v2 "-Dmaven.repo.local=$taskMavenCache" verify
```

Os logs locais ficam em `.maven-cache/v2-verify.log`, `v2-vitest.log`,
`v2-lint.log` e `v2-frontend-build.log`. A conferência de integridade utiliza
o inventário SHA-256 `.maven-cache/v2-run1-integrity.json`, criado antes das
alterações. Esses arquivos de suporte ficam fora de `results/`.

A V2 não foi iniciada contra o Supabase e nenhuma nova execução das 100 URLs
foi feita nesta revisão.

Resultado final: **27 testes Core, 77 backend e 24 frontend passando**, sem
falhas ou testes ignorados nas suítes Java. `mvn verify -Pscanner-v2`, lint e
build do frontend passaram. SHA-256 confirmou **454 arquivos protegidos
inalterados** e conferiu os **436 artefatos do manifesto original**, incluindo
as 213 capturas preservadas. O registro dessa validação está em
[`scanner-v2-validation.json`](scanner-v2-validation.json).
