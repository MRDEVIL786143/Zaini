package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.ExecutionState
import com.example.engine.MacroExecutionEngine
import com.example.io.MacroJsonManager
import com.example.model.ActionType
import com.example.model.MacroScript
import com.example.model.MacroStep
import com.example.model.TargetResolution
import com.example.service.FloatingOverlayService
import com.example.service.MacroAccessibilityService
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val executionEngine = MacroExecutionEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MacroRecorderDashboard(executionEngine = executionEngine)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroRecorderDashboard(executionEngine: MacroExecutionEngine) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val displayMetrics = context.resources.displayMetrics
    val screenWidth = displayMetrics.widthPixels
    val screenHeight = displayMetrics.heightPixels

    val isAccessibilityActive by MacroAccessibilityService.isServiceActive.collectAsStateWithLifecycle()
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val isOverlayServiceActive by FloatingOverlayService.isOverlayActive.collectAsStateWithLifecycle()

    val executionState by executionEngine.executionState.collectAsState()

    // Sample initial macros
    val sampleMacros = remember {
        mutableStateListOf(
            MacroScript(
                name = "Multi-Tap Loop with Jitter",
                author = "System Architect",
                description = "Demonstrates 5-iteration loop with anti-bot delay variance (250ms ± 25ms).",
                targetResolution = TargetResolution(1080, 2400),
                steps = listOf(
                    MacroStep(actionType = ActionType.LOOP_START, loopCount = 5, label = "Loop 5 Times"),
                    MacroStep(actionType = ActionType.TAP, normalizedX = 0.5f, normalizedY = 0.4f, durationMs = 80L, delayMs = 250L, jitterMs = 30L, label = "Tap Center"),
                    MacroStep(actionType = ActionType.TAP, normalizedX = 0.7f, normalizedY = 0.6f, durationMs = 90L, delayMs = 300L, jitterMs = 40L, label = "Tap Action"),
                    MacroStep(actionType = ActionType.LOOP_END, label = "End Loop")
                )
            ),
            MacroScript(
                name = "Swipe Down Refresh Routine",
                author = "DevOps Automator",
                description = "Executes vertical pull-to-refresh swipe gesture followed by observation delay.",
                targetResolution = TargetResolution(1080, 2400),
                steps = listOf(
                    MacroStep(
                        actionType = ActionType.SWIPE,
                        normalizedX = 0.5f,
                        normalizedY = 0.25f,
                        endNormalizedX = 0.5f,
                        endNormalizedY = 0.75f,
                        durationMs = 450L,
                        delayMs = 800L,
                        label = "Pull Down Swipe"
                    ),
                    MacroStep(actionType = ActionType.DELAY, delayMs = 1200L, jitterMs = 100L, label = "Wait for Network")
                )
            )
        )
    }

    var selectedMacroIndex by remember { mutableIntStateOf(0) }
    val currentMacro = sampleMacros.getOrNull(selectedMacroIndex) ?: sampleMacros.first()

    var showAddStepDialog by remember { mutableStateOf(false) }
    var showJsonPreviewDialog by remember { mutableStateOf(false) }
    var jsonPreviewContent by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }

    // SAF Import / Export Launchers
    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val result = MacroJsonManager.exportToUri(context, uri, currentMacro)
            if (result.isSuccess) {
                Toast.makeText(context, "Exported macro profile successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Export failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val result = MacroJsonManager.importFromUri(context, uri)
            result.onSuccess { importedScript ->
                sampleMacros.add(importedScript)
                selectedMacroIndex = sampleMacros.size - 1
                Toast.makeText(context, "Imported '${importedScript.name}' with ${importedScript.steps.size} steps", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                Toast.makeText(context, "Import failed: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Refresh overlay permission status when returning
    LaunchedEffect(Unit) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Macro Recorder", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            "Display: ${screenWidth}x${screenHeight}px",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        jsonPreviewContent = MacroJsonManager.toJson(currentMacro)
                        showJsonPreviewDialog = true
                    }) {
                        Icon(Icons.Default.Code, contentDescription = "View JSON Schema")
                    }
                    IconButton(onClick = {
                        val exportFileName = "${currentMacro.name.replace(" ", "_").lowercase()}.json"
                        exportFileLauncher.launch(exportFileName)
                    }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export Macro")
                    }
                    IconButton(onClick = {
                        importFileLauncher.launch(arrayOf("application/json", "*/*"))
                    }) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Import Macro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showAddStepDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Macro Step")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Permission Health Banners
            PermissionsStatusHeader(
                isAccessibilityActive = isAccessibilityActive,
                hasOverlayPermission = hasOverlayPermission,
                isOverlayServiceActive = isOverlayServiceActive,
                onOpenAccessibilitySettings = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onRequestOverlayPermission = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                },
                onToggleOverlayService = {
                    if (isOverlayServiceActive) {
                        context.stopService(Intent(context, FloatingOverlayService::class.java))
                    } else {
                        FloatingOverlayService.activeScript = currentMacro
                        context.startService(Intent(context, FloatingOverlayService::class.java))
                    }
                }
            )

            // Live Playback Engine HUD
            ExecutionEngineStatusCard(
                executionState = executionState,
                onStart = {
                    executionEngine.startExecution(currentMacro, screenWidth, screenHeight)
                },
                onPause = { executionEngine.pauseExecution() },
                onResume = { executionEngine.resumeExecution() },
                onStop = { executionEngine.stopExecution() }
            )

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Macro Steps (${currentMacro.steps.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Profiles (${sampleMacros.size})") }
                )
            }

            if (selectedTab == 0) {
                // Steps Sequence List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = currentMacro.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = currentMacro.description,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (currentMacro.steps.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No steps recorded. Tap '+' to append an action.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        itemsIndexed(currentMacro.steps) { index, step ->
                            StepItemCard(
                                index = index,
                                step = step,
                                screenWidth = screenWidth,
                                screenHeight = screenHeight,
                                onDelete = {
                                    val updatedSteps = currentMacro.steps.toMutableList()
                                    updatedSteps.removeAt(index)
                                    val updatedScript = currentMacro.copy(steps = updatedSteps)
                                    sampleMacros[selectedMacroIndex] = updatedScript
                                }
                            )
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            } else {
                // Macro Profiles Tab
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(sampleMacros) { idx, macro ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (idx == selectedMacroIndex)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedMacroIndex = idx },
                            border = if (idx == selectedMacroIndex)
                                CardDefaults.outlinedCardBorder()
                            else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        macro.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        "${macro.steps.size} steps • Target: ${macro.targetResolution.width}x${macro.targetResolution.height}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (macro.description.isNotEmpty()) {
                                        Text(
                                            macro.description,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (idx == selectedMacroIndex) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Active Profile",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Step Dialog
    if (showAddStepDialog) {
        AddStepDialog(
            onDismiss = { showAddStepDialog = false },
            onAddStep = { newStep ->
                val updatedSteps = currentMacro.steps + newStep
                sampleMacros[selectedMacroIndex] = currentMacro.copy(steps = updatedSteps)
                showAddStepDialog = false
            }
        )
    }

    // JSON Preview / Export Dialog
    if (showJsonPreviewDialog) {
        AlertDialog(
            onDismissRequest = { showJsonPreviewDialog = false },
            title = { Text("Macro JSON Schema & Payload") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = jsonPreviewContent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("macro_json", jsonPreviewContent))
                    Toast.makeText(context, "JSON copied to clipboard!", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy JSON")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJsonPreviewDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun PermissionsStatusHeader(
    isAccessibilityActive: Boolean,
    hasOverlayPermission: Boolean,
    isOverlayServiceActive: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onToggleOverlayService: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Accessibility Service Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        if (isAccessibilityActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isAccessibilityActive) Color(0xFF10B981) else Color(0xFFF59E0B),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Accessibility Service: ${if (isAccessibilityActive) "Active" else "Disabled"}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (!isAccessibilityActive) {
                    OutlinedButton(
                        onClick = onOpenAccessibilitySettings,
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text("Enable", fontSize = 12.sp)
                    }
                }
            }

            // Overlay Permission Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        if (hasOverlayPermission) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (hasOverlayPermission) Color(0xFF10B981) else Color(0xFFF59E0B),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Overlay Permission: ${if (hasOverlayPermission) "Granted" else "Missing"}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (!hasOverlayPermission) {
                    OutlinedButton(
                        onClick = onRequestOverlayPermission,
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text("Grant", fontSize = 12.sp)
                    }
                } else {
                    Button(
                        onClick = onToggleOverlayService,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isOverlayServiceActive) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text(if (isOverlayServiceActive) "Stop HUD" else "Launch HUD", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ExecutionEngineStatusCard(
    executionState: ExecutionState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Execution Engine", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    val statusText = when (executionState) {
                        is ExecutionState.Idle -> "Idle - Ready"
                        is ExecutionState.Running -> "Running: Step ${executionState.stepIndex + 1}/${executionState.totalSteps} (${executionState.currentStep.actionType})"
                        is ExecutionState.Paused -> "Paused at Step ${executionState.stepIndex + 1}"
                        is ExecutionState.Finished -> "Execution Completed"
                        is ExecutionState.Stopped -> "Stopped"
                        is ExecutionState.Error -> "Error: ${executionState.message}"
                    }
                    Text(
                        statusText,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    when (executionState) {
                        is ExecutionState.Running -> {
                            IconButton(onClick = onPause) {
                                Icon(Icons.Default.Pause, contentDescription = "Pause")
                            }
                            IconButton(onClick = onStop) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.Red)
                            }
                        }
                        is ExecutionState.Paused -> {
                            IconButton(onClick = onResume) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                            }
                            IconButton(onClick = onStop) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.Red)
                            }
                        }
                        else -> {
                            IconButton(onClick = onStart) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color(0xFF10B981))
                            }
                        }
                    }
                }
            }

            if (executionState is ExecutionState.Running) {
                Spacer(modifier = Modifier.height(8.dp))
                val progress = if (executionState.totalSteps > 0)
                    (executionState.stepIndex + 1f) / executionState.totalSteps
                else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun StepItemCard(
    index: Int,
    step: MacroStep,
    screenWidth: Int,
    screenHeight: Int,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = when (step.actionType) {
                                ActionType.TAP -> Color(0xFF3B82F6)
                                ActionType.SWIPE -> Color(0xFF8B5CF6)
                                ActionType.DELAY -> Color(0xFFF59E0B)
                                ActionType.LOOP_START, ActionType.LOOP_END -> Color(0xFFEC4899)
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${index + 1}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Column {
                    Text(
                        text = if (step.label.isNotEmpty()) "${step.actionType}: ${step.label}" else step.actionType.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    val detail = when (step.actionType) {
                        ActionType.TAP -> {
                            val (x, y) = step.getAbsoluteCoordinates(screenWidth, screenHeight)
                            "Norm: (${"%.2f".format(step.normalizedX)}, ${"%.2f".format(step.normalizedY)}) -> Abs: (${x.toInt()}, ${y.toInt()}px) • ${step.delayMs}ms ±${step.jitterMs}ms"
                        }
                        ActionType.SWIPE -> {
                            "Swipe (${"%.2f".format(step.normalizedX)},${"%.2f".format(step.normalizedY)}) to (${"%.2f".format(step.endNormalizedX)},${"%.2f".format(step.endNormalizedY)}) • ${step.durationMs}ms"
                        }
                        ActionType.DELAY -> "Wait: ${step.delayMs}ms (variance ±${step.jitterMs}ms)"
                        ActionType.LOOP_START -> "Start Loop (Iterations: ${if (step.loopCount <= 0) "Infinite" else "${step.loopCount}x"})"
                        ActionType.LOOP_END -> "End Loop (Stack Pop)"
                    }
                    Text(detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Step", tint = Color.Gray)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStepDialog(
    onDismiss: () -> Unit,
    onAddStep: (MacroStep) -> Unit
) {
    var actionType by remember { mutableStateOf(ActionType.TAP) }
    var normX by remember { mutableStateOf("0.50") }
    var normY by remember { mutableStateOf("0.50") }
    var endNormX by remember { mutableStateOf("0.50") }
    var endNormY by remember { mutableStateOf("0.75") }
    var durationMs by remember { mutableStateOf("100") }
    var delayMs by remember { mutableStateOf("300") }
    var jitterMs by remember { mutableStateOf("25") }
    var loopCount by remember { mutableStateOf("3") }
    var label by remember { mutableStateOf("") }

    var expandedDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Macro Step") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Action Type Selector
                ExposedDropdownMenuBox(
                    expanded = expandedDropdown,
                    onExpandedChange = { expandedDropdown = !expandedDropdown }
                ) {
                    OutlinedTextField(
                        value = actionType.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Action Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false }
                    ) {
                        ActionType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    actionType = type
                                    expandedDropdown = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Step Label / Description") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (actionType == ActionType.TAP || actionType == ActionType.SWIPE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = normX,
                            onValueChange = { normX = it },
                            label = { Text("Norm X (0.0-1.0)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = normY,
                            onValueChange = { normY = it },
                            label = { Text("Norm Y (0.0-1.0)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (actionType == ActionType.SWIPE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = endNormX,
                            onValueChange = { endNormX = it },
                            label = { Text("End X (0.0-1.0)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endNormY,
                            onValueChange = { endNormY = it },
                            label = { Text("End Y (0.0-1.0)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = durationMs,
                        onValueChange = { durationMs = it },
                        label = { Text("Swipe Duration (ms)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (actionType == ActionType.LOOP_START) {
                    OutlinedTextField(
                        value = loopCount,
                        onValueChange = { loopCount = it },
                        label = { Text("Repeat Count (0 = Infinite)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (actionType != ActionType.LOOP_END) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = delayMs,
                            onValueChange = { delayMs = it },
                            label = { Text("Delay (ms)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = jitterMs,
                            onValueChange = { jitterMs = it },
                            label = { Text("Jitter ±(ms)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val step = MacroStep(
                    actionType = actionType,
                    normalizedX = normX.toFloatOrNull() ?: 0.5f,
                    normalizedY = normY.toFloatOrNull() ?: 0.5f,
                    endNormalizedX = endNormX.toFloatOrNull() ?: 0.5f,
                    endNormalizedY = endNormY.toFloatOrNull() ?: 0.5f,
                    durationMs = durationMs.toLongOrNull() ?: 100L,
                    delayMs = delayMs.toLongOrNull() ?: 300L,
                    jitterMs = jitterMs.toLongOrNull() ?: 20L,
                    loopCount = loopCount.toIntOrNull() ?: 1,
                    label = label
                )
                onAddStep(step)
            }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
