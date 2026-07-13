package com.oneims.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.oneims.app.R
import com.oneims.app.core.ApnCatalogEntry
import com.oneims.app.core.ApnCatalogQuery
import com.oneims.app.core.ApnCatalogRepository
import com.oneims.app.core.ApnCatalogSummary
import com.oneims.app.core.ApnCatalogPolicy
import com.oneims.app.core.ApnCountryIndex
import com.oneims.app.core.formatCarrierShortName
import com.oneims.app.model.SimInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun ApnCatalogDialog(
    sim: SimInfo?,
    sims: List<SimInfo> = emptyList(),
    selectedSubId: Int = sim?.subscriptionId ?: -1,
    onSelectSim: ((Int) -> Unit)? = null,
    onDismiss: () -> Unit,
    onCopy: (ApnCatalogEntry) -> Unit,
    onApply: (ApnCatalogEntry?) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { ApnCatalogRepository.get(context) }
    val activeSim = sims.firstOrNull { it.subscriptionId == selectedSubId } ?: sim
    var search by rememberSaveable { mutableStateOf("") }
    var entries by remember { mutableStateOf(emptyList<ApnCatalogEntry>()) }
    var summary by remember { mutableStateOf<ApnCatalogSummary?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(repository) {
        summary = runCatching {
            withContext(Dispatchers.IO) { repository.summary() }
        }.getOrElse { error ->
            errorMessage = error.message ?: error.javaClass.simpleName
            null
        }
    }

    LaunchedEffect(
        repository,
        search,
        activeSim?.mcc,
        activeSim?.mnc,
        activeSim?.carrierId,
    ) {
        delay(180)
        loading = true
        errorMessage = null
        entries = runCatching {
            withContext(Dispatchers.IO) {
                repository.search(
                    ApnCatalogQuery(
                        mcc = activeSim?.mcc.orEmpty(),
                        mnc = activeSim?.mnc.orEmpty(),
                        carrierId = activeSim?.carrierId,
                        search = search,
                    )
                )
            }
        }.getOrElse { error ->
            errorMessage = error.message ?: error.javaClass.simpleName
            emptyList()
        }
        if (entries.none { entry -> entry.id == selectedId }) {
            selectedId = null
        }
        loading = false
    }

    val selected = entries.firstOrNull { entry -> entry.id == selectedId }
    val selectedMatchesSim = selected?.let { entry ->
        activeSim != null && ApnCatalogPolicy.matchesCurrentSim(
            entry = entry,
            mcc = activeSim.mcc,
            mnc = activeSim.mnc,
            carrierId = activeSim.carrierId.takeIf { value -> value > 0 },
        )
    } == true
    val canApplySelected =
        selected?.isSafeImsTemplate == true && selectedMatchesSim

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .heightIn(max = 840.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
            ) {
                Column {
                    CatalogHeader(
                        summary = summary,
                        visibleCount = entries.size,
                        sims = sims,
                        selectedSubId = selectedSubId,
                        onSelectSim = onSelectSim,
                        onDismiss = onDismiss,
                    )
                    OutlinedTextField(
                        value = search,
                        onValueChange = { value -> search = value.take(64) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        },
                        trailingIcon = if (search.isNotEmpty()) {
                            {
                                IconButton(onClick = { search = "" }) {
                                    Icon(
                                        Icons.Filled.Clear,
                                        contentDescription = stringResource(
                                            R.string.apn_catalog_clear_search,
                                        ),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                        label = { Text(stringResource(R.string.apn_catalog_search)) },
                        singleLine = true,
                    )
                    HorizontalDivider()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            loading -> CircularProgressIndicator()
                            errorMessage != null -> Text(
                                text = stringResource(
                                    R.string.apn_catalog_load_failed,
                                    errorMessage.orEmpty(),
                                ),
                                modifier = Modifier.padding(24.dp),
                                color = MaterialTheme.colorScheme.error,
                            )
                            entries.isEmpty() -> Text(
                                text = stringResource(R.string.apn_catalog_empty),
                                modifier = Modifier.padding(24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            else -> LazyColumn(
                                modifier = Modifier.matchParentSize(),
                            ) {
                                items(entries, key = ApnCatalogEntry::id) { entry ->
                                    ApnCatalogRow(
                                        entry = entry,
                                        selected = entry.id == selectedId,
                                        onClick = { selectedId = entry.id },
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    CatalogActions(
                        selected = selected,
                        canApplySelected = canApplySelected,
                        hasSim = activeSim != null,
                        sim = activeSim,
                        onCopy = onCopy,
                        onApply = onApply,
                        onDismiss = onDismiss,
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogHeader(
    summary: ApnCatalogSummary?,
    visibleCount: Int,
    sims: List<SimInfo>,
    selectedSubId: Int,
    onSelectSim: ((Int) -> Unit)?,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 20.dp, end = 12.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.apn_catalog_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = summary?.let { value ->
                    stringResource(
                        R.string.apn_catalog_summary,
                        value.records,
                        value.plmns,
                        value.countries,
                        visibleCount,
                    )
                } ?: stringResource(R.string.apn_catalog_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (sims.isNotEmpty() && onSelectSim != null) {
            SelectedSimPill(
                sims = sims,
                selectedSubId = selectedSubId,
                onSelectSim = onSelectSim,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_cancel),
            )
        }
    }
}

@Composable
private fun ApnCatalogRow(
    entry: ApnCatalogEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = entry.carrier.ifBlank { entry.apn },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.apn_catalog_identity,
                        entry.localizedCountry(),
                        (entry.mcc + entry.mnc).ifBlank {
                            "CID ${entry.carrierId ?: "—"}"
                        },
                        entry.source,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.apn_catalog_profile,
                        entry.apn,
                        entry.types.ifBlank { "*" },
                        entry.protocol.ifBlank { "—" },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entry.isSafeImsTemplate) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "IMS",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
}

@Composable
private fun CatalogActions(
    selected: ApnCatalogEntry?,
    canApplySelected: Boolean,
    hasSim: Boolean,
    sim: SimInfo?,
    onCopy: (ApnCatalogEntry) -> Unit,
    onApply: (ApnCatalogEntry?) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (sim != null) {
            Text(
                text = stringResource(
                    R.string.apn_catalog_apply_target,
                    sim.slotIndex + 1,
                    formatCarrierShortName(sim.carrierName),
                    sim.subscriptionId,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        val guidance = when {
            selected == null -> stringResource(R.string.apn_catalog_select_hint)
            canApplySelected -> stringResource(R.string.apn_catalog_safe_ims)
            else -> stringResource(R.string.apn_catalog_browse_only)
        }
        Text(
            text = guidance,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            if (selected != null) {
                TextButton(onClick = { onCopy(selected) }) {
                    Text(stringResource(R.string.apn_catalog_copy))
                }
            }
            Box(modifier = Modifier.weight(1f))
            if (hasSim) {
                TextButton(onClick = { onApply(null) }) {
                    Text(stringResource(R.string.apn_catalog_generic_ims))
                }
            }
        }
        OneImsPrimaryButton(
            text = stringResource(R.string.apn_catalog_apply_ims),
            onClick = { selected?.let(onApply) },
            enabled = canApplySelected,
        )
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

private fun ApnCatalogEntry.localizedCountry(): String {
    if (countryCode == "INTL" || countryCode.length != 2) return countryCode.ifBlank { "—" }
    return ApnCountryIndex.displayName(countryCode)
}
