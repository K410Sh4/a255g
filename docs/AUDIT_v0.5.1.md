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
30. **Dependências e SDK defasados** — lint apontava Android API 37 e versões estáveis mais recentes de AndroidX/Compose. O projeto foi elevado para compile/target 37 e versões estáveis atuais, com warnings de lint promovidos a erro.
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
