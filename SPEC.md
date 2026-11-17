# Spec: Assistente de Raid para Pokémon GO (Android, só leitura)

Nov 16, 2026 · @Pedro Campagnolo

## Resumo

App Android pessoal que lê a tela do Pokémon GO por captura de tela e OCR, guarda a coleção num banco local e sugere os 6 melhores Pokémon para a raid escolhida.

O app só enxerga pixels. Ele nunca toca no jogo, nunca lê tráfego ou memória, nunca pede a conta do jogo e não usa root nem serviço de acessibilidade.

É de uso único (Pedro): sem login, sem cadastro, sem servidor. A rede serve só para baixar um JSON público com os dados do jogo; nada da coleção sai do celular. A instalação é por APK direto, sem Play Store.

## Regras de só leitura

Estas 8 regras são o contrato do projeto: qualquer funcionalidade que quebre uma delas não entra.

| # | Regra | O que isso proíbe na prática |
| --- | --- | --- |
| R1 | A única entrada vinda do jogo são pixels da tela, via MediaProjection com o consentimento padrão do Android | Ler arquivos, memória ou logs do jogo |
| R2 | Zero input no jogo | AccessibilityService, `dispatchGesture`, toques simulados, adb, macros |
| R3 | Zero rede do jogo | Proxy, VPN, MITM, chamadas à API do jogo |
| R4 | Zero alteração do jogo ou do aparelho | Root, APK modificado, hooks, emulador |
| R5 | Sem conta | Login Google, PTC ou qualquer credencial do jogo |
| R6 | Sem localização | Permissão de GPS no app |
| R7 | Sem automação indireta | "Auto-scan" que rola a lista sozinho; quem troca de Pokémon é o dedo do jogador |
| R8 | Lista fechada de permissões no manifesto, com teste que falha se surgir outra | Permissão nova entrar sem revisão |

**Base nos termos.** Os termos atuais (Scopely Explore, atualizados em 15/07/2026) proíbem acessar os serviços sem autorização, por exemplo com software de terceiros modificado ou não oficial, falsificar localização, usar bots ou raspagem e modificar os apps. As regras R1 a R7 mantêm o app fora de todos esses itens.

**Risco residual.** Não existe lista oficial de apps aprovados, e os termos permitem que o jogo verifique o aparelho em busca de software não autorizado. Um app que só lê a tela fica na mesma categoria de Poke Genie e Calcy IV, mas "risco zero" não pode ser prometido.

## Escopo

O app tem três funções: escanear um Pokémon, manter a coleção e sugerir o time da raid. A seleção do time dentro do jogo continua manual.

| Função | Como funciona | Fase |
| --- | --- | --- |
| Escanear Pokémon | Ao abrir um Pokémon no jogo, o app reconhece a tela sozinho e mostra um cartão com IV, porcentagem e nível; PC, PS, espécie e ataques são salvos na coleção sem nenhum toque extra | 2 |
| Coleção | Lista local com espécie, PC, PS, IVs, nível e ataques; editar, apagar e marcar favorito | 1 |
| Time por chefe escolhido | O jogador escolhe o chefe numa lista; o app mostra os 6 melhores da coleção | 1 |
| Time por raid detectada | Ao entrar na tela da raid, o app lê o nome do chefe sozinho e mostra o time num cartão sobreposto | 3 |
| Extras | Megas, sombrios, clima, backup em arquivo JSON, aviso de ataque fraco | 4 |

**Fora do escopo, de forma permanente:** qualquer toque automático, captura automática de Pokémon, mapa ou scanner, múltiplas contas, login, sincronização em nuvem e publicação em loja.

## Arquitetura

Com a leitura ligada, o app observa a tela, reconhece sozinho as telas que interessam e mostra um cartão; nenhum passo devolve nada ao jogo.

1. **Botão flutuante** (janela `TYPE_APPLICATION_OVERLAY`). Liga e desliga a leitura. Ligado, o app amostra a tela cerca de 2 vezes por segundo.
2. **Captura** de um quadro pelo MediaProjection (`VirtualDisplay` + `ImageReader`).
3. **Classificação da tela**: detalhe do Pokémon, avaliação ou raid, por textos-âncora.
4. **OCR no aparelho** com ML Kit Text Recognition, sem enviar imagem para fora.
5. **Parser**: transforma texto e pixels em campos (espécie, PC, PS, barras de IV, ataques, nome do chefe).
6. **Motor** em Kotlin puro: resolve nível e IV, calcula o ranking da raid.
7. **Banco local** (Room/SQLite) guarda a coleção.
8. **Cartão sobreposto** mostra o resultado e some com um toque.

Regras de implementação da captura:

- O Android exige consentimento do usuário antes de cada sessão de projeção, e o token vale para uma única sessão. O app pede uma vez ao ligar o botão flutuante e mantém a sessão até ser desligado.
- Com alvo em Android 14 ou superior, a captura roda num serviço em primeiro plano do tipo `mediaProjection`, com notificação fixa.
- Cada quadro passa primeiro por uma checagem barata (a tela mudou? é uma tela conhecida?); o OCR só roda quando a resposta é sim. O bitmap é descartado em seguida e nenhuma captura de tela é gravada em disco.
- A janela sobreposta não cobre os botões do jogo e é arrastável.

## Leitura do Pokémon e cálculo de IV

Na tela de detalhe o app mostra na hora o nível e o IV possível (valor único ou faixa); o IV exato é confirmado pelas três barras quando a avaliação é aberta.

| Campo | De onde vem | Método |
| --- | --- | --- |
| PC | Topo da tela de detalhe | OCR numérico |
| Espécie | Nome no topo; se houver apelido, o nome do doce | OCR + busca aproximada na lista de espécies em pt-BR |
| PS | Abaixo do nome | OCR numérico |
| Ataque rápido e carregado(s) | Parte de baixo da tela de detalhe | OCR + busca aproximada na lista de ataques |
| IV de Ataque, Defesa e PS | Barras da tela de avaliação | Medição de pixels: cada barra vale de 0 a 15 |
| Nível | Calculado | Posição do ponto no arco atrás do Pokémon (depende do nível de treinador, configurado no app) e custo de poeira; conferido pela fórmula de PC |

Só com a tela de detalhe, nível, PC e PS deixam uma ou algumas combinações de IV possíveis; o cartão mostra a faixa de porcentagem e marca o registro como "IV estimado". Ao abrir a avaliação do mesmo Pokémon, o app lê as barras, troca para "IV exato" e atualiza o registro. O mesmo Pokémon lido de novo atualiza o registro existente em vez de duplicar.

Fórmulas usadas:

```latex
PC = \max\left(10,\ \left\lfloor \frac{(Atk_{base}+IV_{atk})\,\sqrt{Def_{base}+IV_{def}}\,\sqrt{PS_{base}+IV_{ps}}\;CPM^{2}}{10} \right\rfloor\right)
```

```latex
PS = \max\left(10,\ \left\lfloor (PS_{base}+IV_{ps})\cdot CPM \right\rfloor\right)
```

Caso de teste obrigatório: Swampert (208/175/225) com IV 15/15/15 e CPM 0,7903 dá 2.974 PC.

Se a leitura tiver confiança baixa (nome não encontrado, PC e PS que não fecham com nenhum nível), o app abre um formulário para correção manual em vez de salvar um dado errado.

## Motor de time de raid

Para cada Pokémon da coleção, com os ataques que ele tem hoje, o motor estima dano por segundo (DPS) e dano total antes de desmaiar (TDO) contra o chefe e devolve os 6 melhores.

Dano de um ataque:

```latex
Dano = \left\lfloor \tfrac{1}{2}\cdot Poder \cdot \frac{Atk}{Def} \cdot Multiplicadores \right\rfloor + 1
```

```latex
Atk = (Atk_{base}+IV_{atk})\cdot CPM \qquad Def = (Def_{base}+IV_{def})\cdot CPM
```

Os multiplicadores são eficácia de tipo, STAB, clima e amizade. Os valores numéricos (eficácia, STAB, bônus de sombrio) e os dados do chefe (PS fixo e CPM por nível de raid) vêm de uma tabela de dados, não ficam fixos no código. O chefe usa IV 15 em tudo.

Cálculo, na versão 1 (analítica, sem simular a batalha):

1. Ciclo do atacante: quantos ataques rápidos carregam um ataque carregado; DPS do ciclo completo.
2. Dano recebido: média sobre todos os conjuntos de ataques possíveis do chefe.
3. Tempo vivo = PS do atacante dividido pelo dano recebido por segundo; TDO = DPS × tempo vivo.
4. Nota final = DPS³ × TDO, para favorecer dano sem ignorar resistência.
5. Restrições do time: no máximo 1 Mega; Pokémon repetidos da mesma espécie são permitidos.

Saída por Pokémon: posição, nota, DPS, e um aviso quando outro ataque da mesma espécie renderia bem mais. O aviso é só informação; o app não troca nada.

Validação: para 5 chefes, comparar o top 6 do app com o de um simulador conhecido (Pokebattler) usando a mesma coleção; aceitar se pelo menos 4 dos 6 coincidirem. Um simulador passo a passo fica para depois, se a versão analítica não passar nesse teste.

## Dados do jogo

Os dados vêm de um arquivo JSON público mantido pela comunidade e são convertidos, fora do celular, num arquivo enxuto que o app carrega.

| Dado | Fonte | Observação |
| --- | --- | --- |
| Espécies, status base, ataques, poder, energia, duração, tipos | [PokeMiners/game\_masters](https://github.com/PokeMiners/game_masters) | Repositório com a versão mais recente do Game Master em JSON |
| Nomes em pt-BR de espécies e ataques | [PokeMiners/pogo\_assets](https://github.com/PokeMiners/pogo_assets) | O repositório inclui os arquivos de tradução |
| Tabela de CPM por nível | Game Master | Necessária para PC, PS e dano |
| Chefes de raid ativos | Sem fonte oficial | Versão 1: o jogador escolhe a espécie e o nível da raid |

Fluxo de atualização:

1. Um script de build baixa o Game Master, extrai só o necessário e gera `gamedata.json` com número de versão.
2. O arquivo vai empacotado no APK e também fica no repositório do projeto no GitHub.
3. No app, o botão "Atualizar dados" baixa a versão nova desse repositório. É a única chamada de rede do app.

O app não extrai dados do jogo instalado nem dos servidores; só consome um arquivo público de terceiros.

## Stack, permissões e estrutura

Kotlin nativo com Jetpack Compose. Quase tudo no app é API nativa do Android (captura, janela sobreposta, serviço em primeiro plano), então React Native exigiria módulos nativos para o núcleo inteiro.

| Peça | Escolha |
| --- | --- |
| Linguagem e UI | Kotlin, Jetpack Compose (inclusive no cartão sobreposto) |
| Captura | MediaProjection + ImageReader |
| OCR | ML Kit Text Recognition, modelo embutido no APK |
| Banco | Room (SQLite) |
| Concorrência e JSON | Coroutines, kotlinx.serialization |
| Testes | JUnit no módulo de domínio; imagens de referência para o OCR |
| Distribuição | APK instalado direto pelo Android Studio |

Lista fechada de permissões (R8):

| Permissão | Para quê |
| --- | --- |
| `SYSTEM_ALERT_WINDOW` | Botão flutuante e cartão sobreposto |
| `FOREGROUND_SERVICE` | Manter o serviço de captura ativo |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Tipo de serviço exigido para captura de tela |
| `POST_NOTIFICATIONS` | Notificação fixa do serviço |
| `INTERNET` | Baixar `gamedata.json` |

Módulos:

- `domain`: Kotlin puro, sem Android. Fórmulas de PC, IV, dano e ranking. É onde ficam quase todos os testes.
- `data`: Room, carregamento do `gamedata.json`.
- `capture`: serviço de MediaProjection e janela sobreposta.
- `ocr`: classificação de tela, ML Kit e parsers.
- `app`: telas de coleção, escolha de chefe e configurações.

## Fases de entrega

A fase 1 já entrega o time de raid, com cadastro manual; a captura de tela entra depois, sobre um motor já testado.

| Fase | Entrega | Critério de aceite |
| --- | --- | --- |
| 0. Motor | Módulo `domain` e script que gera `gamedata.json` | Teste do Swampert passa; top 6 bate com o simulador em pelo menos 4 de 6 para 5 chefes |
| 1. App sem captura | Coleção com cadastro manual, escolha de chefe, top 6 | Cadastrar 20 Pokémon e obter o time em menos de 1 segundo, sem rede |
| 2. Escaneamento | Botão flutuante, captura, OCR, leitura das barras | Em 30 leituras reais, pelo menos 27 corretas sem correção manual; nenhuma leitura errada salva em silêncio |
| 3. Raid na tela | Leitura do chefe na tela da raid e cartão com o time | Chefe identificado em 9 de 10 raids; cartão aparece em menos de 2 segundos |
| 4. Extras | Megas, sombrios, clima, backup JSON | Backup restaura a coleção inteira num aparelho limpo |

Em todas as fases: o teste de permissões (R8) passa e nenhuma dependência de acessibilidade, VPN ou root entra no projeto.

## Riscos e pontos em aberto

O maior risco técnico é o OCR quebrar quando a interface do jogo mudar; o maior risco de conta é o residual descrito nas regras.

| Risco | Efeito | Mitigação |
| --- | --- | --- |
| O jogo verificar o aparelho por software não autorizado | Aviso ou punição na conta | Regras R1 a R8; sem root, sem acessibilidade, sem rede do jogo; desligar o botão flutuante quando não estiver usando |
| Atualização do jogo muda o layout | Leituras erradas | Âncoras por texto e posição relativa, não por pixel fixo; imagens de referência nos testes; correção manual sempre disponível |
| Leitura imprecisa das barras de IV | IV errado em 1 ponto | Conferir IV lido contra PC e PS; se não fechar, pedir correção |
| Apelido no lugar do nome da espécie | Espécie não identificada | Usar o nome do doce; depois, escolha manual |
| Consentimento de captura a cada sessão | Um toque extra ao ligar | Aceito; é regra do Android |
| Dados do jogo desatualizados | Ranking sem espécie ou ataque novo | Botão "Atualizar dados" e versão visível na tela |
| Imagens de teste com dados de outras pessoas | Nomes de outros treinadores no repositório | A tela da raid mostra nomes de outros jogadores: recortar ou borrar antes de salvar qualquer imagem de referência |

Pontos em aberto:

- Confirmar no aparelho se o Galaxy A34 5G está em One UI 8.5 (Android 16) ou One UI 9 (Android 17): a One UI 9 começou a chegar ao A34 em novembro de 2026, sem data anunciada para o Brasil.
- Confirmar se o Android 15 ou superior permite que um app saiba que está sendo capturado, e se o Pokémon GO usa isso.
- Valores exatos de PS e CPM dos chefes por nível de raid: conferir contra duas fontes antes da fase 0 fechar.

Decisões já fechadas:

- Aparelho-alvo único: Samsung Galaxy A34 5G, tela de 1080 × 2340. Os recortes de OCR são calibrados para essa proporção, mas definidos em percentual da tela.
- `minSdk` 34 (Android 14) e `targetSdk` 36 (Android 16). Isso cobre o aparelho nas duas versões possíveis e já impõe as regras atuais de captura e de serviço em primeiro plano.
- Idioma do jogo: pt-BR. O OCR e as listas de espécies e ataques usam só os nomes em português do Brasil.

## Fontes

- [Termos de Serviço do Scopely Explore](https://explore.scopely.com/terms/) (atualizados em 15/07/2026): seções 3.1, 4 e 6
- [Política de três strikes](https://niantic.helpshift.com/hc/faq/39-three-strike-discipline-policy)
- [Android: Media projection](https://developer.android.com/media/grow/media-projection)
- [Android: tipos de serviço em primeiro plano](https://developer.android.com/guide/components/fg-service-types)
- [Pokebattler: fórmula de PC e exemplo do Swampert](https://pokebattler.com/articles/2019/07/25/cracking-the-case-cp-formula-for-team-rocket-shadow-pokemon)
- [GamePress: mecânica de dano](https://pogo.gamepress.gg/node/2196)
- [Pokémon GO Hub: mecânica de dano](https://pokemongohub.net/post/wiki/damage-mechanics/)
- [PokeMiners/pogo\_assets](https://github.com/PokeMiners/pogo_assets)

As páginas do Android, Pokebattler, GamePress e Pokémon GO Hub foram consultadas pelos trechos retornados na busca; os termos do Scopely Explore foram lidos por resumo da página.
