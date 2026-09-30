package com.k410sh4.a25lab.data

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.k410sh4.a25lab.model.CameraInfo
import com.k410sh4.a25lab.model.CameraProbeResult

class CameraProbe(context: Context) {
    private val manager = context.getSystemService(CameraManager::class.java)

    fun probe(): CameraProbeResult {
        val errors = mutableListOf<String>()
        val ids = runCatching { manager.cameraIdList.toList() }
            .getOrElse { error ->
                return CameraProbeResult(
                    errors = listOf(
                        "Falha ao listar Camera2 IDs: ${error.message ?: error::class.java.simpleName}",
                    ),
                )
            }

        val cameras = ids.mapNotNull { id ->
            runCatching { probeOne(id) }
                .onFailure { error ->
                    errors += "ID $id: ${error.message ?: error::class.java.simpleName}"
                }
                .getOrNull()
        }

        return CameraProbeResult(
            totalIds = ids.size,
            cameras = cameras,
            errors = errors,
        )
    }

    private fun probeOne(id: String): CameraInfo {
        val c = manager.getCameraCharacteristics(id)
        val capabilities = c
            .get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?.toSet()
            .orEmpty()

        val ois = c
            .get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            ?.map { mode ->
                when (mode) {
                    CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON -> "ON"
                    CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_OFF -> "OFF"
                    else -> mode.toString()
                }
            }
            .orEmpty()

        val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val maxJpeg = map
            ?.getOutputSizes(ImageFormat.JPEG)
            ?.maxByOrNull { it.width.toLong() * it.height.toLong() }
            ?.let { "${it.width}×${it.height}" }
            ?: "N/D"

        val pixelArray = c
            .get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            ?.let { "${it.width}×${it.height}" }
            ?: "N/D"

        return CameraInfo(
            id = id,
            facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_FRONT -> "Frontal"
                CameraCharacteristics.LENS_FACING_BACK -> "Traseira"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "Externa"
                else -> "Desconhecida"
            },
            hardwareLevel = hardwareLevelName(
                c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL),
            ),
            pixelArray = pixelArray,
            maxJpeg = maxJpeg,
            rawSupported =
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW in capabilities,
            manualSensor =
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in capabilities,
            manualPostProcessing =
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING in capabilities,
            logicalMultiCamera =
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA in capabilities,
            oisModes = ois,
        )
    }

    private fun hardwareLevelName(level: Int?): String = when (level) {
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
        else -> "N/D"
    }
}
