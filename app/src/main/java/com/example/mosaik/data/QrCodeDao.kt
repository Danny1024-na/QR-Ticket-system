package com.example.mosaik.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface QrCodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(qrCode: QrCode)

    @Query("SELECT * FROM qr_codes")
    suspend fun getAllQRCodes(): List<QrCode>

    @Query("DELETE FROM qr_codes")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM qr_codes WHERE id = :id")
    suspend fun exists(id: String): Int // Check if a QR code exists

    @Query("DELETE FROM qr_codes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM qr_codes WHERE id NOT LIKE 'V%IP'")
    suspend fun getNormalQRCodes(): List<QrCode>

    @Query("SELECT * FROM qr_codes WHERE id LIKE 'V%IP'")
    suspend fun getVIPQRCodes(): List<QrCode>

    @Query("DELETE FROM qr_codes WHERE id LIKE 'V%IP'")
    suspend fun clearVIPQRCodes()

    @Query("DELETE FROM qr_codes WHERE id NOT LIKE 'V%IP'")
    suspend fun clearNormalQRCodes()
}