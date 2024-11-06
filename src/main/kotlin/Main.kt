package cz.marvincz.transcript.pdfparser

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.installMordantMarkdown
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.path
import cz.marvincz.transcript.pdfparser.parser.TranscriptParser
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Path


fun main(args: Array<String>) = Application().main(args)

private class Application : CliktCommand() {
    init {
        installMordantMarkdown()
    }

    val pdf: Path by option().path(mustExist = true, canBeDir = false, mustBeReadable = true).required()
        .help { "The transcript PDF file" }
    val output: File by option().file(canBeDir = false).required()
        .help { "The output JSON file" }
    val alias: File? by option().file(mustExist = true, canBeDir = false, mustBeReadable = true)
        .help {
            """
                *(optional)* The speaker alias file.
                Format:
                ```
                SPEAKER NAME=ALIAS NAME
                SPEAKER NAME 2=ALIAS NAME 2
                .
                .
                .
                ```
            """.trimIndent()
        }

    override fun run() {
        val speakerAlias = alias?.let { readSpeakerAlias(it) } ?: emptyMap()

        val transcript = TranscriptParser().parseTranscript(pdf, speakerAlias)

        val json = Json {
            explicitNulls = false
        }

        val jsonOutput = json.encodeToString(transcript)
        output.writeText(jsonOutput)
    }
}

private fun readSpeakerAlias(aliasFile: File): Map<String, String> = aliasFile.readLines().associate { line ->
    require(speakerAliasRegex.matches(line)) { "Alias file must have lines in the format \"SPEAKER NAME=ALIAS NAME\"" }

    // map is reversed from the file because we will want to find the speaker name from the alias
    speakerAliasRegex.matchEntire(line)!!.groups.let { it["alias"]!!.value.trim() to it["speaker"]!!.value.trim() }
}

private val speakerAliasRegex = Regex("(?<speaker>[^=]+)=(?<alias>[^=]+)")