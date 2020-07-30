/* Copyright 2016-2020 Norconex Inc.
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
package com.norconex.collector.http.pipeline.queue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.collector.http.HttpCollector;
import com.norconex.collector.http.crawler.HttpCrawler;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.doc.HttpDocInfo;
import com.norconex.collector.http.fetch.HttpFetchClient;
import com.norconex.collector.http.robot.RobotsTxt;
import com.norconex.collector.http.robot.impl.StandardRobotsTxtProvider;

/**
 * @author Pascal Essiembre
 */
public class RobotsTxtFiltersStageTest {

    @Test
    public void testAllow() {
        // An allow for a robot rule should now be rejecting all non-allowing.
        // It should allows sub directories that have their parent rejected
        String robotTxt =
                "User-agent: *\n\n"
              + "Disallow: /rejectMost/*\n"
              + "Allow: /rejectMost/butNotThisOne/*\n";

        Assertions.assertFalse( testAllow(robotTxt,
                "http://rejected.com/rejectMost/blah.html"),
                "Matches Disallow");
        Assertions.assertTrue( testAllow(robotTxt,
                "http://accepted.com/rejectMost/butNotThisOne/blah.html"),
                "Matches Disallow AND Allow");
        Assertions.assertTrue( testAllow(robotTxt,
                "http://accepted.com/notListed/blah.html"),
                "No match in robot.txt");

    }
    private boolean testAllow(final String robotTxt, final String url) {
        StandardRobotsTxtProvider provider = new StandardRobotsTxtProvider() {
            @Override
            public synchronized RobotsTxt getRobotsTxt(
                    HttpFetchClient fetcher, String url) {
                try {
                    return parseRobotsTxt(IOUtils.toInputStream(robotTxt,
                            StandardCharsets.UTF_8), url, "test-crawler");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        HttpCrawlerConfig cfg = new HttpCrawlerConfig();
        cfg.setRobotsTxtProvider(provider);

        HttpQueuePipelineContext ctx = new HttpQueuePipelineContext(
                new HttpCrawler(cfg, new HttpCollector()),
                new HttpDocInfo(url, 0));
        RobotsTxtFiltersStage filterStage = new RobotsTxtFiltersStage();
        return filterStage.execute(ctx);
    }
}