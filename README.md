# W7 Cockpit — Chery Tiggo · Android Launcher 1.0

Launcher Android alternativo, projetado para a central multimídia **Winca W7**, tela paisagem **1024 × 600**, hardware de 2 GB RAM e Android compatível com API 27 ou superior.

## Experiência e interface
- Interface retrabalhada **W7 Cockpit** (padrão) ou **W7 Hybrid**, com escolha persistente.
- Layout automotivo escuro com azul, animações sutis, botões de toque amplo, relógio, navegação, telemetria, mídia e acessos rápidos.
- Símbolo Chery no cabeçalho (arquivo de referência de Wikimedia Commons) e ícone W7 personalizado.
- Lista de aplicativos **em grade de ícones reais**, grandes e rolável.

## Funcionalidades
- **Navegação:** abre Google Maps como opção principal e Waze como alternativa. A navegação por GPS e voz é realizada pelos aplicativos oficiais.
- **Mídia dentro do painel:** navegador WebView dedicado, com YouTube, YouTube Music, Spotify e opção de colar links do YouTube/Spotify. Pode abrir os aplicativos oficiais em tela cheia. A reprodução depende de compatibilidade do WebView, rede, login e restrições dos provedores.
- **Telemetria ELM327:** Bluetooth Classic / SPP via Android, seleção do dispositivo emparelhado, protocolo ELM327 auto, RPM OBD PID 010C, velocidade 010D, temperatura de arrefecimento 0105, tensão via ATRV. Dados exibidos apenas quando recebidos, sem números simulados.
- **Sistema da Winca:** botões para projeção CarPlay/Android Auto, rádio FM, Bluetooth do carro e aplicativo OBD alternativo, com seleção dos aplicativos instalados. O acesso depende de como o firmware Winca expõe cada app ao Android.
- **Configurações:** Android, troca de visual, escolha de ELM327, vinculação de aplicativos e abertura do launcher original quando for encontrado.
- **Segurança:** interface local separada do WebView de páginas externas, sem root e sem modificação de MCU, firmware, comando de volante, câmera de ré ou rádio nativo.

## Como baixar o APK compilado
1. Abra a aba **Actions** neste repositório: https://github.com/marcioribeiromkt/W7-Custom-Launcher/actions
2. Escolha a execução **Build Android APK** mais recente e concluída com sucesso (verde).
3. Em **Artifacts**, baixe `W7CustomLauncher-debug`.
4. Extraia o ZIP e copie o arquivo `app-debug.apk` para um pendrive.
5. Na Winca, instale por cima da versão anterior e abra **W7 Cockpit**.
6. Primeiro abra como aplicativo comum, sem definir imediatamente como launcher HOME padrão.

## Configuração inicial
- Toque em **ADAPTADOR** no painel OBD2 e selecione o ELM327 emparelhado **ao Android**. Se a central usa Bluetooth controlado exclusivamente pela MCU, o ELM327 pode não aparecer para apps Android; use o aplicativo original OBD ou uma interface compatível.
- Vá à engrenagem e vincule os aplicativos nativos de **Projeção, Rádio FM e Bluetooth**, identificados pelos nomes reais da Winca.
- No painel de mídia, toque em YouTube/Spotify/YT Music. Use o ícone de abrir aplicativo externo caso o serviço limite a reprodução no WebView.
- A tela **TODOS OS APLICATIVOS** apresenta ícones grandes, não uma lista estreita.

## Limitações técnicas importantes
- Este APK é **compilado e instalável**, mas a execução de todas as integrações não pode ser garantida sem compatibilidade do hardware e dos serviços externos. Não existe incorporação arbitrária das telas nativas do Waze/Maps/Spotify/YouTube no painel de terceiros.
- Não há alteração de ROM, MCU, CAN bus nem sistema operacional. Serviços nativos de rádio, câmera, telefone e comandos do volante continuam controlados pelo firmware de fábrica.
- A imagem SVG do símbolo Chery no cabeçalho é carregada por HTTPS de Wikimedia Commons; requer conexão para a primeira visualização. O ícone instalado W7 é vetorial e local.
- OBD2 exige adaptador ELM327 Bluetooth Classic SPP visível ao Android, não apenas ao rádio do carro.
- Para segurança, faça configurações e testes **com o veículo parado**.

## Licenciamento
Projeto de personalização pessoal. O símbolo CHERY é marca registrada do titular e a arte de referência é identificada em https://commons.wikimedia.org/wiki/File:Chery_symbol.svg .
