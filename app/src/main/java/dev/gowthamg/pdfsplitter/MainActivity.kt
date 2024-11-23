package dev.gowthamg.pdfsplitter

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gowthamg.pdfsplitter.ui.theme.PDFSplitterTheme
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PDFSplitterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PdfSplitterScreen()
                }
            }
        }
    }
}

@Composable
fun PdfInfoCard(pdfInfo: PdfInfo) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = pdfInfo.fileName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // PDF Preview
            pdfInfo.previewBitmap?.let { bitmap ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp)
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "PDF Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            InfoRow("Number of Pages", "${pdfInfo.numberOfPages}")
            InfoRow("File Size", formatFileSize(pdfInfo.fileSize))
            InfoRow("Author", pdfInfo.author)
            InfoRow("Creator", pdfInfo.creator)
            InfoRow("Producer", pdfInfo.producer)
            InfoRow("Created", formatDate(pdfInfo.creationDate))
            InfoRow("Modified", formatDate(pdfInfo.modificationDate))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatFileSize(size: Long): String {
    val kb = size / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format("%.2f MB", mb)
        kb >= 1.0 -> String.format("%.2f KB", kb)
        else -> String.format("%d bytes", size)
    }
}

private fun formatDate(dateStr: String): String {
    return dateStr
}

@Composable
fun PdfSplitterScreen(
    viewModel: PdfViewModel = viewModel()
) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    val uiState by viewModel.uiState.collectAsState()
    val pdfInfo by viewModel.pdfInfo.collectAsState()

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted && selectedUri != null) {
            viewModel.processFile(context, selectedUri!!)
        }
    }

    // File picker launcher
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedUri = uri
        uri?.let { 
            viewModel.loadPdfInfo(context, it)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = { launcher.launch("application/pdf") }
        ) {
            Text("Select PDF File")
        }

        // Display PDF info card if available
        pdfInfo?.let { info ->
            PdfInfoCard(info)
        }

        selectedUri?.let {
            Button(
                onClick = { 
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                        permissionLauncher.launch(arrayOf(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ))
                    } else {
                        viewModel.processFile(context, it)
                    }
                },
                enabled = uiState !is UiState.Processing
            ) {
                Text("Process PDF")
            }
        }

        when (val state = uiState) {
            is UiState.Processing -> {
                CircularProgressIndicator()
                Text("Processing PDF...")
            }
            is UiState.Success -> {
                Text("Successfully split PDF into ${state.pageCount} pages")
                Text("Output directory: ${state.outputPath}")
            }
            is UiState.Error -> {
                Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
            }
            else -> Unit
        }
    }
}