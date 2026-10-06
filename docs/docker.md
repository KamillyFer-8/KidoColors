# Execução completa local — Fase 9

A configuração reúne React servido pelo Nginx, API Java 21 com Chromium/Playwright e PostgreSQL 17. O Compose inicia cada dependente depois do healthcheck do serviço anterior. A API executa `SELECT 1` em `/api/health`; esse endpoint verifica API e conexão com banco, mas não inicia Chromium.

## Preparação e execução

Requer Docker Desktop iniciado com containers Linux e Docker Compose. No Windows, configure o mecanismo WSL 2 pelo Docker Desktop. JDK, Maven e Node do computador não são necessários para construir as imagens; o primeiro build precisa de internet para imagens e dependências.

Execute na raiz do projeto:

```powershell
docker compose version
# Cria somente se o arquivo ainda não existir, preservando configurações locais.
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

Edite `.env` e substitua `DB_PASSWORD`. Depois:

```powershell
docker compose up --build
```

Abra [a interface](http://localhost:3000). A API é publicada em [localhost:8080](http://localhost:8080/api/health). As portas são vinculadas a `127.0.0.1`; o PostgreSQL fica acessível somente entre os containers. Para iniciar em segundo plano e aguardar a prontidão:

```powershell
docker compose up -d --build --wait --wait-timeout 180
docker compose ps
docker compose logs --tail 100 backend
docker compose logs -f frontend backend postgres
```

Se a inicialização ultrapassar o prazo, consulte os logs e `docker compose ps`; não repita automaticamente solicitações de análise. `Ctrl+C` encerra o acompanhamento dos logs. No comando inicial em primeiro plano, encerra os serviços.

## Portas e variáveis

| Variável em .env | Uso no Compose completo |
|---|---|
| WEB_PORT, padrão 3000 | Porta da interface no computador |
| API_PORT, padrão 8080 | Porta da API no computador |
| DB_NAME, DB_USERNAME, DB_PASSWORD | Banco e credenciais locais |
| SCANNER_TIMEOUT_MS, padrão 15000 | Timeout de cada operação de navegação |
| SCANNER_SETTLE_MS, padrão 500 | Espera depois do carregamento |

`DB_URL`, `SERVER_PORT` e `CAPTURE_STORAGE_PATH` do exemplo servem ao desenvolvimento fora dos containers. O Compose completo define respectivamente `jdbc:postgresql://postgres:5432/<DB_NAME>`, `8080` e `/app/storage/captures`. Mudar API_PORT não altera a porta interna nem o proxy. Credenciais não são incorporadas ao front-end ou às imagens. `.dockerignore` exclui `.env`, caches, capturas e dados de estudo do contexto do build.

Se houver uma API Java local na porta 8080, pare-a ou altere API_PORT. Vite na porta 5173 é independente do Nginx na porta 3000. O arquivo `compose.db.yml` continua disponível para desenvolvimento com Java/Vite no computador; usa o projeto separado `kidocolors-dev-db` e outro volume de banco. Os dois modos não compartilham análises. Essa identificação substitui o nome implícito anterior do Compose de desenvolvimento; volumes anteriores não são removidos nem migrados automaticamente.

## Persistência, parada e reinício

`kidocolors_postgres_data` guarda o banco, e `kidocolors_captures` guarda os PNGs. Preserve ambos para manter relatórios e imagens associados.

```powershell
docker compose stop
docker compose start --wait
# Remove containers e rede, preservando os volumes:
docker compose down
docker compose up -d --build --wait
```

Para consultar o banco com os valores padrão (ajuste usuário/banco se tiver alterado `.env`):

```powershell
docker compose exec postgres psql -U kidocolors -d kidocolors
```

Alterar DB_PASSWORD em `.env` não modifica a senha de um banco já inicializado. Para manter os registros, atualize as credenciais no banco antes de reiniciar a API com a nova senha.

**Recriar tudo apaga análises, lotes e capturas dos dois volumes do Compose completo.** Exporte os dados e preserve cópias do banco e dos PNGs antes de executar conscientemente:

```powershell
docker compose down --volumes
docker compose up -d --build --wait
```

Esse comando não apaga os arquivos `datasets/` e `results/` do computador. Não foi executado nesta implementação.

## Verificação integrada a executar

1. Confirme os três serviços saudáveis em `docker compose ps` e a resposta `{"status":"UP"}` em `/api/health`.
2. Abra `/historico` diretamente e recarregue. O Nginx deve entregar a SPA; pedidos `/api/` devem chegar à API, sem remover esse prefixo.
3. Envie uma única URL pública para uma verificação técnica. Confira o status salvo; caso COMPLETED, confira relatório, captura original e três simulações. Caso FAILED, confira o motivo nos logs e no histórico. Uma falha persistida não comprova o funcionamento do Chromium.
4. Guarde o ID e, após `down` seguido de `up`, consulte o mesmo relatório e suas imagens. Confirme persistência sem executar outra análise.
5. Confira a versão da JVM com `docker compose exec backend /opt/java/openjdk/bin/java -version`. Deve ser Java 21.

Esta verificação técnica não substitui a validação humana da Fase 8 e não autoriza iniciar o estudo de aproximadamente 100 websites. Lotes permanecem síncronos; o proxy permite espera maior, mas desconexões não cancelam necessariamente o processamento. Não reenvie um lote sem conferir se foi salvo.

## Imagens e diagnóstico

O build da API empacota os módulos Maven; o runtime usa a imagem oficial Playwright Java `v1.63.0-noble`, compatível com a dependência do projeto. Java 21 é copiado explicitamente da imagem Temurin e executado por caminho absoluto. Chromium e suas bibliotecas vêm da imagem Playwright. A API roda como `pwuser`, com volume de capturas gravável e `shm_size: 1gb`. Esta configuração é para execução local; não constitui isolamento reforçado para hospedagem pública.

Ao atualizar Playwright em `backend/pom.xml`, atualize também a imagem no Dockerfile e verifique novamente a coleta. O front-end é compilado com Node 24 e servido pelo Nginx com fallback de rotas e proxy `/api`. O build das imagens não executa as suítes de testes; elas são verificadas separadamente.

Falha ao conectar ao Docker exige iniciar o Desktop e conferir o mecanismo Linux. Erro de porta ocupada exige liberar a porta ou mudar WEB_PORT/API_PORT. API não saudável exige conferir banco, credenciais e logs. Falha BROWSER exige conferir versão do Playwright e recursos disponíveis; falha STORAGE exige conferir o volume e permissões do usuário, sem tornar diretórios globalmente graváveis.

Referências: [Docker Compose — ordem e prontidão](https://docs.docker.com/compose/how-tos/startup-order/), [Playwright Java em Docker](https://playwright.dev/java/docs/docker).

## Evidência nesta implementação — 06/10/2026

Os 7 testes `AnalysisApiTest` passaram, incluindo a verificação de prontidão com uma consulta real ao H2 de teste; Maven gerou o JAR executável com BUILD SUCCESS. Docker não foi encontrado no PATH nem nos locais padrão do Desktop. Portanto, `docker compose config`, build das imagens, inicialização dos containers, coleta em Chromium Linux e persistência em PostgreSQL ainda não foram verificados. A configuração está preparada; a validação integrada da Fase 9 permanece pendente.
