package de.randombyte.gptakeouttool

import kotlinx.coroutines.*
import org.apache.commons.codec.digest.DigestUtils
import java.io.File

const val ARGUMENT_COUNT = 1

fun main(args: Array<String>) {
    if (args.size != ARGUMENT_COUNT) {
        throw Exception("Got ${args.size} arguments but expected ${ARGUMENT_COUNT}!")
    }

    val sourceFolder = args[0]

    val internalFileRegex = Regex("/Photos from \\d{4}/")

    data class FileEntry(val path: String, val filename: String, val hash: String, val sizeBytes: Long)

    val mediaFiles = File(sourceFolder).walkTopDown()
        .filter { it.isFile }
        .filterNot { it.extension.equals("json", ignoreCase = true) }
        .toList()

    println("Found ${mediaFiles.size} media files")

    val mediaFilesExternal = mutableListOf<FileEntry>()
    val mediaFilesInternal = mutableListOf<FileEntry>()

    runBlocking {
        val jobs = mediaFiles.map { file ->
            launch {
                val hash = calculateMD5Hash(file)
                val entry = FileEntry(path = file.path, filename = file.name, hash = hash, sizeBytes = file.length())
                if (file.path.contains(internalFileRegex)) {
                    mediaFilesInternal += entry
                } else {
                    mediaFilesExternal += entry
                }
            }
        }

        while (jobs.any { it.isActive }) {
            println("${jobs.count { it.isActive }} Jobs are active")
            delay(5000)
        }
    }

    println("Found ${mediaFilesExternal.size} external files and ${mediaFilesInternal.size} internal files")

    val onlyAsExternalFileAvailable = mediaFilesExternal.filterNot { external ->
        mediaFilesInternal.any { internal ->
            external.filename == internal.filename && external.hash == internal.hash
        }
    }

    println("${onlyAsExternalFileAvailable.size} files are only external!")

    File("onlyAsExternalFileAvailable.txt").bufferedWriter().use { writer ->
        onlyAsExternalFileAvailable.forEach { entry ->
            writer.write(entry.path)
            writer.newLine()
        }
    }

    val externalFileAvailableAsInternalFile = mediaFilesExternal.filter { external ->
        mediaFilesInternal.any { internal ->
            external.filename == internal.filename && external.hash == internal.hash
        }
    }

    println("${externalFileAvailableAsInternalFile.size} files are available externally and internally. Deleting them...")

    val deletedFiles = mutableListOf<FileEntry>()
    runBlocking {
        val jobs = externalFileAvailableAsInternalFile.map { file ->
            launch {
                val mediaFile = File(file.path)
                println("Deleting ${mediaFile.absolutePath}")
                if (!mediaFile.delete()) {
                    throw Exception("Can't delete file ${mediaFile.absolutePath}!")
                }
                deletedFiles += file

                val jsonFile = mediaFile.parentFile.resolve("${mediaFile.name}.json")
                if (jsonFile.exists()) {
                    println("Deleting ${jsonFile.absolutePath}")
                    if (!jsonFile.delete()) {
                        throw Exception("Can't delete file ${jsonFile.absolutePath}!")
                    }
                } else {
                    println("JSON file ${jsonFile.absolutePath} doesn't exist!")
                }
            }
        }

        while (jobs.any { it.isActive }) {
            println("${jobs.count { it.isActive }} Löschjobs are active") //187,6
            delay(5000)
        }
    }

    val deletedFilesGigabytes = deletedFiles.sumOf { it.sizeBytes } / 1000 / 1000
    println("Deleted ${deletedFiles.size} media files which are $deletedFilesGigabytes MB")
}

suspend fun calculateMD5Hash(file: File): String = withContext(Dispatchers.IO) {
    file.inputStream().use { DigestUtils.md5Hex(it) }
}
