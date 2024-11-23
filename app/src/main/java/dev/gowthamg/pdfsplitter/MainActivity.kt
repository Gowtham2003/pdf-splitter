package dev.gowthamg.pdfsplitter

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gowthamg.pdfsplitter.ui.theme.PDFSplitterTheme

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
fun PdfSplitterScreen(
    viewModel: PdfViewModel = viewModel()
) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    val uiState by viewModel.uiState.collectAsState()

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

        selectedUri?.let {
            Button(
                onClick = { 
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                        // Request permissions for Android 10 and below
                        permissionLauncher.launch(arrayOf(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ))
                    } else {
                        // For Android 11+, no need for runtime permissions for app-specific directory
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