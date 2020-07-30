/* Copyright 2017 Norconex Inc.
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
package com.norconex.collector.http.crawler;

import java.util.Iterator;

import org.apache.commons.collections4.iterators.ObjectArrayIterator;
import org.apache.commons.collections4.iterators.ObjectArrayListIterator;

/**
 * Provide starting URLs for crawling. Implement this interface if you need
 * start URLs to be established dynamically when the crawler starts.
 * @author Pascal Essiembre
 * @since 2.7.0
 */
public interface IStartURLsProvider {

    /**
     * Provides an iterator over start URLs. If you have a limited set
     * that is easier to return as array or list, consider wrapping the
     * return value in {@link ObjectArrayIterator} or 
     * {@link ObjectArrayListIterator}.
     * @return iterator of URL strings
     */
    Iterator<String> provideStartURLs();
}
