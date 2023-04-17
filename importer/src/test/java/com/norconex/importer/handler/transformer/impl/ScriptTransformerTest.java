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
package com.norconex.importer.handler.transformer.impl;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.ScriptRunner;
import com.norconex.importer.parser.ParseState;

class ScriptTransformerTest {

    //--- Simple Transform Test ------------------------------------------------

    @ParameterizedTest
    @ArgumentsSource(SimpleScriptProvider.class)
    void testSimpleTransform(String engineName, String script)
            throws ImporterHandlerException, IOException {
        var t = new ScriptTransformer(new ScriptRunner<>(engineName, script));

        var htmlFile = TestUtil.getAliceHtmlFile();
        InputStream is = new BufferedInputStream(new FileInputStream(htmlFile));
        var out = new ByteArrayOutputStream();
        var metadata = new Properties();
        metadata.set(DocMetadata.CONTENT_TYPE, "text/html");
        t.transformDocument(
                TestUtil.newHandlerDoc(htmlFile.getAbsolutePath(), is, metadata),
                is, out, ParseState.PRE);
        is.close();

        var successField = metadata.getString("test");
        Assertions.assertEquals("success", successField);

        var content = new String(out.toString());
        Assertions.assertEquals(0, StringUtils.countMatches(content, "Alice"));
        Assertions.assertEquals(34, StringUtils.countMatches(content, "Roger"));
    }

    static class SimpleScriptProvider implements ArgumentsProvider {
        @Override
        public Stream<Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                // JavaScript with "returnValue"
                Arguments.of(ScriptRunner.JAVASCRIPT_ENGINE, """
                    metadata.add('test', 'success');
                    var returnValue = content.replace(/Alice/g, 'Roger');
                    """),
                // JavaScript with last-assigned variable
                Arguments.of(ScriptRunner.JAVASCRIPT_ENGINE, """
                    metadata.add('test', 'success');
                    text = content.replace(/Alice/g, 'Roger');
                    """),
                // JavaScript with both "returnValue" and last-assigned variable
                Arguments.of(ScriptRunner.JAVASCRIPT_ENGINE, """
                    metadata.add('test', 'success');
                    returnValue = content.replace(/Alice/g, 'Roger');
                    text = "Should not be me";
                    """),

                // Lua with "returnValue"
                Arguments.of(ScriptRunner.LUA_ENGINE, """
                    metadata:add('test', {'success'});
                    returnValue = content:gsub('Alice', 'Roger');
                    """),

                // Lua with explicit return statement
                Arguments.of(ScriptRunner.LUA_ENGINE, """
                    metadata:add('test', {'success'});
                    local text = content:gsub('Alice', 'Roger');
                    return text;
                    """),

//                Arguments.of(ScriptRunner.PYTHON_ENGINE, """
//                    metadata.add('test', 'success')
//                    returnValue = content.replace('Alice', 'Roger')
//                    """),
                Arguments.of(ScriptRunner.VELOCITY_ENGINE, """
                    $metadata.add("test", "success")
                    #set($returnValue = $content.replace('Alice', 'Roger'))
                    """)
            );
        }
    }

    //--- Modify Content Test --------------------------------------------------
    // https://github.com/Norconex/collector-http/issues/665
    @ParameterizedTest
    @ArgumentsSource(ContentModifyScriptProvider.class)
    void testContentModify(String engineName, String script)
            throws ImporterHandlerException, UnsupportedEncodingException {

        var t = new ScriptTransformer();
        t.setScriptRunner(new ScriptRunner<>(engineName, script));
        var metadata = new Properties();
        metadata.set(DocMetadata.CONTENT_TYPE, "text/html");
        var out = new ByteArrayOutputStream();
        var is = IOUtils.toInputStream(
                "World!", StandardCharsets.UTF_8);
        t.transformDocument(
                TestUtil.newHandlerDoc("N/A", is, metadata),
                is, out, ParseState.POST);
        var content = out.toString(StandardCharsets.UTF_8.toString());
        Assertions.assertEquals("Hello World!", content);
    }

    static class ContentModifyScriptProvider implements ArgumentsProvider {
        @Override
        public Stream<Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                Arguments.of(ScriptRunner.JAVASCRIPT_ENGINE, """
                    var ct = metadata.getString('document.contentType');
                    if (ct != null && ct == 'text/html' && content != null) {
                        content = 'Hello ' + content;
                    }
                    """),
                Arguments.of(ScriptRunner.LUA_ENGINE, """
                    local ct = metadata:getString('document.contentType')
                    if(ct ~= nil and ct == 'text/html' and content ~= nil)
                    then
                        content = 'Hello ' .. content
                    end
                    return content
                    """),
//                Arguments.of(ScriptRunner.PYTHON_ENGINE, """
//                    ct = metadata.getString('document.contentType');
//                    if ct and ct == 'text/html' and content:
//                        content = 'Hello ' + content;
//                    returnValue = content
//                    """),
                Arguments.of(ScriptRunner.VELOCITY_ENGINE, """
                    #set($ct = $metadata.getString("document.contentType"))
                    #if ($ct && $ct == "text/html" && $content)
                      #set($returnValue = "Hello " + $content)
                    #end
                    """)
            );
        }
    }

    //--- Write/Read -----------------------------------------------------------

    @Test
    void testWriteRead() {
        XML.assertWriteRead(new ScriptTransformer(
                new ScriptRunner<>(ScriptRunner.JAVASCRIPT_ENGINE,
                        "var blah = 'blah';")), "handler");
    }
}