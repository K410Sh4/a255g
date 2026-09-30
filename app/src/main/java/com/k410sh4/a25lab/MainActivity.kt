package com.k410sh4.a25lab

import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.k410sh4.a25lab.ui.A25LabApp
import com.k410sh4.a25lab.ui.AppViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        setContent {
            A25LabApp(viewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshNfcState()
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

    override fun onPause() {
        // Privacy and battery invariant: live radios/sensors never keep running
        // merely because the Activity moved to the background.
        viewModel.stopLiveModules()
        nfcAdapter?.disableReaderMode(this)
        super.onPause()
    }
}
