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
import com.itextpdf.kernel.pdf.PdfDate
import com.itextpdf.kernel.pdf.PdfString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor

class PdfViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState

    private val _pdfInfo = MutableStateFlow<PdfInfo?>(null)
    val pdfInfo: StateFlow<PdfInfo?> = _pdfInfo

    private fun parsePdfDate(dateStr: String): Calendar? {
        return try {
            // PDF date format: D:YYYYMMDDHHmmSSOHH'mm'
            // Example: D:20240223141509+01'00'
            val calendar = Calendar.getInstance()
            if (dateStr.startsWith("D:")) {
                val date = dateStr.substring(2) // Remove "D:"
                val year = date.substring(0, 4).toInt()
                val month = date.substring(4, 6).toInt() - 1 // Calendar months are 0-based
                val day = date.substring(6, 8).toInt()
                val hour = date.substring(8, 10).toInt()
                val minute = date.substring(10, 12).toInt()
                val second = date.substring(12, 14).toInt()

                calendar.set(year, month, day, hour, minute, second)
                calendar
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun loadPdfInfo(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Create a temporary file to get FileDescriptor
                val tempFile = File(context.cacheDir, "temp.pdf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // Generate preview using PdfRenderer
                val preview = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    val renderer = PdfRenderer(fd)
                    renderer.openPage(0).use { page ->
                        // Create bitmap with appropriate size
                        val bitmap = Bitmap.createBitmap(
                            page.width,
                            page.height,
                            Bitmap.Config.ARGB_8888
                        )
                        // Render the page onto the bitmap
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }

                // Get PDF metadata
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val pdfReader = PdfReader(inputStream)
                    val pdfDoc = PdfDocument(pdfReader)
                    
                    // Get file name
                    val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        cursor.moveToFirst()
                        cursor.getString(nameIndex)
                    } ?: "unknown.pdf"
                    
                    // Get file size
                    val fileSize = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        cursor.moveToFirst()
                        cursor.getLong(sizeIndex)
                    } ?: 0L
                    
                    // Extract metadata
                    val info = pdfDoc.documentInfo
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    
                    _pdfInfo.value = PdfInfo(
                        fileName = fileName,
                        numberOfPages = pdfDoc.numberOfPages,
                        fileSize = fileSize,
                        author = info?.getAuthor() ?: "Unknown",
                        creator = info?.getCreator() ?: "Unknown",
                        producer = info?.getProducer() ?: "Unknown",
                        creationDate = try {
                            // Try PDF metadata first
                            info?.getMoreInfo("CreationDate")?.let { dateStr ->
                                if (dateStr.startsWith("D:")) {
                                    try {
                                        val cleanDate = dateStr.substring(2) // Remove "D:"
                                        val year = cleanDate.substring(0, 4)
                                        val month = cleanDate.substring(4, 6)
                                        val day = cleanDate.substring(6, 8)
                                        val hour = cleanDate.substring(8, 10)
                                        val minute = cleanDate.substring(10, 12)
                                        val second = cleanDate.substring(12, 14)
                                        "$year-$month-$day $hour:$minute:$second"
                                    } catch (e: Exception) {
                                        null
                                    }
                                } else null
                            } ?: run {
                                // Fall back to file creation time
                                context.contentResolver.query(uri, 
                                    arrayOf(MediaStore.MediaColumns.DATE_ADDED),
                                    null, null, null)?.use { cursor ->
                                    if (cursor.moveToFirst()) {
                                        val seconds = cursor.getLong(0)
                                        val date = Date(seconds * 1000)
                                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date)
                                    } else null
                                }
                            } ?: "Unknown"
                        } catch (e: Exception) {
                            "Unknown"
                        },
                        modificationDate = try {
                            // Try PDF metadata first
                            info?.getMoreInfo("ModDate")?.let { dateStr ->
                                if (dateStr.startsWith("D:")) {
                                    try {
                                        val cleanDate = dateStr.substring(2) // Remove "D:"
                                        val year = cleanDate.substring(0, 4)
                                        val month = cleanDate.substring(4, 6)
                                        val day = cleanDate.substring(6, 8)
                                        val hour = cleanDate.substring(8, 10)
                                        val minute = cleanDate.substring(10, 12)
                                        val second = cleanDate.substring(12, 14)
                                        "$year-$month-$day $hour:$minute:$second"
                                    } catch (e: Exception) {
                                        null
                                    }
                                } else null
                            } ?: run {
                                // Fall back to file modification time
                                context.contentResolver.query(uri,
                                    arrayOf(MediaStore.MediaColumns.DATE_MODIFIED),
                                    null, null, null)?.use { cursor ->
                                    if (cursor.moveToFirst()) {
                                        val seconds = cursor.getLong(0)
                                        val date = Date(seconds * 1000)
                                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date)
                                    } else null
                                }
                            } ?: "Unknown"
                        } catch (e: Exception) {
                            "Unknown"
                        },
                        previewBitmap = preview
                    )
                    
                    pdfDoc.close()
                }
                
                // Clean up temp file
                tempFile.delete()
                
            } catch (e: Exception) {
                _pdfInfo.value = null
            }
        }
    }

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

data class PdfInfo(
    val fileName: String,
    val numberOfPages: Int,
    val fileSize: Long,
    val author: String,
    val creator: String,
    val producer: String,
    val creationDate: String,
    val modificationDate: String,
    val previewBitmap: Bitmap? = null
) 