# Execução alternativa com Docker — Fase 9

A configuração reúne React servido pelo Nginx, API Java 21 com Chromium/Playwright e PostgreSQL 17. O Compose inicia cada dependente depois do healthcheck do serviço anterior. A API executa `SELECT 1` em `/api/health`; esse endpoint verifica API e conexão com banco, mas não inicia Chromium.

## Preparação e execução

Requer Docker Desktop iniciado com containers Linux e Docker Compose. No Windows, configure o mecanismo WSL 2 pelo Docker Desktop. JDK, Maven e Node do computador não são necessários para construir as imagens; o primeiro build precisa de internet para imagens e dependências.

Execute na raiz do projeto:

```powershell
docker compose version
# Cria somente se o arquivo ainda não existir, preservando configurações locais.
if (-not (Test-Path .env.docker)) { Copy-Item .env.docker.example .env.docker }
```

Edite `.env.docker` e substitua `DB_PASSWORD`. Depois:

```powershell
docker compose --env-file .env.docker up --build
```

Abra [a interface](http://localhost:3000). A API é publicada em [localhost:8080](http://localhost:8080/api/health). As portas são vinculadas a `127.0.0.1`; o PostgreSQL fica acessível somente entre os containers. Para iniciar em segundo plano e aguardar a prontidão:

```powershell
docker compose --env-file .env.docker up -d --build --wait --wait-timeout 180
docker compose --env-file .env.docker ps
docker compose --env-file .env.docker logs --tail 100 backend
docker compose --env-file .env.docker logs -f frontend backend postgres
```

Se a inicialização ultrapassar o prazo, consulte os logs e `docker compose --env-file .env.docker ps`; não repita automaticamente solicitações de análise. `Ctrl+C` encerra o acompanhamento dos logs. No comando inicial em primeiro plano, encerra os serviços.

## Portas e variáveis

| Variável em .env.docker | Uso no Compose completo |
|---|---|
| WEB_PORT, padrão 3000 | Porta da interface no computador |
| API_PORT, padrão 8080 | Porta da API no computador |
| DB_NAME, DB_USERNAME, DB_PASSWORD | Banco e credenciais locais |
| SCANNER_TIMEOUT_MS, padrão 15000 | Timeout de cada operação de navegação |
| SCANNER_SETTLE_MS, padrão 500 | Espera depois do carregamento |

O Compose completo define `DB_URL`, `SERVER_PORT` e `CAPTURE_STORAGE_PATH` respectivamente `jdbc:postgresql://postgres:5432/<DB_NAME>`, `8080` e `/app/storage/captures`. Mudar API_PORT não altera a porta interna nem o proxy. Credenciais não são incorporadas ao front-end ou às imagens. `.dockerignore` exclui `.env.docker`, caches, capturas e dados de estudo do contexto do build.

Se houver uma API Java local na porta 8080, pare-a ou altere API_PORT. Vite na porta 5173 é independente do Nginx na porta 3000. O arquivo `compose.db.yml` continua disponível para desenvolvimento com Java/Vite no computador; usa o projeto separado `kidocolors-dev-db` e outro volume de banco. Os dois modos não compartilham análises. Essa identificação substitui o nome implícito anterior do Compose de desenvolvimento; volumes anteriores não são removidos nem migrados automaticamente.

## Persistência, parada e reinício

`kidocolors_postgres_data` guarda o banco, e `kidocolors_captures` guarda os PNGs. Preserve ambos para manter relatórios e imagens associados.

```powershell
docker compose --env-file .env.docker stop
docker compose --env-file .env.docker start --wait
# Remove containers e rede, preservando os volumes:
docker compose --env-file .env.docker down
docker compose --env-file .env.docker up -d --build --wait
```

Para consultar o banco com os valores padrão (ajuste usuário/banco se tiver alterado `.env.docker`):

```powershell
docker compose --env-file .env.docker exec postgres psql -U kidocolors -d kidocolors
```

Alterar DB_PASSWORD em `.env.docker` não modifica a senha de um banco já inicializado. Para manter os registros, atualize as credenciais no banco antes de reiniciar a API com a nova senha.

**Recriar tudo apaga análises, lotes e capturas dos dois volumes do Compose completo.** Exporte os dados e preserve cópias do banco e dos PNGs antes de executar conscientemente:

```powershell
docker compose --env-file .env.docker down --volumes
docker compose --env-file .env.docker up -d --build --wait
```

Esse comando não apaga os arquivos `datasets/` e `results/` do computador. Não foi executado nesta implementação.

## Verificação integrada a executar

1. Confirme os três serviços saudáveis em `docker compose --env-file .env.docker ps` e a resposta `{"status":"UP"}` em `/api/health`.
2. Abra `/historico` diretamente e recarregue. O Nginx deve entregar a SPA; pedidos `/api/` devem chegar à API, sem remover esse prefixo.
3. Envie uma única URL pública para uma verificação técnica. Confira o status salvo; caso COMPLETED, confira relatório, captura original e três simulações. Caso FAILED, confira o motivo nos logs e no histórico. Uma falha persistida não comprova o funcionamento do Chromium.
4. Guarde o ID e, após `down` seguido de `up`, consulte o mesmo relatório e suas imagens. Confirme persistência sem executar outra análise.
5. Confira a versão da JVM com `docker compose --env-file .env.docker exec backend /opt/java/openjdk/bin/java -version`. Deve ser Java 21.

Esta verificação técnica não substitui a validação humana da Fase 8 e não autoriza iniciar o estudo de aproximadamente 100 websites. Lotes permanecem síncronos; o proxy permite espera maior, mas desconexões não cancelam necessariamente o processamento. Não reenvie um lote sem conferir se foi salvo.

## Imagens e diagnóstico

O build da API empacota os módulos Maven; o runtime usa a imagem oficial Playwright Java `v1.63.0-noble`, compatível com a dependência do projeto. Java 21 é copiado explicitamente da imagem Temurin e executado por caminho absoluto. Chromium e suas bibliotecas vêm da imagem Playwright. A API roda como `pwuser`, com volume de capturas gravável e `shm_size: 1gb`. Esta configuração é para execução local; não constitui isolamento reforçado para hospedagem pública.

Ao atualizar Playwright em `backend/pom.xml`, atualize também a imagem no Dockerfile e verifique novamente a coleta. O front-end é compilado com Node 24 e servido pelo Nginx com fallback de rotas e proxy `/api`. O build das imagens não executa as suítes de testes; elas são verificadas separadamente.

Falha ao conectar ao Docker exige iniciar o Desktop e conferir o mecanismo Linux. Erro de porta ocupada exige liberar a porta ou mudar WEB_PORT/API_PORT. API não saudável exige conferir banco, credenciais e logs. Falha BROWSER exige conferir versão do Playwright e recursos disponíveis; falha STORAGE exige conferir o volume e permissões do usuário, sem tornar diretórios globalmente graváveis.

Referências: [Docker Compose — ordem e prontidão](https://docs.docker.com/compose/how-tos/startup-order/), [Playwright Java em Docker](https://playwright.dev/java/docs/docker).

## Independência e verificação

Docker é opcional: o [desenvolvimento local](local-development.md) usa PostgreSQL hospedado e não executa estes comandos. `.env.local` contém apenas a conexão hospedada; `.env.docker` contém as configurações do banco em container. O Compose sobrescreve DB_URL com seu host interno `postgres`, sem conexão com Supabase. `compose.db.yml` é uma alternativa adicional, não um requisito.

O usuário desinstalou Docker Desktop do notebook. Nesta atualização foram revisados estaticamente Dockerfiles, Compose, proxy Nginx, healthchecks, variáveis e exclusão de credenciais do contexto. Nenhuma execução local de containers é declarada concluída. A Fase 9 não depende de reinstalar Docker para seguir o desenvolvimento.

Há evidência histórica separada no GitHub: a [execução 37548042414](https://github.com/KamillyFer-8/KidoColors/actions/runs/37548042414), commit `2d618b3`, passou nos três jobs. As imagens foram construídas e Nginx/API/PostgreSQL ficaram saudáveis no runner; consultas HTTP e Java 21 foram verificados. Isso não comprova análise real, capturas ou persistência após reinício de containers no notebook.

A [execução 37617334795](https://github.com/KamillyFer-8/KidoColors/actions/runs/37617334795), commit `822ad57`, também passou nos três jobs após tornar Docker opcional. A verificação de containers ocorreu no runner do GitHub, sem Docker instalado no notebook. O CI verifica construção, prontidão e contratos HTTP; análise completa em container e persistência após reinício continuam fora dessa verificação.
