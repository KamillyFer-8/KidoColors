# Amostra proposta para o estudo experimental

Seleção realizada em **07/10/2026**, antes do experimento. **Aguardando revisão e aprovação; não congelada.**

- `websites.csv`: 100 páginas, oito colunas na ordem solicitada, CSV UTF-8 com BOM, vírgulas e escape RFC 4180. Datas em ISO 8601 (`2026-10-07`).
- `kidocolors-dataset.xlsx`: a mesma amostra para revisão no Excel, com tabela filtrável e aba de resumo.
- `validation/labels-template.csv`: apenas modelo de rotulagem humana, sem rótulos atribuídos nesta etapa.

## Método de seleção

Amostragem não probabilística, intencional, por quotas iguais: 20 E-commerce, 20 Educação, 20 Notícias e conteúdo, 20 Serviços e 20 Empresas e instituições. A seleção busca variedade de atividades, tipos de organização e regiões, sem ranking de tráfego, sorteio ou pretensão de representar todos os websites brasileiros. Não corresponde aos “100 maiores sites”. Há instituições educacionais de todas as regiões; não foram estabelecidas quotas regionais.

A unidade selecionada é uma página inicial pública de uma organização identificável. Preferiu-se a homepage oficial; em sites internacionais e portais compartilhados, usou-se a página inicial da edição brasileira/em português ou do caminho institucional. BBC e DW são organizações internacionais com edições em português. Bosch e Siemens usam suas homepages brasileiras. Login para compras, conta, área do aluno, aplicativo contratado ou serviços pessoais não integra a página selecionada. Artigos internos de jornais podem ter assinatura; a amostra contém somente suas homepages públicas.

Pesquisa e consulta a páginas oficiais verificaram identidade, URL, conteúdo público e atividade. Consultas HTTP GET comuns, sem autenticação, verificaram resposta e redirecionamentos; não houve inspeção de estilos, contraste, WCAG, score ou outra medida de acessibilidade. HTTP 200 isoladamente não garante disponibilidade em outro ambiente. Consultas web e navegador complementaram os casos de HTTP 403 ou respostas dependentes de JavaScript. Nenhum certificado inválido foi ignorado e nenhum bloqueio de segurança foi contornado.

Os critérios de escolha não consideraram boa ou má acessibilidade aparente. Não foram incluídas fixtures ou páginas produzidas para testes. Substituições resolveram disponibilidade não confirmada, diversidade setorial/regional ou prevenção de repetição organizacional. Essa seleção por disponibilidade também limita a generalização dos resultados futuros.

## Duplicidades e classificação

Conferidos: 100 IDs distintos, 100 nomes distintos, 100 URLs distintas e exatamente 20 registros em cada categoria. A revisão organizacional evitou repetir marcas conhecidas de uma mesma organização na seleção. Não se confundiu domínio compartilhado com organização repetida: INPE e INPA são instituições diferentes no gov.br. Opera Mundi identifica editora própria, [Última Instância Editorial Ltda.](https://operamundi.uol.com.br/sobre-opera-mundi/); a parceria de portal com UOL não o torna a mesma organização da Folha. O critério trata instituições distintas como unidades, e não todo o governo federal como uma única organização. Isso não equivale a auditoria completa de participações societárias.

Categorias foram atribuídas pelo papel da página e pela atividade escolhida para esta amostra. Os casos que precisam de sua revisão estão identificados na coluna `observacao`:

- **Unimed do Brasil (KC-078):** Serviços pelo setor de saúde; a página da confederação também pode ser entendida como institucional.
- **BNDES (KC-084):** Empresas e instituições pela missão pública de desenvolvimento; também oferece serviços financeiros.
- **TOTVS (KC-091) e Stefanini (KC-092):** Empresas e instituições pelas páginas corporativas; a atividade também envolve serviços.
- **Omie (KC-061) e Conta Azul (KC-080):** Serviços pelo software oferecido como serviço; apresentam também conteúdo corporativo.

Não ficou uma URL sem confirmação suficiente na versão entregue. Isso não garante que todas estarão disponíveis ao scanner no experimento: há restrições de consulta documentadas.

## Status e ressalvas

- `verificado`: página oficial com conteúdo público confirmado em consulta web ou HTML identificado em resposta HTTP sem autenticação.
- `verificado_com_ressalva`: conteúdo confirmado, mas com restrição em uma das consultas, variação de idioma/localização, edição internacional ou classificação que exige revisão.
- `pendente_verificacao_manual`: reservado a URL/conteúdo ainda não confirmado suficientemente; nenhum registro final neste estado.

UFRJ (KC-023) e Suzano (KC-086) apresentaram variação entre português e inglês conforme o cliente de consulta. TIM (KC-070) redirecionou para a homepage regional RJ; o contexto de localização deve ser padronizado e registrado no experimento futuro. Drogaria Araujo apresentou conteúdo público em uma consulta oficial e 403 nas seguintes. Magazine Luiza foi confirmado no navegador com ofertas e categorias públicas apesar do 403 na consulta HTTP. Outros bloqueios e redirecionamentos estão descritos individualmente no CSV/XLSX. As URLs oficiais registradas na coluna `url` são as fontes primárias de cada seleção.

Casas Bahia foi substituída por Lupo porque a política de segurança do navegador bloqueou sua abertura. Não houve tentativa de contornar esse bloqueio.

## Uso futuro no KidoColors

**Não importar ou executar a amostra antes da aprovação.** O CSV de seleção solicitado contém oito colunas. O importador atual do KidoColors aceita exatamente `id,site,url,categoria`, conforme [documentação do processamento em lote](../docs/studies.md). Depois da aprovação e do congelamento, será necessário criar uma projeção dessas quatro colunas para a importação, preservando IDs, URLs, categorias e o arquivo original de seleção. Essa projeção não foi gerada nem executada nesta etapa; o código do importador não foi alterado.

**Nesta preparação não foi executado o scanner do KidoColors, não foram geradas análises ou métricas de acessibilidade e `results/` não foi alterado.** A pesquisa de disponibilidade não é resultado experimental. Nenhum rótulo humano foi atribuído. Consulte o [protocolo de validação](../docs/validation.md) para a etapa futura, após sua aprovação.
