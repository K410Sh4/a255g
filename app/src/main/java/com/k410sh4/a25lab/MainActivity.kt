package com.k410sh4.a25lab

import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import com.k410sh4.a25lab.ui.A25LabApp
import com.k410sh4.a25lab.ui.AppViewModel
import com.k410sh4.a25lab.ui.Screen

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null
    private var resumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nfcAdapter = getSystemService(NfcManager::class.java)?.defaultAdapter

        setContent {
            DisposableEffect(viewModel.screen) {
                syncNfcReaderMode()
                onDispose {
                    disableNfcReaderMode()
                }
            }

            A25LabApp(viewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        viewModel.refreshNfcState()
        viewModel.onAppForeground()
        syncNfcReaderMode()
    }

    override fun onPause() {
        resumed = false
        viewModel.onAppBackground()
        disableNfcReaderMode()
        super.onPause()
    }

    private fun syncNfcReaderMode() {
        if (!resumed || viewModel.screen != Screen.Nfc) {
            disableNfcReaderMode()
            return
        }

        runCatching {
            nfcAdapter?.enableReaderMode(
                this,
                { tag -> viewModel.onNfcTag(tag) },
                NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NFC_BARCODE,
                null,
            )
        }
    }

    private fun disableNfcReaderMode() {
        runCatching {
            nfcAdapter?.disableReaderMode(this)
        }
    }
}
