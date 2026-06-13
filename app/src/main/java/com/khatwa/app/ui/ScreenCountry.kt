package com.khatwa.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.khatwa.app.data.Prefs
import java.util.Locale

private data class Country(val code: String, val name: String, val flag: String)

private fun flagEmoji(code: String): String =
    code.uppercase().map { c ->
        String(Character.toChars(0x1F1E6 - 'A'.code + c.code))
    }.joinToString("")

@Composable
fun CountryScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var query by remember { mutableStateOf("") }

    val countries = remember {
        Locale.getISOCountries()
            .map { code -> Country(code, Locale("", code).displayCountry, flagEmoji(code)) }
            .filter { it.name.isNotBlank() }
            .sortedBy { it.name.lowercase() }
    }
    val shown = remember(query, countries) {
        if (query.isBlank()) countries
        else countries.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }

    Column(
        Modifier.fillMaxSize().background(NightBg).statusBarsPadding().navigationBarsPadding()
    ) {
        RunningIntro(Modifier.padding(top = 8.dp))
        Column(Modifier.padding(horizontal = 22.dp)) {
            Text("Where are you", style = MaterialTheme.typography.displaySmall, color = Sand)
            Text("running from?", style = MaterialTheme.typography.displaySmall, color = Ember)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Search your country", color = Muted) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Ember, unfocusedBorderColor = Outline,
                    cursorColor = Ember, focusedTextColor = Sand, unfocusedTextColor = Sand
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.code }) { c ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable {
                            prefs.country = c.name
                            onDone()
                        }
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(c.flag, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(14.dp))
                    Text(c.name, style = MaterialTheme.typography.bodyLarge, color = Sand)
                }
                HorizontalDivider(color = Outline.copy(alpha = 0.35f))
            }
        }
    }
}
