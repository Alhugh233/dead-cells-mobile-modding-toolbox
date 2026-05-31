package com.deadcells.modding

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
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
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

class PakActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!hasStoragePermission()) {
            showPermissionDialog()
        }

        setContent {
            DCMMTTheme {
                PakScreen(
                    onRunOperation = { op ->
                        GlobalScope.launch(Dispatchers.IO) {
                            val ok = op()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@PakActivity,
                                    if (ok) getString(R.string.pak_complete, "OK")
                                    else getString(R.string.pak_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onOpenAtlas = {
                        startActivity(Intent(this, AtlasActivity::class.java))
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!hasStoragePermission()) {
            Toast.makeText(this, getString(R.string.pak_no_permission), Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true
    }

    private fun showPermissionDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.pak_need_permission_title))
                .setMessage(getString(R.string.pak_need_permission_msg))
                .setPositiveButton(getString(R.string.pak_grant)) { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                }
                .setNegativeButton(getString(R.string.cancel)) { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }
    }
}

@Composable
fun PakScreen(
    onRunOperation: (suspend () -> Boolean) -> Unit,
    onOpenAtlas: () -> Unit
) {
    val unpackPakState = rememberTextFieldState()
    val unpackDirState = rememberTextFieldState()
    val packDirState = rememberTextFieldState()
    val packOutState = rememberTextFieldState()
    val mergeInState = rememberTextFieldState()
    val mergeOutState = rememberTextFieldState()

    Scaffold(
        topBar = {
            TopAppBar(title = stringResource(R.string.pak_title))
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            SectionTitle(stringResource(R.string.pak_unpack))
            TextField(
                state = unpackPakState,
                label = stringResource(R.string.pak_unpack_hint_pak),
                useLabelAsPlaceholder = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                state = unpackDirState,
                label = stringResource(R.string.pak_unpack_hint_dir),
                useLabelAsPlaceholder = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val pp = unpackPakState.text.toString()
                    val ud = unpackDirState.text.toString()
                    if (pp.isEmpty() || ud.isEmpty()) return@Button
                    onRunOperation { PakTool.unpack(pp.trim(), ud.trim()) }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.pak_unpack_btn))
            }

            SectionTitle(stringResource(R.string.pak_pack))
            TextField(
                state = packDirState,
                label = stringResource(R.string.pak_pack_hint_dir),
                useLabelAsPlaceholder = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                state = packOutState,
                label = stringResource(R.string.pak_pack_hint_pak),
                useLabelAsPlaceholder = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val pd = packDirState.text.toString()
                    val po = packOutState.text.toString()
                    if (pd.isEmpty() || po.isEmpty()) return@Button
                    onRunOperation { PakTool.pack(pd.trim(), po.trim(), null) }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.pak_pack_btn))
            }

            SectionTitle(stringResource(R.string.pak_merge))
            TextField(
                state = mergeInState,
                label = stringResource(R.string.pak_merge_hint_in),
                useLabelAsPlaceholder = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                state = mergeOutState,
                label = stringResource(R.string.pak_merge_hint_out),
                useLabelAsPlaceholder = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val mi = mergeInState.text.toString()
                    val mo = mergeOutState.text.toString()
                    if (mi.isEmpty() || mo.isEmpty()) return@Button
                    val inputs = mi.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    if (inputs.size < 2) return@Button
                    onRunOperation { PakTool.merge(mo.trim(), null, *inputs.toTypedArray()) }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.pak_merge_btn))
            }

            ArrowPreference(
                title = stringResource(R.string.atlas_title),
                summary = stringResource(R.string.atlas_launch_hint),
                onClick = onOpenAtlas
            )
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        color = MiuixTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}
