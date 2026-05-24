package com.deadcells.modding

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.deadcells.modding.databinding.ActivityMainBinding
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedService.OnScopeEventListener

@SuppressLint("SetTextI18n")
class MainActivity : Activity(), App.ServiceStateListener {
    private var mService: XposedService? = null
    private lateinit var binding: ActivityMainBinding

    private val knownPackages = arrayOf(
        "com.bilibili.deadcells.mobile",
        "com.playdigious.deadcells.mobile"
    )

    private val mCallback = object : OnScopeEventListener {
        override fun onScopeRequestApproved(approved: List<String>) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, getString(R.string.toast_scope_ok, approved.toString()), Toast.LENGTH_SHORT).show()
                binding.scope.text = mService?.scope?.joinToString("\n")
            }
        }

        override fun onScopeRequestFailed(message: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, getString(R.string.toast_scope_fail, message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        binding.let {
            setContentView(it.root)
            it.binder.text = getString(R.string.status_loading)
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

    private fun showScopeDialog() {
        val svc = mService ?: return
        val currentScope = svc.scope.toSet()
        val checked = BooleanArray(knownPackages.size) { knownPackages[it] in currentScope }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.request_scope))
            .setMultiChoiceItems(knownPackages, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(getString(R.string.apply)) { _, _ ->
                val selected = knownPackages.filterIndexed { i, _ -> checked[i] }
                if (selected.isNotEmpty()) svc.requestScope(selected, mCallback)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onServiceStateChanged(service: XposedService?) {
        mService = service
        runOnUiThread {
            binding.pakTool.setOnClickListener {
                val intent = Intent()
                intent.setClassName(this@MainActivity, "com.deadcells.modding.PakActivity")
                startActivity(intent)
            }
            binding.about.setOnClickListener {
                val intent = Intent()
                intent.setClassName(this@MainActivity, "com.deadcells.modding.AboutActivity")
                startActivity(intent)
            }

            if (service == null) {
                binding.binder.text = getString(R.string.status_inactive)
                binding.framework.text = ""
                binding.frameworkVersion.text = ""
                binding.scope.text = ""
            } else {
                binding.binder.text = getString(R.string.status_active, service.apiVersion)
                binding.framework.text = getString(R.string.framework_format,
                    service.frameworkName, service.frameworkVersion)
                binding.frameworkVersion.text = ""
                binding.scope.text = service.scope.joinToString("\n")
                    .ifEmpty { getString(R.string.scope_required) }

                binding.requestScope.setOnClickListener { showScopeDialog() }
            }
        }
    }
}
