# Git e integração contínua — Fase 10

O repositório local usa a branch `main`. `.gitignore` exclui `.env`, `.env.local`, `.env.docker`, dependências, builds, cache Maven, navegador baixado e capturas locais; `.env.example` e `.env.docker.example` contêm somente exemplos sem credenciais reais e permanecem versionáveis. `.gitattributes` padroniza finais de linha dos arquivos de código. O template de pull request pede descrição da mudança e evidência de verificação.

Docker é opcional no computador de desenvolvimento. O [modo local](local-development.md) usa PostgreSQL hospedado; os jobs Java e Front-End usam testes isolados sem Supabase. O job Containers continua como verificação da alternativa Docker no runner do GitHub, independente do notebook e de seu banco hospedado.

## O que o CI verifica

`.github/workflows/ci.yml` define três jobs independentes em Ubuntu 24.04, disparados por push à main, pull requests e execução manual:

| Job | Verificações |
|---|---|
| Java — Core e API | Java 21, instalação do Chromium e bibliotecas Linux, `mvn verify`: compilação, testes e JAR |
| Front-End — lint, testes e build | Node 24.18.0, `npm ci`, ESLint, Vitest, TypeScript e build Vite |
| Containers — Nginx, API e PostgreSQL | Validação Compose, build das imagens, prontidão dos serviços, HTTP pelo Nginx e JVM Java 21 |

O passo inicial Maven com `-DskipTests install` instala os módulos locais para que a CLI Playwright consiga resolver o Core. A verificação obrigatória ocorre depois, com `verify` sem pular testes. O cache guarda dependências Maven/npm; o Chromium é instalado explicitamente em cada execução Java. A versão do navegador é escolhida pela dependência Playwright do projeto.

Testes da API usam H2 e scanner simulado; testes próprios do scanner usam Chromium real e páginas locais de teste. O job Containers constrói as imagens e consulta API/banco através do Nginx, usando PostgreSQL descartável no runner. `scripts/check-container-http.py` faz somente GET: health, histórico e documentos das rotas da SPA. Confere a resposta JSON para evitar confundir um fallback HTML com uma API funcionando. O job remove seus próprios volumes ao terminar; não acessa dados locais do computador.

Essas consultas não renderizam React num navegador, não iniciam uma análise, não verificam a gravação de PNGs ou sua persistência após recriar containers. O CI não coleta websites do estudo nem executa revisão humana. As verificações complementares estão em [Docker](docker.md) e [validação](validation.md). Um CI verde não certifica acessibilidade nem eficácia do detector.

O workflow tem permissão de leitura do código e não publica pacotes, imagens ou site. O banco isolado do job Containers usa uma senha identificada como descartável; não usa credenciais reais, tokens próprios ou secrets. As versões das actions foram conferidas nas páginas oficiais: [checkout](https://github.com/actions/checkout/releases/tag/v7.0.1), [setup-java](https://github.com/actions/setup-java/releases/tag/v6.0.1), [setup-node](https://github.com/actions/setup-node/releases/tag/v7.0.0). A instalação segue a [documentação Playwright Java para CI](https://playwright.dev/java/docs/ci).

## Comandos no terminal PowerShell do VS Code

Confira alterações antes de registrá-las:

```powershell
git status --short
git diff
git add .
git diff --cached --stat
git diff --cached --check
git commit -m "feat: estrutura inicial do KidoColors e CI"
```

Se Git solicitar identidade, configure seu nome e o e-mail que você escolheu para commits, preferencialmente o endereço noreply fornecido pela sua conta GitHub. Use valores reais escolhidos por você, sem alterar a configuração global:

```powershell
git config user.name 'SEU_NOME'
git config user.email 'SEU_EMAIL_DE_COMMITS'
```

Essas duas linhas são modelos: substitua os textos antes de executar. Um commit é um registro local; não envia arquivos ao GitHub.

## Publicação no GitHub

Crie um repositório vazio na sua conta, com a visibilidade que escolher, sem inicializar README ou .gitignore. Substitua a URL abaixo pela URL desse repositório; não execute com o marcador:

```powershell
git remote add origin 'URL_DO_SEU_REPOSITORIO'
git remote -v
git push -u origin main
```

O Git pode solicitar autenticação pelo navegador. Não coloque tokens no código, no `.env.example` ou no endereço do remoto. Se origin já existir, confira `git remote -v` antes de alterar qualquer configuração. Após o push, abra a aba Actions e confira os três jobs e seus logs. Para uma alteração posterior:

```powershell
git switch -c feat/minha-alteracao
# Edite e execute as verificações pertinentes.
git add .
git commit -m "feat: descreva a alteração"
git push -u origin feat/minha-alteracao
```

Abra uma pull request para main. O CI compara a mudança pelo conjunto de testes; a revisão humana avalia comportamento, metodologia e limitações. Se configurar regras de proteção da main, use os nomes dos jobs efetivamente apresentados pela primeira execução.

## Situação desta fase

A configuração de CI está preparada localmente. Em 06/10/2026, `mvn verify` passou com 100 testes (27 Core e 73 Backend), incluindo Chromium real sobre fixtures locais no Windows; o JAR foi gerado. Os 24 testes do front-end, lint, TypeScript e build passaram. Essas execuções locais não confirmam o comportamento do runner Linux ou do workflow remoto.

O repositório público [KamillyFer-8/KidoColors](https://github.com/KamillyFer-8/KidoColors) foi criado em 06/10/2026. A execução GitHub Actions deve ser conferida pela aba Actions; os resultados locais acima não são evidência de execução remota. Não use badge de sucesso antes de confirmar o workflow. Screenshots existentes em `docs/screenshots/` são capturas reais da interface com suas limitações descritas no README; não são resultados do estudo.

A [primeira execução remota, 37548042414](https://github.com/KamillyFer-8/KidoColors/actions/runs/37548042414), sobre o commit `2d618b3`, concluiu com sucesso os três jobs em 06/10/2026. O runner Linux verificou Java/Core/API e front-end, construiu as imagens e iniciou os três containers saudáveis. O script HTTP passou por Nginx, API e PostgreSQL; o container confirmou Java 21.0.12.1. Isso não inclui análise de URL pública, renderização no navegador, PNGs ou persistência após reinício.

Antes da Fase 11, ainda é necessário verificar a aplicação com PostgreSQL/containers, executar o protocolo humano de validação e aprovar a seleção do dataset. O estudo não foi iniciado.
