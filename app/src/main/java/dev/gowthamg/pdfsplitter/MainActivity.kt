package dev.gowthamg.pdfsplitter

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
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

import android.provider.DocumentsContract
import android.os.Environment
import android.widget.Toast

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
    var showTutorial by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(initialPdfUri) }
    val uiState by viewModel.uiState.collectAsState()
    val pdfInfo by viewModel.pdfInfo.collectAsState()
    val pageRange by viewModel.pageRange.collectAsState()
    val splitPdfs by viewModel.splitPdfs.collectAsState()

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
                actions = {
                    IconButton(onClick = { showTutorial = true }) {
                        Icon(
                            Icons.Rounded.Help,
                            contentDescription = "Help"
                        )
                    }
                    IconButton(onClick = { showAbout = true }) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = "About"
                        )
                    }
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
                    onSelectNewFile = { launcher.launch("application/pdf") },
                    onShowPreview = { /* Handle preview */ },
                    selectedUri = selectedUri!!
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
            StatusMessages(uiState, splitPdfs)
        }
    }
    
    // Show tutorial dialog when needed
    if (showTutorial) {
        TutorialDialog(onDismiss = { showTutorial = false })
    }

    // Show about dialog when needed
    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
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
fun PdfInfoCard(
    pdfInfo: PdfInfo,
    onSelectNewFile: () -> Unit,
    onShowPreview: () -> Unit,
    selectedUri: Uri
) {
    var showPreviewDialog by remember { mutableStateOf(false) }
    var currentPreviewPage by remember { mutableStateOf(1) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        // Preview Section with click handler
        pdfInfo.previewBitmap?.let { bitmap ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { showPreviewDialog = true }
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "PDF Preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                
                // Preview overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            shape = CircleShape
                        )
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.Fullscreen,
                        contentDescription = "Show preview",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
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

    if (showPreviewDialog) {
        PdfPreviewDialog(
            pdfUri = selectedUri,
            onDismiss = { showPreviewDialog = false },
            currentPage = currentPreviewPage,
            onPageSelected = { currentPreviewPage = it },
            totalPages = pdfInfo.numberOfPages
        )
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
private fun StatusMessages(
    uiState: UiState,
    splitPdfs: List<SplitPdfInfo> = emptyList()
) {
    val context = LocalContext.current

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
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Success Icon with background
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.TaskAlt,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Success Title
                    Text(
                        text = "PDF Split Complete",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Success Details
                    Text(
                        text = "${uiState.pageCount} pages have been extracted",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Output Path with icon
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Rounded.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = uiState.outputPath,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (splitPdfs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Share Button
                        FilledTonalButton(
                            onClick = {
                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND_MULTIPLE
                                    type = "application/pdf"
                                    putParcelableArrayListExtra(
                                        Intent.EXTRA_STREAM,
                                        ArrayList(splitPdfs.map { it.uri })
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share PDFs"))
                            }
                        ) {
                            Icon(
                                Icons.Rounded.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share All")
                        }
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewDialog(
    pdfUri: Uri,
    onDismiss: () -> Unit,
    currentPage: Int,
    onPageSelected: (Int) -> Unit,
    totalPages: Int
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                SmallTopAppBar(
                    title = { Text("PDF Preview") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close")
                        }
                    }
                )

                // Current page preview
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    PdfPagePreview(
                        pdfUri = pdfUri,
                        pageNumber = currentPage,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Navigation buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { 
                                if (currentPage > 1) onPageSelected(currentPage - 1) 
                            },
                            enabled = currentPage > 1
                        ) {
                            Icon(Icons.Rounded.NavigateBefore, "Previous page")
                        }
                        
                        IconButton(
                            onClick = { 
                                if (currentPage < totalPages) onPageSelected(currentPage + 1) 
                            },
                            enabled = currentPage < totalPages
                        ) {
                            Icon(Icons.Rounded.NavigateNext, "Next page")
                        }
                    }
                }

                // Thumbnails grid
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(totalPages) { index ->
                        val pageNum = index + 1
                        PdfThumbnail(
                            pdfUri = pdfUri,
                            pageNumber = pageNum,
                            isSelected = pageNum == currentPage,
                            onClick = { onPageSelected(pageNum) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PdfThumbnail(
    pdfUri: Uri,
    pageNumber: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .aspectRatio(0.707f),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PdfPagePreview(
                pdfUri = pdfUri,
                pageNumber = pageNumber,
                modifier = Modifier.fillMaxSize()
            )
            Text(
                text = pageNumber.toString(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(4.dp),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun PdfPagePreview(
    pdfUri: Uri,
    pageNumber: Int,
    modifier: Modifier = Modifier
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current

    LaunchedEffect(pdfUri, pageNumber) {
        withContext(Dispatchers.IO) {
            try {
                val tempFile = File(context.cacheDir, "temp_preview.pdf")
                context.contentResolver.openInputStream(pdfUri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    val renderer = PdfRenderer(fd)
                    renderer.openPage(pageNumber - 1).use { page ->
                        val newBitmap = Bitmap.createBitmap(
                            page.width,
                            page.height,
                            Bitmap.Config.ARGB_8888
                        )
                        page.render(newBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap = newBitmap
                    }
                }
                tempFile.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    bitmap?.let { bmp ->
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Page $pageNumber",
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun SharePdfsButton(pdfs: List<SplitPdfInfo>) {
    val context = LocalContext.current
    
    FilledTonalButton(
        onClick = {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND_MULTIPLE
                type = "application/pdf"
                putParcelableArrayListExtra(
                    Intent.EXTRA_STREAM,
                    ArrayList(pdfs.map { it.uri })
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share PDFs"))
        }
    ) {
        Icon(
            Icons.Rounded.Share,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Share PDFs")
    }
}

@Composable
fun TutorialDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "How to Use",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, "Close")
                    }
                }

                // Tutorial steps
                TutorialStep(
                    icon = Icons.Rounded.FileOpen,
                    title = "1. Select PDF",
                    description = "Tap the 'Select PDF' button or share a PDF file from another app"
                )

                TutorialStep(
                    icon = Icons.Rounded.Preview,
                    title = "2. Preview and Select",
                    description = "Preview the PDF and optionally specify page ranges to split"
                )

                TutorialStep(
                    icon = Icons.Rounded.Splitscreen,
                    title = "3. Split Pages",
                    description = "Tap 'Split PDF' to extract selected pages into separate PDF files"
                )

                TutorialStep(
                    icon = Icons.Rounded.Share,
                    title = "4. Share",
                    description = "Share the split PDFs directly or find them in your Documents folder"
                )

                // Tips section
                Text(
                    "Tips:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BulletPoint("Use comma-separated numbers for individual pages (e.g., 1,3,5)")
                    BulletPoint("Use hyphens for page ranges (e.g., 1-5)")
                    BulletPoint("Combine both formats (e.g., 1-3,5,7-9)")
                    BulletPoint("Leave page range empty to split all pages")
                }
            }
        }
    }
}

@Composable
private fun TutorialStep(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .padding(top = 4.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BulletPoint(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    MaterialTheme.colorScheme.primary,
                    CircleShape
                )
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "About",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, "Close")
                    }
                }

                // App Icon
                Icon(
                    Icons.Rounded.PictureAsPdf,
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            CircleShape
                        )
                        .padding(12.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )

                // App Name and Version
                Text(
                    "PDF Splitter",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Repository Link
                FilledTonalButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Gowtham2003/pdf-splitter"))
                        context.startActivity(intent)
                    }
                ) {
                    Icon(
                        Icons.Rounded.Code,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Source")
                }

                // Made with love message
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Made with ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        " by Gowtham",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}