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

1. validação automática do Gradle Wrapper;
2. build Debug;
3. build Release com shrink/R8;
4. testes unitários;
5. compilação dos testes instrumentados;
6. Android Lint com warnings relevantes promovidos a erro;
7. smoke tests instrumentados em emulador Android 16/API 36;
8. upload de relatórios e APKs somente conforme o resultado das etapas.

O build usa exclusivamente o Gradle Wrapper 9.6.0 versionado no repositório. A distribuição binária possui SHA-256 fixado em `gradle-wrapper.properties`.

A validação de sensores vendor, GNSS raw real, Camera2 físico, NFC, BLE, áudio e aceleração de IA ainda exige teste físico no SM-A256E.


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

## UI premium e navegação por categorias (v0.5)

A navegação continua sem NavController, porque o app ainda possui uma Activity e um estado simples de tela mantido pelo AppViewModel. Foram adicionados quatro destinos raiz: Dashboard, Percepção, Conectividade e Laboratório. As telas de detalhe retornam ao grupo pai correspondente.

PremiumHome.kt concentra dashboard e hubs de categoria. A25LabTheme fornece paleta clara/escura própria sem depender de bibliotecas visuais extras.

## Pose 3D por quaternion

SensorRepository usa SensorManager.getQuaternionFromVector para obter a orientação do Rotation Vector e suaviza o quaternion com nlerp antes de publicar o estado.

QuaternionMath concentra normalização, conjugação, multiplicação, rotação de vértices, orientação relativa e interpolação normalizada. Essas operações são cobertas por testes unitários.

O render PhonePose3D usa um quaternion relativo a uma referência recenterizável. Isso reduz gimbal lock e evita a ordem Euler arbitrária usada na v0.4.


## Hardening de lifecycle e concorrência (v0.5.1)

`AppViewModel` diferencia suspensão de background de parada intencional do usuário. Sensores ligados à tela são restaurados ao voltar ao foreground; GNSS, BLE e áudio só são retomados se o usuário os havia iniciado.

`MainActivity` mantém NFC Reader Mode restrito à tela NFC. O inventário estático roda fora da thread principal e o clipboard não é alterado automaticamente enquanto o app estiver em background.

`AudioAnalyzer` usa geração de sessão para invalidar loops antigos após stop/start. A leitura preenche frames completos antes da FFT, e a implementação `FftAnalyzer` reutiliza buffers e remove componente DC.

`BleRepository` mantém limite de dispositivos, expiração por tempo e publicação limitada para UI. O scan não depende de `BLUETOOTH_CONNECT` e o app não coleta endereço dos dispositivos observados.

`GnssRepository` usa `GnssCapabilities` para suporte a medições e observa o modo de localização do Android; não depende do callback de status de measurements legado.

`CameraProbe` isola falhas por ID e preserva resultados válidos.

## Limites de validação

Continuam exigindo aparelho físico:

- alinhamento perceptivo dos eixos do modelo 3D;
- semântica dos valores do sensor vendor `light_cct`;
- taxa efetiva/jitter de AOIS e VDIS;
- campos efetivamente preenchidos em `GnssMeasurement`;
- processamento real aplicado à fonte de áudio solicitada como `UNPROCESSED`;
- consumo, temperatura e estabilidade em sessões longas.


## Build reproduzível e supply chain

O repositório contém `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` e `gradle-wrapper.properties`. O Wrapper está fixado no Gradle 9.6.0, com checksum SHA-256 da distribuição `-bin`. A CI usa `gradle/actions/setup-gradle`, que valida o JAR do wrapper contra checksums oficiais antes de executar os comandos.

As GitHub Actions usadas no workflow são referenciadas por commit imutável, reduzindo o risco de uma tag upstream mudar de conteúdo.
