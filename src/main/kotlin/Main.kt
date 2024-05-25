@file:OptIn(ExperimentalTime::class)

package de.randombyte.gptakeouttool

import kotlinx.coroutines.*
import org.apache.commons.codec.digest.DigestUtils
import java.io.File
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime

const val ARGUMENT_COUNT = 2

data class FileEntry(
    val path: String,
    val filenameWithoutExtension: String,
    val extension: String,
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

suspend fun calculateMD5Hash(file: File): String = withContext(Dispatchers.IO) {
    file.inputStream().buffered().use { DigestUtils.md5Hex(it) }
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
        async {
            FileEntry(
                path = file.path,
                filenameWithoutExtension = file.nameWithoutExtension,
                extension = file.extension,
                hash = calculateMD5Hash(file),
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

    val groupedByFilenames = uniqueFiles.groupBy { it.filename }
    val duplicatedFilenames = groupedByFilenames.filter { it.value.size > 1 }
    println("Found ${duplicatedFilenames.size} duplicated files names")

    val jobs = groupedByFilenames.flatMap { group ->
        val isFileNameDuplicated = group.value.size > 1
        group.value.mapIndexed { i, fileEntry ->
            launch {
                val originalFile = File(fileEntry.path)
                val suffix = if (isFileNameDuplicated) "_${i + 1}" else ""
                val newFilename = "${fileEntry.filenameWithoutExtension}$suffix.${fileEntry.extension}"
                val movedFile = destinationFolder.resolve(newFilename)
                originalFile.renameTo(movedFile)
            }
        }
    }

    while (jobs.any { it.isActive }) {
        println("${jobs.count { it.isActive }} move jobs are active")
        delay(5000)
    }
}
