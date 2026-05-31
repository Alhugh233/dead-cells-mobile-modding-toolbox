package com.deadcells.modding

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deadcells.modding.ui.theme.DCMMTTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference

class AboutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DCMMTTheme {
                AboutScreen(onOpenUrl = { url ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                })
            }
        }
    }
}

@Composable
fun AboutScreen(onOpenUrl: (String) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = stringResource(R.string.about))
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            ArrowPreference(
                title = stringResource(R.string.about_dccm_name),
                summary = stringResource(R.string.about_dccm_desc),
                onClick = { onOpenUrl("https://github.com/dead-cells-core-modding/core") }
            )

            ArrowPreference(
                title = stringResource(R.string.about_alive_name),
                summary = stringResource(R.string.about_alive_desc),
                onClick = { onOpenUrl("https://github.com/N3rdL0rd/alivecells") }
            )

            ArrowPreference(
                title = "Miuix",
                summary = "Compose Multiplatform UI library\nhttps://github.com/compose-miuix-ui/miuix\nLicensed under Apache-2.0",
                onClick = { onOpenUrl("https://github.com/compose-miuix-ui/miuix") }
            )
        }
    }
}
