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

import java.util.Iterator;

import org.apache.commons.lang3.StringUtils;

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.batch.AbstractBatchCommitter;
import com.norconex.commons.lang.xml.XML;

import lombok.extern.slf4j.Slf4j;


@SuppressWarnings("javadoc")
@Slf4j
public class GoogleCloudSearchCommitter extends AbstractBatchCommitter {

    @Override
    protected void initBatchCommitter() throws CommitterException {
    }

    @Override
    protected void commitBatch(Iterator<CommitterRequest> it)
            throws CommitterException {

        var json = new StringBuilder();

        var docCount = 0;
        try {
            while (it.hasNext()) {
                var req = it.next();
                if (req instanceof UpsertRequest upsert) {
                    appendUpsertRequest(json, upsert);
                } else if (req instanceof DeleteRequest delete) {
                    appendDeleteRequest(json, delete);
                } else {
                    throw new CommitterException("Unsupported request: " + req);
                }
                docCount++;
            }
            if (LOG.isTraceEnabled()) {
                LOG.trace("JSON POST:\n{}", StringUtils.trim(json.toString()));
            }

            LOG.info("Sent {} commit operations to Google Cloud Search.", 
                    docCount);
        } catch (CommitterException e) {
            throw e;
        } catch (Exception e) {
            throw new CommitterException(
                    "Could not commit JSON batch to Google Cloud Search.", e);
        }
    }

    private void appendDeleteRequest(StringBuilder json, DeleteRequest delete) {        
    }
    
    @Override
    protected void closeBatchCommitter() throws CommitterException {
    }

    private void appendUpsertRequest(StringBuilder json, UpsertRequest req)
            throws CommitterException {
    }

    @Override
    protected void saveBatchCommitterToXML(XML xml) {
    }
    @Override
    protected void loadBatchCommitterFromXML(XML xml) {
        
    }
}
