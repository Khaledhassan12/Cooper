package pro.sketchware.utility

import android.os.Environment
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Migrates Cooper's legacy shared-storage folders to the public Cooper directory.
 *
 * Conflicting data is never overwritten: it is retained under Cooper/LegacyMigrationBackup.
 * Source data is deleted only after a successful rename or a verified copy.
 */
object CooperStorageMigration {
    private const val TAG = "CooperStorageMigration"
    private const val COOPER_DIRECTORY_NAME = "Cooper"
    private const val CONFLICT_BACKUP_DIRECTORY = "LegacyMigrationBackup"

    @JvmStatic
    fun getRootDirectory(): File =
        File(Environment.getExternalStorageDirectory(), COOPER_DIRECTORY_NAME)

    @JvmStatic
    fun migrateLegacyStorage() {
        try {
            val storage = Environment.getExternalStorageDirectory()
            val cooperRoot = File(storage, COOPER_DIRECTORY_NAME)

            // Main workspace: project data, libraries, resources, Git metadata, backups,
            // build temporaries, and recycle-bin content.
            migrateRoot(
                File(storage, ".sketchware"),
                cooperRoot,
                File(cooperRoot, "$CONFLICT_BACKUP_DIRECTORY/from_hidden_workspace")
            )

            // The legacy public root contains signed APK/AAB files, exported source, keystores,
            // localization files, and other user-managed content. Merge everything into Cooper.
            migrateRoot(
                File(storage, "sketchware"),
                cooperRoot,
                File(cooperRoot, "$CONFLICT_BACKUP_DIRECTORY/from_public_workspace")
            )

            // Ensure fresh installs can create settings and project data under Cooper even if
            // there is no legacy directory to rename.
            if (!cooperRoot.exists() && !cooperRoot.mkdirs()) {
                Log.w(TAG, "Could not create Cooper storage root: $cooperRoot")
            }
            val dataDirectory = File(cooperRoot, "data")
            if (!dataDirectory.exists() && !dataDirectory.mkdirs()) {
                Log.w(TAG, "Could not create Cooper data directory: $dataDirectory")
            }
        } catch (error: Exception) {
            // Keep launch working if storage access is temporarily unavailable. The migration
            // is retried on the next launch; source files are retained on incomplete copies.
            Log.e(TAG, "Legacy storage migration could not finish; it will retry on next launch.", error)
        }
    }

    private fun migrateRoot(source: File, destination: File, conflictRoot: File) {
        if (!source.exists()) return

        // The common case is a same-volume rename, which is fast even for large project folders.
        if (!destination.exists()) {
            val parent = destination.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                Log.e(TAG, "Could not create destination parent: $parent")
                return
            }
            if (source.renameTo(destination)) {
                Log.i(TAG, "Migrated legacy folder to $destination")
                return
            }
        }

        if (!mergeNode(source, destination, conflictRoot, source.name)) {
            Log.w(TAG, "Migration is incomplete; legacy data was retained where possible: $source")
        }
    }

    private fun mergeNode(
        source: File,
        destination: File,
        conflictRoot: File,
        relativePath: String
    ): Boolean {
        if (!source.exists()) return true

        if (!destination.exists()) {
            val moved = moveNode(source, destination)
            if (!moved) Log.w(TAG, "Could not migrate $source to $destination")
            return moved
        }

        if (source.isDirectory && destination.isDirectory) {
            val children = source.listFiles()
            if (children == null) {
                Log.w(TAG, "Could not list legacy directory: $source")
                return false
            }
            var allChildrenMigrated = true
            for (child in children) {
                val childRelativePath = relativePath + "/" + child.name
                if (!mergeNode(child, File(destination, child.name), conflictRoot, childRelativePath)) {
                    allChildrenMigrated = false
                }
            }

            if (allChildrenMigrated && source.listFiles()?.isEmpty() == true) {
                source.delete()
            }
            return allChildrenMigrated && !source.exists()
        }

        if (source.isFile && destination.isFile && filesAreIdentical(source, destination)) {
            // Only remove a duplicate after comparing its full contents.
            return source.delete() || !source.exists()
        }

        // Do not overwrite a destination that exists with different data or a different type.
        val backupDestination = nextAvailableBackup(conflictRoot, relativePath)
        val backedUp = moveNode(source, backupDestination)
        if (backedUp) {
            Log.w(TAG, "Preserved a conflicting legacy item at $backupDestination")
        } else {
            Log.e(TAG, "Could not preserve conflicting legacy item: $source")
        }
        return backedUp
    }

    private fun moveNode(source: File, destination: File): Boolean {
        if (destination.exists()) return false
        val parent = destination.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) return false

        if (source.renameTo(destination)) return true

        // renameTo can fail across filesystems. Verify a copy before deleting the source.
        if (!copyNodeVerified(source, destination)) return false
        return deleteRecursively(source)
    }

    private fun copyNodeVerified(source: File, destination: File): Boolean {
        if (destination.exists()) return false
        if (source.isDirectory) {
            if (!destination.mkdirs() && !destination.isDirectory) return false
            val children = source.listFiles() ?: return false
            var allCopied = true
            for (child in children) {
                if (!copyNodeVerified(child, File(destination, child.name))) allCopied = false
            }
            return allCopied
        }

        if (!source.isFile) return false
        val parent = destination.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) return false

        return try {
            BufferedInputStream(FileInputStream(source)).use { input ->
                BufferedOutputStream(FileOutputStream(destination)).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count > 0) output.write(buffer, 0, count)
                    }
                    output.flush()
                }
            }
            val valid = source.length() == destination.length() &&
                filesAreIdentical(source, destination)
            if (!valid) destination.delete()
            valid
        } catch (error: IOException) {
            Log.e(TAG, "Could not copy legacy file $source", error)
            destination.delete()
            false
        }
    }

    private fun filesAreIdentical(first: File, second: File): Boolean {
        if (!first.isFile || !second.isFile || first.length() != second.length()) return false
        return try {
            BufferedInputStream(FileInputStream(first)).use { inputA ->
                BufferedInputStream(FileInputStream(second)).use { inputB ->
                    val bufferA = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                    val bufferB = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                    while (true) {
                        val readA = inputA.read(bufferA)
                        val readB = inputB.read(bufferB)
                        if (readA != readB) return false
                        if (readA < 0) return true
                        for (index in 0 until readA) {
                            if (bufferA[index] != bufferB[index]) return false
                        }
                    }
                }
            }
        } catch (error: IOException) {
            Log.w(TAG, "Could not compare $first and $second", error)
            false
        }
    }

    private fun nextAvailableBackup(conflictRoot: File, relativePath: String): File {
        val cleanedPath = relativePath
            .replace('\\', '/')
            .trimStart('/')
            .ifBlank { "legacy_item" }
        var candidate = File(conflictRoot, cleanedPath)
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(candidate.parentFile, candidate.name + ".legacy-" + suffix)
            suffix++
        }
        return candidate
    }

    private fun deleteRecursively(file: File): Boolean {
        if (file.isDirectory) {
            val children = file.listFiles() ?: return false
            for (child in children) {
                if (!deleteRecursively(child)) return false
            }
        }
        return file.delete() || !file.exists()
    }
}
