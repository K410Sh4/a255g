# A25 Lab — Arquitetura

## Objetivo

A25 Lab é um laboratório Android voltado inicialmente ao Galaxy A25 5G (SM-A256E), mas usa APIs públicas e detecção de capacidades em runtime para evitar assumir recursos que o firmware/HAL não expõe.

## Fluxo

UI Jetpack Compose
→ `AppViewModel`
→ probes/repositories pequenos e independentes
→ Android framework / HAL exposto
→ estado observável na UI

Não há serviço em background permanente e nenhum dado de microfone, GNSS, BLE ou NFC é enviado para servidores.

## Componentes

- `HardwareProbe`: Build, RAM, storage, display, bateria, thermal e features básicas.
- `SensorRepository`: inventário completo e stream de acelerômetro/giroscópio/magnetômetro.
- `GnssRepository`: GNSS status + `GnssMeasurementsEvent`.
- `BleRepository`: descoberta BLE e RSSI.
- `AudioAnalyzer`: captura PCM local + RMS + FFT.
- `CameraProbe`: Camera2 capabilities/HAL.
- `NfcParser`: reader mode + NDEF text.
- `NetworkProbe`: capacidades da rede ativa.
- `ComputeBenchmark`: baseline CPU determinístico; não é usado para alegar desempenho de NPU.

## Decisões

### Sem Hilt nesta fase

Há uma única activity e poucos repositórios sem grafo de dependências complexo. Introduzir DI framework agora aumentaria código e build sem resolver um problema real. Se novos módulos passarem a compartilhar dependências com escopos diferentes, Hilt pode ser introduzido de forma localizada.

### Sem root/Shizuku

A versão inicial mede o máximo disponível por APIs Android suportadas. Isso cria uma baseline segura e reproduzível antes de adicionar um modo avançado opcional.

### IA/NPU

O Exynos 1280 possui aceleração de IA no SoC, mas isso não prova que um APK consiga endereçar diretamente a NPU. O caminho correto é adicionar um modelo de referência LiteRT e medir os backends/delegates que estiverem realmente disponíveis no SM-A256E.

## Validação

A CI executa:

1. build Debug;
2. testes unitários;
3. Android Lint;
4. upload do APK somente se as etapas anteriores passarem.

A validação de sensores, GNSS raw, Camera2, NFC, BLE, áudio e aceleração de IA exige teste físico no SM-A256E.


## Inventário interno automático (v0.2)

`HardwareProbe` passou a coletar também build/board/product/bootloader, ABIs, kernel, OpenGL ES, display, bateria e a lista de `systemAvailableFeatures`.

`ReportFormatter` produz um inventário detalhado de cada sensor e câmera. `AppViewModel` copia esse inventário automaticamente para o clipboard após a varredura e persiste o último snapshot somente no diretório privado do app. Identificadores pessoais e de telecomunicações não fazem parte do probe.


## Superpowers Engine (v0.3)

`SensorRepository` ganhou um fluxo específico de superpoderes. Ele registra acelerômetro, giroscópio, magnetômetro, luz, rotation vector e tenta abrir o canal vendor `com.samsung.sensor.light_cct`. O estado derivado é publicado no máximo a cada 50 ms para reduzir carga de UI.

`SuperpowerMath` mantém cálculos puros/testáveis de magnitude vetorial, aceleração dinâmica e classificações relativas. `SuperpowersScreen` apenas renderiza estado e aciona BLE/áudio; BLE e microfone continuam exigindo permissão explícita.

AOIS (`com.samsung.sensor.gyroscope_aois`) e VDIS (`com.samsung.sensor.vdis_gyro`) são tratados como capacidades detectadas, não como streams validados. Isso evita transformar `minDelay` declarado em uma alegação de frequência real sem medição.


## Visualização 3D e legibilidade (v0.4)

`Realtime3D.kt` contém renderizadores Compose Canvas para:
- cubo de orientação em perspectiva;
- vetor magnético tridimensional projetado em eixos isométricos.

`ThreeDMath` concentra rotação Euler e normalização vetorial em funções puras cobertas por testes unitários.

A tela `SuperpowersScreen` foi reestruturada com hierarquia humana:
1. visualização;
2. métrica principal;
3. métricas secundárias;
4. detalhes técnicos sob demanda.

A taxa de atualização de estado permanece limitada pelo `SensorRepository` a cerca de 20 atualizações de UI por segundo, evitando que a renderização 3D transforme cada evento bruto de sensor em recomposição Compose.
