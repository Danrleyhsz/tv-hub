# PROGRESS — TV Hub (launcher da TCL)

Objetivo: reproduzir o launcher do vídeo "TV hack — part 1" na TCL Smart TV Pro (Android 12,
1920x1080, `192.168.1.147:5555`). Gabarito: `launcher-tv-spec/prints/`.

## Base

Fork de [Hume Launcher](https://github.com/petrobot-admin/hume-launcher) (Apache 2.0), Java puro,
sem Gradle e sem bibliotecas. Escolhido porque já tem dock de vidro, clima Open-Meteo, control
center, protetor de tela, perfis, servidor web de pareamento com QR e foco/D-pad funcionando.
Descartado: `Deepu-mohan/tv-home-launcher` (exige Android 14, a TV é 12).

## Como compilar

```bash
export ANDROID_HOME="/c/Users/Danrley/AppData/Local/Android/Sdk"
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot"
bash check.sh                     # checagens fora do aparelho
bash build.sh && adb -s 192.168.1.147:5555 install -r build/tvos-home.apk
```

JDK **17**, não o 21: o d8 do build-tools 34.0.0 estoura NullPointerException nas classes anônimas
compiladas pelo javac 21. `build.sh` foi ajustado para achar os binários `.exe`/`.bat` do SDK no
Windows e para usar `jar` quando não existe `zip` (Git Bash não traz `zip`).

## Feito

- [x] Base escolhida, compilada e instalada na TV (APK 1,2 MB).
- [x] Home no gabarito do print 01: relógio fino grande (Inter Display Thin), data em português,
      clima de Imperatriz-MA em °C com cidade em caixa alta, pílula de dica e dock de vidro.
- [x] Carrossel de recomendações e notícias desligado por padrão (o spec proíbe conteúdo
      patrocinado na home); o relógio pequeno saiu da barra de status, que ficou só com o ícone
      de rede e os botões.
- [x] Fundo de paisagem (`Paisagens` em Ajustes → Home → Background), trocando a cada 15 min com
      fade de 1,2 s.
- [x] Clima, previsão e textos da home em português.
- [x] Protetor de tela no gabarito dos prints 05/06: depois de 5 min parado (Ajustes → Tela
      inicial → "Protetor de tela depois de"), a paisagem toma a tela inteira e fica só um relógio
      pequeno com a data no canto superior esquerdo. Qualquer tecla volta pra home.
- [x] Interface em português: assistente de primeira execução, ajustes, central de controle,
      busca, perfis, permissões e avisos.
- [x] Repositório privado https://github.com/Danrleyhsz/tv-hub, com README, LICENSE e NOTICE da
      base, e os scripts `instalar.bat`, `definir-como-padrao.bat` e `restaurar.bat`. O instalar e
      o restaurar foram rodados de ponta a ponta; o definir-como-padrao não, porque troca a tela
      inicial da TV.
- [x] Conteúdo indiano fora: jornais (agora g1, Folha, Agência Brasil, UOL e BBC Brasil),
      provedores de streaming (Netflix, Prime, Apple TV, Disney+, Max, Globoplay, Crunchyroll),
      região do TMDB/JustWatch em BR, idiomas, voz em pt-BR e teclado de acentos no lugar do
      devanágari.
- [x] Controle pelo celular (no lugar dos bilhetes do print 07): página em `/controle` servida
      pelo próprio launcher, com pareamento por PIN e QR em Ajustes → Controle pelo celular.
      Setas, OK, Voltar, Início, Recentes, volume e abrir apps.
- [x] Temas (2026-10-29): pastas `Pictures/fundo/<tema>` e `Pictures/protetor/<tema>`, escolhidas
      em Ajustes → Tela inicial. Protetor estilo "quadro" (moldura, passe-partout e parede escura)
      ou foto em tela cheia, com legenda de obra e artista no canto inferior direito, trocando a
      cada 3 min. Pacotes: fundo Paisagens (as 4 de amostra) e Natureza; protetor Impressionistas
      e Mestres e ukiyo-e. Script em `tools/baixar-pacotes.py`.
- [x] Fundo refeito (2026-10-29): os pacotes "Paisagens pintadas" e "Terra vista do espaço" saíram.
      Vinham de originais menores que a tela (o AIC serve 1686 px de largura, e boa parte das fotos
      da NASA é menor ainda), então o corte para 1920x1080 ampliava e borrava. No lugar entrou
      "Natureza", do Wikimedia Commons, só com originais de 2560x1440 para cima; `save()` agora
      recusa qualquer imagem de fundo menor que a tela, para o erro não voltar.

- [x] Fundo vivo (2026-10-29): zoom e deslize lentos no fundo, um ciclo de 2,5 min montado com
      múltiplos inteiros do ângulo, então ele fecha sem salto. O zoom mínimo de 1,08 abre a margem
      que o deslize usa, por isso nenhuma borda fica descoberta; os dois ImageViews do fundo se
      movem juntos, senão a foto pularia durante o crossfade. Para quando outro app está na frente
      ou o protetor cobre a tela. Reaproveita o `startWallpaperDrift()` que veio da base e nunca
      tinha sido chamado.
- [x] Fundo por hora (2026-10-29): `Themes.forHour()` separa as fotos por brilho médio e escolhe a
      faixa conforme o horário — claras das 10h às 16h, escuras das 19h às 6h, as do meio no resto.
      Tema com menos de 6 fotos usa todas. O brilho é um palpite grosseiro, lido de uma miniatura
      e guardado por arquivo.

- [x] Protetor refeito (2026-10-29): só a imagem na tela, sem relógio, sem legenda e sem moldura —
      a ideia é parecer um quadro na parede, e qualquer coisa desenhada por cima entrega que é uma
      tela. Saíram os pacotes de pintura: quadro é retrato, e cortá-lo para 16:9 mostra um pedaço
      dele; o acervo também não entrega resolução para tela cheia. No lugar entrou "Vistas", 16
      fotos do Commons (costas, cidades, lagos, cachoeiras, astronomia) na mesma regra do fundo.
      Com isso saíram `Themes.frame()`, `Themes.caption()`, o relógio do protetor e a opção
      "Estilo do protetor".

- [x] Movimento no protetor (2026-10-29): o mesmo zoom e deslize do fundo, agora num `driftOn()`
      que os dois chamam. O deslize passou a sair da margem que o próprio zoom abre (45% dela), em
      vez de um número solto — mexer no zoom não tem mais como descobrir a borda da tela.

## Imagens

Veja "Temas do fundo e do protetor" no README. **Desinstalar ou limpar os dados apaga as pastas.**

## Falta

- [ ] Assistente de IA (prints 09–11) — adiado a pedido.
- [ ] Medir o desempenho do foco na TV (seção 6 do spec).
- [ ] Setas e OK em qualquer app: o Android só libera isso para um serviço de acessibilidade a
      partir do 13 (`GLOBAL_ACTION_DPAD_*`), e a TV está no 12. Hoje as setas só mexem na tela do
      próprio launcher; volume, Início, Voltar, Recentes e abrir app funcionam em qualquer lugar.

## Decisões / pendências

- Launcher **é** a tela inicial da TV desde 2026-10-26. Para isso o `definir-como-padrao.bat`
  desativou `com.google.android.apps.tv.launcherx`, `com.tcl.tv.tclhome_passive` e
  `com.google.android.tungsten.setupwraith` — esse último registra uma tela inicial de recuperação
  com prioridade maior que a nossa, e sem desativá-lo o botão Home caía numa tela vazia. Os três
  estão anotados em `launchers-desativados.txt` e o `restaurar.bat` reativa exatamente eles.
- Permissão de localização **negada**; o clima usa cidade fixa.
- Imperatriz-MA é o padrão no código (`Weather.DEF_PLACE`), mas o assistente ainda oferece "Usar
  minha localização" como primeira opção — escolher ela volta a detectar a cidade pelo IP. Dá para
  corrigir em Ajustes → Clima → Localização.
- O assistente de primeira execução foi concluído na TV em 2026-10-26, com o clima em Imperatriz,
  fundo Paisagens, prateleira de cima desligada e localização negada.
- 2026-10-29: o APK passou a ser compilado em outro PC, com outra chave de debug, então o TV Hub
  foi desinstalado e reinstalado (ajustes zerados, assistente refeito com as mesmas escolhas,
  protetor em "Tela cheia do launcher"). Para atualizar sem perder dados, compile sempre com o
  mesmo `debug.keystore` (está fora do git; há uma cópia em `%USERPROFILE%\.android\tvhub-debug.keystore`).
- O protetor do sistema escolhido na TV é `com.gathin.tvhome/.ClockDream`, que foi desativado na
  limpeza; por isso o protetor fica em "Tela cheia do launcher".
