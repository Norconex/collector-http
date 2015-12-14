/* Copyright 2015 Norconex Inc.
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
package com.norconex.collector.http.pipeline.importer;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.data.CrawlState;
import com.norconex.collector.core.data.store.ICrawlDataStore;
import com.norconex.collector.http.crawler.HttpCrawlerEvent;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.data.HttpCrawlState;
import com.norconex.collector.http.fetch.HttpFetchResponse;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipeline;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipelineContext;
import com.norconex.collector.http.redirect.RedirectStrategyWrapper;

/**
 * <p>Fetches (i.e. download for processing) a document.</p>
 * <p>Prior to 2.3.0, the code for this class was part of 
 * {@link HttpImporterPipeline}.
 * @author Pascal Essiembre
 * @since 2.3.0
 */
/*default*/ class DocumentFetcherStage extends AbstractImporterStage {
    
    private static final Logger LOG = 
            LogManager.getLogger(DocumentFetcherStage.class);
    
    @Override
    public boolean executeStage(HttpImporterPipelineContext ctx) {
        HttpCrawlData crawlData = ctx.getCrawlData();
        
        HttpFetchResponse response =
                ctx.getConfig().getDocumentFetcher().fetchDocument(
                        ctx.getHttpClient(), ctx.getDocument());

        HttpImporterPipelineUtil.enhanceHTTPHeaders(
                ctx.getDocument().getMetadata());
        HttpImporterPipelineUtil.applyMetadataToDocument(ctx.getDocument());

        //-- Start dealing with redirects ---
        String redirectURL = RedirectStrategyWrapper.getRedirectURL();
        if (StringUtils.isNotBlank(redirectURL)) {
            queueRedirectURL(ctx, response, redirectURL);
            return false;
        }
        
        CrawlState state = response.getCrawlState();
        crawlData.setState(state);
        if (state.isGoodState()) {
            ctx.fireCrawlerEvent(HttpCrawlerEvent.DOCUMENT_FETCHED, 
                    crawlData, response);
        } else {
            String eventType = null;
            if (state.isOneOf(HttpCrawlState.NOT_FOUND)) {
                eventType = HttpCrawlerEvent.REJECTED_NOTFOUND;
            } else {
                eventType = HttpCrawlerEvent.REJECTED_BAD_STATUS;
            }
            ctx.fireCrawlerEvent(eventType, crawlData, response);
            return false;
        }
        return true;
    }

    // Keep this method static so multi-threads treat this method as one
    // instance.
    private static synchronized void queueRedirectURL(
            HttpImporterPipelineContext ctx, 
            HttpFetchResponse response,
            String redirectURL) {
        ICrawlDataStore store = ctx.getCrawlDataStore();
        HttpCrawlData crawlData = ctx.getCrawlData();            
        String originalURL =  crawlData.getReference();
        
        //--- Do not queue if previously handled ---
        //TODO throw an event if already active/processed(ing)?
        if (store.isActive(redirectURL)) {
            rejectRedirectDup("being processed", originalURL, redirectURL);
            return;
        } else if (store.isQueued(redirectURL)) {
            rejectRedirectDup("queued", originalURL, redirectURL);
            return;
        } else if (store.isProcessed(redirectURL)) {
            rejectRedirectDup("processed", originalURL, redirectURL);
            return;
        }

        //--- Fresh URL, queue it! ---
        crawlData.setState(HttpCrawlState.REDIRECT);
        HttpFetchResponse newResponse = new HttpFetchResponse(
                HttpCrawlState.REDIRECT, 
                response.getStatusCode(),
                response.getReasonPhrase() + " (" + redirectURL + ")");
        ctx.fireCrawlerEvent(HttpCrawlerEvent.REJECTED_REDIRECTED, 
                crawlData, newResponse);
        
        HttpCrawlData newData = new HttpCrawlData(
                redirectURL, crawlData.getDepth());
        newData.setReferrerReference(crawlData.getReferrerReference());
        newData.setReferrerLinkTag(crawlData.getReferrerLinkTag());
        newData.setReferrerLinkText(crawlData.getReferrerLinkText());
        newData.setReferrerLinkTitle(crawlData.getReferrerLinkTitle());
        if (ctx.getConfig().getURLCrawlScopeStrategy().isInScope(
                crawlData.getReference(), redirectURL)) {
            HttpQueuePipelineContext newContext = 
                    new HttpQueuePipelineContext(
                            ctx.getCrawler(), store, newData);
            new HttpQueuePipeline().execute(newContext);
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("URL redirect target not in scope: " + redirectURL);
            }
            newData.setState(HttpCrawlState.REJECTED);
            ctx.fireCrawlerEvent(
                    HttpCrawlerEvent.REJECTED_FILTER, newData, 
                    ctx.getConfig().getURLCrawlScopeStrategy());
        }
    }
    
    private static void rejectRedirectDup(String action, 
            String originalURL, String redirectURL) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Redirect target URL is already " + action
                    + ": " + redirectURL + " (from: " + originalURL + ").");
        }
    }   
}