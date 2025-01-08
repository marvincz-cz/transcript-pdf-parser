package cz.marvincz.transcript.pdfparser.parser

import cz.marvincz.transcript.pdfparser.model.Line
import cz.marvincz.transcript.pdfparser.model.LineType
import cz.marvincz.transcript.pdfparser.model.Transcript
import org.apache.pdfbox.Loader
import org.apache.pdfbox.contentstream.PDFStreamEngine
import org.apache.pdfbox.contentstream.operator.Operator
import org.apache.pdfbox.contentstream.operator.OperatorName
import org.apache.pdfbox.contentstream.operator.OperatorProcessor
import org.apache.pdfbox.cos.COSBase
import org.apache.pdfbox.cos.COSNumber
import org.apache.pdfbox.io.RandomAccessReadBufferedFile
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import java.awt.geom.Rectangle2D
import java.nio.file.Path


class TranscriptParser {
    fun parseTranscript(pdfPath: Path, speakerAlias: Map<String, String>): Transcript {

        val lines = Loader.loadPDF(RandomAccessReadBufferedFile(pdfPath)).use { document ->
            TranscriptStripper().parseTranscript(document, speakerAlias)
        }

        return Transcript(
            speakers = lines.mapNotNullTo(mutableSetOf(), Line::speaker).toList(),
            lines = lines,
        )
    }
}

private class TranscriptStripper : PDFTextStripper() {
    init {
        addOperator(AppendRectangleToPath(this))
    }

    fun parseTranscript(document: PDDocument, speakerAlias: Map<String, String>): List<Line> {
        this.speakerAlias = speakerAlias
        startPage = document.documentCatalog.pageLabels.labelsByPageIndices.indexOfFirst { it.startsWith("T") } + 1

        getText(document)

        return lines
    }

    private val _lines = arrayListOf<Line>()
    private val lines: List<Line> = _lines

    private var pageNumber: String? = null
    private var lineNumber: Int = 0
    private var speaker: String? = null
    private var questioning: String? = null
    private var answering: String? = null

    private var speakerAlias = emptyMap<String, String>()
    private var minLineStartX: Float? = null

    override fun startDocument(document: PDDocument?) {
        super.startDocument(document)

        _lines.clear()
    }

    override fun startPage(page: PDPage?) {
        super.startPage(page)

        rectangles.clear()
        pageNumber = null
        lineNumber = 0
    }

    /**
     * Parse a line of text
     */
    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
        if (text.isBlank()) return

        if (pageNumber == null) {
            // first line on the page is a page number
            pageNumber = text.trim()
        } else {
            val line = text.replace(Regex("\\s*$lineNumber\\s*$"), "")
            // if there is other text than just the page number, we track the leftmost text position
            if (line.isNotBlank()) {
                minLineStartX = min(minLineStartX, textPositions.lineStartX)
            }

            if (textPositions.any { rectangles.any { r -> r.intersects(it) } }) {
                // if there's a long rectangle on this line, it's a ruler
                addRuler()
                speaker = null
                writeString("===")
            } else if (textPositions.any(TextPosition::isBold) || swornRegex.matches(line)) {
                swornRegex.matchEntire(line)?.let { answering = it.groups["sworn"]!!.value }
                addHeader(text = line.trim())
                speaker = null
                writeString("NARRATOR: ${text.trim()}")
            } else {
                var speakerMatched = false

                val lineText = speakerRegex.matchEntire(line)?.let { speakerMatch ->
                    // if the line is too indented, we don't try to match speaker to help with false positives
                    if (textPositions.leftIndent() > 20) return@let null

                    val matchedSpeaker = speakerMatch.groups["speaker"]!!.value.trimEnd(':')
                    speaker = when {
                        "Q" == matchedSpeaker -> questioning ?: "Q"
                        "A" == matchedSpeaker -> answering ?: "A"
                        matchedSpeaker.startsWith("Q ") -> matchedSpeaker.substringAfter("Q ")
                            .also { questioning = it }

                        else -> matchedSpeaker
                    }
                    speakerMatched = true
                    speakerMatch.groups["text"]!!.value
                } ?: line

                if (lineText.isNotBlank()) {
                    if (!speakerMatched && parenthesisRegex.matches(lineText) && (lines.isEmpty() || lines.last().type == LineType.PARAGRAPH)) {
                        val annotationText = lineText.trim().trim('(', ')')
                        addAnnotation(text = annotationText)
                        writeString(annotationText)
                    } else if (speaker == null) {
                        val infoText = lineText.trim().trim('(', ')')
                        addInfo(text = infoText)
                        writeString(infoText)
                    } else {
                        val spokenText = lineText.trim()
                        addSpeech(speaker = speaker!!, text = spokenText)
                        writeString("$speaker: $spokenText")
                    }
                } else {
                    addParagraph()
                }
            }
        }

        lineNumber++
    }

    private fun addRuler() = _lines.add(
        Line(
            type = LineType.RULER,
            page = pageNumber!!,
            line = lineNumber,
        )
    )

    private fun addHeader(text: String) = _lines.add(
        Line(
            type = LineType.HEADER,
            text = text,
            page = pageNumber!!,
            line = lineNumber,
        )
    )

    private fun addInfo(text: String) = _lines.add(
        Line(
            type = LineType.INFO,
            text = text,
            page = pageNumber!!,
            line = lineNumber,
        )
    )

    private fun addAnnotation(text: String) = _lines.add(
        Line(
            type = LineType.ANNOTATION,
            text = text,
            page = pageNumber!!,
            line = lineNumber,
        )
    )

    private fun addSpeech(speaker: String, text: String) = _lines.add(
        Line(
            type = LineType.SPEECH,
            speaker = speakerAlias.getOrDefault(speaker, speaker),
            text = text,
            page = pageNumber!!,
            line = lineNumber,
        )
    )

    private fun addParagraph() = _lines.add(
        Line(
            type = LineType.PARAGRAPH,
            page = pageNumber!!,
            line = lineNumber,
        )
    )

    private inner class AppendRectangleToPath(context: PDFStreamEngine?) : OperatorProcessor(context) {
        override fun getName() = OperatorName.APPEND_RECT

        override fun process(operator: Operator, arguments: List<COSBase>) {
            val x = arguments[0] as COSNumber
            val y = arguments[1] as COSNumber
            val w = arguments[2] as COSNumber
            val h = arguments[3] as COSNumber

            val x1 = x.floatValue()
            val y1 = y.floatValue()
            val x2 = x1 + w.floatValue()
            val y2 = y1 + h.floatValue()

            val rectangle = transformedPoints(x1, y1, x2, y2)

            // there is the body of the separator, but also small thin lines for shading; we want just the main one
            if (rectangle.width > 100 && rectangle.height > 1) {
                rectangles.add(TransformedRectangle(rectangle.apply {
                    // we now make the rectangle full page width so it intersects the text next to it when checking what line it's on
                    setRect(0f, this.y, context.currentPage.cropBox.width, this.height)
                }))
            }
        }

        // transform to PDF coordinates
        private fun transformedPoints(x1: Float, y1: Float, x2: Float, y2: Float): Rectangle2D.Float {
            val position = floatArrayOf(x1, y1, x2, y2)
            context.graphicsState.currentTransformationMatrix.createAffineTransform().transform(
                position, 0, position, 0, 2
            )
            return Rectangle2D.Float(position[0], position[1], position[2] - position[0], position[3] - position[1])
        }
    }

    private val rectangles: MutableList<TransformedRectangle> = arrayListOf()

    private data class TransformedRectangle(val rectangle: Rectangle2D.Float) {
        fun intersects(textPosition: TextPosition): Boolean {
            val other = Rectangle2D.Float(
                textPosition.x,
                textPosition.pageHeight - textPosition.y,
                textPosition.width,
                textPosition.height
            )
            return rectangle.intersects(other)
        }
    }

    private fun List<TextPosition>.leftIndent() = lineStartX?.let { x ->
        minLineStartX?.let { minX -> x - minX }
    } ?: 0f
}

private val swornRegex = Regex("^(?<sworn>[^,\\p{Ll}]+), (?:Previously )?(?:Sworn|Affirmed|Acknowledges Oath), (?:Cross-e|E)xamined by .+\$")
private val speakerRegex = Regex("^(?! )(?<speaker>[^\\p{Ll}]+:|Q|A)\\s+(?<text>.+)")
private val parenthesisRegex = Regex("\\s*\\([^()]+\\)\\s*")

/**
 * Check if the text is bold. Sometimes there is bold font on whitespace, that is a false positive for what we want
 */
private fun TextPosition.isBold() = "Bold" in font.name && visuallyOrderedUnicode.isNotBlank()

private val List<TextPosition>.lineStartX
    get() = firstOrNull { it.visuallyOrderedUnicode.isNotBlank() }?.x

private fun min(x: Float?, y: Float?): Float? {
    return if (x == null) y
    else if (y == null) x
    else kotlin.math.min(x, y)
}