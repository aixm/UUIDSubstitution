/*
BSD 2-Clause License

Copyright (c) 2021, EUROCONTROL
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 Project:      aixm-uuid-subst
 Created:      14.10.21
 Author:       Manfred Odenstein, SOLITEC Software Solutions G.m.b.H.

*/
package com.solitec.aixm.uuidsubst

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

class AixmUUIDSupstCLI : CliktCommand(
    name = "aixm-uuid-subst", help = """
    This command replaces the existing identifier with a new one, with an optional annotation (--remark) for the given <EFFECTIVE-DATE>.
    
    Example:
    ```
    aixm-uuid-subst --remark "changed identifiers" "2022-12-24T00:00:00Z" input.xml output.xml
    ```
""".trimIndent()
) {

    private val effectiveDate by argument(
        "<EFFECTIVE-DATE>",
        help = """The new effective date, e.g. "2022-12-24T00:00:00Z"."""
    ).convert {
        XMLTool.parseXMLDateTime(it)
    }
    private val remark by option("-r", "--remark", help = "This text will be placed in the annotation element.")
    private val csvFile by option(
        "--csv-output-file",
        help = "A CSV output file containing the old and the new values of the identifiers."
    ).file()
    private val pretty by option("--pretty", help = "The output file will be human readable, pretty printed.").flag(
        default = false
    )
    private val inputFile by argument(
        "<INPUT-FILE>",
        help = "An AIXM 5.1 Basic Message file as input."
    ).file(mustExist = true)
    private val outputFile by argument("<OUTPUT-FILE>", help = "The output file.").file()

    override fun run() {
        val inputStream1 = BufferedInputStream(inputFile.inputStream())
        val tempFile = File.createTempFile("uuid_subst", null)
        try {
            val idMap = IdentifierExtractor.execute(inputStream1)
            if (csvFile != null) {
                val csvOutputStream = BufferedOutputStream(csvFile!!.outputStream())
                csvOutputStream.use {
                    IdentifierExtractor.exportCSV(idMap, it)
                }
            }
            val inputStream2 = BufferedInputStream(inputFile.inputStream())
            val params = SubstitutionParams(effectiveDate, remark, idMap)
            BufferedOutputStream(tempFile.outputStream()).use {
                AIXMUUIDSubstitution.execute(inputStream2, it, params)
            }
            if (pretty) {
                prettyPrint(tempFile, outputFile)
            } else {
                tempFile.copyTo(outputFile, overwrite = true)
            }

        } finally {
            tempFile.delete()
        }
    }

    /**
     * simply pretty prints an XML file
     *
     * @param inputFile
     *      The input file.
     *
     * @param outputFile
     *      The output file.
     */
    private fun prettyPrint(inputFile: File, outputFile: File) {
        val transformer = TransformerFactory.newInstance()
            .newTransformer().apply {
                setOutputProperty(OutputKeys.INDENT, "yes")
                setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            }
        transformer.transform(StreamSource(inputFile), StreamResult(outputFile))
    }
}

fun main(args: Array<String>) = AixmUUIDSupstCLI().main(args)
