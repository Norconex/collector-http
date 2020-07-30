/* Copyright 2019-2020 Norconex Inc.
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
package com.norconex.collector.http.web.feature;

import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Assertions;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.fetch.impl.GenericHttpFetcher;
import com.norconex.collector.http.web.AbstractTestFeature;
import com.norconex.committer.core3.UpsertRequest;
import com.norconex.committer.core3.impl.MemoryCommitter;
import com.norconex.importer.doc.DocMetadata;

/**
 * Test proper charset detection when the declared one does not match
 * actual one.
 * @author Pascal Essiembre
 */
// Next two related to https://github.com/Norconex/importer/issues/41
public class ContentTypeCharset extends AbstractTestFeature {

    @Override
    public int numberOfRun() {
        return 2;
    }

    @Override
    protected void doConfigureCralwer(HttpCrawlerConfig crawlerConfig)
            throws Exception {
        if (isSecondRun()) {
            GenericHttpFetcher fetcher = new GenericHttpFetcher();
            fetcher.getConfig().setForceContentTypeDetection(true);
            fetcher.getConfig().setForceCharsetDetection(true);
            crawlerConfig.setHttpFetchers(fetcher);
        }
    }

    @Override
    public void service(HttpServletRequest req, HttpServletResponse resp)
                throws Exception {
        resp.setContentType("application/javascript");
        resp.setCharacterEncoding("Big5");
        String out = "<html style=\"font-family:Arial, "
                 + "Helvetica, sans-serif;\">"
                 + "<head><title>ContentType + Charset ☺☻"
                 + "</title></head>"
                 + "<body>This page returns the Content-Type as "
                 + "\"application/javascript; charset=Big5\" "
                 + "while in reality it is \"text/html; charset=UTF-8\"."
                 + "Éléphant à noël. ☺☻"
                 + "</body>"
                 + "</html>";
        resp.getOutputStream().write(out.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {

        assertListSize("document", committer.getUpsertRequests(), 1);
        UpsertRequest doc = committer.getUpsertRequests().get(0);

        if (isFirstRun()) {
            Assertions.assertEquals(doc.getMetadata().getString(
                    DocMetadata.CONTENT_TYPE),
                    "application/javascript");
            Assertions.assertEquals("Big5", doc.getMetadata().getString(
                    DocMetadata.CONTENT_ENCODING));
        } else {
            Assertions.assertEquals("text/html",
                    doc.getMetadata().getString(
                            DocMetadata.CONTENT_TYPE));
            Assertions.assertEquals(StandardCharsets.UTF_8.toString(),
                    doc.getMetadata().getString(
                            DocMetadata.CONTENT_ENCODING));
        }
    }
}
