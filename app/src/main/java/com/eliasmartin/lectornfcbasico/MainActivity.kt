package com.eliasmartin.lectornfcbasico

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.*
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.content.IntentFilter
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import com.eliasmartin.lectornfcbasico.ui.theme.LectorNFCBasicoTheme
import java.io.IOException
import java.nio.ByteBuffer


class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private val viewModel: NFCViewModel by viewModels()
    private val sector9KeyA = byteArrayOf(
        0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)

        setContent {
            LectorNFCBasicoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NFCReaderScreen(
                        modifier = Modifier.padding(innerPadding),
                        nfcStatusMessage = viewModel.nfcStatusMessage,
                        cardId = viewModel.cardId,
                        sectorData = viewModel.sectorData,
                        saldo = viewModel.saldo // Pasamos el saldo
                    )
                }
            }
        }

        if (nfcAdapter == null) {
            viewModel.nfcStatusMessage = "Este dispositivo no soporta NFC."
            return
        }

        if (!nfcAdapter!!.isEnabled) {
            viewModel.nfcStatusMessage = "NFC está desactivado. Actívalo en ajustes."
        }
    }

    override fun onResume() {
        super.onResume()
        setupForegroundDispatch()
    }

    override fun onPause() {
        super.onPause()
        stopForegroundDispatch()
    }

    private fun setupForegroundDispatch() {
        if (nfcAdapter != null && nfcAdapter!!.isEnabled) {
            val techListsArray = arrayOf(
                arrayOf(MifareClassic::class.java.name),
            )

            val intentFiltersArray = arrayOf<IntentFilter>()
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray)
        }
    }

    private fun stopForegroundDispatch() {
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action
        ) {
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                val id = bytesToHexString(tag.id)
                viewModel.cardId = "ID de la tarjeta: $id"
                readSector37(tag)
            }
        }
    }

    private fun readSector37(tag: Tag) {
        val mifareClassic = MifareClassic.get(tag)
        viewModel.sectorData = ""
        viewModel.saldo = "" // Limpiamos el saldo
        if (mifareClassic != null) {
            try {
                mifareClassic.connect()
                val sectorIndex = 9
                val blockIndex = 37
                val authenticate: Boolean

                if (mifareClassic.authenticateSectorWithKeyA(sectorIndex, sector9KeyA)) {
                    authenticate = true
                } else if (mifareClassic.authenticateSectorWithKeyB(sectorIndex, sector9KeyA)) {
                    authenticate = true
                } else {
                    authenticate = false
                }


                if (authenticate) {
                    val blockNumber = mifareClassic.sectorToBlock(sectorIndex) + (blockIndex % 4)

                    val blockData = mifareClassic.readBlock(blockNumber)
                    viewModel.sectorData = "Bloque 37: ${bytesToHexString(blockData)}"

                    //Calculamos el saldo
                    val saldoHex = bytesToHexString(blockData).substring(0, 4) //Primeros 4 caracteres (2 bytes)
                    viewModel.saldo = calcularSaldo(saldoHex)


                } else {
                    viewModel.sectorData = "Fallo autenticacion"
                }
            } catch (e: IOException) {
                viewModel.sectorData = "Error leyendo sector: ${e.message}"
            } finally {
                try {
                    mifareClassic.close()
                } catch (e: IOException) {
                    viewModel.sectorData = "Error al cerrar: ${e.message}"
                }
            }
        } else {
            viewModel.sectorData = "No es una tarjeta Mifare Classic"
        }
    }

    private fun bytesToHexString(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02X", b))
        }
        return sb.toString()
    }

    private fun calcularSaldo(valorHex: String): String {
        if (valorHex.length != 4) {
            return "Error: Valor hexadecimal inválido."
        }

        val reversedHexString = valorHex.substring(2, 4) + valorHex.substring(0, 2)
        val intValue = reversedHexString.toLong(16) // Usamos toLong para evitar problemas con valores grandes
        val saldo = (intValue / 2.0) / 100.0

        return String.format("%.2f €", saldo)
    }

}

@Composable
fun NFCReaderScreen(nfcStatusMessage: String, cardId: String, sectorData: String, saldo:String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = nfcStatusMessage,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = cardId,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (sectorData.isNotEmpty()) {
            Text(
                text = sectorData,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (saldo.isNotEmpty()){
            Text(
                text = "Saldo: $saldo",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

class NFCViewModel : ViewModel() {
    var nfcStatusMessage by mutableStateOf("Acerque una tarjeta NFC")
    var cardId by mutableStateOf("")
    var sectorData by mutableStateOf("")
    var saldo by mutableStateOf("") // Nuevo estado para el saldo
}