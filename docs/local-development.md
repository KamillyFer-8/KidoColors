# Desenvolvimento local sem Docker

React/Vite e Spring Boot/Core executam no computador. A API acessa PostgreSQL hospedado por JDBC/JPA. Supabase fornece somente o banco: não há SDK, Auth, Storage nem acesso do React ao banco. Docker é uma [alternativa independente](docker.md).

## Criar e configurar PostgreSQL no Supabase

1. Acesse [Supabase Dashboard](https://supabase.com/dashboard) e crie um projeto dedicado ao desenvolvimento do KidoColors. Escolha a região e guarde a senha do banco localmente. Confira o plano escolhido antes de concluir; não é necessário contratar um recurso pago para seguir este guia.
2. Como a aplicação usa somente JDBC, desative a Data API nas configurações do projeto. Isso evita expor as tabelas JPA pelas APIs automáticas do Supabase. Não são necessárias chaves `anon`, `publishable` ou `service_role`.
3. Clique em **Connect → Session pooler**. Copie o host, a porta, o nome do banco e o usuário exatamente como aparecem. A porta desse modo é `5432`; o usuário normalmente tem o formato `postgres.<referência-do-projeto>`. Não deduza o host pela região.
4. Na raiz do repositório, crie o arquivo local somente se ainda não existir:

```powershell
if (-not (Test-Path .env.local)) { Copy-Item .env.example .env.local }
```

5. Abra `.env.local` no VS Code e preencha as três variáveis. O exemplo abaixo contém marcadores, não uma conexão real:

```dotenv
DB_URL=jdbc:postgresql://HOST_COPIADO:5432/postgres?sslmode=require
DB_USERNAME=USUARIO_COPIADO
DB_PASSWORD=SENHA_DO_BANCO
```

Use o nome de banco exibido em Connect se for diferente de `postgres`. A senha é a senha PostgreSQL, não a senha da conta Supabase. Mantenha usuário/senha fora da URL. Valores podem estar entre aspas; `#`, `$` e `=` dentro da senha são tratados como caracteres, sem execução ou expansão. Não escreva comentários depois dos valores.

O Session pooler suporta IPv4 e prepared statements utilizados pelo Hibernate. A conexão direta também serve quando sua rede oferece IPv6. Não use o Transaction pooler na porta `6543` para esta configuração.

`sslmode=require` exige criptografia, mas não verifica a identidade do servidor. Para verificação completa, baixe o certificado raiz em Database settings e configure `sslmode=verify-full&sslrootcert=C:/caminho/para/certificado.cer` na URL JDBC. Consulte os parâmetros do driver PostgreSQL se o caminho contiver caracteres que precisem ser escapados.

`.env.local` e `.env.docker` são ignorados pelo Git e pelo contexto Docker. Nunca copie credenciais para arquivos `VITE_*`, screenshots, issues, commits ou mensagens de chat. O arquivo `.env` antigo não é carregado pelo script local; a alternativa Docker usa `.env.docker` explicitamente.

## Preparar o Backend e Chromium uma vez

Requer JDK 21 com `javac`, Maven 3.9+ e internet. Na raiz, no PowerShell do VS Code:

```powershell
$mavenCache = Join-Path (Get-Location).Path '.maven-cache'
$env:PLAYWRIGHT_BROWSERS_PATH = Join-Path (Get-Location).Path '.playwright'
$env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = '1'
mvn.cmd -B -ntp "-Dmaven.repo.local=$mavenCache" '-DskipTests' install
mvn.cmd -B -ntp -f backend/pom.xml "-Dmaven.repo.local=$mavenCache" exec:java '-Dexec.mainClass=com.microsoft.playwright.CLI' '-Dexec.args=install chromium'
mvn.cmd -B -ntp "-Dmaven.repo.local=$mavenCache" verify
```

Interrompa a sequência se um comando falhar. O primeiro comando prepara o JAR e as dependências para instalar o navegador; não comprova testes. O último executa Core e Backend, incluindo Chromium sobre fixtures locais. Reinstale o Chromium ao atualizar Playwright. Os caches exigem espaço local; Supabase elimina o servidor PostgreSQL local, não o navegador utilizado pelo scanner.

## Iniciar no VS Code

Terminal 1, na raiz:

```powershell
.\scripts\start-backend.ps1
```

O script importa somente as variáveis conhecidas de `.env.local`, exige as três configurações PostgreSQL, define o caminho do Chromium e inicia o JAR. Não executa Docker nem carrega credenciais de Compose. Se a política do PowerShell bloquear scripts locais, use `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1`; essa opção vale apenas para esse processo.

No Windows, o script aplica o mesmo contorno de sockets dos testes: `jdk.net.unixdomain.tmpdir` aponta para `backend/target/disabled-unix-sockets`, que deve permanecer inexistente. Isso faz o pipe interno do Java NIO usar TCP local quando sockets AF_UNIX falham com `Invalid argument: connect` em caminhos temporários remapeados. A opção vale apenas para a JVM iniciada pelo script; a conexão JDBC e o endereço HTTP permanecem os configurados. Não é aplicada no Linux ou nos containers. Contexto técnico: [discussão do OpenJDK sobre AF_UNIX no Windows](https://mail.openjdk.org/pipermail/nio-dev/2023-March/013297.html).

Para revisar o formato sem iniciar a API nem conectar ao banco:

```powershell
.\scripts\start-backend.ps1 -CheckConfigOnly
```

Terminal 2, na raiz:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

Abra [a interface Vite](http://127.0.0.1:5173). O proxy `/api` encaminha para Spring em `localhost:8080`. [Prontidão API/banco](http://localhost:8080/api/health) executa `SELECT 1`. `Ctrl+C` encerra cada servidor. Após alterar Java, repita o build e reinicie a API; o Vite recarrega alterações React automaticamente.

## Persistência e testes

`application.yml` já usa `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`; não foi necessário alterar entidades, repositories ou dependências. PostgreSQL continua sendo o banco da aplicação. Hibernate mantém a configuração existente `ddl-auto=update`, criando/atualizando as tabelas do projeto de desenvolvimento ao iniciar. Isso não substitui migrações versionadas para produção; não há Flyway/Liquibase configurado atualmente.

Os registros ficam no PostgreSQL hospedado. Capturas continuam em `storage/captures` no computador, sem Supabase Storage; preserve essa pasta junto com o banco para manter as imagens dos relatórios. A desinstalação de Docker não migra os dados dos antigos volumes para Supabase. Não há migração automática entre os modos.

Os testes Spring ativam o perfil `test`, com H2 em memória e `create-drop`; H2 é uma dependência somente de teste. Os testes unitários usam mocks e os testes do scanner usam HTML/HTTP locais. `mvn verify` não deve ser executado com overrides `SPRING_DATASOURCE_*` apontando para um banco real. A preparação e os testes acima não importam `.env.local` nem iniciam o script da API.

No Front-End:

```powershell
npm.cmd run lint
npm.cmd test
npm.cmd run build
```

Sem configuração real do Supabase, podem ser verificados testes/builds e o formato do script; a conexão PostgreSQL hospedada e o fluxo navegador → API → banco continuam pendentes. Nenhuma credencial precisa ser enviada ao chat. Depois de configurar localmente, informe apenas que o arquivo está pronto para realizar a verificação integrada.

Verificação em 07/10/2026: `mvn verify` passou com 27 testes Core e 73 Backend, sem falhas ou testes ignorados, e gerou o JAR. O Front-End passou nos 24 testes, lint, TypeScript e build. O script foi verificado quanto a sintaxe, preservação de senha literal, rejeição de configuração incompleta e de variáveis inesperadas. Nenhuma conexão real ao Supabase foi realizada e nenhum Docker foi executado nesta atualização.

Verificação integrada posterior em 07/10/2026: após configurar as credenciais locais e aplicar o contorno de sockets Windows, a API iniciou na porta 8080 com PostgreSQL hospedado conectado e JPA inicializado. `/api/health` respondeu `UP` executando `SELECT 1`, e `/api/analyses?page=0&size=20` respondeu com histórico vazio. Os mesmos endpoints responderam pelo proxy Vite na porta 5173. Isso verifica conexão e leitura, sem comprovar análise completa, geração de capturas ou persistência de novos relatórios. Nenhum Docker ou lote do estudo foi executado.

Referências: [Supabase com Spring Boot](https://supabase.com/docs/guides/getting-started/quickstarts/spring-boot), [conexões, pooling e SSL](https://supabase.com/docs/guides/database/connecting-to-postgres), [desativação da Data API](https://supabase.com/docs/guides/api/securing-your-api), [SSL no PostgreSQL JDBC](https://jdbc.postgresql.org/documentation/ssl/).
