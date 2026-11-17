package com.raiddex.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.raiddex.data.PokemonEntity
import com.raiddex.domain.Formulas
import com.raiddex.domain.GameData
import com.raiddex.domain.IvSolver
import com.raiddex.domain.Ivs
import com.raiddex.domain.RaidContext
import com.raiddex.domain.RankedAttacker
import com.raiddex.domain.Search
import com.raiddex.domain.Species
import com.raiddex.domain.Weather
import kotlinx.coroutines.launch

private val numeric = KeyboardOptions(keyboardType = KeyboardType.Number)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(vm: MainViewModel) {
    val list by vm.collection.collectAsState()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Coleção (${list.size})") }) },
        floatingActionButton = { FloatingActionButton(onClick = { vm.screen = Screen.Edit(null) }) { Text("+") } },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.screen = Screen.Team }, Modifier.weight(1f)) { Text("Time da raid") }
                OutlinedButton(onClick = { vm.screen = Screen.Settings }, Modifier.weight(1f)) { Text("Configurações") }
            }
        },
    ) { pad ->
        if (list.isEmpty()) {
            Text("Nenhum Pokémon cadastrado. Toque em + para adicionar.", Modifier.padding(pad).padding(16.dp))
        } else {
            LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(list, key = { it.id }) { p ->
                    val sp = vm.data.speciesById[p.speciesId]
                    Card(Modifier.fillMaxWidth().clickable { vm.screen = Screen.Edit(p.id) }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                val title = (sp?.name ?: p.speciesId) + if (p.shadow) " (sombrio)" else ""
                                Text(title, style = MaterialTheme.typography.titleMedium)
                                Text("PC ${p.cp} · PS ${p.hp} · nível ${fmtLevel(p.level)} · IV ${p.atkIv}/${p.defIv}/${p.staIv} (${"%.0f".format(Ivs(p.atkIv, p.defIv, p.staIv).percent)}%)")
                                Text("${moveName(vm.data, p.fastId)} + ${moveName(vm.data, p.chargedId)}", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { vm.toggleFavorite(p) }) { Text(if (p.favorite) "★" else "☆") }
                        }
                    }
                }
            }
        }
    }
}

private fun fmtLevel(l: Double) = if (l % 1.0 == 0.0) l.toInt().toString() else l.toString()
private fun moveName(d: GameData, id: String) = d.movesById[id]?.name ?: id

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(vm: MainViewModel, id: Long?) {
    val data = vm.data
    var loaded by remember { mutableStateOf(id == null) }
    var existing by remember { mutableStateOf<PokemonEntity?>(null) }
    var query by remember { mutableStateOf("") }
    var species by remember { mutableStateOf<Species?>(null) }
    var nickname by remember { mutableStateOf("") }
    var cp by remember { mutableStateOf("") }
    var hp by remember { mutableStateOf("") }
    var atk by remember { mutableStateOf("") }
    var def by remember { mutableStateOf("") }
    var sta by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("") }
    var fast by remember { mutableStateOf<String?>(null) }
    var charged by remember { mutableStateOf<String?>(null) }
    var shadow by remember { mutableStateOf(false) }
    var favorite by remember { mutableStateOf(false) }
    var force by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(id) {
        if (id != null) {
            vm.load(id)?.let { p ->
                existing = p
                species = data.speciesById[p.speciesId]
                query = species?.name.orEmpty()
                nickname = p.nickname; cp = p.cp.toString(); hp = p.hp.toString()
                atk = p.atkIv.toString(); def = p.defIv.toString(); sta = p.staIv.toString()
                level = fmtLevel(p.level); fast = p.fastId; charged = p.chargedId
                shadow = p.shadow; favorite = p.favorite
            }
            loaded = true
        }
    }

    fun solveLevel() {
        val sp = species; val c = cp.toIntOrNull(); val h = hp.toIntOrNull()
        val a = atk.toIntOrNull(); val d = def.toIntOrNull(); val s = sta.toIntOrNull()
        if (sp == null || c == null || h == null || a == null || d == null || s == null ||
            a !in 0..15 || d !in 0..15 || s !in 0..15
        ) { message = "Preencha espécie, PC, PS e os três IVs (0 a 15)."; return }
        val found = IvSolver.solve(data, sp, c, h, Ivs(a, d, s), vm.settings.maxPokemonLevel)
        if (found.isEmpty()) {
            message = "PC e PS não fecham com esses IVs em nenhum nível. Confira os números ou informe o nível à mão."
        } else {
            level = fmtLevel(found.first().level)
            message = if (found.size > 1) "Níveis possíveis: ${found.joinToString { fmtLevel(it.level) }}. Usando ${level}." else "Nível $level."
        }
    }

    fun save() {
        val sp = species; val c = cp.toIntOrNull(); val h = hp.toIntOrNull()
        val a = atk.toIntOrNull(); val d = def.toIntOrNull(); val s = sta.toIntOrNull(); val l = level.replace(',', '.').toDoubleOrNull()
        if (sp == null || c == null || h == null || a == null || d == null || s == null || l == null ||
            a !in 0..15 || d !in 0..15 || s !in 0..15 || fast == null || charged == null
        ) { message = "Preencha todos os campos obrigatórios."; return }
        if (l < 1.0 || l > vm.settings.maxPokemonLevel || (l * 2) % 1.0 != 0.0) {
            message = "Nível inválido (1 a ${fmtLevel(vm.settings.maxPokemonLevel)}, de meio em meio)."; return
        }
        val cpm = data.cpm(l)
        val ivs = Ivs(a, d, s)
        if (!force && (Formulas.cp(sp, ivs, cpm) != c || Formulas.hp(sp, ivs, cpm) != h)) {
            message = "PC/PS não conferem com nível e IVs (esperado PC ${Formulas.cp(sp, ivs, cpm)}, PS ${Formulas.hp(sp, ivs, cpm)}). Corrija ou marque \"Salvar mesmo assim\"."
            return
        }
        vm.save(
            PokemonEntity(
                id = existing?.id ?: 0, speciesId = sp.id, nickname = nickname, cp = c, hp = h, level = l,
                atkIv = a, defIv = d, staIv = s, fastId = fast!!, chargedId = charged!!, shadow = shadow, favorite = favorite,
            ),
        )
    }

    Scaffold(topBar = { TopAppBar(title = { Text(if (id == null) "Novo Pokémon" else "Editar Pokémon") }) }) { pad ->
        if (!loaded) return@Scaffold
        Column(Modifier.padding(pad).padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it; if (species?.name != it) species = null }, label = { Text("Espécie") }, modifier = Modifier.fillMaxWidth())
            if (species == null) {
                Search.species(data.file.species.filter { !it.mega }, query).forEach { sp ->
                    Text(sp.name, Modifier.fillMaxWidth().clickable {
                        species = sp; query = sp.name; fast = sp.fast.firstOrNull(); charged = sp.charged.firstOrNull(); shadow = false
                    }.padding(8.dp))
                }
            }
            species?.let { sp ->
                MovePicker("Ataque rápido", fast, sp.fast + sp.eliteFast, sp.eliteFast.toSet(), data) { fast = it }
                MovePicker("Ataque carregado", charged, sp.charged + sp.eliteCharged, sp.eliteCharged.toSet(), data) { charged = it }
            }
            OutlinedTextField(nickname, { nickname = it }, label = { Text("Apelido (opcional)") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(cp, { cp = it }, label = { Text("PC") }, keyboardOptions = numeric, modifier = Modifier.weight(1f))
                OutlinedTextField(hp, { hp = it }, label = { Text("PS") }, keyboardOptions = numeric, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(atk, { atk = it }, label = { Text("IV Ata") }, keyboardOptions = numeric, modifier = Modifier.weight(1f))
                OutlinedTextField(def, { def = it }, label = { Text("IV Def") }, keyboardOptions = numeric, modifier = Modifier.weight(1f))
                OutlinedTextField(sta, { sta = it }, label = { Text("IV PS") }, keyboardOptions = numeric, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(level, { level = it }, label = { Text("Nível") }, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = ::solveLevel) { Text("Calcular nível") }
            }
            if (species?.shadowable == true) CheckRow("Sombrio", shadow) { shadow = it }
            CheckRow("Favorito", favorite) { favorite = it }
            CheckRow("Salvar mesmo assim (PC/PS não conferem)", force) { force = it }
            if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ::save) { Text("Salvar") }
                OutlinedButton(onClick = { vm.screen = Screen.Collection }) { Text("Cancelar") }
                existing?.let { p -> TextButton(onClick = { vm.delete(p) }) { Text("Apagar") } }
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange)
        Text(label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovePicker(label: String, value: String?, options: List<String>, elite: Set<String>, data: GameData, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }) {
        OutlinedTextField(
            value = value?.let { moveName(data, it) + if (it in elite) " (elite)" else "" }.orEmpty(),
            onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(open, { open = false }) {
            options.forEach { id ->
                DropdownMenuItem(text = { Text(moveName(data, id) + if (id in elite) " (elite)" else "") }, onClick = { onPick(id); open = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(vm: MainViewModel) {
    val data = vm.data
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var boss by remember { mutableStateOf<Species?>(null) }
    var tierId by remember { mutableStateOf(data.file.bosses.first { it.id == "T5" }.id) }
    var weather by remember { mutableStateOf<Weather?>(null) }
    var friendship by remember { mutableStateOf(0) }
    var team by remember { mutableStateOf<List<RankedAttacker>?>(null) }
    var elapsed by remember { mutableStateOf(0L) }
    val bosses = remember(data) { data.file.species }

    Scaffold(topBar = { TopAppBar(title = { Text("Time da raid") }) }) { pad ->
        Column(Modifier.padding(pad).padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it; if (boss?.name != it) boss = null }, label = { Text("Chefe") }, modifier = Modifier.fillMaxWidth())
            if (boss == null) Search.species(bosses, query).forEach { sp ->
                Text(sp.name, Modifier.fillMaxWidth().clickable { boss = sp; query = sp.name }.padding(8.dp))
            }
            SimplePicker("Nível da raid", data.file.bosses.map { it.id to it.label + if (!it.confirmed) " *" else "" }, tierId) { tierId = it }
            SimplePicker("Clima", listOf("" to "Sem clima") + Weather.entries.map { it.name to it.name }, weather?.name.orEmpty()) { weather = Weather.entries.firstOrNull { w -> w.name == it } }
            SimplePicker("Amizade", (0..5).map { it.toString() to "Nível $it" }, friendship.toString()) { friendship = it.toInt() }
            Button(enabled = boss != null, onClick = {
                scope.launch {
                    val t0 = System.nanoTime()
                    team = vm.rank(RaidContext(boss!!.id, tierId, weather, friendship))
                    elapsed = (System.nanoTime() - t0) / 1_000_000
                }
            }) { Text("Calcular time") }
            team?.let { t ->
                if (t.isEmpty()) Text("Coleção vazia.")
                Text("Calculado em $elapsed ms", style = MaterialTheme.typography.bodySmall)
                t.forEach { r ->
                    val sp = data.speciesById[r.speciesId]
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${r.rank}. ${sp?.name ?: r.speciesId}", style = MaterialTheme.typography.titleMedium)
                            Text("Nota %.0f · DPS %.2f · TDO %.0f".format(r.score, r.dps, r.tdo))
                            Text("${moveName(data, r.attacker.fastId)} + ${moveName(data, r.attacker.chargedId)}", style = MaterialTheme.typography.bodySmall)
                            r.betterMove?.let {
                                Text("Aviso: ${moveName(data, it.fastId)} + ${moveName(data, it.chargedId)} renderia bem mais.", color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
            }
            Text("* PS/CPM do chefe ainda não confirmados em duas fontes.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimplePicker(label: String, options: List<Pair<String, String>>, value: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == value }?.second.orEmpty(), onValueChange = {}, readOnly = true,
            label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(open, { open = false }) {
            options.forEach { (id, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { onPick(id); open = false }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    var level by remember { mutableStateOf(vm.trainerLevel.toString()) }
    Scaffold(topBar = { TopAppBar(title = { Text("Configurações") }) }) { pad ->
        Column(Modifier.padding(pad).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(level, { level = it; it.toIntOrNull()?.let(vm::changeTrainerLevel) }, label = { Text("Nível de treinador") }, keyboardOptions = numeric, modifier = Modifier.fillMaxWidth())
            var arc by remember { mutableStateOf(vm.settings.arcMaxLevel.toString()) }
            OutlinedTextField(arc, { arc = it; it.toIntOrNull()?.let { v -> vm.settings.arcMaxLevel = v } }, label = { Text("Nível na ponta do arco (detalhe do Pokémon)") }, keyboardOptions = numeric, modifier = Modifier.fillMaxWidth())
            Text("Dados do jogo: versão ${vm.data.version}")
            Button(onClick = { vm.updateData() }) { Text("Atualizar dados") }
            if (vm.updateStatus.isNotEmpty()) Text(vm.updateStatus)
            OutlinedButton(onClick = { vm.screen = Screen.Collection }) { Text("Voltar") }
        }
    }
}
