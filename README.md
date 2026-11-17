# W7 Custom Launcher — versão de teste 0.2

Launcher Android em paisagem, desenvolvido para teste na central Winca W7 (1024 × 600).

## Funcionalidades implementadas
- Tela HOME alternativa com dois modos selecionáveis: W7 Cockpit e W7 Hybrid.
- Navegação via abertura do Waze ou Google Maps.
- Atalhos de mídia: Spotify, YouTube Music e YouTube.
- Lista dos aplicativos instalados.
- Pacotes dos atalhos configuráveis por toque em engrenagem.
- Preferência de skin salva localmente.

## Ainda não implementado
- Telemetria OBD2 ELM327 real.
- Reprodução de vídeo dentro do painel.
- Navegação incorporada.
- Integração com rádio nativo, comandos de volante, câmera de ré e MCU.

## APK: instalação de teste
1. Abra a aba **Actions** deste repositório.
2. Entre na execução **Build Android APK** que esteja marcada em verde (success).
3. Na seção **Artifacts**, baixe **W7CustomLauncher-debug**.
4. Extraia o ZIP e copie `app-debug.apk` para um pendrive FAT32.
5. Na multimídia, abra o gerenciador de arquivos e instale o APK, autorizando a instalação de fontes desconhecidas se necessário.
6. Abra **W7 Custom Launcher** pelo menu de apps antes de configurá-lo como HOME padrão.
7. Teste Waze, YouTube, Spotify, retorno ao launcher, Bluetooth, câmera de ré e comandos do volante. Só então considere defini-lo como HOME padrão.

### Segurança e reversão
O app não altera a MCU nem os aplicativos nativos. Para reverter o HOME padrão, abra as configurações Android, procure aplicativos padrão / app de início e selecione o launcher original. A opção exata varia conforme o firmware Winca. Se a interface não mostrar essa configuração, não desinstale o launcher original.

**Aviso:** versão de teste, ainda não validada fisicamente na Winca W7. Não teste vídeo nem configure o aparelho enquanto dirige.
