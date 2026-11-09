package com.example.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.LiaAppUsageHabitItem
import com.example.data.LiaConversationThread
import com.example.data.LiaLearnedShortcut
import com.example.data.LiaLongTermMemoryItem
import com.example.data.LiaMemoryAndHistoryStore
import com.example.data.LiaSouvenirItem
import com.example.data.LiaUserFact
import com.example.data.RagRetrievalOutcome
import com.example.security.SafeContactResolver
import com.example.ui.theme.GoogleBackground
import com.example.ui.theme.GoogleBlue
import com.example.ui.theme.GoogleBlueContainer
import com.example.ui.theme.GoogleBlueVivid
import com.example.ui.theme.GoogleBorderSubtle
import com.example.ui.theme.GoogleGreen
import com.example.ui.theme.GoogleGreenContainer
import com.example.ui.theme.GooglePurple
import com.example.ui.theme.GooglePurpleContainer
import com.example.ui.theme.GoogleRed
import com.example.ui.theme.GoogleSurface
import com.example.ui.theme.GoogleSurfaceVariant
import com.example.ui.theme.GoogleTextMuted
import com.example.ui.theme.GoogleTextPrimary
import com.example.ui.theme.GoogleTextSecondary
import com.example.ui.theme.GoogleYellow
import com.example.ui.theme.GoogleYellowContainer
import java.util.Locale

data class PendingSafeSmsConfirmation(
    val messageId: String,
    val recipientName: String,
    val phoneNumber: String,
    val messageBody: String
)

/**
 * Modale de confirmation obligatoire du Safe Contact Resolver avant l'envoi effectif d'un SMS.
 * Permet de vérifier la correspondance floue (Nom ↔ Numéro), de choisir un contact alternatif
 * ou de corriger le numéro/texte avant de déclencher l'envoi.
 */
@Composable
fun LiaSafeSmsConfirmationModal(
    pending: PendingSafeSmsConfirmation,
    userFacts: List<LiaUserFact>,
    onConfirmSend: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var recipient by rememberSaveable(pending.messageId) { mutableStateOf(pending.recipientName) }
    var phone by rememberSaveable(pending.messageId) { mutableStateOf(pending.phoneNumber) }
    var body by rememberSaveable(pending.messageId) { mutableStateOf(pending.messageBody) }

    val resolution = SafeContactResolver.resolveContact(recipient.ifBlank { phone }, userFacts)
    val best = resolution.bestCandidate
    val isPhoneValid = LiaMemoryAndHistoryStore.isPhoneNumberValid(phone)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = GoogleSurface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, GoogleBlue, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = GoogleBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Confirmer l'envoi du SMS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GoogleTextPrimary
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", tint = GoogleTextSecondary)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isPhoneValid) GoogleGreenContainer else GoogleYellowContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isPhoneValid) {
                                "✓ Destinataire vérifié : $recipient"
                            } else {
                                "⚠️ Numéro à compléter pour « $recipient »"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isPhoneValid) GoogleGreen else GoogleYellow
                        )
                        Text(
                            text = if (isPhoneValid) "Numéro prêt : $phone" else "Saisissez ou choisissez un contact ci-dessous.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GoogleTextPrimary
                        )
                    }
                }

                if (resolution.alternatives.isNotEmpty()) {
                    Text(
                        text = "Vos contacts :",
                        style = MaterialTheme.typography.labelSmall,
                        color = GoogleTextSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        (listOf(best) + resolution.alternatives)
                            .filter { it.phoneNumber.isNotBlank() }
                            .distinctBy { it.contactKey }
                            .forEach { cand ->
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = GoogleSurfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable {
                                            recipient = cand.contactName
                                            phone = cand.phoneNumber
                                        }
                                ) {
                                    Text(
                                        text = "${cand.contactName} (${cand.phoneNumber})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GoogleBlue,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                    }
                }

                OutlinedTextField(
                    value = recipient,
                    onValueChange = { recipient = it },
                    label = { Text("Destinataire confirmé") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Numéro de téléphone") },
                    placeholder = { Text("+33 6...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Message SMS final") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Annuler", color = GoogleTextSecondary)
                    }
                    Button(
                        onClick = { onConfirmSend(recipient, phone, body) },
                        enabled = isPhoneValid && body.isNotBlank(),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = GoogleGreen),
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("modal_confirm_sms_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Confirmer & Envoyer",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LiaConversationHistoryDialog(
    threads: List<LiaConversationThread>,
    activeThreadId: String,
    onSelectThread: (String) -> Unit,
    onNewConversation: () -> Unit,
    onDeleteThread: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = GoogleSurface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, GoogleBorderSubtle, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🕒 Mes discussions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GoogleTextPrimary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", tint = GoogleTextSecondary)
                    }
                }

                Text(
                    text = "Retrouvez toutes vos conversations avec LIA.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GoogleTextSecondary
                )

                Button(
                    onClick = onNewConversation,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = GoogleBlueVivid),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Nouvelle discussion", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    threads.forEach { thread ->
                        val isSelected = thread.id == activeThreadId
                        val lastMsg = thread.messages.lastOrNull()?.text ?: "Aucun message"
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) GoogleBlueContainer else GoogleSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onSelectThread(thread.id) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = "${thread.title} (${thread.messages.size} msg)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) GoogleBlue else GoogleTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = lastMsg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GoogleTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = thread.updatedAt,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GoogleTextMuted
                                    )
                                }

                                if (threads.size > 1) {
                                    IconButton(
                                        onClick = { onDeleteThread(thread.id) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Supprimer",
                                            tint = GoogleRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiaPersonalDatabaseAndLearningDialog(
    souvenirs: List<LiaSouvenirItem>,
    facts: List<LiaUserFact>,
    shortcuts: List<LiaLearnedShortcut>,
    longTermMemories: List<LiaLongTermMemoryItem>,
    appUsageHabits: List<LiaAppUsageHabitItem> = emptyList(),
    lastRagOutcome: RagRetrievalOutcome? = null,
    onSaveSouvenir: (String, String, String, String, String) -> Unit,
    onDeleteSouvenir: (String) -> Unit,
    onSaveFact: (String, String, String) -> Unit,
    onDeleteFact: (String) -> Unit,
    onLaunchShortcut: (LiaLearnedShortcut) -> Unit,
    onBoostShortcut: (LiaLearnedShortcut) -> Unit,
    onLaunchInstalledApp: (String, String) -> Unit = { _, _ -> },
    onDeleteLongTermMemory: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Souvenirs, 1 = Contacts & Infos, 2 = RL Actions, 3 = LongTermMemory
    var newSouvenirType by rememberSaveable { mutableStateOf("FAIT") }
    var newSouvenirTitle by rememberSaveable { mutableStateOf("") }
    var newSouvenirValue by rememberSaveable { mutableStateOf("") }
    var newFactLabel by rememberSaveable { mutableStateOf("") }
    var newFactValue by rememberSaveable { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = GoogleSurface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, GoogleBorderSubtle, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🧠 Mes souvenirs & Contacts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GoogleTextPrimary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", tint = GoogleTextSecondary)
                    }
                }

                // 3 Clean Consumer Sub-tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tabs = listOf(
                        0 to "💭 Souvenirs (${souvenirs.size})",
                        1 to "👤 Contacts (${facts.size})",
                        2 to "⚡ Habitudes (${shortcuts.size + appUsageHabits.size})"
                    )
                    tabs.forEach { (idx, title) ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (selectedTab == idx) GoogleBlueContainer else GoogleSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { selectedTab = idx }
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == idx) GoogleBlue else GoogleTextSecondary,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                            )
                        }
                    }
                }

                when (selectedTab) {
                    0 -> {
                        if (souvenirs.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = GoogleBackground,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Aucun souvenir enregistré pour le moment. Ajoutez une information ci-dessous ou dites-la simplement à LIA dans la discussion.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GoogleTextMuted,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 210.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                souvenirs.forEach { item ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = GoogleSurfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "[${item.type}] ${item.title}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = GooglePurple
                                                )
                                                Text(
                                                    text = item.detailValue,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = GoogleTextPrimary
                                                )
                                            }
                                            IconButton(
                                                onClick = { onDeleteSouvenir(item.id) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = GoogleRed, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Type selector + add souvenir
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("FAIT", "PREFERENCE", "EVENEMENT").forEach { typeOpt ->
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (newSouvenirType == typeOpt) GooglePurpleContainer else GoogleSurfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable { newSouvenirType = typeOpt }
                                ) {
                                    Text(
                                        text = typeOpt,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (newSouvenirType == typeOpt) GooglePurple else GoogleTextSecondary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newSouvenirTitle,
                                onValueChange = { newSouvenirTitle = it },
                                placeholder = { Text("Sujet (ex: Âge)") },
                                singleLine = true,
                                modifier = Modifier.weight(0.45f)
                            )
                            OutlinedTextField(
                                value = newSouvenirValue,
                                onValueChange = { newSouvenirValue = it },
                                placeholder = { Text("Détail (ex: 24 ans)") },
                                singleLine = true,
                                modifier = Modifier.weight(0.55f)
                            )
                            IconButton(
                                onClick = {
                                    if (newSouvenirTitle.isNotBlank() && newSouvenirValue.isNotBlank()) {
                                        val key = newSouvenirTitle.lowercase(Locale.getDefault()).replace(" ", "_")
                                        onSaveSouvenir(
                                            newSouvenirType,
                                            key,
                                            newSouvenirTitle.trim(),
                                            newSouvenirValue.trim(),
                                            "$key,${newSouvenirTitle.lowercase()}"
                                        )
                                        if (newSouvenirType == "FAIT") {
                                            onSaveFact(key, newSouvenirTitle.trim(), newSouvenirValue.trim())
                                        }
                                        newSouvenirTitle = ""
                                        newSouvenirValue = ""
                                    }
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(GoogleBlueVivid)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Ajouter", tint = Color.White)
                            }
                        }
                    }

                    1 -> {
                        val context = LocalContext.current
                        var syncStatusBanner by rememberSaveable { mutableStateOf("") }
                        val contactsPermissionLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.RequestPermission()
                        ) { granted ->
                            if (granted) {
                                val count = SafeContactResolver.syncAndroidContactsIntoRoom(context)
                                syncStatusBanner = if (count > 0) {
                                    "✓ $count contact(s) réel(s) du répertoire Android synchronisé(s) dans Room."
                                } else {
                                    "ℹ️ Répertoire Android vide sur cet émulateur — ajoutez un contact ci-dessous."
                                }
                            } else {
                                syncStatusBanner = "⚠️ Permission READ_CONTACTS refusée."
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Vos contacts et numéros enregistrés.",
                                style = MaterialTheme.typography.bodySmall,
                                color = GoogleTextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = GoogleBlueContainer,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable {
                                        if (SafeContactResolver.hasReadContactsPermission(context)) {
                                            val count = SafeContactResolver.syncAndroidContactsIntoRoom(context)
                                            syncStatusBanner = if (count > 0) {
                                                "✓ $count contact(s) importé(s) depuis votre téléphone."
                                            } else {
                                                "ℹ️ Aucun nouveau contact trouvé sur cet appareil."
                                            }
                                        } else {
                                            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                        }
                                    }
                                    .testTag("sync_android_contacts_button")
                            ) {
                                Text(
                                    text = "🔄 Importer mes contacts",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = GoogleBlue,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        if (syncStatusBanner.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = GoogleGreenContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = syncStatusBanner,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GoogleGreen,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        if (facts.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = GoogleBackground,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Aucun contact ou fait pré-enregistré. Ajoutez par exemple « Numéro Maman » ci-dessous.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GoogleTextMuted,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                facts.forEach { fact ->
                                    var editVal by rememberSaveable(fact.key, fact.value) { mutableStateOf(fact.value) }
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = GoogleSurfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = fact.label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = GoogleBlue
                                                )
                                                OutlinedTextField(
                                                    value = editVal,
                                                    onValueChange = {
                                                        editVal = it
                                                        onSaveFact(fact.key, fact.label, it)
                                                    },
                                                    placeholder = { Text("Valeur...") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                            IconButton(
                                                onClick = { onDeleteFact(fact.key) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = GoogleRed, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newFactLabel,
                                onValueChange = { newFactLabel = it },
                                placeholder = { Text("Nom (ex: Maman)") },
                                singleLine = true,
                                modifier = Modifier.weight(0.45f)
                            )
                            OutlinedTextField(
                                value = newFactValue,
                                onValueChange = { newFactValue = it },
                                placeholder = { Text("Numéro ou info") },
                                singleLine = true,
                                modifier = Modifier.weight(0.55f)
                            )
                            IconButton(
                                onClick = {
                                    if (newFactLabel.isNotBlank() && newFactValue.isNotBlank()) {
                                        val rawKey = newFactLabel.lowercase(Locale.getDefault()).replace(" ", "_")
                                        val finalKey = if (newFactValue.any { it.isDigit() } && !rawKey.endsWith("_tel")) {
                                            "${rawKey}_tel"
                                        } else rawKey
                                        onSaveFact(finalKey, newFactLabel.trim(), newFactValue.trim())
                                        newFactLabel = ""
                                        newFactValue = ""
                                    }
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(GoogleBlueVivid)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Ajouter", tint = Color.White)
                            }
                        }
                    }

                    2 -> {
                        if (shortcuts.isEmpty() && appUsageHabits.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = GoogleBackground,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Vos applications et actions fréquentes apparaîtront automatiquement ici au fil de votre utilisation.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GoogleTextMuted,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 260.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (appUsageHabits.isNotEmpty()) {
                                    Text(
                                        text = "📱 Vos applications fréquentes",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = GoogleBlue
                                    )
                                    appUsageHabits.forEach { habit ->
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = GoogleSurfaceVariant,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Text(
                                                        text = "📱 ${habit.appLabel}",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        color = GoogleTextPrimary
                                                    )
                                                    Text(
                                                        text = "Ouverte ${habit.launchCount} fois • Dernière fois à ${habit.lastTimeFormatted}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = GoogleTextSecondary
                                                    )
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(50),
                                                    color = GoogleBlueContainer,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(50))
                                                        .clickable { onLaunchInstalledApp(habit.packageName, habit.appLabel) }
                                                ) {
                                                    Text(
                                                        text = "Ouvrir",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = GoogleBlue,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                shortcuts.forEach { shortcut ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = GoogleSurfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = "⚡ ${shortcut.triggerPhrase} (x${shortcut.usesCount})",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = GoogleTextPrimary
                                                )
                                                Text(
                                                    text = "Score Q=${"%.2f".format(shortcut.qValueScore)} • ${shortcut.preferredTimeBucket} • ${shortcut.avgLatencyMs} ms",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = GoogleGreen
                                                )
                                                Text(
                                                    text = shortcut.actionTitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = GoogleTextSecondary
                                                )
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Surface(
                                                    shape = RoundedCornerShape(50),
                                                    color = GooglePurpleContainer,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(50))
                                                        .clickable { onBoostShortcut(shortcut) }
                                                ) {
                                                    Text(
                                                        text = "+ Renforcer",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = GooglePurple,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                    )
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(50),
                                                    color = GoogleGreenContainer,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(50))
                                                        .clickable { onLaunchShortcut(shortcut) }
                                                ) {
                                                    Text(
                                                        text = "Ouvrir",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = GoogleGreen,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    3 -> {
                        Text(
                            text = "Table Room `LongTermMemory` : préférences, raccourcis d'applications et patterns d'interaction.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GoogleTextSecondary
                        )

                        if (longTermMemories.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = GoogleBackground,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "La table LongTermMemory est vierge et prête à stocker vos préférences et raccourcis au fil de vos échanges.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GoogleTextMuted,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 260.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                longTermMemories.forEach { ltm ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = GoogleSurfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "[${ltm.category}] ${ltm.title} (x${ltm.accessFrequency})",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = GoogleBlue
                                                )
                                                Text(
                                                    text = ltm.contentValue,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = GoogleTextPrimary
                                                )
                                            }
                                            IconButton(
                                                onClick = { onDeleteLongTermMemory(ltm.id) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = GoogleRed, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog dedicated to:
 * 1) Google Workspace & Ecosystem integration (OAuth + native deep-links for Gmail, Google Agenda,
 *    Google Drive, Google Docs, Google Sheets, Google Tasks, Google Contacts, Google Maps, YouTube, Photos).
 * 2) Universal Phone App Directory (PackageManager + live BroadcastReceiver for newly installed apps).
 */
@Composable
fun LiaUniversalAppsAndGoogleDialog(
    hubState: com.example.data.UniversalHubState,
    workspaceAuthState: com.example.data.GoogleWorkspaceAuthState = com.example.data.GoogleWorkspaceAuthState(),
    onSignInWithCredentialManager: () -> Unit = {},
    onSyncGoogleWorkspaceApis: () -> Unit = {},
    onLaunchGoogleSpec: (com.example.data.GoogleWorkspaceIntegrationSpec) -> Unit,
    onTriggerPromptCommand: (String) -> Unit,
    onLaunchPhoneApp: (packageName: String, appLabel: String) -> Unit,
    onRescanApps: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Google Ecosystem & OAuth, 1 = Toutes les Apps du Téléphone
    var searchQuery by rememberSaveable { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = GoogleSurface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, GoogleBorderSubtle, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "📱 Mes applications & Services",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GoogleTextPrimary
                        )
                        Text(
                            text = "${hubState.installedPhoneApps.size} applications disponibles sur votre téléphone",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoogleTextSecondary
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", tint = GoogleTextSecondary)
                    }
                }

                // Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabs = listOf(
                        "🌐 Services Google" to 0,
                        "📱 Sur mon téléphone (${hubState.installedPhoneApps.size})" to 1
                    )
                    tabs.forEach { (title, idx) ->
                        val active = selectedTab == idx
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (active) GoogleBlueContainer else GoogleSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .clickable { selectedTab = idx }
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (active) GoogleBlue else GoogleTextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                when (selectedTab) {
                    0 -> {
                        // Clean Google Account & Sync Banner
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = GoogleGreenContainer.copy(alpha = 0.35f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, GoogleGreen.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (workspaceAuthState.isSignedIn)
                                        "✅ Compte connecté (${workspaceAuthState.userDisplayName.ifBlank { workspaceAuthState.userEmail }})"
                                    else
                                        "🔑 Connecter votre compte Google",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = GoogleGreen
                                )
                                Text(
                                    text = workspaceAuthState.lastSyncSummary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GoogleTextSecondary
                                )

                                // Live Cloud APIs counters row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val stats = listOf(
                                        "📧 ${workspaceAuthState.recentEmails.size} mails",
                                        "📅 ${workspaceAuthState.upcomingEvents.size} RDV",
                                        "📁 ${workspaceAuthState.recentDriveFiles.size} Drive",
                                        "👤 ${workspaceAuthState.syncedContacts.size} contacts"
                                    )
                                    stats.forEach { statText ->
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = GoogleSurfaceVariant
                                        ) {
                                            Text(
                                                text = statText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = GoogleTextPrimary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = onSignInWithCredentialManager,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = GoogleBlueVivid,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = if (workspaceAuthState.isSignedIn) "🔑 Compte Google" else "🔑 Google Sign-In",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = onSyncGoogleWorkspaceApis,
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = if (workspaceAuthState.isSyncingWorkspace) "⏳ Sync..." else "🔄 Sync Cloud APIs",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = GoogleGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // List of Google Apps
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 310.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            hubState.googleAppsCatalog.forEach { spec ->
                                val isInstalledOnDevice = hubState.installedPhoneApps.any {
                                    it.packageName.equals(spec.packageName, ignoreCase = true)
                                }
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = GoogleSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(text = spec.emoji, style = MaterialTheme.typography.titleMedium)
                                                Column {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = spec.title,
                                                            style = MaterialTheme.typography.labelLarge,
                                                            fontWeight = FontWeight.Bold,
                                                            color = GoogleTextPrimary
                                                        )
                                                        Surface(
                                                            shape = RoundedCornerShape(50),
                                                            color = if (isInstalledOnDevice) GoogleGreenContainer else GoogleBlueContainer
                                                        ) {
                                                            Text(
                                                                text = if (isInstalledOnDevice) "APK Installé" else "Cloud / Deep-Link",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = if (isInstalledOnDevice) GoogleGreen else GoogleBlue,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = spec.capabilitiesDescription,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = GoogleTextSecondary
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { onLaunchGoogleSpec(spec) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = GoogleBlueVivid,
                                                    contentColor = Color.White
                                                ),
                                                shape = RoundedCornerShape(50),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Ouvrir ${spec.title}", style = MaterialTheme.typography.labelSmall)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    onTriggerPromptCommand(spec.sampleVoiceCommand)
                                                },
                                                shape = RoundedCornerShape(50),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Demander à LIA", style = MaterialTheme.typography.labelSmall, color = GoogleBlue)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = { Text("Rechercher une application…") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = onRescanApps,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text("🔄 Actualiser", style = MaterialTheme.typography.labelSmall, color = GoogleBlue)
                            }
                        }

                        val filteredApps = if (searchQuery.isBlank()) {
                            hubState.installedPhoneApps
                        } else {
                            val q = searchQuery.trim().lowercase()
                            hubState.installedPhoneApps.filter {
                                it.appLabel.lowercase().contains(q) ||
                                    it.packageName.lowercase().contains(q)
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 310.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            filteredApps.forEach { app ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = GoogleSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = "${app.emoji} ${app.appLabel}",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = GoogleTextPrimary
                                            )
                                            if (app.isNewlyInstalledAfterBoot) {
                                                Surface(
                                                    shape = RoundedCornerShape(50),
                                                    color = GoogleGreenContainer
                                                ) {
                                                    Text(
                                                        text = "Nouveau",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = GoogleGreen,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Button(
                                            onClick = { onLaunchPhoneApp(app.packageName, app.appLabel) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = GoogleGreenContainer,
                                                contentColor = GoogleGreen
                                            ),
                                            shape = RoundedCornerShape(50)
                                        ) {
                                            Text("Ouvrir", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

