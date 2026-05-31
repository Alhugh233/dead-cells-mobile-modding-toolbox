package com.deadcells.modding

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deadcells.modding.ui.theme.DCMMTTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

class AtlasActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DCMMTTheme {
                AtlasScreen(
                    onRunOperation = { op ->
                        GlobalScope.launch(Dispatchers.IO) {
                            val ok = op()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@AtlasActivity,
                                    if (ok) getString(R.string.pak_complete, "OK")
                                    else getString(R.string.pak_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AtlasScreen(
    onRunOperation: (suspend () -> Boolean) -> Unit
) {
    val atlasState = rememberTextFieldState()
    val outDirState = rememberTextFieldState()
    val pngDirState = rememberTextFieldState()
    val atlasOutState = rememberTextFieldState()
    val pngOutState = rememberTextFieldState()

    Scaffold(
        topBar = {
            TopAppBar(title = stringResource(R.string.atlas_title))
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            SectionTitle(stringResource(R.string.pak_atlas_unpack))
            TextField(
                state = atlasState,
                label = stringResource(R.string.pak_atlas_unpack_hint),
                useLabelAsPlaceholder = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                state = outDirState,
                label = stringResource(R.string.pak_unpack_hint_dir),
                useLabelAsPlaceholder = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val ap = atlasState.text.toString()
                    val od = outDirState.text.toString()
                    if (ap.isEmpty() || od.isEmpty()) return@Button
                    onRunOperation { PakTool.atlasUnpack(ap.trim(), od.trim()) }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.pak_atlas_unpack_btn))
            }

            SectionTitle(stringResource(R.string.pak_atlas_pack))
            TextField(
                state = pngDirState,
                label = stringResource(R.string.pak_atlas_pack_hint_dir),
                useLabelAsPlaceholder = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                state = atlasOutState,
                label = stringResource(R.string.pak_atlas_pack_hint_atlas),
                useLabelAsPlaceholder = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                state = pngOutState,
                label = stringResource(R.string.pak_atlas_pack_hint_png),
                useLabelAsPlaceholder = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val pd = pngDirState.text.toString()
                    val ao = atlasOutState.text.toString()
                    val po = pngOutState.text.toString()
                    if (pd.isEmpty() || ao.isEmpty() || po.isEmpty()) return@Button
                    onRunOperation { PakTool.atlasPack(pd.trim(), ao.trim(), po.trim()) }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.pak_atlas_pack_btn))
            }
        }
    }
}
