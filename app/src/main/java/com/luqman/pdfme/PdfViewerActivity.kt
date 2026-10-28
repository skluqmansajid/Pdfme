package com.luqman.pdfme

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.luqman.pdfme.databinding.ActivityPdfViewerBinding
import com.luqman.pdfme.databinding.DialogSignatureBinding
import java.io.File
import java.io.FileOutputStream

class PdfViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPdfViewerBinding
    private lateinit var sourceUri: Uri
    private lateinit var workingFile: File

    private var renderer: PdfRenderer? = null
    private var currentPage: PdfRenderer.Page? = null
    private var pagePfd: ParcelFileDescriptor? = null
    private var pageCount = 0
    private var currentIndex = 0
    private var pageBitmap: Bitmap? = null

    // Edits kept per page until saved. Points/rects are normalized to the page.
    private val strokesByPage = mutableMapOf<Int, MutableList<Stroke>>()
    private val stampByPage = mutableMapOf<Int, SigStamp?>()
    private var structuralDirty = false

    private val saveAsLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
            if (uri != null) {
                bakeAndCopyTo(uri, R.string.saved_copy)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sourceUri = Uri.parse(intent.getStringExtra("uri") ?: "")
        workingFile = File(cacheDir, "working.pdf")
        try {
            contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(workingFile).use { output -> input.copyTo(output) }
            }
        } catch (_: Exception) {
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding.prevButton.setOnClickListener { goToPage(currentIndex - 1) }
        binding.nextButton.setOnClickListener { goToPage(currentIndex + 1) }
        binding.drawButton.setOnClickListener { binding.drawingView.moveMode = false }
        binding.moveButton.setOnClickListener { binding.drawingView.moveMode = true }
        binding.penBlackButton.setOnClickListener { binding.drawingView.drawColor = Color.BLACK }
        binding.penRedButton.setOnClickListener { binding.drawingView.drawColor = Color.RED }
        binding.penBlueButton.setOnClickListener { binding.drawingView.drawColor = Color.BLUE }
        binding.clearButton.setOnClickListener {
            binding.drawingView.clearPage()
            strokesByPage[currentIndex] = mutableListOf()
            stampByPage[currentIndex] = null
        }
        binding.signatureButton.setOnClickListener { showSignatureDialog() }
        binding.rotateButton.setOnClickListener { rotateCurrentPage() }
        binding.pagesButton.setOnClickListener { showDeletePagesDialog() }
        binding.saveButton.setOnClickListener { bakeAndCopyTo(sourceUri, R.string.saved) }
        binding.saveAsButton.setOnClickListener {
            val name = queryName(sourceUri).removeSuffix(".pdf") + "_edited.pdf"
            saveAsLauncher.launch(name)
        }

        openRenderer()
    }

    private fun queryName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx) ?: "document.pdf"
            }
        }
        return "document.pdf"
    }

    private fun openRenderer() {
        closeRenderer()
        try {
            pagePfd = ParcelFileDescriptor.open(workingFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pagePfd!!)
            pageCount = renderer!!.pageCount
        } catch (_: Exception) {
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (pageCount == 0) {
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        currentIndex = currentIndex.coerceIn(0, pageCount - 1)
        renderPage(currentIndex)
    }

    private fun closeRenderer() {
        currentPage?.close()
        currentPage = null
        renderer?.close()
        renderer = null
        pagePfd?.close()
        pagePfd = null
        pageBitmap?.recycle()
        pageBitmap = null
    }

    private fun saveCurrentPageState() {
        strokesByPage[currentIndex] = binding.drawingView.strokes.toMutableList()
        stampByPage[currentIndex] = binding.drawingView.stamp
    }

    private fun goToPage(index: Int) {
        if (index < 0 || index >= pageCount) return
        saveCurrentPageState()
        currentIndex = index
        renderPage(index)
    }

    private fun renderPage(index: Int) {
        val r = renderer ?: return
        currentPage?.close()
        val page = r.openPage(index)
        currentPage = page
        val screenW = resources.displayMetrics.widthPixels
        val bw = minOf(1600, screenW * 2)
        val bh = (bw * page.height / page.width).coerceAtLeast(1)
        pageBitmap?.recycle()
        val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        pageBitmap = bitmap
        binding.pageImage.setImageBitmap(bitmap)
        val params = FrameLayout.LayoutParams(bw, bh)
        params.gravity = Gravity.CENTER_HORIZONTAL
        binding.pageImage.layoutParams = params
        val overlayParams = FrameLayout.LayoutParams(bw, bh)
        overlayParams.gravity = Gravity.CENTER_HORIZONTAL
        binding.drawingView.layoutParams = overlayParams
        binding.drawingView.setPage(
            strokesByPage[index] ?: mutableListOf(),
            stampByPage[index]
        )
        binding.pageLabel.text = getString(R.string.page_of, index + 1, pageCount)
    }

    private fun showSignatureDialog() {
        val dialogBinding = DialogSignatureBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()
        dialogBinding.clearSigButton.setOnClickListener { dialogBinding.signaturePad.clear() }
        dialogBinding.cancelSigButton.setOnClickListener { dialog.dismiss() }
        dialogBinding.useSigButton.setOnClickListener {
            val sig = dialogBinding.signaturePad.getBitmap()
            if (sig == null) {
                Toast.makeText(this, R.string.draw_signature_hint, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val page = currentPage ?: return@setOnClickListener
            val pageAspect = page.width.toFloat() / page.height.toFloat()
            val rectW = 0.5f
            val rectH = rectW * pageAspect * sig.height.toFloat() / sig.width.toFloat()
            val rect = RectF(0.25f, 0.7f, 0.25f + rectW, (0.7f + rectH).coerceAtMost(0.98f))
            binding.drawingView.stamp = SigStamp(sig, rect)
            binding.drawingView.moveMode = true
            binding.drawingView.invalidate()
            saveCurrentPageState()
            dialog.dismiss()
            Toast.makeText(this, R.string.sig_placed, Toast.LENGTH_LONG).show()
        }
        dialog.show()
    }

    private fun rotateCurrentPage() {
        saveCurrentPageState()
        val tmp = File(cacheDir, "tmp.pdf")
        try {
            PdfEditor.rotatePage(workingFile, tmp, currentIndex)
            tmp.copyTo(workingFile, overwrite = true)
            tmp.delete()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
            return
        }
        structuralDirty = true
        clearAllEdits()
        openRenderer()
    }

    private fun showDeletePagesDialog() {
        if (pageCount <= 1) {
            Toast.makeText(this, R.string.cannot_delete_all, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = Array(pageCount) { i -> "Page ${i + 1}" }
        val checked = BooleanArray(pageCount)
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_pages_title)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.delete) { _, _ ->
                val selected = checked.indices.filter { checked[it] }.toSet()
                if (selected.isEmpty()) {
                    Toast.makeText(this, R.string.no_pages_selected, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (selected.size >= pageCount) {
                    Toast.makeText(this, R.string.cannot_delete_all, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                saveCurrentPageState()
                val tmp = File(cacheDir, "tmp.pdf")
                try {
                    PdfEditor.deletePages(workingFile, tmp, selected)
                    tmp.copyTo(workingFile, overwrite = true)
                    tmp.delete()
                } catch (_: Exception) {
                    Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                structuralDirty = true
                clearAllEdits()
                currentIndex = 0
                openRenderer()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun clearAllEdits() {
        strokesByPage.clear()
        stampByPage.clear()
        binding.drawingView.clearPage()
        Toast.makeText(this, R.string.drawings_cleared_note, Toast.LENGTH_SHORT).show()
    }

    private fun bakeAndCopyTo(targetUri: Uri, successMessage: Int) {
        saveCurrentPageState()
        val overlays = mutableMapOf<Int, OverlayData>()
        for ((index, strokes) in strokesByPage) {
            val stamp = stampByPage[index]
            if (strokes.isNotEmpty() || stamp != null) {
                overlays[index] = OverlayData(strokes.toList(), stamp)
            }
        }
        val tmp = File(cacheDir, "baked.pdf")
        try {
            val bw = pageBitmap?.width ?: 1440
            PdfEditor.bakeOverlays(workingFile, tmp, overlays, bw)
            tmp.copyTo(workingFile, overwrite = true)
            tmp.delete()
            val pfd = contentResolver.openFileDescriptor(targetUri, "w")
            if (pfd != null) {
                FileOutputStream(pfd.fileDescriptor).use { it.write(workingFile.readBytes()) }
                pfd.close()
            }
        } catch (_: Exception) {
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show()
            return
        }
        // Baked into the file now; clear in-memory edits so a second save cannot duplicate them.
        strokesByPage.clear()
        stampByPage.clear()
        binding.drawingView.clearPage()
        structuralDirty = false
        Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        closeRenderer()
        super.onDestroy()
    }
}
