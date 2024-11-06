package cz.marvincz.transcript.pdfparser.model

import kotlinx.serialization.Serializable

@Serializable
data class Transcript(
    val speakers: List<String>,
    val lines: List<Line>,
)

@Serializable
data class Line(
    val type: LineType,
    val speaker: String? = null,
    val text: String? = null,
    val page: String,
    val line: Int,
)

enum class LineType {
    SPEECH,
    HEADER,
    ANNOTATION,
    INFO,
    RULER,
    PARAGRAPH
}