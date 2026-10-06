# Front-End — Fases 5 e 6

Interface React e TypeScript com Vite, React Router, CSS puro e Fetch. O formulário envia uma URL à API; o relatório apresenta score próprio, cobertura, contagens, duração, ocorrências, sugestões e capturas original/protanopia/deuteranopia/tritanopia. A página Metodologia explica critérios e limitações. O histórico lista análises persistidas, filtra por URL e compara dois relatórios anteriores.

Ambiente verificado: Node.js 24.18.0. `package-lock.json` registra as versões instaladas para reprodução com `npm.cmd ci`.

## Executar no terminal do VS Code

Na raiz do projeto, em um segundo terminal PowerShell:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

Abra o endereço mostrado pelo Vite (normalmente http://127.0.0.1:5173). Mantenha o back-end e o PostgreSQL em execução, seguindo `../backend/README.md`. O servidor Vite encaminha `/api` para `http://localhost:8080`, conforme `vite.config.ts`, evitando configuração CORS para o desenvolvimento. Se mudar a porta do back-end, ajuste esse destino. Nenhuma credencial de banco pertence ao front-end.

O proxy do Vite só existe no servidor de desenvolvimento. O build gera arquivos estáticos em `dist`; `nginx.conf` configura o proxy `/api` e o fallback das rotas da SPA para execução em Docker. Consulte [execução completa local](../docs/docker.md). `npm.cmd run preview` serve para conferir o build, não para executar a aplicação completa.

## Verificações

```powershell
npm.cmd run lint
npm.cmd test
npm.cmd run build
```

Vitest, React Testing Library e user-event verificam validação, POST único, carregamento, erros HTTP/rede, falhas persistidas, cobertura zero, filtros, seleção, sugestões, troca das simulações e cancelamento de leitura ao sair da página. `src/test/fixtures.ts` contém apenas dados sintéticos; a aplicação não utiliza fixtures ou screenshots de exemplo como resultados reais. Os testes de componente simulam Fetch e não substituem a verificação integrada com PostgreSQL e Chromium.

## Organização

- `src/api.ts`: métodos Fetch e tratamento de falhas.
- `src/types.ts`: contratos correspondentes aos DTOs da API Java.
- `src/pages`: formulário, relatório, histórico e metodologia.
- `src/components`: comparação de capturas, evolução e detalhes das ocorrências.
- `src/styles.css`: layout responsivo, foco visível e estilos.
- `src/test`: testes e fixtures isoladas.

O POST é síncrono; o botão fica desabilitado até a resposta. O limite de espera do cliente é 3 minutos, sem progresso percentual inventado ou reenvio automático. O servidor pode continuar processando após uma interrupção do cliente. GETs têm limite de 30 segundos e podem ser repetidos pelo botão de recarregar. Análises com status FAILED mostram o motivo e não exibem score. Registros antigos sem relatório mostram o status disponível.

Capturas são carregadas da API. Ao selecionar uma ocorrência, as bounding boxes aparecem sobre as duas imagens usando suas dimensões naturais, com texto e seletor disponíveis nos detalhes. A diferença visual das cores não é usada como única indicação de severidade.

Há rótulos de formulário, avisos `role="alert"`, estados `role="status"`, link para pular ao conteúdo, foco no conteúdo ao navegar, controles por teclado e layout para telas estreitas. Isso não constitui uma auditoria completa de acessibilidade.

## Verificação da Fase 5 — 06/10/2026

12 testes de componente passaram; lint e build terminaram sem erros. A Home foi aberta no navegador e o aviso de URL inválida foi confirmado pela interface. O servidor de desenvolvimento iniciou em `http://127.0.0.1:5173`. A API em `localhost:8080` recusou conexão; portanto, o fluxo completo navegador → API → PostgreSQL → relatório ainda não foi validado neste ambiente. Nenhum resultado real foi substituído por uma fixture.

## Histórico e evolução — Fase 6

A rota `/historico` usa `GET /api/analyses?page=0&size=20`. O filtro utiliza `GET /api/analyses/by-url` com URL codificada por `URLSearchParams`; página e filtro ficam na URL do navegador e podem ser compartilhados ou revisitados. A API normaliza o endereço e ordena por data decrescente, com UUID como desempate.

A tabela mostra URL, data, status, score, total de problemas, duração e acesso ao relatório. Estados de carregamento, falha de conexão, busca sem resultados, histórico vazio e página inexistente são distintos. Os botões de paginação respeitam os limites retornados pela API. A tabela pode ser rolada horizontalmente em telas estreitas.

As métricas resumem somente os registros da página exibida: quantidades por status e média dos scores de análises COMPLETED com score não nulo. Score zero é válido. A média não representa todo o histórico, o estudo experimental ou uma comparação controlada entre versões do motor.

“Ver evolução” abre o filtro pela URL daquela análise. Selecione duas análises concluídas, com score e relatório disponível, na mesma página. O front-end carrega ambos os relatórios e apresenta versão do motor, score, cobertura, falhas de contraste, avisos por simulação e duração. A variação é calculada como score mais recente menos score anterior somente quando URL e versão do motor coincidem, ambos os scores estão disponíveis e as datas são diferentes. Datas iguais não determinam uma direção temporal.

Conteúdo, cobertura e condições da captura podem variar. A diferença de score não comprova melhoria ou piora da acessibilidade. Coleta parcial e recursos bloqueados geram aviso. Nenhuma nova análise é executada ao consultar o histórico ou comparar registros.

Verificação em 06/10/2026: 24 testes de componente passaram (12 do fluxo individual e 12 de histórico/evolução), lint e build sem erros. Os testes cobrem paginação, codificação de URL, filtros, ausência de registros, falhas, média com scores nulos, comparação entre versões e ordenação de datas com frações de segundo. A navegação para o histórico foi conferida no navegador. O proxy registrou ECONNREFUSED ao acessar a API; a interface exibiu erro de solicitação, sem declarar histórico vazio. A integração com dados persistidos em PostgreSQL continua pendente.
