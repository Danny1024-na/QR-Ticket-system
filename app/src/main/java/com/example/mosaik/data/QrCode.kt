package com.example.mosaik.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "qr_codes")
data class QrCode(
    @PrimaryKey val id: String // Unique identifier for the QR code
)