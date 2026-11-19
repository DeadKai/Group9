package com.pouch.ui.scan

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pouch.domain.formatMoney
import com.pouch.ui.common.BackTopBar
import com.pouch.ui.common.ReceiptSplitEditor
import com.pouch.ui.common.parseCents
import com.pouch.domain.SplitCalculator
import com.pouch.domain.SplitMode
import com.pouch.ui.common.EmptyView
import com.pouch.ui.common.LoadingButton
import com.pouch.ui.common.LoadingScreen
import com.pouch.ui.common.LoadingView
import com.pouch.ui.common.ValidatedTextField
import com.pouch.ui.common.pouchViewModel
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ScanScreen(
    presetGroupId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ScanViewModel = pouchViewModel {
        ScanViewModel(presetGroupId, it.groupRepository, it.receiptScanner, it.success)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Where the camera app writes the photo. Saved so it survives screen rotation.
    var photoUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = photoUri
        if (success && uri != null) viewModel.onImagePicked(uri)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onImagePicked(uri)
    }

    val openCamera: () -> Unit = {
        try {
            val uri = createPhotoUri(context)
            photoUri = uri
            takePicture.launch(uri)
        } catch (e: ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar("No camera app found. Pick a photo from the gallery instead.") }
        }
    }
    val openGallery: () -> Unit = {
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Scaffold(
        topBar = { BackTopBar("Scan receipt", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when {
            !state.groupsLoaded -> LoadingView(modifier)

            state.groupsLoadError != null -> EmptyView(
                title = "Could not load groups",
                message = state.groupsLoadError.orEmpty(),
                actionLabel = "Go back",
                onAction = onBack,
                modifier = modifier,
            )

            state.groups.isEmpty() -> EmptyView(
                title = "Create a group first",
                message = "Receipts are split inside a group. Go back and create one on the Home screen.",
                actionLabel = "Back to Home",
                onAction = onBack,
                modifier = modifier,
            )

            state.step == ScanStep.CHOOSE_SOURCE -> ChooseSource(
                modifier = modifier,
                onCamera = openCamera,
                onGallery = openGallery,
                onManual = viewModel::enterManually,
            )

            state.step == ScanStep.READING -> LoadingScreen(
                title = "Reading receipt…",
                steps = listOf("Looking for text", "Finding the total", "Almost done"),
                onCancel = viewModel::cancelScan,
                modifier = modifier,
            )

            else -> EditReceipt(state, viewModel, modifier)
        }
    }
}

private fun createPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
    val file = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
private fun ChooseSource(
    modifier: Modifier,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onManual: () -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Add a receipt", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Take a photo and we'll read the total for you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onCamera, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Take photo") }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onGallery, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Choose from gallery") }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onManual) { Text("Enter manually") }
    }
}

@Composable
private fun EditReceipt(state: ScanUiState, viewModel: ScanViewModel, modifier: Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        state.scanMessage?.let {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.height(12.dp))
        }

        if (!state.groupLocked) {
            Text("Group", style = MaterialTheme.typography.labelLarge)
            ChipRow {
                state.groups.forEach { group ->
                    FilterChip(
                        selected = group.id == state.selectedGroupId,
                        onClick = { viewModel.onGroupSelected(group.id) },
                        label = { Text("${group.icon.emoji} ${group.name}") },
                    )
                }
            }
            ErrorText(state.groupError)
            Spacer(Modifier.height(8.dp))
        }

        ValidatedTextField(
            value = state.title, onValueChange = viewModel::onTitleChange,
            label = "What was it for?", error = state.titleError,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        ValidatedTextField(
            value = state.amount, onValueChange = viewModel::onAmountChange,
            label = "Total (€)", error = state.amountError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        )

        state.selectedGroup?.let { group ->
            Spacer(Modifier.height(16.dp))

            val total = parseCents(state.amount) ?: 0L

            val paymentValues: Map<String, Long> =
                if (state.payments.isEmpty()) {
                    val payerId = state.paidByMemberId
                        ?: group.members.firstOrNull()?.id

                    if (payerId != null)
                        mapOf(payerId to total)
                    else
                        emptyMap()
                } else {
                    state.payments
                }

            val equalAmounts =
                if (group.members.isNotEmpty())
                    SplitCalculator.equalShares(
                        total,
                        group.members.size
                    )
                else
                    emptyList()

            val equalShareValues =
                group.members.mapIndexed { index, member ->
                    member.id to equalAmounts[index]
                }.toMap()

            val shareValues: Map<String, Long> =
                if (state.splitMode == SplitMode.EQUAL) {
                    equalShareValues
                } else {
                    state.shares.ifEmpty { equalShareValues }
                }

            ReceiptSplitEditor(
                members = group.members,
                totalCents = total,
                payments = paymentValues,
                shares = shareValues,
                splitMode = state.splitMode,
                onSplitModeChange = viewModel::onSplitModeChange,
                onPaymentsChange = viewModel::onPaymentsChange,
                onSharesChange = viewModel::onSharesChange
            )

            ErrorText(state.allocationError)
        }

        Spacer(Modifier.height(32.dp))
        LoadingButton(
            text = "Save receipt",
            loadingText = "Saving…",
            isLoading = state.isSaving,
            onClick = viewModel::save,
        )
        TextButton(onClick = viewModel::backToSource, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Scan another photo")
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun ErrorText(error: String?) {
    if (error != null) {
        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}
