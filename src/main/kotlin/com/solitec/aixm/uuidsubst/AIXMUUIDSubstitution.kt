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

import org.w3c.dom.Document
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import javax.xml.datatype.XMLGregorianCalendar
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The implementation of substitution process.
 *
 * @param outputStream
 *      The target [OutputStream] of the generated result.
 * @param params
 *      The configuration parameters for the substitution process.
 */
class AIXMUUIDSubstitution(private val outputStream: OutputStream, private val params: SubstitutionParams) :
    AIXMPartialHandler() {

    companion object {
        fun execute(inputStream: InputStream, outputStream: OutputStream, params: SubstitutionParams) {
            val aixmUuidSubstitution = AIXMUUIDSubstitution(outputStream, params)
            PartialDocumentHandler.parse(InputSource(inputStream), aixmUuidSubstitution)
            aixmUuidSubstitution.partialWriter?.endDocument()
        }
    }

    private var partialWriter: PartialXMLWriter? = null
    private val document = DocumentBuilderFactory.newInstance().run {
        isNamespaceAware = true
        newDocumentBuilder().newDocument()
    }
    private val relaxedUUIDregex = """([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})""".toRegex()

    override fun firstPartial(partial: Node, namespaceContext: NamespaceContextEx) {
        partialWriter = partialWriter ?: PartialXMLWriter(
            outputStream,
            "utf-8",
            namespaceContext
        ).apply {
            startDocument(document.firstChild)
            changeSeparator(
                AUSConstants.AIXM51_BM_NS_URI,
                AUSConstants.AIXM51_BM_SEPARATOR_LOCAL
            )
        }
    }

    override fun handleFeature(partial: Node, namespaceContext: NamespaceContextEx) {
        val feature = partial.firstChild

        val timeSlices = XPathTool.extractNodeList(feature, """aixm:timeSlice""")
        val timeSlice = if (timeSlices.size > 1) {
            timeSlices.map { TimesliceContainer(it) }
                .maxByOrNull { timesliceContainer -> timesliceContainer.ordinal }!!
                .timesliceNode
        } else {
            timeSlices.first()
        }

        if (timeSlices.size > 1) {
            timeSlices.filter { node -> node != timeSlice }
                .forEach { node -> feature.removeChild(node) }
        }

        XPathTool.extractNode(timeSlice, """descendant::gml:validTime/gml:TimePeriod/gml:beginPosition""")?.also {
            it.textContent = params.effectiveDate.toXMLFormat()
        }

        XPathTool.extractNode(timeSlice, """descendant::aixm:sequenceNumber""")?.textContent = "1"
        XPathTool.extractNode(timeSlice, """descendant::aixm:correctionNumber""")?.textContent = "0"

        XPathTool.extractNode(feature, """gml:identifier""")?.also { identifierNode ->
            val newUUID = params.idMap[identifierNode.textContent]
            if (timeSlices.size > 1) {
                println("Warning, feature with identifier ${identifierNode.textContent} contains more than 1 timeslice.")
            }
            identifierNode.textContent = newUUID
            XPathTool.extractNode(feature, """self::node()/@gml:id""")?.also {
                it.textContent = "uuid.${newUUID}"
            }
        }

        XPathTool.extractNodeList(feature, """descendant::node()/@xlink:href""").forEach { href ->
            val checkedForGmlIds = handleGmlIdRefs(href.textContent)
            val newHrefContent = relaxedUUIDregex.findAll(checkedForGmlIds)
                .fold(checkedForGmlIds) { acc, matchResult -> replaceIfExist(acc, matchResult.groupValues[1]) }
            href.textContent = newHrefContent
        }

        if (params.remark != null) {
            val fTimeSlice = timeSlice.firstChild
            val emptyAnnotation = XPathTool.extractNode(fTimeSlice!!, """aixm:annotation[@xsi:nil = "true"]""")
            if (emptyAnnotation != null) {
                fTimeSlice.removeChild(emptyAnnotation)
            }
            val annotation = createAnnotation(params.remark, fTimeSlice.ownerDocument)
            placeAnnotation(feature.localName, fTimeSlice, annotation)
        }

        feature.also {
            document.adoptNode(it)
            partialWriter!!.streamElement(it)
        }

    }

    private fun handleGmlIdRefs(textContent: String): String {
        return params.gmlIdMap.entries.fold(textContent) { acc, entry ->
            acc.replace(entry.key, entry.value)
        }
    }

    private fun replaceIfExist(text: String, uuid: String): String {
        val newUUID = params.idMap[uuid]
        return if (newUUID != null) {
            text.replace(uuid, newUUID)
        } else {
            text
        }
    }

    /**
     * This method places the [annotation] on the correct place as child in the given [timeSlice]
     * The [name] attribute is the local-name of the feature.
     */
    private fun placeAnnotation(name: String, timeSlice: Node, annotation: Node) {
        val elements = convertToList(timeSlice.childNodes)
            .filter { it.nodeType == Node.ELEMENT_NODE }.toList()
        // getFirstNodeGreaterThanAnno, if found insertBefore, else appendChild
        val featureProperties = FeaturePropertiesFactory.propertiesFor(name)
        val afterAnnotation = elements.filter { node -> featureProperties.isAfterAnnotation(node.localName) }

        if (afterAnnotation.isNotEmpty()) {
            timeSlice.insertBefore(annotation, afterAnnotation.first())
        } else {
            timeSlice.appendChild(annotation)
        }

    }

    /**
     * This method converts the ugly [nodeList] to a collection.
     */
    private fun convertToList(nodeList: NodeList): List<Node> {
        val list = mutableListOf<Node>()
        for (i in 0 until nodeList.length) {
            list.add(nodeList.item(i))
        }
        return list
    }

    /**
     * This function creates a new annotation element tree, with the purpose of "REMARK" and the content of [text]
     *
     * @param text  The content of the annotation.
     * @param doc   The "element factory".
     */
    private fun createAnnotation(text: String, doc: Document): Node {
        val annotation = doc.createElementNS(AUSConstants.AIXM51_NS_URI, "aixm:annotation")

        val note = doc.createElementNS(AUSConstants.AIXM51_NS_URI, "aixm:Note")
        note.setAttributeNS(AUSConstants.GML_NS_URI, "gml:id", "uuid.${UUID.randomUUID()}")

        val purpose = doc.createElementNS(AUSConstants.AIXM51_NS_URI, "aixm:purpose")
        purpose.textContent = "REMARK"
        note.appendChild(purpose)

        val translatedNote = doc.createElementNS(AUSConstants.AIXM51_NS_URI, "aixm:translatedNote")
        val linguisticNode = doc.createElementNS(AUSConstants.AIXM51_NS_URI, "aixm:LinguisticNote")
        linguisticNode.setAttributeNS(AUSConstants.GML_NS_URI, "gml:id", "uuid.${UUID.randomUUID()}")
        val innerNote = doc.createElementNS(AUSConstants.AIXM51_NS_URI, "aixm:note")
        innerNote.textContent = text
        linguisticNode.appendChild(innerNote)
        translatedNote.appendChild(linguisticNode)
        note.appendChild(translatedNote)

        annotation.appendChild(note)
        return annotation
    }

    override fun rootElement(
        uri: String,
        localName: String,
        qName: String,
        attributes: Attributes,
        namespaceContext: NamespaceContextEx
    ) {
        val rootElement = document.createElementNS(uri, qName)
        for (i in 0 until attributes.length) {
            rootElement.setAttributeNS(attributes.getURI(i), attributes.getQName(i), attributes.getValue(i))
        }
        document.appendChild(rootElement)
    }

}

/**
 * This data class encapsulate parameters for the substitution processor.
 *
 * @param effectiveDate
 *      The new effective date.
 * @param remark
 *      The optional remark.
 * @param idMap
 *      A map containing the original and new UUID values.
 */
data class SubstitutionParams(
    val effectiveDate: XMLGregorianCalendar,
    val remark: String?,
    val idMap: MutableMap<String, String>,
    val gmlIdMap: MutableMap<String, String>
)

/**
 * This class helps to calculate the the more recent timeslice in the case more than one exists
 *
 * @param timesliceNode
 *      A [Node] containing a timeslice
 */
class TimesliceContainer(val timesliceNode: Node) {
    val ordinal: Int

    init {
        val seq = convertToInt(XPathTool.extractNode(timesliceNode, """descendant::aixm:sequenceNumber""")?.textContent)
        val corr =
            convertToInt(XPathTool.extractNode(timesliceNode, """descendant::aixm:correctionNumber""")?.textContent)
        ordinal = seq * 10000 + corr
    }

    private fun convertToInt(text: String?): Int {
        return text?.toInt() ?: 0
    }
}