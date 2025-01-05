package cz.marvincz.transcript.pdfparser

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.installMordantMarkdown
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.path
import cz.marvincz.transcript.pdfparser.model.Transcript
import cz.marvincz.transcript.pdfparser.parser.TranscriptParser
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Path


fun main(args: Array<String>) = Application().subcommands(Parse(), Combine()).main(args)

private class Application : CliktCommand() {
    init {
        installMordantMarkdown()
    }

    override fun helpEpilog(context: Context) =
        terminal.theme.warning("Command help:") + " application <command> --help"

    override fun run() = Unit
}

private class Parse : CliktCommand() {
    override fun help(context: Context) = "Parse a transcript PDF"

    private val pdf: Path by option().path(mustExist = true, canBeDir = false, mustBeReadable = true).required()
        .help { "The transcript PDF file" }
    private val output: File by option().file(canBeDir = false).required()
        .help { "The output JSON file" }
    private val alias: File? by option().file(mustExist = true, canBeDir = false, mustBeReadable = true)
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

    private fun readSpeakerAlias(aliasFile: File): Map<String, String> = aliasFile.readLines().associate { line ->
        require(speakerAliasRegex.matches(line)) { "Alias file must have lines in the format \"SPEAKER NAME=ALIAS NAME\"" }

        // map is reversed from the file because we will want to find the speaker name from the alias
        speakerAliasRegex.matchEntire(line)!!.groups.let { it["alias"]!!.value.trim() to it["speaker"]!!.value.trim() }
    }

    private val speakerAliasRegex = Regex("(?<speaker>[^=]+)=(?<alias>[^=]+)")
}

private class Combine : CliktCommand() {
    override fun help(context: Context) = "Combine multiple transcript JSONs into one"

    private val inputs: List<Transcript> by option("-i", "--input").file(mustExist = true, canBeDir = false, mustBeReadable = true)
        .convert { Json.decodeFromString<Transcript>(it.readText()) }.multiple(required = true)
        .help { "The JSON files to be combined. Repeat for each file." }
    private val output: File by option("-o", "--output").file(canBeDir = false).required()
        .help { "The combined JSON file" }

    override fun helpEpilog(context: Context) = terminal.theme.warning("Example:") + " application combine -i t1.json -i t2.json -o combined.json"

    override fun run() {
        val transcript = inputs.reduce { acc, transcript ->
            acc.copy(
                speakers = (acc.speakers + transcript.speakers).distinct(),
                lines = acc.lines + transcript.lines
            )
        }

        val json = Json {
            explicitNulls = false
        }

        val jsonOutput = json.encodeToString(transcript)
        output.writeText(jsonOutput)
    }
}