package com.deadcells.modding

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deadcells.modding.ui.theme.DCMMTTheme
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedService.OnScopeEventListener
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

class ScopeActivity : ComponentActivity() {

    private val knownPackages = listOf(
        "com.bilibili.deadcells.mobile",
        "com.playdigious.deadcells.mobile"
    )

    private val packageLabels = mapOf(
        "com.bilibili.deadcells.mobile" to "Dead Cells (Bilibili)",
        "com.playdigious.deadcells.mobile" to "Dead Cells (Global)"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DCMMTTheme {
                var currentScope by remember { mutableStateOf(emptySet<String>()) }
                var selected by remember { mutableStateOf(emptySet<String>()) }
                var initialized by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    val svc = App.mService
                    if (svc != null) {
                        currentScope = svc.scope.toSet()
                        selected = currentScope
                    }
                    initialized = true
                }

                if (initialized) {
                    ScopeScreen(
                        packages = knownPackages,
                        labels = packageLabels,
                        currentScope = currentScope,
                        selected = selected,
                        onToggle = { pkg ->
                            selected = if (pkg in selected) selected - pkg else selected + pkg
                        },
                        onApply = {
                            val svc = App.mService ?: return@ScopeScreen
                            val added = selected - currentScope
                            val removed = currentScope - selected

                            if (removed.isNotEmpty()) {
                                svc.removeScope(removed.toList())
                                currentScope = currentScope - removed
                            }

                            if (added.isNotEmpty()) {
                                svc.requestScope(added.toList(), object : OnScopeEventListener {
                                    override fun onScopeRequestApproved(approved: List<String>) {
                                        runOnUiThread {
                                            currentScope = currentScope + approved.toSet()
                                            Toast.makeText(
                                                this@ScopeActivity,
                                                getString(R.string.toast_scope_ok, approved.toString()),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }

                                    override fun onScopeRequestFailed(message: String) {
                                        runOnUiThread {
                                            Toast.makeText(
                                                this@ScopeActivity,
                                                getString(R.string.toast_scope_fail, message),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                })
                            }
                        },
                        onClose = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
fun ScopeScreen(
    packages: List<String>,
    labels: Map<String, String>,
    currentScope: Set<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onApply: () -> Unit,
    onClose: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.scope_title),
                actions = {
                    Button(onClick = onClose) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            // Status card
            val scopedCount = currentScope.size
            Card(modifier = Modifier.padding(bottom = 12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (scopedCount > 0) stringResource(R.string.scope_count, scopedCount)
                        else stringResource(R.string.scope_none)
                    )
                    Text(
                        text = stringResource(R.string.scope_hint),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            packages.forEach { pkg ->
                val checked = pkg in selected
                val isCurrentlyScoped = pkg in currentScope
                BasicComponent(
                    title = labels[pkg] ?: pkg,
                    summary = if (isCurrentlyScoped) stringResource(R.string.scope_scoped) else stringResource(R.string.scope_not_scoped),
                    endActions = {
                        Switch(
                            checked = checked,
                            onCheckedChange = { onToggle(pkg) }
                        )
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            val hasChanges = selected != currentScope
            Button(
                onClick = onApply,
                enabled = hasChanges,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.apply))
            }
        }
    }
}
