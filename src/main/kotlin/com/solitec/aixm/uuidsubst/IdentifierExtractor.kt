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

import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintWriter
import java.util.*

/**
 * Extracts the identifier values out of stream and stores them beside a newly generated UUID.
 */
class IdentifierExtractor : AIXMPartialHandler() {

    private val identifierMap: MutableMap<String, String> = mutableMapOf()

    companion object {

        /**
         * This function takes the [inputStream] and extracts the identifiers, generate a new value (uuid v4) and
         * returns the combination in a map.
         *
         * @param inputStream
         *      An XML source.
         *
         * @return
         *      A MutableMap containing the old value of the identifier as key and new one as value.
         */
        fun execute(inputStream: InputStream): MutableMap<String, String> {
            val identifierExtractor = IdentifierExtractor()
            PartialDocumentHandler.parse(InputSource(inputStream), identifierExtractor)
            return identifierExtractor.identifierMap
        }

        /**
         * This function generates a CSV data out of the [identifierMap] and writes them to the given [outputStream]
         *
         * @param identifierMap
         *      A MutableMap which contains the original value as key and the new one as value.
         *
         * @param outputStream
         *      A target OutputStream the content will be written to.
         */
        fun exportCSV(identifierMap: MutableMap<String, String>, outputStream: OutputStream) {
            val printWriter = PrintWriter(outputStream)
            printWriter.use { writer ->
                writer.println("\"original\",\"new\"")
                identifierMap.forEach { (old, changed) -> writer.println("\"${old}\",\"${changed}\"") }
            }
        }
    }

    override fun handleFeature(partial: Node, namespaceContext: NamespaceContextEx) {
        val feature = partial.firstChild
        XPathTool.extractNode(feature, """gml:identifier""")?.also {
            identifierMap[it.textContent] = UUID.randomUUID().toString()
        }
    }
}