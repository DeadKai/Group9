package com.pouch.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pouch.domain.Member
import com.pouch.domain.MemberAmount
import com.pouch.domain.ReceiptItem
import com.pouch.domain.SplitMode
import java.math.BigDecimal

fun parseCents(value: String): Long? {
    val normalized = value.trim().replace(',', '.')

    if (!Regex("""\d{1,9}(\.\d{1,2})?""").matches(normalized)) {
        return null
    }

    return try {
        BigDecimal(normalized)
            .movePointRight(2)
            .longValueExact()
    } catch (_: Exception) {
        null
    }
}

fun centsText(cents: Long): String =
    BigDecimal.valueOf(cents, 2).toPlainString()

fun validateAllocations(
    members: List<Member>,
    total: Long,
    entries: Map<String, String>,
    label: String
): Pair<List<MemberAmount>?, String?> {
    val amounts = mutableListOf<MemberAmount>()

    for (member in members) {
        val text = entries[member.id].orEmpty()

        val cents = if (text.isBlank()) {
            0L
        } else {
            parseCents(text)
        }

        if (cents == null) {
            return null to "Invalid $label for ${member.name}"
        }

        amounts += MemberAmount(member.id, cents)
    }

    if (amounts.sumOf { it.amountCents } != total) {
        return null to "$label must add up to ${centsText(total)} €"
    }

    return amounts to null
}
@Composable
fun ReceiptSplitEditor(
    members: List<Member>,
    totalCents: Long,
    payments: Map<String, Long>,
    shares: Map<String, Long>,
    splitMode: SplitMode,
    onSplitModeChange: (SplitMode) -> Unit,
    onPaymentsChange: (Map<String, Long>) -> Unit,
    onSharesChange: (Map<String, Long>) -> Unit,
    items: List<ReceiptItem> = emptyList(),
    onSaveItem: (String?, String, Long, List<String>) -> Unit =
        { _, _, _, _ -> },
    onRemoveItem: (String) -> Unit = {}
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Paid by",
            style = MaterialTheme.typography.titleMedium
        )

        members.forEach { member ->
            AmountField(
                label = member.name,
                amountCents = payments[member.id] ?: 0L,
                onAmountChange = { amount ->
                    onPaymentsChange(
                        payments + (member.id to amount)
                    )
                }
            )
        }

        Text(
            "Split between",
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = splitMode == SplitMode.EQUAL,
                onClick = {
                    onSplitModeChange(SplitMode.EQUAL)
                },
                label = { Text("Equal split", softWrap = false) }
            )

            FilterChip(
                selected = splitMode == SplitMode.CUSTOM,
                onClick = {
                    onSplitModeChange(SplitMode.CUSTOM)
                },
                label = { Text("Custom split", softWrap = false) }
            )
            FilterChip(
                selected = splitMode == SplitMode.BY_ITEM,
                onClick = { onSplitModeChange(SplitMode.BY_ITEM) },
                label = { Text("By item", softWrap = false) }
            )
        }

        if (splitMode != SplitMode.BY_ITEM) {
            members.forEach { member ->
                AmountField(
                    label = member.name,
                    amountCents = shares[member.id] ?: 0L,
                    enabled = splitMode == SplitMode.CUSTOM,
                    onAmountChange = { amount ->
                        onSharesChange(shares + (member.id to amount))
                    }
                )
            }
        }

        if (splitMode == SplitMode.BY_ITEM) {

            var itemName by remember { mutableStateOf("") }
            var itemPrice by remember { mutableStateOf("") }
            var selectedMembers by remember {
                mutableStateOf<List<String>>(emptyList())
            }
            var editingItemId by remember {
                mutableStateOf<String?>(null)
            }
            var itemError by remember {
                mutableStateOf<String?>(null)
            }

            fun clearForm() {
                itemName = ""
                itemPrice = ""
                selectedMembers = emptyList()
                editingItemId = null
                itemError = null
            }

            Text(
                "Added items (${items.size})",
                style = MaterialTheme.typography.titleMedium
            )

            if (items.isEmpty()) {
                Text(
                    "No items added yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items.forEach { item ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        item.name.ifBlank { "Unnamed item" },
                        style = MaterialTheme.typography.titleSmall
                    )

                    Text("${centsText(item.totalCents)} €")

                    Text(
                        members
                            .filter { it.id in item.assignedMemberIds }
                            .joinToString(", ") { it.name }
                            .ifBlank { "No members selected" },
                        style = MaterialTheme.typography.bodySmall
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                editingItemId = item.id
                                itemName = item.name
                                itemPrice = centsText(item.totalCents)
                                selectedMembers =
                                    item.assignedMemberIds.toList()
                                itemError = null
                            }
                        ) {
                            Text("Edit")
                        }

                        TextButton(
                            onClick = {
                                onRemoveItem(item.id)

                                if (editingItemId == item.id) {
                                    clearForm()
                                }
                            }
                        ) {
                            Text(
                                "Delete",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }

            Text(
                "Items total: ${
                    centsText(items.sumOf { it.totalCents })
                } €",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                if (editingItemId == null) {
                    "Add an item"
                } else {
                    "Edit item"
                },
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = itemName,
                onValueChange = {
                    itemName = it
                    itemError = null
                },
                label = { Text("Item name") },
                placeholder = { Text("e.g. Pizza") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = itemPrice,
                onValueChange = { value ->
                    if (
                        value.isEmpty() ||
                        value.matches(
                            Regex("""\d*([.,]\d{0,2})?""")
                        )
                    ) {
                        itemPrice = value
                        itemError = null
                    }
                },
                label = { Text("Price (€)") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                "Who is this item for?",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                members.forEach { member ->
                    FilterChip(
                        selected = member.id in selectedMembers,
                        onClick = {
                            selectedMembers =
                                if (member.id in selectedMembers) {
                                    selectedMembers - member.id
                                } else {
                                    selectedMembers + member.id
                                }

                            itemError = null
                        },
                        label = {
                            Text(member.name, softWrap = false)
                        }
                    )
                }
            }

            itemError?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    val price = parseCents(itemPrice)

                    when {
                        itemName.isBlank() -> {
                            itemError = "Enter an item name"
                        }

                        price == null || price <= 0L -> {
                            itemError = "Enter a valid price"
                        }

                        selectedMembers.isEmpty() -> {
                            itemError = "Select at least one member"
                        }

                        else -> {
                            onSaveItem(
                                editingItemId,
                                itemName.trim(),
                                price,
                                selectedMembers.toList()
                            )

                            clearForm()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (editingItemId == null) {
                        "+ Add item"
                    } else {
                        "Save item"
                    }
                )
            }

            if (editingItemId != null) {
                TextButton(
                    onClick = { clearForm() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel editing")
                }
            }
        }


        val paidTotal = payments.values.sum()
        val splitTotal = shares.values.sum()

        Text("Receipt total: ${centsText(totalCents)} €")
        Text("Paid total: ${centsText(paidTotal)} €")
        Text("Split total: ${centsText(splitTotal)} €")

        if (paidTotal != totalCents) {
            Text(
                "Payments must equal receipt total",
                color = MaterialTheme.colorScheme.error
            )
        }

        if (splitTotal != totalCents) {
            Text(
                "Shares must equal receipt total",
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}



@Composable
private fun AmountField(
    label: String,
    amountCents: Long,
    enabled: Boolean = true,
    onAmountChange: (Long) -> Unit
) {
    var input by remember(label) {
        mutableStateOf(centsText(amountCents))
    }

    var lastReported by remember(label) {
        mutableStateOf(amountCents)
    }

    LaunchedEffect(amountCents) {
        if (amountCents != lastReported) {
            input = centsText(amountCents)
            lastReported = amountCents
        }
    }

    OutlinedTextField(
        value = input,
        enabled = enabled,
        onValueChange = { value ->
            if (
                value.isEmpty() ||
                value.matches(
                    Regex("""\d*([.,]\d{0,2})?""")
                )
            ) {
                input = value

                val parsed = if (value.isEmpty()) {
                    0L
                } else {
                    parseCents(value)
                }

                if (parsed != null) {
                    lastReported = parsed
                    onAmountChange(parsed)
                }
            }
        },
        label = { Text(label) },
        suffix = { Text("€") },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}