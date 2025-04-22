package com.example.mosaik

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mosaik.data.AppDatabase
import com.example.mosaik.data.QrCode
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.google.zxing.BarcodeFormat
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Random

class MainActivity : AppCompatActivity() {

    private lateinit var generateButton: Button
    private lateinit var qrCodeImageView: ImageView
    private lateinit var readButton: Button
    private lateinit var db: AppDatabase
    private lateinit var qrCodesAdapter: QRCodesAdapter
    private lateinit var qrCodeRecyclerView: RecyclerView

    private lateinit var showNormalButton: Button
    private lateinit var showVIPButton: Button
    private lateinit var emptyTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = AppDatabase.getDatabase(this)

        generateButton = findViewById(R.id.generateButton)
        qrCodeImageView = findViewById(R.id.qrCodeImageView)
        readButton = findViewById(R.id.readButton)
        //showDatabaseButton = findViewById(R.id.showDatabaseButton)
        showNormalButton = findViewById(R.id.showDatabaseButton)
        showVIPButton = findViewById(R.id.showVipQrCodes)
        emptyTextView = findViewById(R.id.emptyTextView)

        qrCodeRecyclerView = findViewById(R.id.qrCodeRecyclerView)
        qrCodeRecyclerView.layoutManager = LinearLayoutManager(this)
        qrCodesAdapter = QRCodesAdapter(this, emptyList()) { qrCode ->
            Toast.makeText(this, "Clicked QR Code: ${qrCode.id}", Toast.LENGTH_SHORT).show()
            // Optional: add logic if clicked QR code should be handled
        }
        qrCodeRecyclerView.adapter = qrCodesAdapter

        // Click listener for generating QR code
        generateButton.setOnClickListener {
            showQRCodeTypeDialog()
        }

        // Click listener for reading QR code
        readButton.setOnClickListener {
            val integrator = IntentIntegrator(this)
            integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            integrator.setPrompt("Scan a QR Code")
            integrator.setCameraId(0)  // Use default camera
            integrator.setBeepEnabled(false)
            integrator.setBarcodeImageEnabled(true)
            integrator.initiateScan()
        }

        showNormalButton.setOnClickListener {
            loadNormalQRCodes()
        }

        showVIPButton.setOnClickListener {
            loadVIPQRCodes()
        }

    }

    private fun loadNormalQRCodes() {
        lifecycleScope.launch {
            val intent = Intent(this@MainActivity, ShowQRCodesActivity::class.java)
            intent.putExtra("DISPLAY_MODE", "NORMAL")
            startActivity(intent)
        }
    }

    private fun loadVIPQRCodes() {
        lifecycleScope.launch {
            val intent = Intent(this@MainActivity, ShowQRCodesActivity::class.java)
            intent.putExtra("DISPLAY_MODE", "VIP")
            startActivity(intent)
        }
    }

    private fun showQRCodeTypeDialog() {
        val options = arrayOf("Normal", "VIP")
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select QR Code Type")
        builder.setItems(options) { _, which ->
            val type = options[which]
            showNumberInputDialog(type)
        }
        builder.show()
    }

    private fun showNumberInputDialog(type: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Enter Number of QR Codes")

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        builder.setView(input)

        builder.setPositiveButton("OK") { dialog, _ ->
            val number = input.text.toString().toIntOrNull() ?: 0
            lifecycleScope.launch {
                repeat(number) {
                    val randomString = generateRandomString(20, type)
                    generateQRCode(randomString)
                }
            }
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    private fun generateQRCode(text: String) {
        val barcodeEncoder = BarcodeEncoder()
        val bitmap: Bitmap = barcodeEncoder.encodeBitmap(text, BarcodeFormat.QR_CODE, 400, 400)
        qrCodeImageView.setImageBitmap(bitmap)

        // Store the generated QR code value in the database
        lifecycleScope.launch {
            val qrCode = QrCode(id = text) // Create a new QrCode object
            withContext(Dispatchers.IO) {
                db.qrCodeDao().insert(qrCode) // Save to the database
            }
        }
    }

    private suspend fun generateRandomString(length: Int, type: String): String {
        val characters = "ABCDEFGHJKLMNOPQRSTUVWXYZabcdefghjklmnopqrstuvwxyz0123456789"
        val random = Random()
        var result: String

        do {
            val stringBuilder = StringBuilder()
            if (type == "VIP") stringBuilder.append("V")
            for (i in 0 until length - 2) {
                stringBuilder.append(characters[random.nextInt(characters.length)])
            }
            if (type == "VIP") stringBuilder.append("IP")
            result = stringBuilder.toString()
        } while (isQRCodeExists(result))

        return result
    }

    private suspend fun isQRCodeExists(qrCode: String): Boolean {
        val count = db.qrCodeDao().exists(qrCode) // Query the database to check if it exists
        return count > 0 // Return true if the QR code exists
    }

    // Handle the result from the QR code scanner
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents != null) {
                // Get the scanned text
                val scannedText = result.contents
                lifecycleScope.launch {
                    val qrCodes = withContext(Dispatchers.IO) {
                        db.qrCodeDao().getAllQRCodes()
                    }
                    if (qrCodes.any { it.id == scannedText }) {
                        val isVIP = scannedText.startsWith("V") && scannedText.endsWith("IP")
                        val qrType = if (isVIP) "VIP" else "Normal"

                        Toast.makeText(this@MainActivity, "Welcome Baby! ($qrType QR Code)", Toast.LENGTH_SHORT).show()
                        withContext(Dispatchers.IO) {
                            db.qrCodeDao().deleteById(scannedText) // Delete the QR code by ID
                        }

                        if (isVIP) {
                            val trueImageView = findViewById<ImageView>(R.id.vip)

                            findViewById<ImageView>(R.id.vip).apply {
                                visibility = View.VISIBLE
                            }
                            Handler().postDelayed({
                                trueImageView.visibility = View.GONE
                            }, 3000) // 3000 milliseconds = 3 seconds
                        } else {
                            val trueImageView =findViewById<ImageView>(R.id.true_img)

                            findViewById<ImageView>(R.id.true_img).apply {
                                visibility = View.VISIBLE
                            }
                            Handler().postDelayed({
                                trueImageView.visibility = View.GONE
                            }, 3000) // 3000 milliseconds = 3 seconds
                        }


                    } else {
                        Toast.makeText(this@MainActivity, "Boss Danny says you are not on the list", Toast.LENGTH_SHORT).show()
                        val falseImageView = findViewById<ImageView>(R.id.false_img)

                        findViewById<ImageView>(R.id.false_img).apply {
                            visibility = View.VISIBLE
                        }
                        Handler().postDelayed({
                            falseImageView.visibility = View.GONE
                        }, 3000) // 3000 milliseconds = 3 seconds
                    }
                }
            } else {
                Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }
}