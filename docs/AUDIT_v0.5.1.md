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
