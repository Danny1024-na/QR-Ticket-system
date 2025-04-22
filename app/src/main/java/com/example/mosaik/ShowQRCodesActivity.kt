package com.example.mosaik

import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mosaik.data.AppDatabase
import generateQRCodeBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

class ShowQRCodesActivity : AppCompatActivity() {

    private lateinit var qrCodeRecyclerView: RecyclerView
    private lateinit var qrCodesAdapter: QRCodesAdapter
    private lateinit var emptyTextView: TextView
    private lateinit var clearButton: Button
    private lateinit var saveVipButton: Button
    private lateinit var saveNormalButton: Button
    private lateinit var db: AppDatabase
    private var displayMode: String = "ALL"
    private var qrCodeTypeToSave: String = "" // Store which type of QR code to save after permission granted

    // Add this constant for permission request
    private val PERMISSION_REQUEST_STORAGE = 101

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_show_qrcodes)

        db = AppDatabase.getDatabase(this)
        qrCodeRecyclerView = findViewById(R.id.qrCodeRecyclerView)
        emptyTextView = findViewById(R.id.emptyTextView)
        clearButton = findViewById(R.id.button)
        saveVipButton = findViewById(R.id.saveVipButton)
        saveNormalButton = findViewById(R.id.saveNormalButton)

        displayMode = intent.getStringExtra("DISPLAY_MODE") ?: "ALL"

        qrCodeRecyclerView.layoutManager = LinearLayoutManager(this)
        qrCodesAdapter = QRCodesAdapter(this, emptyList()) { qrCode ->
            Toast.makeText(this, "Clicked QR Code: ${qrCode.id}", Toast.LENGTH_SHORT).show()
            // Optional: Intent logic to send back to MainActivity
        }
        qrCodeRecyclerView.adapter = qrCodesAdapter

        loadQRCodes()

        clearButton.setOnClickListener {
            showClearConfirmationDialog()
        }

        saveVipButton.setOnClickListener {
            saveQRCodes("VIP")
        }

        saveNormalButton.setOnClickListener {
            saveQRCodes("NORMAL")
        }
    }

    private fun loadQRCodes() {
        lifecycleScope.launch {
            val qrCodes = withContext(Dispatchers.IO) {
                when (displayMode) {
                    "VIP" -> db.qrCodeDao().getVIPQRCodes()
                    "NORMAL" -> db.qrCodeDao().getNormalQRCodes()
                    else -> db.qrCodeDao().getAllQRCodes()
                }
            }

            if (qrCodes.isNotEmpty()) {
                qrCodesAdapter.updateQRCodes(qrCodes)
                emptyTextView.visibility = View.GONE
                qrCodeRecyclerView.visibility = View.VISIBLE
            } else {
                emptyTextView.visibility = View.VISIBLE
                qrCodeRecyclerView.visibility = View.GONE
            }
        }
    }

    // Handle permission request result
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_STORAGE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permission was granted, proceed with saving
                    Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show()
                    // Try to save again if we were in the middle of saving
                    if (qrCodeTypeToSave.isNotEmpty()) {
                        saveQRCodesActual(qrCodeTypeToSave)
                        qrCodeTypeToSave = "" // Reset after using
                    }
                } else {
                    // Permission was denied
                    Toast.makeText(this, "Storage permission required to save QR codes", Toast.LENGTH_LONG).show()
                }
                return
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveQRCodes(type: String) {
        /*if (!checkStoragePermission()) {
            return
        }*/

        saveQRCodesActual(type)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveQRCodesActual(type: String) {
        lifecycleScope.launch {
            val qrCodes = withContext(Dispatchers.IO) {
                when (type.uppercase(Locale.getDefault())) {
                    "VIP" -> db.qrCodeDao().getVIPQRCodes()
                    "NORMAL" -> db.qrCodeDao().getNormalQRCodes()
                    else -> db.qrCodeDao().getAllQRCodes()
                }
            }

            if (qrCodes.isEmpty()) {
                Toast.makeText(
                    this@ShowQRCodesActivity,
                    "No ${type.lowercase()} QR codes to save",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            val resolver = contentResolver
            val folderName = "Download/${type.uppercase()}" // Correct relative path

            qrCodes.forEach { qrCode ->
                try {
                    val fileName = "qr_${qrCode.id}.jpg"
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/${type.uppercase()}")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }

                    val imageUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                    if (imageUri != null) {
                        val bitmap = generateQRCodeBitmap(qrCode.id.toString())

                        resolver.openOutputStream(imageUri)?.use { outStream ->
                            val success = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
                            if (!success) {
                                throw IOException("Failed to compress bitmap")
                            }
                        } ?: throw IOException("Output stream is null")

                        // Mark writing as done
                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(imageUri, contentValues, null, null)
                    } else {
                        throw IOException("Failed to create URI for $fileName")
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(
                        this@ShowQRCodesActivity,
                        " ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            Toast.makeText(
                this@ShowQRCodesActivity,
                "${type.uppercase()} QR codes saved to Downloads/${type.uppercase()}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showClearConfirmationDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Clear Memory")

        val message = when (displayMode) {
            "VIP" -> "Are you sure you want to clear all VIP QR codes?"
            "NORMAL" -> "Are you sure you want to clear all normal QR codes?"
            else -> "Are you sure you want to clear all QR codes?"
        }

        builder.setMessage(message)
        builder.setPositiveButton("Yes") { dialog, _ ->
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    when (displayMode) {
                        "VIP" -> db.qrCodeDao().clearVIPQRCodes()
                        "NORMAL" -> db.qrCodeDao().clearNormalQRCodes()
                        else -> db.qrCodeDao().clearAll()
                    }
                }
                loadQRCodes() // Reload the QR codes after clearing
                Toast.makeText(this@ShowQRCodesActivity, "QR codes cleared", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        builder.setNegativeButton("No") { dialog, _ ->
            dialog.dismiss()
        }
        val dialog = builder.create()
        dialog.show()
    }
}
