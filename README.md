# A25 Lab

Laboratório Android para descobrir e usar, de forma verificável, as capacidades que o Galaxy A25 5G expõe a aplicativos.

## Estado atual — v0.4.0

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
- inventário interno ampliado (build, ABI, kernel, tela, bateria, OpenGL ES e system features);
- cópia automática do inventário completo para a área de transferência ao abrir/atualizar;
- snapshot completo também salvo no armazenamento interno privado do app;
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


## Inventário automático

Na inicialização e sempre que **Atualizar + copiar** é usado, o app:

1. coleta o snapshot do dispositivo, build, SoC/ABI, RAM/storage, tela, bateria/térmico, rede, NFC, sensores, Camera2 e `PackageManager.systemAvailableFeatures`;
2. gera um relatório textual completo;
3. copia o relatório para a área de transferência;
4. salva uma cópia privada em `filesDir/a25lab_last_internal_specs.txt`.

Por privacidade, o inventário não coleta IMEI, número de telefone, Android ID, contas, contatos, histórico de localização ou conteúdo pessoal.


## Superpoderes — v0.3

A tela **Superpoderes** reúne seis modos de percepção ampliada usando apenas APIs Android e os sensores já confirmados no SM-A256E:

- **Visão Magnética:** vetor XYZ e intensidade total do magnetômetro em µT.
- **Detector de Movimento:** aceleração dinâmica e velocidade angular, com indicação relativa de movimento.
- **Visão de Luz:** lux do sensor padrão e leitura bruta do canal Samsung `light_cct` quando o stream puder ser aberto.
- **Orientação 3D:** yaw, pitch e roll derivados do `TYPE_ROTATION_VECTOR`.
- **Radar BLE:** dispositivos próximos, RSSI e classificação relativa de força do sinal.
- **Ouvido Espectral:** PCM local, RMS dBFS e frequência dominante por FFT.

O stream de sensores usado pela UI é limitado a aproximadamente 20 atualizações por segundo para evitar recomposições excessivas, embora os sensores continuem sendo amostrados pelo Android nas taxas solicitadas.

AOIS e VDIS são detectados e exibidos com o `minDelay` anunciado pelo HAL, mas a v0.3 **não afirma taxa efetiva** desses canais até um benchmark físico específico medir eventos por segundo e jitter.


## Visualizações 3D e UX — v0.4

A v0.4 corrige a ausência de representações 3D na tela Superpoderes e reorganiza a interface para priorizar leitura humana.

- **Orientação 3D:** cubo com projeção em perspectiva atualizado por yaw/pitch/roll do Rotation Vector.
- **Visão Magnética 3D:** eixos espaciais + vetor normalizado do campo magnético.
- métricas principais usam tipografia maior e rótulos curtos;
- detalhes AOIS/VDIS ficam recolhidos em **Ver detalhes técnicos**;
- valores longos deixam de competir com rótulos em duas colunas apertadas;
- BLE e áudio usam ações claras de iniciar/parar;
- textos técnicos foram reduzidos para observações curtas.

As visualizações são desenhadas com Compose Canvas, sem engine 3D externa. Isso reduz dependências e mantém o render leve para este caso de uso.
