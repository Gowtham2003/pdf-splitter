package dev.gowthamg.pdfsplitter

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class PdfViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState

    fun processFile(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = UiState.Processing
                
                // Get original file name without extension
                val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    cursor.getString(nameIndex).substringBeforeLast(".")
                } ?: "unknown"
                
                val outputDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // For Android 10 and above, use MediaStore
                    null
                } else {
                    // For older versions, use external storage
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "PDF Splitter/$fileName")
                        .also { it.mkdirs() }
                }
                
                // Open PDF document
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val pdfReader = PdfReader(inputStream)
                    val pdfDoc = PdfDocument(pdfReader)
                    val numberOfPages = pdfDoc.numberOfPages
                    
                    for (i in 1..numberOfPages) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            // Save using MediaStore for Android 10+
                            val contentValues = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, "page_${i}.pdf")
                                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/PDF Splitter/$fileName")
                            }

                            val pdfUri = context.contentResolver.insert(
                                MediaStore.Files.getContentUri("external"),
                                contentValues
                            )

                            pdfUri?.let { uri ->
                                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                                    val pdfWriter = PdfWriter(outputStream)
                                    val newPdf = PdfDocument(pdfWriter)
                                    pdfDoc.copyPagesTo(i, i, newPdf)
                                    newPdf.close()
                                }
                            }
                        } else {
                            // Legacy way for older Android versions
                            val outputFile = File(outputDir, "page_${i}.pdf")
                            val pdfWriter = PdfWriter(FileOutputStream(outputFile))
                            val newPdf = PdfDocument(pdfWriter)
                            pdfDoc.copyPagesTo(i, i, newPdf)
                            newPdf.close()
                        }
                    }
                    
                    pdfDoc.close()
                    _uiState.value = UiState.Success(
                        numberOfPages,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) 
                            "${Environment.DIRECTORY_DOCUMENTS}/PDF Splitter/$fileName"
                        else 
                            outputDir?.absolutePath ?: ""
                    )
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }
}

sealed class UiState {
    object Initial : UiState()
    object Processing : UiState()
    data class Success(val pageCount: Int, val outputPath: String) : UiState()
    data class Error(val message: String) : UiState()
} 