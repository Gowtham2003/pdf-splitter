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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Pages
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material.icons.rounded.Update
import androidx.compose.ui.graphics.vector.ImageVector
import android.content.Intent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get shared PDF URI if available
        val sharedPdfUri = when (intent?.action) {
            Intent.ACTION_SEND -> {
                intent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            Intent.ACTION_VIEW -> {
                intent?.data
            }
            else -> null
        }

        setContent {
            PDFSplitterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PdfSplitterScreen(sharedPdfUri)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfSplitterScreen(
    initialPdfUri: Uri? = null,
    viewModel: PdfViewModel = viewModel()
) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(initialPdfUri) }
    val uiState by viewModel.uiState.collectAsState()
    val pdfInfo by viewModel.pdfInfo.collectAsState()
    val pageRange by viewModel.pageRange.collectAsState()

    // Load initial PDF if provided
    LaunchedEffect(initialPdfUri) {
        initialPdfUri?.let {
            viewModel.loadPdfInfo(context, it)
        }
    }

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

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { 
                    Text(
                        "PDF Splitter",
                        style = MaterialTheme.typography.headlineLarge
                    )
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Initial state with file selection
            if (pdfInfo == null) {
                EmptyState(onSelectFile = { launcher.launch("application/pdf") })
            }

            // PDF Info Card
            pdfInfo?.let { info ->
                PdfInfoCard(
                    info,
                    onSelectNewFile = { launcher.launch("application/pdf") }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                PageRangeSelector(
                    totalPages = info.numberOfPages,
                    onRangeChange = { viewModel.updatePageRange(it) },
                    rangeText = pageRange,
                    isValid = viewModel.isValidPageRange(pageRange, info.numberOfPages)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProcessButton(
                        onClick = { 
                            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                                permissionLauncher.launch(arrayOf(
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                    Manifest.permission.READ_EXTERNAL_STORAGE
                                ))
                            } else {
                                selectedUri?.let { viewModel.processFile(context, it) }
                            }
                        },
                        isProcessing = uiState is UiState.Processing,
                        enabled = pageRange.isEmpty() || 
                                 viewModel.isValidPageRange(pageRange, pdfInfo!!.numberOfPages),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Status messages
            StatusMessages(uiState)
        }
    }
}

@Composable
private fun EmptyState(onSelectFile: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.PictureAsPdf,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Text(
            "Select a PDF to split",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        FilledTonalButton(
            onClick = onSelectFile,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Icon(
                Icons.Rounded.FileOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Select PDF")
        }
    }
}

@Composable
fun PdfInfoCard(pdfInfo: PdfInfo, onSelectNewFile: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        // Preview Section
        pdfInfo.previewBitmap?.let { bitmap ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "PDF Preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // Info Section
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pdfInfo.fileName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                
                FilledTonalIconButton(
                    onClick = onSelectNewFile,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Icon(
                        Icons.Rounded.FileOpen,
                        contentDescription = "Select new PDF",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            InfoRow(
                icon = Icons.Rounded.Pages,
                label = "Pages",
                value = "${pdfInfo.numberOfPages}"
            )
            InfoRow(
                icon = Icons.Rounded.DataUsage,
                label = "Size",
                value = formatFileSize(pdfInfo.fileSize)
            )
            if (pdfInfo.author != "Unknown") {
                InfoRow(
                    icon = Icons.Rounded.Person,
                    label = "Author",
                    value = pdfInfo.author
                )
            }
            InfoRow(
                icon = Icons.Rounded.Update,
                label = "Modified",
                value = formatDate(pdfInfo.modificationDate)
            )
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ProcessButton(
    onClick: () -> Unit,
    isProcessing: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    ElevatedButton(
        onClick = onClick,
        enabled = enabled && !isProcessing,
        modifier = modifier
    ) {
        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Processing...")
        } else {
            Icon(
                Icons.Rounded.Splitscreen,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Split PDF")
        }
    }
}

@Composable
private fun StatusMessages(uiState: UiState) {
    when (uiState) {
        is UiState.Processing -> {
            ProcessingProgress(
                currentPage = uiState.currentPage,
                totalPages = uiState.totalPages,
                currentFileName = uiState.currentFileName
            )
        }
        is UiState.Success -> {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Successfully split into ${uiState.pageCount} pages",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "Saved to: ${uiState.outputPath}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
        is UiState.Error -> {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        uiState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        else -> Unit
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageRangeSelector(
    totalPages: Int,
    onRangeChange: (String) -> Unit,
    rangeText: String,
    isValid: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        OutlinedTextField(
            value = rangeText,
            onValueChange = onRangeChange,
            label = { Text("Page Range") },
            placeholder = { Text("e.g., 1-3, 5, 7-9") },
            supportingText = {
                Text(
                    "Enter page numbers or ranges (1-$totalPages)",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            isError = rangeText.isNotEmpty() && !isValid,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(
                    Icons.Rounded.Pages,
                    contentDescription = null
                )
            }
        )
    }
}

@Composable
private fun ProcessingProgress(
    currentPage: Int,
    totalPages: Int,
    currentFileName: String
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Processing PDF",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LinearProgressIndicator(
                progress = currentPage.toFloat() / totalPages,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Page $currentPage of $totalPages",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${(currentPage.toFloat() / totalPages * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Creating $currentFileName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}