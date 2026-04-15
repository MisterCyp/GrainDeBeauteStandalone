package com.grainbeaute.androidweb.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Liste des migrations de la base de données GrainDeBeauté.
 */
object Migrations {

    /**
     * Migration de la version 1 à 2 :
     * - Ajout de la table `dermatologist_visits`
     * - Ajout de la table `mole_diagnoses` avec index et clés étrangères
     * - Ajout de la table `app_settings`
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Création de la table dermatologist_visits
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `dermatologist_visits` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                    `date` INTEGER NOT NULL, 
                    `globalNote` TEXT, 
                    `createdAt` INTEGER NOT NULL
                )
            """.trimIndent())

            // Création de la table mole_diagnoses
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `mole_diagnoses` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                    `visitId` INTEGER NOT NULL, 
                    `moleId` INTEGER, 
                    `moleName` TEXT NOT NULL, 
                    `category` TEXT NOT NULL, 
                    `note` TEXT, 
                    FOREIGN KEY(`visitId`) REFERENCES `dermatologist_visits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, 
                    FOREIGN KEY(`moleId`) REFERENCES `moles`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
            """.trimIndent())
            
            // Création des index pour mole_diagnoses
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_mole_diagnoses_visitId` ON `mole_diagnoses` (`visitId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_mole_diagnoses_moleId` ON `mole_diagnoses` (`moleId`)")

            // Création de la table app_settings
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `app_settings` (
                    `id` INTEGER PRIMARY KEY NOT NULL, 
                    `nextAppointmentDate` INTEGER, 
                    `practitionerName` TEXT, 
                    `practitionerPhone` TEXT, 
                    `practitionerEmail` TEXT, 
                    `reminderDaysBefore` INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    /**
     * Migration de la version 2 à 3 :
     * - Suppression de `practitionerEmail`
     * - Ajout de `practitionerAddress`
     * (Reconstruction de la table car SQLite limite le DROP COLUMN)
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Création de la nouvelle table avec le schéma correct
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `app_settings_new` (
                    `id` INTEGER PRIMARY KEY NOT NULL, 
                    `nextAppointmentDate` INTEGER, 
                    `practitionerName` TEXT, 
                    `practitionerPhone` TEXT, 
                    `practitionerAddress` TEXT, 
                    `reminderDaysBefore` INTEGER NOT NULL
                )
            """.trimIndent())

            // 2. Copie des données existantes (on ignore l'email, l'adresse sera nulle au départ)
            db.execSQL("""
                INSERT INTO `app_settings_new` (id, nextAppointmentDate, practitionerName, practitionerPhone, reminderDaysBefore)
                SELECT id, nextAppointmentDate, practitionerName, practitionerPhone, reminderDaysBefore FROM `app_settings`
            """.trimIndent())

            // 3. Suppression de l'ancienne table et renommage
            db.execSQL("DROP TABLE `app_settings`")
            db.execSQL("ALTER TABLE `app_settings_new` RENAME TO `app_settings`")
        }
    }

    /**
     * Migration de la version 3 à 4 :
     * - Ajout de `practitionerName` à la table `dermatologist_visits`
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `dermatologist_visits` ADD COLUMN `practitionerName` TEXT")
        }
    }

    /**
     * Migration de la version 4 à 5 :
     * - Ajout de `practitionerAddress` à `dermatologist_visits`
     * - Reconstruction de `app_settings` pour supprimer `practitionerPhone`
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Ajout de la colonne adresse aux visites
            db.execSQL("ALTER TABLE `dermatologist_visits` ADD COLUMN `practitionerAddress` TEXT")

            // 2. Reconstruction de app_settings
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `app_settings_new` (
                    `id` INTEGER PRIMARY KEY NOT NULL, 
                    `nextAppointmentDate` INTEGER, 
                    `practitionerName` TEXT, 
                    `practitionerAddress` TEXT, 
                    `reminderDaysBefore` INTEGER NOT NULL
                )
            """.trimIndent())

            db.execSQL("""
                INSERT INTO `app_settings_new` (id, nextAppointmentDate, practitionerName, practitionerAddress, reminderDaysBefore)
                SELECT id, nextAppointmentDate, practitionerName, practitionerAddress, reminderDaysBefore FROM `app_settings`
            """.trimIndent())

            db.execSQL("DROP TABLE `app_settings`")
            db.execSQL("ALTER TABLE `app_settings_new` RENAME TO `app_settings`")
        }
    }

    /**
     * Migration de la version 5 à 6 :
     * - Ajout de bodyPositionX, bodyPositionY, bodyFace à la table `moles`
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `moles` ADD COLUMN `bodyPositionX` REAL")
            db.execSQL("ALTER TABLE `moles` ADD COLUMN `bodyPositionY` REAL")
            db.execSQL("ALTER TABLE `moles` ADD COLUMN `bodyFace` TEXT")
        }
    }

    /**
     * Liste ordonnée de toutes les migrations à appliquer au builder.
     */
    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
}
