/* Copyright 2022-2023 Norconex Inc.
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
package com.norconex.crawler.web;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.apache.commons.io.input.NullInputStream;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.jeasy.random.randomizers.misc.BooleanRandomizer;
import org.jeasy.random.randomizers.number.LongRandomizer;
import org.jeasy.random.randomizers.number.NumberRandomizer;
import org.jeasy.random.randomizers.text.StringRandomizer;

import com.norconex.committer.core.Committer;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.commons.lang.CircularRange;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.spoil.SpoiledReferenceStrategizer;
import com.norconex.crawler.core.spoil.impl.GenericSpoiledReferenceStrategizer;
import com.norconex.crawler.core.store.DataStore;
import com.norconex.crawler.core.store.DataStoreEngine;
import com.norconex.crawler.web.crawler.HttpCrawlerConfig;
import com.norconex.crawler.web.crawler.StartURLsProvider;
import com.norconex.crawler.web.delay.DelayResolver;
import com.norconex.crawler.web.delay.impl.GenericDelayResolver;
import com.norconex.crawler.web.doc.HttpDocRecord;
import com.norconex.crawler.web.fetch.HttpFetcher;
import com.norconex.crawler.web.fetch.impl.GenericHttpFetcher;
import com.norconex.crawler.web.link.impl.DOMLinkExtractor;
import com.norconex.crawler.web.processor.HttpDocumentProcessor;
import com.norconex.crawler.web.processor.impl.FeaturedImageProcessor;
import com.norconex.crawler.web.recrawl.RecrawlableResolver;
import com.norconex.crawler.web.robot.RobotsTxtProvider;
import com.norconex.crawler.web.robot.impl.StandardRobotsTxtProvider;
import com.norconex.crawler.web.sitemap.SitemapResolver;
//import com.norconex.crawler.core.store.DataStore;
//import com.norconex.crawler.core.store.DataStoreEngine;
//import com.norconex.crawler.core.store.MockDataStore;
//import com.norconex.crawler.core.store.MockDataStoreEngine;
import com.norconex.importer.ImporterConfig;
import com.norconex.importer.doc.DocMetadata;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

public final class WebStubber {

    public static final String MOCK_CRAWLER_ID = "test-crawler";
    public static final String MOCK_CRAWL_SESSION_ID = "test-session";

    private static EasyRandom easyRandom = new EasyRandom(
            new EasyRandomParameters()
            .seed(System.currentTimeMillis())
            .collectionSizeRange(1, 5)
            .randomizationDepth(5)
            .scanClasspathForConcreteTypes(true)
            .overrideDefaultInitialization(true)
            .randomize(File.class,
                    () -> new File(new StringRandomizer(100).getRandomValue()))
            .randomize(Path.class,
                    () -> Path.of(new StringRandomizer(100).getRandomValue()))
            .randomize(Long.class,
                    () -> Math.abs(new LongRandomizer().getRandomValue()))
            .randomize(ImporterConfig.class, ImporterConfig::new)
            .randomize(UpsertRequest.class,
                    () -> new UpsertRequest(
                            new StringRandomizer(100).getRandomValue(),
                            new Properties(),
                            new NullInputStream()))
            .randomize(DeleteRequest.class,
                    () -> new DeleteRequest(
                            new StringRandomizer(100).getRandomValue(),
                            new Properties()))
            .randomize(Committer.class, MemoryCommitter::new)
            .randomize(SpoiledReferenceStrategizer.class,
                    GenericSpoiledReferenceStrategizer::new)
            .randomize(AtomicBoolean.class, () -> new AtomicBoolean(
                    new BooleanRandomizer().getRandomValue()))

            .excludeType(DataStoreEngine.class::equals)
            .excludeType(DataStore.class::equals)
            .excludeType(SitemapResolver.class::equals)
            .excludeType(FeaturedImageProcessor.class::equals)
            .excludeType(HttpDocumentProcessor.class::equals)
            .excludeType(RecrawlableResolver.class::equals)
            .randomize(StartURLsProvider.class, () -> null) // <-- TODO mock this
            .randomize(Charset.class, () -> StandardCharsets.UTF_8)
            .randomize(CircularRange.class, () -> {
                int a = new NumberRandomizer().getRandomValue();
                int b = new NumberRandomizer().getRandomValue();
                return CircularRange.between(Math.min(a, b), Math.max(a, b));
            })
            .randomize(CachedInputStream.class,
                    CachedInputStream::nullInputStream)
            .randomize(HttpFetcher.class, GenericHttpFetcher::new)
            .randomize(RobotsTxtProvider.class, StandardRobotsTxtProvider::new)
            .randomize(Pattern.class, () -> Pattern.compile(
                    new StringRandomizer(20).getRandomValue()))
            .randomize(DelayResolver.class, () -> {
                var resolv = new GenericDelayResolver();
                resolv.setScope("crawler");
                return resolv;
            })
            .randomize(DOMLinkExtractor.class, () -> {
                var extractor = new DOMLinkExtractor();
                extractor.addLinkSelector("text");
                return extractor;
            })
    );

    private WebStubber() {}

    public static <T> T randomize(Class<T> cls) {
        return easyRandom.nextObject(cls);
    }

    /**
     * <p>Random crawler config stub:</p>
     * <ul>
     *   <li>Single-threaded</li>
     *   <li>1 Memory Committer</li>
     *   <li>Random values for everything else.</li>
     * </ul>
     * @return random crawler config
     */
    public static HttpCrawlerConfig crawlerConfigRandom() {
        var cfg = easyRandom.nextObject(HttpCrawlerConfig.class);
        cfg.setNumThreads(1);
        cfg.setCommitters(List.of(new MemoryCommitter()));
        return cfg;
    }

    public static CrawlDoc crawlDoc(
            String ref, ContentType ct, InputStream is) {
        var docRecord = new HttpDocRecord(ref);
        docRecord.setContentType(ct);
        var doc = new CrawlDoc(docRecord, CachedInputStream.cache(is));
        doc.getMetadata().set(DocMetadata.CONTENT_TYPE, ct);
        return doc;
    }

    public static HtmlPage htmlPage() {
        return new HtmlPage();
    }

    @Data
    @Accessors(fluent = true)
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class HtmlPage {
        String title = "Mock HTML Page";
        //TODO if needed, replace with collections, of scripts, css, etc.
        String head = "";
        String body = "Mock HTML page content.";
        public String build() {
            return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <title>%s</title>
                  %s
                </head>
                <body>
                %s
                </body>
                </html>
                """.formatted(title, head, body);
        }
    }
}
