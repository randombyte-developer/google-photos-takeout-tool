package de.randombyte.gptakeouttool

import java.io.File

const val ARGUMENT_COUNT = 1

fun main(args: Array<String>) {
    if (args.size != ARGUMENT_COUNT) {
        throw Exception("Got ${args.size} arguments but expected ${ARGUMENT_COUNT}!")
    }

    val sourceFolder = args[0]

    val mediaFiles = hashSetOf<String>()
    val jsonFiles = hashSetOf<String>()

    File(sourceFolder).walkTopDown()
        .filter { it.isFile }
        .filterNot { it.extension.equals("mp", ignoreCase = true) }
        .forEach { file ->
            val pathWithoutExtension = "${file.parent}/${file.nameWithoutExtension}"
            if (file.extension.equals("json", ignoreCase = true)) {
                val withoutSecondExtension = pathWithoutExtension.substringBeforeLast(".")
                jsonFiles += withoutSecondExtension
            } else {
                if (!pathWithoutExtension.contains("bearbeitet")) {
                    mediaFiles += pathWithoutExtension
                }
            }
        }

    println("Found ${mediaFiles.size} media files and ${jsonFiles.size} JSON files")

    val notFound = mediaFiles.filter { mediaFile -> !jsonFiles.contains(mediaFile) }

    println("${notFound.size} files not found!")
}
