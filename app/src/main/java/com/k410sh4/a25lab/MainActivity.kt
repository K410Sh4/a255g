package com.k410sh4.a25lab

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.ContextCompat
import com.k410sh4.a25lab.ui.A25LabApp
import com.k410sh4.a25lab.ui.AppViewModel
import com.k410sh4.a25lab.ui.Screen

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null
    private var resumed = false
    private var nfcStateReceiverRegistered = false

    private val nfcStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == NfcAdapter.ACTION_ADAPTER_STATE_CHANGED) {
                viewModel.refreshNfcState()
                syncNfcReaderMode()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

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
        registerNfcStateReceiver()
        viewModel.refreshNfcState()
        viewModel.onAppForeground()
        syncNfcReaderMode()
    }

    override fun onPause() {
        resumed = false
        viewModel.onAppBackground()
        disableNfcReaderMode()
        unregisterNfcStateReceiver()
        super.onPause()
    }

    private fun registerNfcStateReceiver() {
        if (nfcStateReceiverRegistered) return

        runCatching {
            ContextCompat.registerReceiver(
                this,
                nfcStateReceiver,
                IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            nfcStateReceiverRegistered = true
        }
    }

    private fun unregisterNfcStateReceiver() {
        if (!nfcStateReceiverRegistered) return

        runCatching {
            unregisterReceiver(nfcStateReceiver)
        }
        nfcStateReceiverRegistered = false
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
