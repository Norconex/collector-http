/* Copyright 2010-2020 Norconex Inc.
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
package com.norconex.collector.http.doc;

import com.norconex.collector.core.doc.CrawlState;

/**
 * Represents a URL crawling status.
 * @author Pascal Essiembre
 * @see CrawlState
 */
public class HttpCrawlState extends CrawlState {

    private static final long serialVersionUID = 1466828686562714860L;

    public static final HttpCrawlState TOO_DEEP =
            new HttpCrawlState("TOO_DEEP");

    /**
     * @since 2.3.0
     */
    public static final HttpCrawlState REDIRECT =
            new HttpCrawlState("REDIRECT");

    protected HttpCrawlState(String state) {
        super(state);
    }
}