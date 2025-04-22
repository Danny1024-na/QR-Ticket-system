package com.example.mosaik

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.mosaik.data.QrCode
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder

class QRCodesAdapter(private val context: Context, private var qrCodes: List<QrCode>,
                     private val onItemClick: (QrCode) -> Unit) :
    RecyclerView.Adapter<QRCodesAdapter.QRCodeViewHolder>() {

    inner class QRCodeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val qrCodeImageView: ImageView = itemView.findViewById(R.id.qrCodeImageView)
        val qrCodeNumberTextView: TextView = itemView.findViewById(R.id.qrCodeNumberTextView)
        init {
            view.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(qrCodes[position])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QRCodeViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_qr_code, parent, false)
        return QRCodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: QRCodeViewHolder, position: Int) {
        try {
            val qrCodeEntity = qrCodes[position] // Get the QR code entity
            holder.qrCodeNumberTextView.text = "${position + 1}" // Display the index number

            // Generate the QR code bitmap
            val barcodeEncoder = BarcodeEncoder()
            val bitmap = barcodeEncoder.encodeBitmap(qrCodeEntity.id, BarcodeFormat.QR_CODE, 400, 400)
            holder.qrCodeImageView.setImageBitmap(bitmap)
        } catch (e: Exception) {
            android.util.Log.e("QRCodesAdapter", "Error binding ViewHolder: ${e.message}", e)
        }
    }

    override fun getItemCount(): Int = qrCodes.size

    // Method to update the list of QR codes
    fun updateQRCodes(newQRCodes: List<QrCode>) {
        qrCodes = newQRCodes // Update the internal list
        notifyDataSetChanged() // Notify the adapter to refresh the views
    }
}