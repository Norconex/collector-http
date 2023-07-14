/* Copyright 2023 Norconex Inc.
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
package com.norconex.committer.googlecloudsearch;

import java.io.IOException;
import java.io.Reader;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.committer.core.batch.queue.impl.FSQueue;
import com.norconex.commons.lang.ResourceLoader;
import com.norconex.commons.lang.net.Host;
import com.norconex.commons.lang.security.Credentials;
import com.norconex.commons.lang.xml.XML;

/**
 * @author Harinder Hanjan
 */ 
class GoogleCloudSearchCommitterConfigTest {

    @Test
    void testWriteRead() throws IOException {
        var c = new GoogleCloudSearchCommitter();
//        c.setAccessKey("accessKey");
        
        FSQueue q = new FSQueue();
        q.setBatchSize(10);
        q.setMaxPerFolder(5);
        c.setCommitterQueue(q);

        Credentials creds = new Credentials();
        creds.setPassword("mypassword");
        creds.setUsername("myusername");

        c.setFieldMapping("subject", "title");
        c.setFieldMapping("body", "content");
//
//        c.setSourceIdField("mySourceIdField");
//        c.setTargetContentField("myTargetContentField");
//
//        c.getProxySettings().setHost(new Host("example.com", 1234));

        XML.assertWriteRead(c, "committer");
    }

    @Test
    void testValidation() {
        Assertions.assertDoesNotThrow(() -> {
            try (Reader r = ResourceLoader.getXmlReader(this.getClass())) {
                XML xml = XML.of(r).create();
                xml.toObjectImpl(GoogleCloudSearchCommitter.class);
            }
        });
    }
}
