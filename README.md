# TV Hub

Launcher para a TV TCL (Android TV 12), no visual do vídeo "TV hack — part 1": foto de paisagem em
tela cheia, relógio fino no canto, clima de Imperatriz-MA, pílula de dica e uma dock de vidro com
os apps. Sem recomendação, sem banner, sem patrocinado.

![Tela inicial](docs/tvhub-home.png)

## O que tem

- **Tela inicial** no gabarito: relógio grande em Inter Display Thin, data em português, clima em
  °C com a cidade em caixa alta, pílula de dica tirada da previsão e dock de vidro.
- **Fundo de paisagem** que troca a cada 15 minutos com fade de 1,2 s, lido de uma pasta na TV.
- **Protetor de tela**: depois de 20 s parado a paisagem toma a tela inteira, com um relógio
  pequeno e a data. Qualquer tecla volta.
- **Central de controle**, busca (apps, ajustes, filmes e séries), entradas HDMI, perfis, timer de
  sono e protetor — tudo em português.

## Como usar

Precisa de: JDK **17**, Android SDK com build-tools 34.0.0 e platform android-34, e a TV com
depuração por Wi-Fi ligada. O IP da TV está no topo dos `.bat`.

```bash
export ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot"
bash check.sh     # checagens fora do aparelho
bash build.sh     # gera build/tvos-home.apk
```

Depois, no Explorer:

| Script | O que faz |
|---|---|
| `instalar.bat` | Instala o APK na TV e abre. **Não** mexe na tela inicial. |
| `definir-como-padrao.bat` | Faz o botão Home abrir o TV Hub, desativando (sem apagar) as outras telas iniciais. Anota o que desativou. |
| `restaurar.bat` | Desfaz tudo: reativa o que foi desativado, devolve o botão Home para a tela de fábrica e desinstala o TV Hub. |

O `restaurar.bat` funciona mesmo que o TV Hub nunca tenha virado padrão.

> JDK 17, não o 21: o `d8` do build-tools 34 estoura NullPointerException com as classes anônimas
> compiladas pelo javac 21.

## Fotos do fundo

Ficam numa pasta do próprio app, sem precisar de permissão de armazenamento:

```bash
adb -s 192.168.1.147:5555 push foto.jpg /sdcard/Android/data/com.petrobot.hume/files/Pictures/
```

Com a pasta vazia o fundo cai no gradiente Aurora. **Desinstalar ou limpar os dados apaga a
pasta** — guarde as fotos no PC.

## Base e licença

Fork do [Hume Launcher](https://github.com/petrobot-admin/hume-launcher), de Petrobot, sob
[Apache 2.0](LICENSE) — a mesma licença vale para este fork. Veja o [NOTICE](NOTICE) para os
componentes de terceiros (fonte Inter sob SIL OFL, gerador de QR Code de Project Nayuki sob MIT).

O que mudou em cima da base está no [PROGRESS.md](PROGRESS.md).
