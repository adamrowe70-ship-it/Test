package runcoach.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import runcoach.core.IntervalPlan
import runcoach.core.RunnerProfile
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onConnectHealth: () -> Unit,
    onConnectSpotify: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val s by vm.state.collectAsState()

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("RunCoach", style = MaterialTheme.typography.headlineMedium)
            if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            s.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Connections", style = MaterialTheme.typography.titleMedium)
                    ConnectRow("Garmin via Health Connect", s.healthConnected, onConnectHealth)
                    ConnectRow("Spotify", s.spotifyConnected, onConnectSpotify)
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Next run", style = MaterialTheme.typography.titleMedium)
                    Text(s.plan.describe(), style = MaterialTheme.typography.headlineSmall)
                    Text("Plus ${s.plan.warmupSec / 60} min warm-up and ${s.plan.cooldownSec / 60} min cool-down walk · ${s.plan.totalSec / 60} min total")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.analyze(null) }, enabled = !s.busy && s.healthConnected) {
                            Text("Check latest run")
                        }
                        OutlinedButton(onClick = vm::makePlaylist, enabled = !s.busy && s.spotifyConnected) {
                            Text("New playlist")
                        }
                    }
                    s.playlistUrl?.let { url ->
                        OutlinedButton(onClick = { onOpenUrl(url) }) { Text("Open playlist in Spotify") }
                    }
                }
            }

            s.report?.let { report ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Last run review", style = MaterialTheme.typography.titleMedium)
                        Text(report, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        RpeInput(enabled = !s.busy) { vm.analyze(it) }
                    }
                }
            }

            Settings(s, onSave = vm::saveSettings)
        }
    }
}

@Composable
private fun ConnectRow(label: String, connected: Boolean, onConnect: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.padding(top = 12.dp))
        if (connected) Text("✓ Connected", Modifier.padding(top = 12.dp))
        else OutlinedButton(onClick = onConnect) { Text("Connect") }
    }
}

@Composable
private fun RpeInput(enabled: Boolean, onRate: (Int) -> Unit) {
    var rpe by remember { mutableFloatStateOf(5f) }
    Text("How hard did it feel? ${rpe.roundToInt()}/10 (1 = very easy, 10 = all-out)")
    Slider(value = rpe, onValueChange = { rpe = it }, valueRange = 1f..10f, steps = 8)
    OutlinedButton(onClick = { onRate(rpe.roundToInt()) }, enabled = enabled) {
        Text("Update advice with my rating")
    }
}

@Composable
private fun Settings(s: UiState, onSave: (RunnerProfile, IntervalPlan, Int) -> Unit) {
    // Keyed on the saved values so the fields refresh when the coach changes the plan.
    var age by remember(s.profile) { mutableStateOf(s.profile.age.toString()) }
    var maxHr by remember(s.profile) { mutableStateOf(s.profile.maxHr?.toString().orEmpty()) }
    var run by remember(s.plan) { mutableStateOf((s.plan.runSec / 60.0).fmt()) }
    var walk by remember(s.plan) { mutableStateOf((s.plan.walkSec / 60.0).fmt()) }
    var reps by remember(s.plan) { mutableStateOf(s.plan.reps.toString()) }
    var warmup by remember(s.plan) { mutableStateOf((s.plan.warmupSec / 60).toString()) }
    var tolerance by remember(s.bpmTolerance) { mutableStateOf(s.bpmTolerance.toString()) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Settings", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Age", age, Modifier.weight(1f)) { age = it }
                NumberField("Max HR (optional)", maxHr, Modifier.weight(1f)) { maxHr = it }
            }
            Text("Current session (if the coach got it wrong, set it here)", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Run min", run, Modifier.weight(1f)) { run = it }
                NumberField("Walk min", walk, Modifier.weight(1f)) { walk = it }
                NumberField("Reps", reps, Modifier.weight(1f)) { reps = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Warm-up/cool-down min", warmup, Modifier.weight(1f)) { warmup = it }
                NumberField("BPM tolerance ±", tolerance, Modifier.weight(1f)) { tolerance = it }
            }
            Button(onClick = {
                val profile = RunnerProfile(age.toIntOrNull() ?: s.profile.age, maxHr.toIntOrNull())
                val wu = ((warmup.toIntOrNull() ?: 5) * 60).coerceAtLeast(0)
                val plan = runCatching {
                    IntervalPlan(
                        runSec = ((run.toDoubleOrNull() ?: 0.0) * 60).roundToInt(),
                        walkSec = ((walk.toDoubleOrNull() ?: 0.0) * 60).roundToInt(),
                        reps = reps.toIntOrNull() ?: 0,
                        warmupSec = wu,
                        cooldownSec = wu,
                    )
                }.getOrDefault(s.plan)
                onSave(profile, plan, (tolerance.toIntOrNull() ?: s.bpmTolerance).coerceIn(1, 15))
            }) { Text("Save") }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

private fun Double.fmt() = if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(this)
