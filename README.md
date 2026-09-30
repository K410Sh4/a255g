# A25 Lab

Laboratório Android para descobrir e usar, de forma verificável, as capacidades que o Galaxy A25 5G expõe a aplicativos.

## Estado atual — v0.1.0

Implementado:

- inventário de hardware/Android;
- inventário completo de sensores;
- acelerômetro, giroscópio e magnetômetro ao vivo;
- GNSS status e callback de medições brutas;
- scanner Bluetooth Low Energy;
- Audio Lab com PCM 44,1 kHz, RMS dBFS e FFT;
- probe Camera2 para RAW, manual sensor, OIS, hardware level e resoluções;
- NFC Reader Mode com ID, tecnologias e NDEF Text;
- NetworkCapabilities;
- baseline CPU determinística;
- compartilhamento de relatório de capacidades;
- CI para build + testes + lint + APK.

## Princípio do projeto

**Capacidade do SoC não é tratada como capacidade acessível do APK.**

O aplicativo consulta o Android/firmware/HAL e mostra o que está realmente exposto. Isso evita alegações falsas, por exemplo assumir acesso à NPU apenas porque o Exynos 1280 possui um bloco neural.

## Stack

- Kotlin
- Jetpack Compose 1.11.4 + Material 3 1.3.2
- Android Gradle Plugin 9.4.1
- Gradle 9.6.0 na CI
- compile/target SDK 36
- min SDK 31

## Permissões

- `ACCESS_FINE_LOCATION`: callbacks GNSS e medições brutas.
- `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`: descoberta BLE moderna.
- `RECORD_AUDIO`: Audio Lab local.
- `NFC`: leitura de tags em primeiro plano.
- `ACCESS_NETWORK_STATE`: leitura das capacidades da rede ativa.

O probe Camera2 consulta apenas características e, por isso, não pede permissão de câmera nesta versão. O app também não declara `INTERNET`: nenhum áudio, GNSS, BLE ou NFC é enviado para servidores.

## Build local

Requer JDK 17, Android SDK 36, Build Tools 36.0.0 e Gradle 9.6.0.

```bash
gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK:

`app/build/outputs/apk/debug/app-debug.apk`

## Próximas validações no SM-A256E

1. registrar quais campos de `GnssMeasurement` são efetivamente preenchidos;
2. medir Camera2 por lente física/lógica;
3. validar fonte `UNPROCESSED` do microfone e latência real;
4. adicionar benchmark LiteRT com o mesmo modelo em backends disponíveis e medir latência/RAM/temperatura;
5. somente depois considerar modo avançado ADB/Shizuku para diagnósticos adicionais.

Veja `docs/ARCHITECTURE.md`.


### Nota de compatibilidade do CI

O runner atual não disponibiliza `platforms;android-37` via SDK Manager. As bibliotecas Compose foram fixadas na linha 1.11.4 e Lifecycle 2.10.0, anteriores à migração transitiva para compileSdk 37, mantendo compile/target SDK 36 sem suprimir a validação de AAR metadata.
