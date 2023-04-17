/* Copyright 2015-2022 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.importer.handler.splitter.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.doc.DocRecord;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.CommonRestrictions;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.splitter.AbstractDocumentSplitter;
import com.norconex.importer.parser.ParseState;
import com.norconex.importer.util.CharsetUtil;
import com.norconex.importer.util.DOMUtil;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>Splits HTML, XHTML, or XML document on elements matching a given
 * selector.
 * </p>
 * <p>
 * This class constructs a DOM tree from the document content. That DOM tree
 * is loaded entirely into memory. Use this splitter with caution if you know
 * you'll need to parse huge files. It may be preferable to use a stream-based
 * approach if this is a concern (e.g., {@link XMLStreamSplitter}).
 * </p>
 * <p>
 * The <a href="http://jsoup.org/">jsoup</a> parser library is used to load a
 * document content into a DOM tree. Elements are referenced using a
 * <a href="http://jsoup.org/cookbook/extracting-data/selector-syntax">
 * CSS or JQuery-like syntax</a>.
 * </p>
 * <p>Should be used as a pre-parse handler.</p>
 *
 * <h3>Content-types</h3>
 * <p>
 * By default, this filter is restricted to (applies only to) documents matching
 * the restrictions returned by
 * {@link CommonRestrictions#domContentTypes(String)}.
 * You can specify your own content types if you know they represent a file
 * with HTML or XML-like markup tags.
 * </p>
 *
 * <p><b>Since 2.5.0</b>, when used as a pre-parse handler,
 * this class attempts to detect the content character
 * encoding unless the character encoding
 * was specified using {@link #setSourceCharset(String)}. Since document
 * parsing converts content to UTF-8, UTF-8 is always assumed when
 * used as a post-parse handler.
 * </p>
 *
 * <p><b>Since 2.8.0</b>, you can specify which parser to use when reading
 * documents. The default is "html" and will normalize the content
 * as HTML. This is generally a desired behavior, but this can sometimes
 * have your selector fail. If you encounter this
 * problem, try switching to "xml" parser, which does not attempt normalization
 * on the content. The drawback with "xml" is you may not get all HTML-specific
 * selector options to work.  If you know you are dealing with XML to begin
 * with, specifying "xml" should be a good option.
 * </p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.splitter.impl.DOMSplitter"
 *     selector="(selector syntax)"
 *     parser="[html|xml]"
 *     sourceCharset="(character encoding)" >
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 * </handler>
 * }
 *
 * {@nx.xml.example
 * <handler class="DOMSplitter" selector="div.contact" />
 * }
 *
 * <p>
 * The above example splits contacts found in an HTML document, each one being
 * stored within a div with a class named "contact".
 * </p>
 *
 * @see XMLStreamSplitter
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public class DOMSplitter extends AbstractDocumentSplitter
        implements XMLConfigurable {

    private String selector;
    private String sourceCharset = null;
    private String parser = DOMUtil.PARSER_HTML;

    public DOMSplitter() {
        addRestrictions(
                CommonRestrictions.domContentTypes(DocMetadata.CONTENT_TYPE));
    }

    public String getSelector() {
        return selector;
    }
    public void setSelector(String selector) {
        this.selector = selector;
    }
    /**
     * Gets the assumed source character encoding.
     * @return character encoding of the source to be transformed
         */
    public String getSourceCharset() {
        return sourceCharset;
    }
    /**
     * Sets the assumed source character encoding.
     * @param sourceCharset character encoding of the source to be transformed
         */
    public void setSourceCharset(String sourceCharset) {
        this.sourceCharset = sourceCharset;
    }

    /**
     * Gets the parser to use when creating the DOM-tree.
     * @return <code>html</code> (default) or <code>xml</code>.
         */
    public String getParser() {
        return parser;
    }
    /**
     * Sets the parser to use when creating the DOM-tree.
     * @param parser <code>html</code> or <code>xml</code>.
         */
    public void setParser(String parser) {
        this.parser = parser;
    }

    @Override
    protected List<Doc> splitApplicableDocument(
            HandlerDoc doc, InputStream input, OutputStream output,
            ParseState parseState) throws ImporterHandlerException {

        var inputCharset = CharsetUtil.firstNonBlankOrUTF8(
                parseState,
                sourceCharset,
                doc.getDocInfo().getContentEncoding());
        List<Doc> docs = new ArrayList<>();
        try {
            var soupDoc = Jsoup.parse(input, inputCharset,
                    doc.getReference(), DOMUtil.toJSoupParser(getParser()));
            var elms = soupDoc.select(selector);

            // if there only 1 element matched, make sure it is not the same as
            // the parent document to avoid infinite loops (the parent
            // matching itself recursively).
            if (elms.size() == 1) {
                var matchedElement = elms.get(0);
                var parentElement = getBodyElement(soupDoc);
                if (matchedElement.equals(parentElement)) {
                    return docs;
                }
            }

            // process "legit" child elements
            for (Element elm : elms) {
                var childMeta = new Properties();
                childMeta.loadFromMap(doc.getMetadata());
                var childContent = elm.outerHtml();
                var childEmbedRef = elm.cssSelector();
                var childRef = doc.getReference() + "!" + childEmbedRef;
                CachedInputStream content = null;
                if (childContent.length() > 0) {
                    content = doc.getStreamFactory().newInputStream(
                            childContent);
                } else {
                    content = doc.getStreamFactory().newInputStream();
                }
                var childDoc =
                        new Doc(childRef, content, childMeta);

                var childInfo = childDoc.getDocRecord();
                childInfo.addEmbeddedParentReference(doc.getReference());
                childMeta.set(
                        DocMetadata.EMBEDDED_REFERENCE, childEmbedRef);

//                childInfo.setEmbeddedReference(childEmbedRef);

//                childMeta.setReference(childRef);
//                childMeta.setEmbeddedReference(childEmbedRef);
//                childMeta.setEmbeddedParentReference(doc.getReference());
//                childMeta.setEmbeddedParentRootReference(doc.getReference());
                docs.add(childDoc);
            }
        } catch (IOException e) {
            throw new ImporterHandlerException(
                    "Cannot parse document into a DOM-tree.", e);
        }
        return docs;
    }

    private Element getBodyElement(Document soupDoc) {
        var body = soupDoc.body();
        if (body.childNodeSize() == 1) {
            return body.child(0);
        }
        return null;
    }

    @Override
    protected void loadHandlerFromXML(XML xml) {
        setSelector(xml.getString("@selector", selector));
        setSourceCharset(xml.getString("@sourceCharset", sourceCharset));
        setParser(xml.getString("@parser", parser));
    }

    @Override
    protected void saveHandlerToXML(XML xml) {
        xml.setAttribute("selector", selector);
        xml.setAttribute("sourceCharset", sourceCharset);
        xml.setAttribute("parser", parser);
    }
}