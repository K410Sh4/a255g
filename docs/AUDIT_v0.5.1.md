# A25 Lab v0.5.1 — Auditoria de hardening

Base auditada: branch `feature/a25-lab-v1`.

## Escopo

Auditoria estática e correções em lifecycle, sensores, 3D, áudio, BLE, GNSS, NFC, Camera2, rede, inventário, benchmark, privacidade, UI e CI.

## Falhas confirmadas no código e tratamento aplicado

1. **Lifecycle de streams** — `onPause()` encerrava módulos sem restauração. Agora background suspende e foreground restaura somente o que faz sentido para a tela e intenção do usuário.
2. **NFC excessivamente ativo** — Reader Mode ficava ligado em todo foreground. Agora é restrito à tela NFC.
3. **Quaternion inicial** — suavização partia da identidade, podendo produzir deriva após auto-centralização. O primeiro sample real passa a ser a baseline do filtro.
4. **Filtro dependente da taxa** — alpha fixo mudava o comportamento conforme sample rate. Agora o alpha deriva de delta de tempo.
5. **Aceleração dinâmica aproximada** — `|a|-g` era usado mesmo havendo sensor linear. O app prioriza `TYPE_LINEAR_ACCELERATION` e marca explicitamente o fallback.
6. **Qualidade magnética invisível** — accuracy era descartada. Agora é exposta ao usuário.
7. **Semântica CCT presumida** — apenas `values[0]` era mostrado como dado CCT. Agora o vetor vendor completo é mostrado sem atribuir unidade/semântica não validada.
8. **Race de áudio** — stop/start rápido podia deixar loop antigo concorrer com nova sessão. Sessões agora possuem geração e loops antigos são invalidados.
9. **Leitura parcial de AudioRecord** — frames incompletos eram descartados. Agora são acumulados até o frame FFT estar completo.
10. **FFT com alocação no hot path** — arrays eram recriados a cada frame. O analyzer mantém workspace reutilizável.
11. **FFT falsa em silêncio/DC** — silêncio podia produzir bin mínimo e offset DC podia vazar para baixas frequências. Agora silêncio retorna 0 Hz e a média é removida antes da janela.
12. **BLE com identidade instável** — RSSI fazia parte da chave anônima, podendo duplicar o mesmo emissor conforme o sinal mudava. A chave deixou de depender do RSSI.
13. **BLE sem limite/expiração** — lista podia crescer durante scans longos. Agora há limite, expiração e manutenção periódica.
14. **Permissão BLE acima do necessário** — scan exigia CONNECT. O fluxo atual solicita somente SCAN e não coleta endereço.
15. **Rede 'Online' falsa** — capacidade INTERNET era confundida com acesso validado. Estados foram separados.
16. **Camera2 all-or-nothing** — exceção em um ID podia apagar o inventário inteiro. O probe agora isola falhas por câmera.
17. **Contagem Camera2 ambígua** — número de probes válidos podia ser confundido com IDs disponíveis. Total e lidos com sucesso foram separados.
18. **Trabalho de inicialização na UI** — probes, formatação e persistência podiam bloquear a thread principal. O inventário foi movido para executor de I/O.
19. **Clipboard em background** — conclusão tardia do probe podia copiar dados após o app sair do foreground. A cópia automática agora aguarda retorno.
20. **Benchmark instável** — uma única execução sofria com JIT/warmup. Agora usa aquecimento e mediana.
21. **UI duplicada** — componentes antigos permaneciam no mesmo arquivo após o redesign premium. Foram removidos.
22. **Backup/transferência** — política estava incompleta para Android moderno. Regras explícitas foram adicionadas.
23. **Release não exercitado na CI** — somente debug era montado. A CI agora também executa `assembleRelease` com R8/shrink.

## Itens que não podem ser declarados corrigidos sem o SM-A256E físico

- orientação visual dos eixos 3D versus movimento percebido;
- valores/índices reais de `com.samsung.sensor.light_cct`;
- frequência efetiva e jitter de AOIS/VDIS;
- qualidade dos campos de GNSS raw;
- efeito real da fonte de áudio UNPROCESSED no firmware Samsung;
- consumo térmico/energético em sessões longas;
- comportamento de BLE em ambientes muito densos.

Esses itens permanecem explicitamente classificados como **REQUER TESTE EM DISPOSITIVO**.

24. **Estado inicial de foreground incorreto** — o ViewModel podia considerar o app visível antes do primeiro `onResume`, permitindo efeito tardio de clipboard durante inicialização. O estado agora começa suspenso e só muda no lifecycle real.
25. **Unidades de armazenamento ambíguas** — o formatter dividia por 1024 mas rotulava KB/MB/GB, e o `StatFs(filesDir)` era apresentado como armazenamento físico total. O relatório agora usa KiB/MiB/GiB e identifica corretamente a partição de dados acessível ao app.
26. **GNSS não retomava após reativar localização** — se o usuário desligasse e religasse a localização durante uma sessão ativa, o estado voltava mas os callbacks não. O receiver de mudança de modo reinicia a sessão somente enquanto o pedido do usuário continua ativo.


27. **Preview de clipboard exposto** — o relatório de hardware podia aparecer em texto claro na prévia visual do clipboard do Android. A cópia agora usa o hint `android.content.extra.IS_SENSITIVE`; a compatibilidade final depende do sistema respeitar esse hint.
28. **Dados NFC persistindo durante a sessão** — UID/tecnologias/NDEF permaneciam em memória após sair da tela NFC. Agora os dados transitórios da tag são descartados ao navegar para outra tela.
29. **NDEF sem limite de apresentação** — payloads de texto grandes podiam causar UI desnecessariamente pesada. A apresentação é limitada a 4096 caracteres sem alterar a leitura técnica das tecnologias da tag.
30. **Dependências e SDK defasados** — lint apontou Android API 37 e AndroidX/Compose mais recentes. A atualização foi testada, mas não mantida porque o SDK 37 ainda não está disponível no canal estável usado pela CI; as versões compatíveis com API 36 permanecem pinadas e a decisão está documentada nos itens 32–34.
31. **Instrumentation sem verificação de compilação** — a CI agora também monta o APK de testes instrumentados, garantindo que a suíte Android continue compilável mesmo antes de adicionarmos execução em emulador/dispositivo.


32. **API 37 indisponível no canal estável da CI** — a tentativa objetiva de instalar `platforms;android-37` falhou no `sdkmanager` do runner. Compile/target permanecem em 36 até a plataforma 37 estar disponível no canal estável; o aviso `OldTargetApi` é desabilitado de forma documentada, sem esconder outros warnings.
33. **Actions em runtime Node 20** — o runner já sinalizou depreciação para `actions/checkout@v4` e `actions/upload-artifact@v4`. A CI foi migrada para majors com Node 24 (`checkout@v6` e `upload-artifact@v6`).


34. **AndroidX mais novo incompatível com compileSdk 36** — a atualização para Compose 1.12.1/Core 1.19.1/Lifecycle 2.11.0 falhou objetivamente em `checkDebugAarMetadata`: esses artefatos exigem compileSdk 37. Como API 37 não está disponível no canal estável da CI, as versões que já passaram na baseline foram restauradas. O lint ignora apenas `OldTargetApi` e `GradleDependency` por decisão documentada; demais warnings continuam promovidos a erro.
35. **setup-gradle ainda em Node 20** — o log da CI apontou `gradle/actions/setup-gradle@v4`. A action foi atualizada para `@v6`, cujo runtime é Node 24 segundo a documentação atual do projeto.


36. **Race assíncrona no NFC** — uma leitura iniciada na tela NFC podia terminar depois da navegação e recolocar UID/NDEF na memória. Leituras agora possuem geração, apenas a mais recente pode publicar e qualquer saída/background invalida resultados pendentes.
37. **Snapshot salvo em diretório de backup comum** — apesar das regras de backup já bloquearem exportação, o snapshot agora usa `noBackupFilesDir` como defesa adicional.
38. **Callbacks tardios após ViewModel encerrado** — tarefas de I/O podiam tentar publicar estado após `onCleared()`. O ViewModel agora invalida publicações tardias explicitamente.
39. **Yaw/Pitch/Roll não correspondiam ao 3D suavizado** — os números vinham do sample bruto enquanto o telefone 3D usava quaternion filtrado. As métricas agora são derivadas do mesmo quaternion suavizado.
40. **Reinício BLE podia entregar estado antigo ao callback novo** — a sessão anterior agora é parada e desacoplada antes de registrar o callback da nova sessão; falhas de scan também cancelam manutenção periódica.
41. **Receiver GNSS mais exposto que o necessário** — o receiver dinâmico de mudança de modo de localização agora é `RECEIVER_NOT_EXPORTED`.
42. **Dashboard permitia ações durante refresh** — Atualizar, Copiar e Compartilhar agora refletem o estado real do snapshot e evitam ações concorrentes durante a atualização.


43. **Abertura de AudioRecord na thread principal** — construir/iniciar a rota de áudio podia bloquear a UI em dispositivos/firmwares lentos. A abertura agora ocorre no executor dedicado, com estado `starting`, cancelamento por geração e descarte seguro de sessões obsoletas.
44. **Start/stop durante abertura de áudio** — uma sessão podia ser parada antes de terminar de abrir e ainda assim publicar estado depois. A geração da sessão é validada antes e depois de abrir o recorder, e recursos de sessões obsoletas são liberados.


45. **Permissão de áudio podia ser revogada entre UI e executor** — a checagem feita antes de enfileirar a abertura não era suficiente para lint nem para uma revogação concorrente. O executor revalida `RECORD_AUDIO` imediatamente antes de tocar nas APIs protegidas.
46. **Warnings de testes por APIs depreciadas** — o smoke test Compose migrou para a API junit4 v2 e o teste duplicado que exercitava o alias depreciado de aceleração foi removido.


47. **GNSS podia permanecer passivo** — registrar `GnssStatus` e `GnssMeasurementsEvent` não garante que o receptor físico seja iniciado quando nenhum cliente de localização está ativo. O botão Iniciar agora cria uma solicitação GPS explícita, limitada à tela/foreground, enquanto as coordenadas retornadas são descartadas imediatamente.
48. **Privacidade GNSS/NFC descrita de forma ampla demais** — o relatório agora diferencia inventário persistido de dados transitórios: coordenadas GNSS são descartadas e UID/NDEF de NFC não entram no inventário.


49. **Texto vindo de rádio/NFC era tratado como confiável** — nomes BLE e NDEF são entradas externas e podiam carregar controles Unicode/bidi capazes de confundir a apresentação. Foi criado um sanitizador de display que preserva Unicode normal, torna controles perigosos visíveis e limita tamanho por code point.
50. **Dados BLE transitórios permaneciam no ViewModel após sair** — nomes/RSSI agora são removidos ao deixar a tela Bluetooth ou enviar o app ao background. O scan pode ser retomado pela intenção do usuário, mas começa com estado visual limpo.
51. **Estado NFC podia ficar desatualizado ao entrar na tela** — a disponibilidade/estado do adaptador é atualizada ao navegar para NFC, além da atualização no resume.


52. **Teste de relatório ficou inconsistente após a correção de privacidade** — o teste ainda buscava a frase antiga “Não coletado: IMEI”. A asserção foi atualizada e ganhou regressão explícita garantindo que UID/NDEF transitórios não apareçam no inventário persistido.
53. **Nome BLE podia usar quebras de linha para deformar a UI** — o sanitizador ganhou modo de linha única; nomes anunciados por dispositivos próximos agora removem newline/CR/tab sem perder Unicode legítimo.
54. **NDEF conectado ainda usava snapshot cached** — depois de conectar à tag, a leitura passa a usar a mensagem NDEF atual em vez do cache do objeto Tag.
55. **Botão Voltar do sistema nas categorias raiz encerrava o app** — Percepção, Conectividade e Laboratório agora voltam para Início; apenas Início mantém o comportamento padrão de sair.


56. **GNSS capability vs callback ativo estavam misturados** — o campo `rawMeasurementsSupported` virava falso quando o Android recusava o registro do callback, confundindo capacidade anunciada com estado operacional. Agora existem estados separados para suporte e atividade.
57. **Cleanup GNSS após revogação de permissão** — `removeUpdates` era pulado quando a permissão havia sido revogada, podendo deixar um listener registrado por mais tempo que o necessário. O cleanup agora é sempre tentado e tratado como best-effort.
58. **Cleanup BLE após revogação de permissão** — o mesmo padrão existia no scan BLE. `stopScan` agora é tentado mesmo após revogação e `SecurityException` é tratada.
59. **Métricas live obsoletas** — Áudio, GNSS e sensores podiam continuar mostrando valores da sessão anterior após parar/sair/ocultar o app. Os estados transitórios agora são zerados nas transições correspondentes.
60. **Sanitização quebrava emoji/idiomas legítimos** — ZWNJ/ZWJ eram tratados como controles perigosos. Eles passam a ser preservados; controles bidi e de direção continuam neutralizados.
61. **Actions da CI dependiam de tags mutáveis** — checkout, setup-java, setup-gradle e upload-artifact agora são fixados em commits imutáveis, mantendo comentário com o major correspondente.


62. **Falhas silenciosas no inventário estático** — sensores, system features, rede e snapshot usavam fallback vazio sem deixar claro que havia ocorrido exceção. O snapshot agora mantém avisos de probe no estado, dashboard e relatório exportado.
63. **Benchmark CPU falhava sem explicação** — exceções do benchmark agora viram diagnóstico visível em vez de simplesmente retornar para “Não medido”.
64. **Diagnóstico evita vazar mensagens arbitrárias de exceção** — os avisos persistidos registram componente + classe da exceção; mensagens internas potencialmente sensíveis não são copiadas automaticamente para o relatório.


65. **OpenGL ES em system features podia aparecer como inteiro codificado** — `FeatureInfo.glEsVersion` usa major/minor empacotados em 32 bits. O inventário agora decodifica corretamente, por exemplo `0x00030002 → 3.2`, com teste unitário.
66. **Percentual de bateria não era limitado** — valores anômalos vindos do sticky intent podiam produzir porcentagem fora de 0–100. O snapshot agora faz clamp defensivo.
67. **Strings locais podiam corromper a estrutura do relatório exportado** — campos de build, sensores, câmeras, erros e system features passam pelo mesmo sanitizador de linha única antes de copiar/compartilhar.
68. **Sanitizador de linha única não tinha regressão própria** — foi adicionado teste garantindo remoção de newline/tab sem quebrar Unicode normal.


69. **Leitura NFC compartilhava executor com inventário/persistência** — uma operação de tag lenta podia atrasar refresh do hardware e escrita do snapshot. NFC agora possui executor dedicado e lifecycle próprio.
70. **Instrumentation era apenas compilada, não executada** — a CI ganhou um job separado em emulador Android 16/API 36 que executa `connectedDebugAndroidTest` após build/unit/lint. A action do emulador também está fixada em commit imutável.


71. **Build dependia de Gradle global** — o repositório não possuía Wrapper, então ambiente local e CI podiam usar distribuições diferentes. Foram adicionados os arquivos oficiais do Gradle Wrapper 9.6.0, incluindo JAR versionado, scripts POSIX/Windows e `distributionSha256Sum` da distribuição binária.
72. **CI ainda ignorava o Wrapper recém-adicionado** — build, lint, unit tests e instrumentation agora chamam `./gradlew`; `setup-gradle` permanece apenas para cache/diagnóstico e valida automaticamente o JAR oficial.
73. **Documentação operacional estava defasada** — README ainda citava `BLUETOOTH_CONNECT`, `filesDir` e instalação global de Gradle. A documentação foi alinhada ao manifesto, `noBackupFilesDir`, Wrapper e smoke tests Android 16.


74. **Contrato BLE ainda permitia armazenar endereço** — o modelo de dispositivo ainda possuía campo `address`, mesmo preenchido com texto neutro. O campo foi removido do contrato. A deduplicação mantém apenas o objeto `BluetoothDevice` durante a sessão e publica um ID sequencial efêmero, nunca o endereço.
75. **Dimensões de tela podiam refletir a janela do app** — `resources.displayMetrics` não é a fonte ideal para especificação física. O snapshot agora prioriza `Display.Mode.physicalWidth/physicalHeight/refreshRate`, com fallback seguro.
76. **Falha no fechamento NDEF apagava uma leitura válida** — leitura e fechamento foram separados; texto já decodificado é preservado e erros de open/read/close aparecem como diagnóstico sem entrar no inventário persistido.
77. **3D renderizava identidade durante reinicialização do sensor** — após resume podia existir um frame com quaternion identidade. A tela agora aguarda a primeira amostra real antes de renderizar a pose e mostra estado de espera legível.
78. **Métrica de aceleração indisponível parecia zero real** — quando nenhum sensor/fallback estava disponível, a UI mostrava `0 m/s²`. Agora exibe `N/D`.
79. **Intensidade magnética zero ainda desenhava halo mínimo** — a escala visual passa a zero de verdade, evitando indicar campo não medido antes da primeira amostra.
80. **Atualizações de dependências eram manuais** — foi adicionada configuração do Dependabot para Gradle e GitHub Actions em cadência semanal, sem auto-merge.
81. **Privacidade BLE sem regressão estrutural** — teste unitário verifica que `BleDeviceInfo` não volte a expor um campo `address`.


82. **Zero magnético de inicialização parecia uma leitura real** — antes do primeiro evento do magnetômetro a tela podia exibir `0 µT`, embora nenhuma amostra tivesse chegado. O estado agora possui `magneticSampleReady` e a visualização aguarda explicitamente a primeira medição.


83. **Avisos do probe eram passados ao formatter mas não exportados** — o ViewModel já acumulava falhas de sensores/rede/build, porém o relatório ignorava `probeWarnings`. O inventário agora inclui uma seção `[AVISOS DO PROBE]` sanitizada.
84. **Falha manual de rede parecia simplesmente “sem rede”** — exceções do refresh manual eram convertidas silenciosamente em estado vazio. `NetworkState` agora carrega diagnóstico e a tela/relatório diferenciam falha de probe de ausência real de conexão.
85. **Camera2 ainda podia persistir mensagens arbitrárias de exceção** — os erros por ID/global agora registram somente a classe da exceção, preservando diagnóstico sem copiar mensagens internas potencialmente sensíveis.


86. **Áudio parado parecia uma medição real** — o estado inicial exibia `0 Hz` e `-120 dBFS` mesmo sem captura ativa. Frequência e nível agora mostram `N/D` até existir uma sessão de áudio em execução.
87. **GNSS não verificado parecia estado real** — os defaults diziam implicitamente “localização ativada / GPS indisponível / raw não suportado” antes de qualquer probe. Esses campos passaram a aceitar estado desconhecido e a UI mostra “não verificado” até uma tentativa real.


88. **Métricas GNSS inativas pareciam zeros medidos** — satélites visíveis/usados e contagem raw agora mostram `N/D` enquanto o receptor/callback não estiver ativo, e “Não suportado” somente após capacidade realmente verificada.
89. **Superfície de permissões não tinha contrato instrumentado completo** — o teste Android agora garante ausência de `INTERNET`, `BLUETOOTH_CONNECT`, `CAMERA`, localização em background, telefonia e contatos, além de confirmar que apenas a Activity launcher fica exportada.
90. **Glifos decorativos podiam poluir TalkBack** — símbolos usados como ícones na navegação e cards foram removidos da árvore semântica; os rótulos humanos permanecem como fonte acessível.
91. **Ações do inventário podiam apertar em larguras/fontes maiores** — Atualizar e Copiar agora dividem a largura disponível de forma previsível, reduzindo colisões de layout.
92. **BLE não tratava toda falha de runtime ao iniciar/parar scan** — além de revogação de permissão, estados inesperados do adaptador agora viram diagnóstico seguro; nomes vazios de anúncio recebem fallback legível.
93. **UID NFC vazio aparecia como campo em branco** — identificadores vazios agora são tratados como ausentes.
94. **Lookup NFC ainda usava API estática antiga** — Activity e ViewModel passaram a obter o adaptador por `NfcManager`, reduzindo dependência de API de lookup depreciada.


95. **Estado NFC podia ficar desatualizado com Quick Settings** — ligar/desligar NFC sem pausar a Activity não garantia novo `onResume`. A Activity agora observa `ACTION_ADAPTER_STATE_CHANGED`, atualiza a UI e sincroniza Reader Mode em tempo real.
96. **Scan BLE podia ficar marcado como ativo após desligar o Bluetooth** — a manutenção periódica verifica se o scanner continua disponível; se o adaptador for desligado ou o scanner desaparecer, a sessão é encerrada com diagnóstico e referências internas são limpas.
97. **Separadores Unicode U+2028/U+2029 escapavam da sanitização** — ambos passam a ser neutralizados como controles de layout, com regressão unitária.
98. **NDEF grande era decodificado integralmente antes do limite visual** — o parser limita a janela de decodificação a 16 KiB, preserva limite visual de 4096 caracteres e indica truncamento.
99. **Decoder NDEF não tinha cobertura unitária direta** — foram adicionados testes para Text RTD UTF-8, payload malformado, controles bidi e truncamento.
100. **Artefatos de CI não carregavam checksum dentro do pacote** — a pipeline agora gera `SHA256SUMS.txt` para APK debug e release unsigned e publica junto dos artefatos.


101. **Limite BLE não era realmente aplicado** — o mapa era indexado por `BluetoothDevice`, mas a remoção do item mais antigo tentava usar a chave textual exibida na UI. O limite de 128 dispositivos agora remove a chave real do mapa.
102. **Estado GNSS raw podia permanecer “ativo” após stop/falha** — encerramento, exceções e desativação do provedor agora limpam `rawMeasurementsActive` sem apagar a informação separada de suporte do hardware.
103. **Zero inicial era apresentado como leitura real de movimento** — aceleração e giroscópio agora distinguem stream registrado, primeira amostra recebida e indisponibilidade. A UI mostra “Aguardando” até existir dado real.
104. **Falha de registro de giroscópio/magnetômetro/luz ficava invisível** — o estado de cada stream relevante agora registra se o listener foi realmente aceito pelo SensorManager.
105. **NaN/Infinity do HAL podiam contaminar quaternion e Canvas 3D** — amostras não finitas são ignoradas antes de entrar no estado; normalização de quaternion/vetor também possui fallback finito coberto por testes.


101. **BLE dependia de broadcast que exige BLUETOOTH_CONNECT** — `ACTION_STATE_CHANGED` exige `BLUETOOTH_CONNECT` em Android moderno, enquanto o radar foi deliberadamente projetado com apenas `BLUETOOTH_SCAN`. A dependência foi removida; a manutenção da sessão verifica a disponibilidade real de `BluetoothLeScanner` e encerra o scan se o rádio desaparecer.
102. **ReaderCallback NFC podia tocar estado do Compose fora da main thread** — a entrega da tag agora é serializada na thread principal antes de consultar lifecycle/tela; o parsing continua no executor NFC dedicado.
103. **Benchmark continuava consumindo CPU fora da tela/background** — o benchmark agora coopera com interrupção, é mantido por `Future` e cancelado ao sair do Compute, enviar o app ao background ou limpar o ViewModel.
104. **Executor NFC podia ser encerrado entre checagem e enqueue** — `RejectedExecutionException` passa a ser tratada sem crash.
105. **Referência 3D sobrevivia à suspensão do stream** — quando a orientação fica indisponível, a referência é descartada e o primeiro sample da sessão seguinte volta a recentralizar a pose.


106. **Solicitação concorrente de permissões podia sobrescrever a ação pendente** — a UI agora mantém um estado de requisição em andamento e ignora novos launches até a resposta do Android, evitando substituir a ação associada ao diálogo aberto.
107. **Smoke test de navegação dependia de texto não único** — “Percepção” também aparece no conteúdo do dashboard. A navegação inferior ganhou tags semânticas estáveis de teste e o smoke test passou a usá-las.
108. **Texto de ambiente ainda chamava o canal vendor de CCT validado** — a UI foi alinhada à auditoria: o canal permanece identificado como `light_cct` até sua semântica ser confirmada no SM-A256E.


109. **Cleanup BLE continha chamada órfã após simplificação do monitoramento** — chamadas para um receiver já removido permaneceram em stop/erro e quebravam compilação. O cleanup agora depende apenas do scanner + maintenance loop realmente existentes.
110. **Conclusão antiga do benchmark podia sobrescrever uma execução nova** — cancelamento e reinício rápido agora usam geração monotônica; callbacks de uma execução obsoleta não podem zerar o estado nem apagar o Future da execução atual.
