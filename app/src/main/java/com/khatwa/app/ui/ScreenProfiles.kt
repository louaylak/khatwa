package com.khatwa.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.khatwa.app.data.ActivityStore
import com.khatwa.app.data.Gender
import com.khatwa.app.data.Prefs
import com.khatwa.app.data.Profile
import com.khatwa.app.data.ProfileStore
import java.io.File

// ---------------------------------------------------------------- avatar

@Composable
fun Avatar(profile: Profile?, sizeDp: Int, pickedPreview: Any? = null) {
    val model: Any? = pickedPreview ?: profile?.avatarPath?.let { File(it) }
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Surface2, Surface1))),
        contentAlignment = Alignment.Center
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = profile?.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                (profile?.name?.firstOrNull() ?: '+').uppercase(),
                style = MaterialTheme.typography.displaySmall,
                color = EmberGlow
            )
        }
    }
}

// ---------------------------------------------------------------- picker

@Composable
fun ProfilePickerScreen(
    onPicked: (Profile) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Profile) -> Unit
) {
    val ctx = LocalContext.current
    var profiles by remember { mutableStateOf<List<Profile>>(emptyList()) }
    LaunchedEffect(Unit) { profiles = ProfileStore(ctx).list().sortedBy { it.name.lowercase() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(NightBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp)
    ) {
        Spacer(Modifier.height(34.dp))
        Text("Who's moving", style = MaterialTheme.typography.displaySmall, color = Sand)
        Text("today?", style = MaterialTheme.typography.displaySmall, color = Ember)
        Spacer(Modifier.height(8.dp))
        Text(
            "Each profile gets its own history, stats and calorie model.",
            style = MaterialTheme.typography.bodyMedium, color = Muted
        )
        Spacer(Modifier.height(26.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(profiles, key = { it.id }) { p ->
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Surface1,
                    modifier = Modifier.clickable { onPicked(p) }
                ) {
                    Box {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Avatar(p, 92)
                            Spacer(Modifier.height(12.dp))
                            Text(p.name, style = MaterialTheme.typography.titleMedium, color = Sand)
                        }
                        IconButton(
                            onClick = { onEdit(p) },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = Muted, modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Surface1.copy(alpha = 0.55f),
                    modifier = Modifier.clickable { onAdd() }
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier.size(92.dp).clip(CircleShape).background(Surface2),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add", tint = Ember, modifier = Modifier.size(38.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("New profile", style = MaterialTheme.typography.titleMedium, color = Muted)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- editor

@Composable
fun ProfileEditScreen(
    existingId: String?,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val store = remember { ProfileStore(ctx) }
    val prefs = remember { Prefs(ctx) }
    val existing = remember { existingId?.let { store.get(it) } }

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var gender by remember { mutableStateOf(existing?.gender ?: Gender.MALE) }
    var age by remember { mutableStateOf(existing?.age?.toString() ?: "25") }
    var height by remember { mutableStateOf(existing?.heightCm?.toString() ?: "172") }
    var weight by remember { mutableStateOf(existing?.weightKg?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "70") }
    var pickedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) pickedUri = uri }

    val valid = name.trim().isNotEmpty() &&
            (age.toIntOrNull() ?: 0) in 5..110 &&
            (height.toIntOrNull() ?: 0) in 90..230 &&
            (weight.toDoubleOrNull() ?: 0.0) in 25.0..250.0

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Ember,
        unfocusedBorderColor = Outline,
        focusedLabelColor = EmberGlow,
        unfocusedLabelColor = Muted,
        cursorColor = Ember,
        focusedTextColor = Sand,
        unfocusedTextColor = Sand
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(NightBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Sand)
            }
            Text(
                if (existing == null) "New profile" else "Edit profile",
                style = MaterialTheme.typography.headlineSmall, color = Sand
            )
        }
        Spacer(Modifier.height(18.dp))

        Box(Modifier.align(Alignment.CenterHorizontally)) {
            Box(
                Modifier.clickable {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            ) {
                Avatar(existing?.copy(name = name.ifBlank { "?" }) ?: Profile("?", name.ifBlank { "?" }, gender, 0, 0, 0.0), 116, pickedPreview = pickedUri)
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Ember),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = "Pick photo", tint = Color(0xFF1A0D04), modifier = Modifier.size(19.dp))
            }
        }
        Spacer(Modifier.height(22.dp))

        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        Text("GENDER · used for the calorie model", style = MaterialTheme.typography.labelMedium, color = Muted)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = gender == Gender.MALE,
                onClick = { gender = Gender.MALE },
                label = { Text("Male") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Ember,
                    selectedLabelColor = Color(0xFF1A0D04)
                )
            )
            FilterChip(
                selected = gender == Gender.FEMALE,
                onClick = { gender = Gender.FEMALE },
                label = { Text("Female") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Ember,
                    selectedLabelColor = Color(0xFF1A0D04)
                )
            )
        }
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = age, onValueChange = { age = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("Age") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = fieldColors,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = height, onValueChange = { height = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("Height cm") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = fieldColors,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = weight, onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                label = { Text("Weight kg") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = fieldColors,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(26.dp))

        Button(
            onClick = {
                val id = existing?.id ?: ProfileStore.newId()
                val avatarPath = pickedUri?.let { store.saveAvatar(id, it) } ?: existing?.avatarPath
                val p = Profile(
                    id = id,
                    name = name.trim(),
                    gender = gender,
                    age = age.toIntOrNull() ?: 25,
                    heightCm = height.toIntOrNull() ?: 170,
                    weightKg = weight.toDoubleOrNull() ?: 70.0,
                    avatarPath = avatarPath
                )
                store.save(p)
                prefs.activeProfileId = id
                onDone()
            },
            enabled = valid,
            colors = ButtonDefaults.buttonColors(containerColor = Ember),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Text("Save profile", style = MaterialTheme.typography.titleMedium)
        }

        if (existing != null) {
            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Delete profile", color = Danger)
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Surface1,
            title = { Text("Delete ${existing.name}?", color = Sand) },
            text = {
                Text(
                    "This removes the profile and all of its recorded activities. This cannot be undone.",
                    color = Muted, textAlign = TextAlign.Start
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    ActivityStore(ctx).deleteAllFor(existing.id)
                    store.delete(existing.id)
                    if (prefs.activeProfileId == existing.id) prefs.activeProfileId = null
                    confirmDelete = false
                    onBack()
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = Muted) }
            }
        )
    }
}
