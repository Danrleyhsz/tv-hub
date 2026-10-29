# TV Hub

Launcher para a TV TCL (Android TV 12), no visual do vídeo "TV hack — part 1": foto de paisagem em
tela cheia, relógio fino no canto, clima de Imperatriz-MA, pílula de dica e uma dock de vidro com
os apps. Sem recomendação, sem banner, sem patrocinado.

![Tela inicial](docs/tvhub-home.png)

## O que tem

- **Tela inicial** no gabarito: relógio grande em Inter Display Thin, data em português, clima em
  °C com a cidade em caixa alta, pílula de dica tirada da previsão e dock de vidro.
- **Fundo de fotos com temas** (Paisagens, Paisagens pintadas, Terra vista do espaço…), trocando a
  cada 15 minutos com fade de 1,2 s.
- **Protetor de tela de pinturas**: depois de 5 min parado entra uma pintura numa moldura com
  passe-partout (ou foto em tela cheia), com legenda de obra e artista, relógio pequeno e data.
  Pacotes Impressionistas e Mestres e ukiyo-e. Troca a cada 3 min; qualquer tecla volta.
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

## Temas do fundo e do protetor

Cada tema é uma pasta de imagens dentro da pasta do próprio app (sem permissão de armazenamento):

```
/sdcard/Android/data/com.petrobot.hume/files/Pictures/fundo/<tema>/      fundo da tela inicial
/sdcard/Android/data/com.petrobot.hume/files/Pictures/protetor/<tema>/   protetor de tela
```

Escolha em Ajustes → Tela inicial → **Tema das fotos**, **Imagens do protetor** e **Estilo do
protetor**. A legenda do protetor vem do nome do arquivo: `Artista - Título (ano).jpg`.

Os pacotes prontos (Art Institute of Chicago, CC0; NASA, domínio público) são baixados, cortados
para 1920x1080 e enviados por um script (Python 3 com `requests` e `Pillow`):

```bash
python tools/baixar-pacotes.py pacotes --push --adb "$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
```

Para um tema seu, crie a pasta e mande as fotos com `adb push`. Fotos soltas direto em
`Pictures/` também valem como fundo. **Desinstalar ou limpar os dados apaga as pastas** — guarde
as imagens no PC.

## Base e licença

Fork do [Hume Launcher](https://github.com/petrobot-admin/hume-launcher), de Petrobot, sob
[Apache 2.0](LICENSE) — a mesma licença vale para este fork. Veja o [NOTICE](NOTICE) para os
componentes de terceiros (fonte Inter sob SIL OFL, gerador de QR Code de Project Nayuki sob MIT).

O que mudou em cima da base está no [PROGRESS.md](PROGRESS.md).
