package com.luqman.pdfme

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.luqman.pdfme.databinding.ActivityMainBinding
import com.luqman.pdfme.databinding.ItemRecentBinding
import org.json.JSONArray
import org.json.JSONObject

data class RecentFile(val uri: String, val name: String)

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val recents = mutableListOf<RecentFile>()
    private lateinit var adapter: RecentsAdapter

    private val openPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }
            addRecent(uri, queryName(uri))
            openViewer(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadRecents()
        adapter = RecentsAdapter(
            recents,
            onOpen = { openViewer(Uri.parse(it.uri)) },
            onRemove = { file -> confirmRemove(file) }
        )
        binding.recentsList.layoutManager = LinearLayoutManager(this)
        binding.recentsList.adapter = adapter
        refreshEmptyState()

        binding.openPdfButton.setOnClickListener {
            openPicker.launch(arrayOf("application/pdf"))
        }

        // Opened from a file manager / another app.
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            val uri = intent.data!!
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }
            addRecent(uri, queryName(uri))
            openViewer(uri)
        }
    }

    private fun refreshEmptyState() {
        binding.emptyText.visibility =
            if (recents.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun queryName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx) ?: "document.pdf"
            }
        }
        return uri.lastPathSegment ?: "document.pdf"
    }

    private fun loadRecents() {
        recents.clear()
        val json = getSharedPreferences("app", MODE_PRIVATE).getString("recents", "[]") ?: "[]"
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            recents.add(RecentFile(obj.getString("uri"), obj.getString("name")))
        }
    }

    private fun saveRecents() {
        val arr = JSONArray()
        for (f in recents) {
            arr.put(JSONObject().put("uri", f.uri).put("name", f.name))
        }
        getSharedPreferences("app", MODE_PRIVATE).edit().putString("recents", arr.toString()).apply()
    }

    private fun addRecent(uri: Uri, name: String) {
        recents.removeAll { it.uri == uri.toString() }
        recents.add(0, RecentFile(uri.toString(), name))
        saveRecents()
        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
            refreshEmptyState()
        }
    }

    private fun openViewer(uri: Uri) {
        val intent = Intent(this, PdfViewerActivity::class.java)
        intent.putExtra("uri", uri.toString())
        startActivity(intent)
    }

    private fun confirmRemove(file: RecentFile) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete_file_confirm))
            .setPositiveButton(R.string.delete) { _, _ ->
                val uri = Uri.parse(file.uri)
                val deleted = try {
                    DocumentsContract.deleteDocument(contentResolver, uri)
                } catch (_: Exception) {
                    false
                }
                recents.remove(file)
                saveRecents()
                adapter.notifyDataSetChanged()
                refreshEmptyState()
                Toast.makeText(
                    this,
                    if (deleted) R.string.file_deleted else R.string.removed,
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private class RecentsAdapter(
        private val items: List<RecentFile>,
        private val onOpen: (RecentFile) -> Unit,
        private val onRemove: (RecentFile) -> Unit
    ) : RecyclerView.Adapter<RecentsAdapter.Holder>() {

        class Holder(val binding: ItemRecentBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = ItemRecentBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return Holder(binding)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val file = items[position]
            holder.binding.fileName.text = file.name
            holder.binding.root.setOnClickListener { onOpen(file) }
            holder.binding.removeButton.setOnClickListener { onRemove(file) }
        }

        override fun getItemCount(): Int = items.size
    }
}
