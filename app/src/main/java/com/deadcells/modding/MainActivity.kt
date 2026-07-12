package com.deadcells.modding

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deadcells.modding.ui.theme.DCMMTTheme
import io.github.libxposed.service.XposedService
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity(), App.ServiceStateListener {
    private var mService: XposedService? = null
    private var serviceActive by mutableStateOf(false)
    private var frameworkInfo by mutableStateOf("")
    private var apiVersion by mutableStateOf(0)

    private val scopeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        onServiceStateChanged(mService)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DCMMTTheme {
                MainScreen(
                    serviceActive = serviceActive,
                    frameworkInfo = frameworkInfo,
                    apiVersion = apiVersion,
                    onOpenScope = { scopeLauncher.launch(Intent(this, ScopeActivity::class.java)) },
                    onOpenPakTool = { startActivity(Intent(this, PakActivity::class.java)) },
                    onOpenGitHub = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Alhugh233/dead-cells-mobile-modding-toolbox"))) },
                    onOpenAbout = { startActivity(Intent(this, AboutActivity::class.java)) }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        App.addServiceStateListener(this, true)
    }

    override fun onStop() {
        App.removeServiceStateListener(this)
        super.onStop()
    }

    override fun onServiceStateChanged(service: XposedService?) {
        mService = service
        serviceActive = service != null
        apiVersion = service?.apiVersion ?: 0
        frameworkInfo = if (service != null) {
            "${service.frameworkName} v${service.frameworkVersion}"
        } else ""
    }
}

@Composable
fun MainScreen(
    serviceActive: Boolean,
    frameworkInfo: String,
    apiVersion: Int,
    onOpenScope: () -> Unit,
    onOpenPakTool: () -> Unit,
    onOpenGitHub: () -> Unit,
    onOpenAbout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = "DCMMT")
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Card(
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = if (serviceActive) "✓ " + stringResource(R.string.status_active, apiVersion)
                        else stringResource(R.string.status_inactive),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (serviceActive) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (frameworkInfo.isNotEmpty()) {
                        Text(
                            text = frameworkInfo,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            BasicComponent(
                title = stringResource(R.string.request_scope),
                summary = stringResource(R.string.scope_required),
                endActions = {
                    Button(onClick = onOpenScope) {
                        Text(stringResource(R.string.apply))
                    }
                },
                onClick = onOpenScope
            )

            ArrowPreference(
                title = stringResource(R.string.pak_tool),
                summary = "Unpack / Pack / Merge PAK and Atlas files",
                onClick = onOpenPakTool
            )

            ArrowPreference(
                title = "GitHub",
                summary = "github.com/Alhugh233/dead-cells-mobile-modding-toolbox",
                onClick = onOpenGitHub
            )

            ArrowPreference(
                title = stringResource(R.string.about),
                summary = "Credits and licenses",
                onClick = onOpenAbout
            )
        }
    }
}
