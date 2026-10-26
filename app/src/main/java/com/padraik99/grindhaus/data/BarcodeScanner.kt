package com.padraik99.grindhaus.data

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed interface ScanResult {
    data class Code(val value: String) : ScanResult
    data object Cancelled : ScanResult
    data class Failed(val reason: String?) : ScanResult
}

fun interface BarcodeScanner {
    suspend fun scan(): ScanResult
}

/**
 * Google Play services' scanner. It runs its own camera screen, so Grindhaus never asks
 * for camera permission. Retail barcodes only (EAN and UPC), with auto-zoom.
 */
class GmsBarcodeScanner(private val context: Context) : BarcodeScanner {
    override suspend fun scan(): ScanResult = suspendCancellableCoroutine { cont ->
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { code ->
                if (cont.isActive) cont.resume(code.rawValue?.let(ScanResult::Code) ?: ScanResult.Failed("No number in that barcode"))
            }
            .addOnCanceledListener { if (cont.isActive) cont.resume(ScanResult.Cancelled) }
            .addOnFailureListener { e -> if (cont.isActive) cont.resume(ScanResult.Failed(e.message)) }
    }
}
