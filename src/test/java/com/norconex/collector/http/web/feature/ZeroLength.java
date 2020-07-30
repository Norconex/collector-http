/* Copyright 2019 Norconex Inc.
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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.norconex.collector.http.web.AbstractTestFeature;
import com.norconex.committer.core3.impl.MemoryCommitter;

/**
 * Test that blank files are committed.
 * @author Pascal Essiembre
 */
// Test case for https://github.com/Norconex/collector-http/issues/313
public class ZeroLength extends AbstractTestFeature {

    @Override
    public void service(
            HttpServletRequest req, HttpServletResponse resp) throws Exception {
        // DO nothing (document is blank)
    }

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {
        assertListSize("document", committer.getUpsertRequests(), 1);
    }
}
