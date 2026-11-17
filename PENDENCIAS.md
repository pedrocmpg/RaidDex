# Pendências (fases 0 e 1)

Estado: motor (`domain`), script de dados (`tools/build_gamedata.py`) e app sem captura (`data` + `app`) implementados. `./gradlew check` passa (testes do domínio, guarda R8 de permissões e de dependências proibidas). **Nada foi commitado.**

## Precisa de você

1. **Validação contra o Pokebattler (fase 0).** Para 5 chefes, montar a mesma coleção no simulador e no app e conferir se pelo menos 4 dos 6 coincidem:
   `./gradlew -q :domain:run --args="app/src/main/assets/gamedata.json tools/colecao_exemplo.json GROUDON T5"` (use `megas` como 5º argumento para incluir Megas).
   Os comandos usam IDs internos (`SWAMPERT`, `WATER_GUN_FAST`, `HYDRO_CANNON`), não nomes em português: procure o `id` em `app/src/main/assets/gamedata.json` (campos `species` e `moves`; o `name` ao lado é o nome pt-BR). A coleção de exemplo está em `tools/colecao_exemplo.json`.
   Se falhar, os parâmetros de ajuste são `bossMoveDelaySeconds` (em `tools/raid_bosses.json`) e `betterMoveRatio`.
2. **PS/CPM dos chefes por nível de raid** (`tools/raid_bosses.json`, campo `confirmed`): nenhum nível tem duas fontes ainda (T1 600 / 0,5974 e Mega 0,79 bateram com a Bulbapedia, uma fonte só). T3 (3600 / 0,7317), T5 (15000 / 0,7903), Mega (9000 / 0,7903) e Primal (22500 / 0,7903) precisam de duas fontes; o Primal veio de memória, sem fonte. Depois de conferir, rodar o script de novo.
3. **Teste no aparelho (Galaxy A34 5G)**: não há aparelho/emulador conectado aqui, então o app só foi compilado (`assembleDebug`), não executado. Falta confirmar: instala e abre, cadastro de 20 Pokémon, time em < 1 s (o motor passa nesse limite em teste de JVM), "Atualizar dados".
4. **Versão do Android** do aparelho (One UI 8.5 / Android 16 ou One UI 9 / Android 17) e se o Pokémon GO detecta captura de tela (pontos em aberto da spec, afetam a fase 2).
5. **"Atualizar dados"** baixa de `https://raw.githubusercontent.com/pedrocmpg/RaidDex/main/app/src/main/assets/gamedata.json`. Só funciona depois que o repositório for publicado com esse arquivo no `main`.

## Prints para as fases 2 e 3 (quando for a hora)

Cortar ou borrar nomes de outros treinadores antes de salvar qualquer imagem na raiz.

- Tela de detalhe de um Pokémon (PC, PS, nome, ataques, arco de nível), 1080 × 2340.
- Tela de avaliação com as três barras de IV (uma com IV 15 cheio, uma baixa).
- Pokémon com apelido, para testar leitura pelo nome do doce.
- Tela do lobby/entrada de raid mostrando o nome do chefe (borrar os jogadores).
- Se possível, 2 ou 3 variações de claro/escuro e de clima, pois mudam o fundo do arco.

## Decisões que tomei sem perguntar

- Pacote `com.raiddex.app`; `compileSdk`/`targetSdk` 36 com AGP 8.13.2 e Gradle 8.13 (AGP 9 e as versões mais novas de Compose/AndroidX exigem SDK 37, fora da spec). Bibliotecas AndroidX fixadas em versões compatíveis com SDK 36.
- Cadastro manual pede espécie, PC, PS e os 3 IVs; o nível é calculado por PC/PS/IV e o salvamento é bloqueado se não fechar (com opção "Salvar mesmo assim").
- Só `INTERNET` está no manifesto (as outras 4 permissões da lista são da fase 2). A permissão que o androidx.core injeta foi removida com `tools:node="remove"`, não adicionada à lista.
- Megas existem no motor (limite de 1 por time, testado), mas a interface da fase 1 não as ativa; entram na fase 4.
- Chefe usa todos os pares de golpes não-elite da espécie; PS/CPM e o atraso entre golpes do chefe (2,0 s) são parâmetros de dados, não de código.
- Nomes de formas regionais em pt-BR são montados como "Raichu (Alola)", e Megas como "Mega X"; não vêm dos textos do jogo.
- Versão dos dados: `AAAAMMDDhhmm-hash`, com o hash calculado sobre o conteúdo final (Game Master, textos e chefes). Qualquer mudança gera versão nova; o app só usa a cópia baixada se for mais nova que a do APK.
- Formas: entradas idênticas à forma base (mesmos tipos, status e golpes) são descartadas; as demais recebem rótulo pt-BR dos textos do jogo ou, na falta, o sufixo em maiúsculas/minúsculas (ex.: "Pikachu (Pop Star)"). O script falha se dois nomes colidirem. Fantasias com golpes próprios continuam na lista.
- Megas ficam fora do seletor da coleção (são estado temporário) e aparecem só no cálculo, quando ativados.
