/* 
 Project:      aixm-uuid-subst   
 Created:      15.10.21
 Author:       Manfred Odenstein

 This code is copyright (c) 2021 SOLITEC Software Solutions G.m.b.H.
*/
package com.solitec.aixm.uuidsubst

import org.w3c.dom.Node
import org.xml.sax.Attributes
import java.util.*

/**
 * Abstract implementation of a [PartialHandler] which knows something about AIXM 5.1
 */
abstract class AIXMPartialHandler : PartialHandler {

    internal enum class State {
        STATE_NOTHING,
        STATE_MESSAGE_META,
        STATE_FEATURE
    }

    internal data class Element(val uri: String, val localName: String) : Comparable<Element> {
        override fun compareTo(other: Element): Int {
            return compareValuesBy(this, other, { it.uri }, { it.localName })
        }
    }

    private var firstPartialHandled = false
    private var state = State.STATE_NOTHING
    private val separatorMap = TreeMap<Element, State>().apply {
        put(Element(AUSConstants.AIXM51_NS_URI, AUSConstants.AIXM51_MESSAGE_METADATA), State.STATE_MESSAGE_META)
        put(Element(AUSConstants.AIXM51_BM_NS_URI, AUSConstants.AIXM51_BM_SEPARATOR_LOCAL), State.STATE_FEATURE)
    }

    override fun isSeparator(uri: String, localName: String): Boolean {
        val tag = Element(uri, localName)
        if (separatorMap.containsKey(tag)) {
            state = separatorMap[tag]!!
            return true
        }
        return false
    }

    override fun handlePartial(partial: Node, namespaceContext: NamespaceContextEx) {
        if (!firstPartialHandled) {
            firstPartial(partial, namespaceContext)
            firstPartialHandled = true
        }

        when (state) {

            State.STATE_MESSAGE_META -> {
                handleMessageMetadata(partial, namespaceContext)
            }

            State.STATE_FEATURE -> {
                handleFeature(partial, namespaceContext)
            }
            else ->
                throw IllegalStateException("Invalid state in handlePartial")
        }

    }

    override fun rootElement(
        uri: String,
        localName: String,
        qName: String,
        attributes: Attributes,
        namespaceContext: NamespaceContextEx
    ) {
    }

    /**
     * This function will be called with first [partial]
     *
     * @namespaceContext
     *      The given NamespaceContextEx implementation
     */
    open fun firstPartial(partial: Node, namespaceContext: NamespaceContextEx) {}

    /**
     * This function will be called if the [partial] is a message metadata
     *
     * @namespaceContext
     *      The given NamespaceContextEx implementation
     */
    open fun handleMessageMetadata(partial: Node, namespaceContext: NamespaceContextEx) {}

    /**
     * This function will be called for every [partial], if it is embedded in hasMember
     *
     * @namespaceContext
     *      The given NamespaceContextEx implementation
     */
    open fun handleFeature(partial: Node, namespaceContext: NamespaceContextEx) {}
}