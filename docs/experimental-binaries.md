# Binários experimentais e política de publicação

Os binários originais do Run 1 e Run 2 estão **preservados localmente, sem alteração**, mas não integram o repositório Git devido ao tamanho. O `.gitignore` exclui especificamente os dois caminhos abaixo; não ignora todos os arquivos `.jar` do projeto. Não há Git LFS ou Release nesta etapa.

## Binários utilizados

| Run | Caminho local, relativo à raiz | Tamanho | Identidade SHA-256 e versão |
|---|---|---|---|
| Run 1 / piloto, Scanner V1 | `results/experimental-study-2026-10-07/backend-used.jar` | 263.207.855 bytes — 251,015 MiB | [Manifesto do piloto](../results/experimental-study-2026-10-07/manifest.json), campo `jar_sha256`; Java 21.0.12.1 |
| Run 2 / oficial, Scanner V2 aprovado | `results/experimental-study-2026-10-07-run-2-scanner-v2/backend-used.jar` | 263.221.394 bytes — 251,027 MiB | [Manifesto oficial](../results/experimental-study-2026-10-07-run-2-scanner-v2/manifest.json), campo `jar_sha256`; Java 21.0.12.1 |

Nome preservado: `backend-used.jar`. Artefato Maven: `dev.kidocolors:backend:0.1.0-SNAPSHOT`. Os inventários SHA-256 de [Run 1](../results/experimental-study-2026-10-07/raw-artifacts-manifest.json) e [Run 2](../results/experimental-study-2026-10-07-run-2-scanner-v2/raw-artifacts-manifest.json) registram também nome, tamanho e hash do arquivo. As versões de Java, motor, sistema e Playwright estão nos respectivos `study.json`; a configuração aprovada/efetiva está nos registros do Run 2. Os hashes foram novamente calculados e conferidos com esses registros antes da exclusão do Git.

Na inspeção de 07/10/2026, nenhum dos dois binários estava rastreado, no índice ou em commits dos refs/reflogs locais consultados. Nenhum JAR e nenhum blob acima de 100 MiB apareceu nesse histórico; o repositório local não é shallow. Não foi necessária remoção do índice ou reescrita de histórico. Essa constatação descreve o histórico local disponível, sem afirmar uma inspeção independente de refs remotos não presentes.

## O que um clone contém

Código-fonte, snapshots de fontes, configurações, versões, nomes dos artefatos, manifestos, hashes, dataset e resultados continuam selecionáveis para versionamento. Os binários originais são mantidos no arquivo experimental local. Por isso, um clone não terá os dois `backend-used.jar`, embora os inventários históricos continuem listando-os: **os inventários descrevem os arquivos preservados no estudo**, não apenas o conteúdo distribuído por Git. Seus JSONs e hashes não foram reescritos.

Para conferir o binário histórico, é necessário ter acesso à cópia local e calcular seu SHA-256, comparando com `jar_sha256` do manifesto correspondente. Os scripts de integridade completos do arquivo experimental também pressupõem essas cópias locais e seus arquivos auxiliares; não são uma verificação autossuficiente de um clone sem binários.

Novos JARs podem ser gerados com Maven seguindo os [comandos de preparação e testes](../README.md#preparar-java-e-chromium). Os resultados de build ficam em `target/`, ou `target-v2/` ao usar `-Pscanner-v2`; ambos já são ignorados. Recompilar permite executar o projeto, mas não garante identidade byte a byte com o binário experimental nem autoriza substituir seu hash ou repetir o estudo. Para o V2, o manifesto preserva também hashes das fontes; para o piloto, a revisão Git e o binário local são as referências registradas.

## Outros JARs de build encontrados

São arquivos locais já excluídos pelas regras de diretório de build, não binários adicionais usados para representar os runs:

| Caminho | Bytes | SHA-256 observado | Relação |
|---|---:|---|---|
| `backend/target/backend-0.1.0-SNAPSHOT.jar` | 110.662 | `032c7eb6c47a82b9cd8f019c334165d0174225ed4d29a8037d88263e5ad9859b` | Artefato intermediário do build normal; não corresponde ao JAR original do piloto |
| `backend/target-v2/backend-0.1.0-SNAPSHOT.jar` | 263.221.394 | Mesmo `jar_sha256` do Run 2 | Build V2; hash igual ao binário aprovado na [validação V2](scanner-v2-validation.json) e preservado no Run 2 |
| `kidocolors-core/target/kidocolors-core-0.1.0-SNAPSHOT.jar` | 23.950 | `ec0c9a4aea46d35f5553e7e5d804d1989933dd9b7270d472b21de17dbb5daaf1` | Build do módulo Core; não foi o executável independente dos estudos |
| `kidocolors-core/target-v2/kidocolors-core-0.1.0-SNAPSHOT.jar` | 23.950 | `62a009ed8f1cef282623da2ffab4f4b79dce97dd5824b999e25bc7157659d2fa` | Build do módulo Core no perfil V2; mesmo papel de biblioteca |

Nenhum desses quatro caminhos estava rastreado ou apareceu em commits locais consultados. JARs de dependências em caches Maven não são artefatos experimentais preservados; os caches já ficam fora do Git.

## Verificação da seleção publicável

Após aplicar as duas regras específicas, não há blob acima de 100 MiB no índice nem arquivo acima desse tamanho na união dos arquivos rastreados com os não rastreados não ignorados. Na conferência de preparação anterior à autorização de publicação, nenhuma alteração estava staged. As regras `**/target/` e `**/target-v2/` já existiam antes desta preparação; não foi acrescentada uma regra `*.jar`.

O conteúdo candidato à publicação soma aproximadamente **1,08 GiB sem empacotamento Git**, principalmente capturas PNG dos dois runs. É uma estimativa da soma dos arquivos locais elegíveis, não do tamanho de download/push, que depende de compressão e deduplicação. As capturas e todos os demais resultados permanecem intactos.

Na publicação autorizada posteriormente, os atributos Git dos resultados, dataset CSV e fontes congeladas foram configurados para não normalizar finais de linha. Isso preserva os bytes identificados pelos SHA-256 existentes, sem editar os arquivos experimentais. Os logs de validação preservados e o log sanitizado do Run 2 são exceções específicas à regra geral de logs; credenciais, caches, builds e os dois JARs continuam excluídos.
