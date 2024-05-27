@file:OptIn(ExperimentalTime::class)

package de.randombyte.gptakeouttool

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Metadata
import com.drew.metadata.exif.ExifSubIFDDirectory
import kotlinx.coroutines.*
import org.apache.commons.codec.digest.DigestUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime

const val ARGUMENT_COUNT = 2

data class FileEntry(
    val path: String,
    val filenameWithoutExtension: String,
    val extension: String,
    val creationDate: Date?,
    val hash: String,
    val sizeBytes: Long
) {
    val filename: String
        get() = "$filenameWithoutExtension.$extension"
}

fun assertFolderExists(path: String): File {
    val folder = File(path)
    if (!folder.exists() || !folder.isDirectory) {
        println("$path is not a directory!")
        exitProcess(1)
    }
    return folder
}

fun readMediaCreationDate(bytes: ByteArray): Date? {
    try {
        val metadata: Metadata? = ImageMetadataReader.readMetadata(bytes.inputStream())
        return metadata?.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)?.dateOriginal
    } catch (ex: Exception) {
        return null
    }
}

fun main(args: Array<String>) = runBlocking {
    if (args.size != ARGUMENT_COUNT) {
        throw Exception("Got ${args.size} arguments but expected ${ARGUMENT_COUNT}!")
    }

    val sourceFolderString = args[0]
    val destinationFolderString = args[1]
    val sourceFolder = assertFolderExists(sourceFolderString)
    val destinationFolder = assertFolderExists(destinationFolderString)

    val mediaFiles = sourceFolder.walkTopDown()
        .filter { it.isFile }
        .filterNot { it.extension.equals("json", ignoreCase = true) }
        .toList()
    println("Found ${mediaFiles.size} media files")

    val fileEntriesJobs = mediaFiles.map { file ->
        async(Dispatchers.IO) {
            val bytes = file.readBytes()
            FileEntry(
                path = file.path,
                filenameWithoutExtension = file.nameWithoutExtension,
                extension = file.extension,
                creationDate = readMediaCreationDate(bytes),
                hash = DigestUtils.md5Hex(bytes),
                sizeBytes = file.length()
            )
        }
    }

    while (fileEntriesJobs.any { it.isActive }) {
        println("${fileEntriesJobs.count { it.isActive }} Jobs are active")
        delay(5000)
    }

    val fileEntries = fileEntriesJobs.map { it.await() }
    println("Found ${fileEntries.size} files")

    val uniqueFiles = fileEntries
        .groupBy { it.hash }
        .map { it.value.first() } // only pick one instance of a file, regardless in which folder it is
    println("${uniqueFiles.size} unique files")

    val filesWithoutCreationDate = uniqueFiles.filter { it.creationDate == null }
    println("Found ${filesWithoutCreationDate.size} files without a creation date!")

    val filesWithCreationDate = uniqueFiles.filter { it.creationDate != null }

    val groupedByFilenames = filesWithCreationDate.groupBy { it.filename }
    val duplicatedFilenames = groupedByFilenames.filter { it.value.size > 1 }
    println("Found ${duplicatedFilenames.size} duplicated files names")

    val yearFormatter = SimpleDateFormat("yyy")
    val monthFormatter = SimpleDateFormat("mm")

    val jobs = groupedByFilenames.flatMap { group ->
        val isFileNameDuplicated = group.value.size > 1
        group.value.mapIndexed { i, fileEntry ->
            launch(Dispatchers.IO) {
                val originalFile = File(fileEntry.path)

                val year = yearFormatter.format(fileEntry.creationDate)
                val month = monthFormatter.format(fileEntry.creationDate)

                val suffix = if (isFileNameDuplicated) "_${i + 1}" else ""
                val newFilename = "${fileEntry.filenameWithoutExtension}$suffix.${fileEntry.extension}"
                val movedFile = destinationFolder.resolve(year).resolve(month).resolve(newFilename)
                movedFile.parentFile.mkdirs()
                originalFile.renameTo(movedFile)
            }
        }
    }

    while (jobs.any { it.isActive }) {
        println("${jobs.count { it.isActive }} move jobs are active")
        delay(5000)
    }
}
