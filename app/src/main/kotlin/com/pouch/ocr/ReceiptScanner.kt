package com.pouch.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.pouch.domain.ReceiptParser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reads text from a receipt photo with Google ML Kit (on the phone, offline, free).
 * Third-party integration for Stage 3.
 */
class ReceiptScanner(private val context: Context) {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun scan(imageUri: Uri): ReceiptParser.Result {
        val image = InputImage.fromFilePath(context, imageUri)
        val text = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        val lines = text.textBlocks
            .flatMap { it.lines }
            .map { line ->
                ReceiptParser.Line(
                    text = line.text,
                    top = line.boundingBox?.top ?: 0,
                    bottom = line.boundingBox?.bottom ?: 0,
                )
            }
            .sortedBy { it.top }
        return ReceiptParser.parse(lines)
    }
}
