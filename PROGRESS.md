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
- [x] Protetor de tela no gabarito dos prints 05/06: depois de 20 s parado (Ajustes → Home →
      "Full-screen carousel when idle"), a paisagem toma a tela inteira e fica só um relógio
      pequeno com a data no canto superior esquerdo. Qualquer tecla volta pra home.
- [x] Interface em português: assistente de primeira execução, ajustes, central de controle,
      busca, perfis, permissões e avisos.
- [x] Conteúdo indiano fora: jornais (agora g1, Folha, Agência Brasil, UOL e BBC Brasil),
      provedores de streaming (Netflix, Prime, Apple TV, Disney+, Max, Globoplay, Crunchyroll),
      região do TMDB/JustWatch em BR, idiomas, voz em pt-BR e teclado de acentos no lugar do
      devanágari.

## Fotos do fundo

Ficam numa pasta do próprio app, sem precisar de permissão de armazenamento:

```bash
adb -s 192.168.1.147:5555 push foto.jpg /sdcard/Android/data/com.petrobot.hume/files/Pictures/
```

Estão lá 4 paisagens de amostra (Unsplash, uso livre). Troque por fotos suas quando quiser; com a
pasta vazia o fundo cai no gradiente Aurora. **Desinstalar ou limpar os dados apaga a pasta.**

## Falta

- [ ] Bilhetes do celular (prints 04, 07, 08) — reaproveitar o `present/AdminServer` + QR.
- [ ] Assistente de IA (prints 09–11) — adiado a pedido.
- [ ] Medir o desempenho do foco na TV (seção 6 do spec).
- [ ] Legenda da foto no canto inferior direito do protetor de tela (print 06) e o bilhete
      translúcido no canto superior direito (print 05) — depende dos bilhetes.
- [ ] Repositório privado no GitHub, script de instalação e de rollback.

## Decisões / pendências

- Launcher **não** foi definido como padrão: a TV continua abrindo o launcher de fábrica
  (`com.google.android.apps.tv.launcherx`).
- Permissão de localização **negada**; o clima usa cidade fixa.
- Imperatriz-MA é o padrão no código (`Weather.DEF_PLACE`), mas o assistente de primeira execução
  ainda oferece "Use My Location" como primeira opção — escolher ela volta a detectar a cidade
  pelo IP (deu Parauapebas). Dá para corrigir em Ajustes → Weather → Location.
