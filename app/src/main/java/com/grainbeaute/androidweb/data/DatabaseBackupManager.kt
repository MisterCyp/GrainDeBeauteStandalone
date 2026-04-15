package com.grainbeaute.androidweb.data

import android.content.Context
import android.util.Log
import com.grainbeaute.androidweb.data.db.AppDatabase
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class DatabaseBackupManager(private val context: Context) {

    private val dbName = "grainbeaute.db"
    private val imagesDirName = "images"

    fun exportBackup(outputStream: OutputStream): Boolean {
        return try {
            // S'assurer que les données en cache sont écrites sur le disque
            AppDatabase.getInstance(context).run {
                if (isOpen) {
                    this.openHelper.writableDatabase.query("PRAGMA checkpoint(FULL)").use { it.moveToFirst() }
                }
            }

            ZipOutputStream(outputStream).use { zipOut ->
                // 1. Ajouter la base de données
                val dbFile = context.getDatabasePath(dbName)
                if (dbFile.exists()) {
                    addToZip(dbFile, dbName, zipOut)
                }

                // 2. Ajouter le dossier des images
                val imagesDir = File(context.filesDir, imagesDirName)
                if (imagesDir.exists() && imagesDir.isDirectory) {
                    imagesDir.listFiles()?.forEach { file ->
                        addToZip(file, "$imagesDirName/${file.name}", zipOut)
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e("Backup", "Error exporting backup", e)
            false
        } finally {
            outputStream.close()
        }
    }

    private fun addToZip(file: File, fileName: String, zipOut: ZipOutputStream) {
        if (file.isHidden) return
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    addToZip(child, "$fileName/${child.name}", zipOut)
                }
            }
            return
        }
        
        file.inputStream().use { inputStream ->
            val zipEntry = ZipEntry(fileName)
            zipOut.putNextEntry(zipEntry)
            inputStream.copyTo(zipOut)
            zipOut.closeEntry()
        }
    }

    fun importBackup(inputStream: InputStream): Boolean {
        val dbFile = context.getDatabasePath(dbName)
        val shmFile = File(dbFile.path + "-shm")
        val walFile = File(dbFile.path + "-wal")
        val imagesDir = File(context.filesDir, imagesDirName)

        return try {
            // 1. Fermer la base de données proprement
            AppDatabase.getInstance(context).close()

            // 2. Supprimer les fichiers temporaires de SQLite s'ils existent
            if (shmFile.exists()) shmFile.delete()
            if (walFile.exists()) walFile.delete()

            // 3. Extraire le ZIP
            ZipInputStream(inputStream).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val file = if (entry.name == dbName) {
                        dbFile
                    } else if (entry.name.startsWith("$imagesDirName/")) {
                        if (!imagesDir.exists()) imagesDir.mkdirs()
                        File(context.filesDir, entry.name)
                    } else {
                        null
                    }

                    if (file != null) {
                        // Créer les dossiers parents si nécessaire
                        file.parentFile?.mkdirs()
                        
                        file.outputStream().use { output ->
                            zipIn.copyTo(output)
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            Log.e("Backup", "Error importing backup", e)
            false
        } finally {
            inputStream.close()
        }
    }

    // Garder les anciennes méthodes pour la compatibilité si besoin ou les supprimer
    // Pour l'instant on utilise exportBackup et importBackup
}
