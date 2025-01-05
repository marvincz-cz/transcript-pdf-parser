# Canadian court transcript parser

A tool for parsing Canadian court transcript PDF files into a machine-readable structure
that can then be used by other tools, e.g. for text-to-speech.

*Note: The PDF format is mostly standardized but there can be some variations
in formatting, and they may throw off some aspect of the parsing of some transcripts.*

## Usage

Start the application from the command line with appropriate arguments.

### Unix
```shell
bin/transcript-pdf-parser <command> [<options>]
```

### Windows
```shell
bin\transcript-pdf-parser.bat <command> [<options>]
```

### Commands:

There are two commands:
* **parse** - Parse a transcript PDF
* **combine** - Combine multiple transcript JSONs into one

#### parse

`--pdf=<path>`
: The transcript PDF file

`--alias=<path>`
: *(optional)* The speaker alias file

`--output=<path>`
: The output JSON file

#### combine

`-i`, `--input`
: The JSON files to be combined. Repeat for each file.

`-o`, `--output`
: The combined JSON file

## Speaker alias file

Sometimes a speaker appears under different names in the transcript.
Especially sworn witnesses, due to the way they appear in a transcript.
For the parts where they appear only as "A", they are parsed from the heading
"NAME, Sworn, Examined by Lawyer.". However, they are usually named differently
there than where they are explicitly named as the speaker.

See for example `CORPORAL HEROUX` and `TERRY HEROUX` in this fragment:

```
27 CORPORAL HEROUX: T-E-R-R-Y H-E-R-O-U-X.
28
29 TERRY HEROUX, Sworn, Examined by Mr. Burge
30
31 THE COURT CLERK: Do you wish to stand or sit?
32
33 A I’ll stand.
```

The alias allows us to unite both of these under one name.

### Format

The alias file uses a simple format:

```
SPEAKER NAME=ALIAS NAME
SPEAKER NAME 2=ALIAS NAME 2
.
.
.
```

## Output JSON

The output is a JSON file with two properties. First is a list of speakers,
second is a list of all lines classified and with the speaker and text isolated.
The line of text is an object with the type, speaker, text, page number and line number as properties.

### Example:
```json
{
  "speakers": ["THE COURT", "THE COURT CLERK"],
  "lines": [
    { "type": "INFO", "text": "Proceedings taken in Edmonton, Alberta", "page": "T1", "line": 1 },
    { "type": "HEADER", "text": "Discussion", "page": "T1", "line": 2 },
    { "type": "SPEECH", "speaker": "THE COURT", "text": "Good morning, everyone. Madam Clerk,", "page": "T1", "line": 3 },
    { "type": "SPEECH", "speaker": "THE COURT", "text": "you can proceed to call the roll.", "page": "T1", "line": 4 },
    { "type": "PARAGRAPH", "page": "T1", "line": 5 },
    { "type": "SPEECH", "speaker": "THE COURT CLERK", "text": "All jurors, as your number is called,", "page": "T1", "line": 6 },
    { "type": "SPEECH", "speaker": "THE COURT CLERK", "text": "answer present.", "page": "T1", "line": 7 }
  ]
}
```

### Schema:
```json
{
  "type": "object",
  "properties": {
    "speakers": {
      "type": "array",
      "items": { "type": "string" }
    },
    "lines": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "type": { "enum": ["SPEECH", "HEADER", "ANNOTATION", "INFO", "RULER", "PARAGRAPH"] },
          "speaker": { "type": "string" },
          "text": { "type": "string" },
          "page": { "type": "string" },
          "line": { "type": "number" }
        }
      }
    }
  }
}
```