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
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.EqualsExclude;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.HashCodeExclude;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringExclude;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.google.api.client.http.InputStreamContent;
import com.google.api.services.cloudsearch.v1.model.Item;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.enterprise.cloudsearch.sdk.config.Configuration;
import com.google.enterprise.cloudsearch.sdk.indexing.IndexingItemBuilder;
import com.google.enterprise.cloudsearch.sdk.indexing.IndexingItemBuilder.FieldOrValue;
import com.google.enterprise.cloudsearch.sdk.indexing.IndexingService;
import com.google.enterprise.cloudsearch.sdk.indexing.IndexingService.ContentFormat;
import com.google.enterprise.cloudsearch.sdk.indexing.IndexingService.RequestMode;
import com.google.enterprise.cloudsearch.sdk.indexing.IndexingServiceImpl;
import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.batch.AbstractBatchCommitter;
import com.norconex.commons.lang.xml.XML;

import lombok.extern.slf4j.Slf4j;

/**
 * Commits documents to Google Cloud Search using the Google Cloud Search
 * Connector SDK.
 *
 * <h3>Configuration</h3>
 *
 * <p>
 * The committer is using the Google Cloud SearchConnector SDK to communicate 
 * with the Google Cloud Search API, therefore a valid Connector SDK 
 * configuration file is required. The location of this file must be set via the
 * {@link #configFilePath} property.
 * 
 * The configuration file must contain these 2 entries.
 * {@value IndexingServiceImpl#SOURCE_ID}, and 
 * api.serviceAccountPrivateKeyFile
 * 
 * An example configuration file can be found in the 
 * <a href="https://developers.google.com/cloud-search/docs/reference/connector-configuration#configuration-file-example">
 * Connector SDK Documentation</a>
 *
 * <h3>XML configuration usage</h3>
 *
 * <committer class="GoogleCloudSearchCommitter">
 *   <configFilePath>/path/to/config</configFilePath>
 * </committer>
 * }
 * 
 * @author Harinder Hanjan
 */

@SuppressWarnings("javadoc")
@Slf4j
public class GoogleCloudSearchCommitter extends AbstractBatchCommitter {

    private static final String CONFIG_KEY_CONFIG_FILE = "configFilePath";
    private static final String TARGET_PRODUCT_NAME = "Google Cloud Search";
    private static final String FIELD_LAST_MODIFIED = "Last-Modified";
    private static final String FIELD_CONTENT_TYPE = "document.contentType";
    private static final String FIELD_TITLE = "title";
    
    @ToStringExclude
    @HashCodeExclude
    @EqualsExclude
    private IndexingService indexingService;
    
    private String configFilePath;
    
    /**
     * Gets the Path to the Connector SDK configuration file
     * 
     * @return Path to the Connector SDK configuration file
     */
    public String getConfigFilePath() {
        return configFilePath;
    }

    /**
     * Sets the Connector SDK config file path
     * 
     * @param   path    Path to the Connector SDK configuration file
     */
    public void setConfigFilePath(String path) {
        configFilePath = path;
    }

    @Override
    protected void initBatchCommitter() throws CommitterException {
        if (StringUtils.isBlank(configFilePath)) {
            throw new CommitterException(
                    "You must specify the path to a Google Connector SDK"
                    + "configuration file via the " + CONFIG_KEY_CONFIG_FILE + 
                    " parameter.");
        }
        
        //TODO: Ensure the config file is valid
    }

    @Override
    protected void commitBatch(Iterator<CommitterRequest> it)
            throws CommitterException {

        try {
            createAndStartIndexingService();
        } catch (IOException | GeneralSecurityException e) {
            throw new CommitterException("Error starting Indexing Service.", e);
        }
        
        var docCountUpserts = 0;
        var docCountDeletes = 0;
        try {
            while (it.hasNext()) {
                var req = it.next();
                if (req instanceof UpsertRequest upsert) {
                    addItem(upsert);                    
                    docCountUpserts++;
                    
                } else if (req instanceof DeleteRequest delete) {
                    deleteItem(delete);                    
                    docCountDeletes++;
                    
                } else {
                    throw new CommitterException("Unsupported request: " + req);
                }
            }

            if(docCountUpserts > 0) {
                LOG.info("Sent {} upsert commit operation(s) to {}.",
                        docCountUpserts,
                        TARGET_PRODUCT_NAME);
            }
            
            if(docCountDeletes> 0) {
                LOG.info(
                        "Sent {} delete commit operation(s) to {}.", 
                        docCountDeletes,
                        TARGET_PRODUCT_NAME);
            }
        } catch (CommitterException e) {
            throw e;
        } catch (Exception e) {
            throw new CommitterException(
                    "Could not commit JSON batch to " + TARGET_PRODUCT_NAME, e);
        } 
//        finally {
//            // Shutdown IndexingService, flush remaining batch queue
//            try {
//                close();
//            } catch (CommitterException e) {
//                LOG.error("Unable to shutdown IndexingService. ", e);
//            }
//        }
    }

    private void addItem(UpsertRequest upsert) throws IOException {
        
        String contentType = upsert.getMetadata().getString(FIELD_CONTENT_TYPE);
        
        var contentStream = new InputStreamContent(
                contentType,
                upsert.getContent());
        
        indexingService.indexItemAndContent(
                createItem(upsert, contentType),
                contentStream,
                null,
                ContentFormat.TEXT, 
                RequestMode.ASYNCHRONOUS);
        
        LOG.debug("Sent doc `{}` for indexing", upsert.getReference());
    }
    
    private void deleteItem(DeleteRequest delete) throws IOException {        
        indexingService.deleteItem(
                delete.getReference(), 
                String.valueOf(
                        System.currentTimeMillis()).getBytes(
                                StandardCharsets.UTF_8),
                RequestMode.ASYNCHRONOUS);
        
        LOG.debug("Sent doc `{}` for deletion", delete.getReference());
    }
    
    private Item createItem(UpsertRequest upsert, String contentType) {
        Multimap<String, Object> propertiesMap = ArrayListMultimap.create();
        for (Map.Entry<String, List<String>> entry 
                : upsert.getMetadata().entrySet()) {
            propertiesMap.putAll(entry.getKey(), entry.getValue());
        }
        
        return IndexingItemBuilder.fromConfiguration(upsert.getReference())
                .setItemType(IndexingItemBuilder.ItemType.CONTENT_ITEM)
                .setMimeType(FieldOrValue.withValue(contentType))
                .setSourceRepositoryUrl(
                        FieldOrValue.withValue(upsert.getReference()))
                .setValues(propertiesMap)
                .setTitle(FieldOrValue.withField(FIELD_TITLE))
                .setUpdateTime(FieldOrValue.withField(FIELD_LAST_MODIFIED))
                .build();  
    }
    
    private void createAndStartIndexingService() 
            throws IOException, GeneralSecurityException {
        
        if(indexingService != null && indexingService.isRunning()) {
            return;
        }
        
        LOG.info("Starting Indexing Service");
        
        String[] args = { "-Dconfig=" + getConfigFilePath() }; 
        Configuration.initConfig(args);
        
        indexingService = IndexingServiceImpl.Builder
                .fromConfiguration(
                        Optional.empty(), 
                        GoogleCloudSearchCommitter.class.getName())
                .build();
        
        indexingService.startAsync().awaitRunning();
        
        LOG.info("Indexing Service started");
    }

    @Override
    protected void closeBatchCommitter() throws CommitterException {
        LOG.info("Shutting down indexing service...");
        final String done = "Done.";

        if(indexingService == null) {
            LOG.info(done);
            return;
        }

        if(! indexingService.isRunning()) {
            LOG.info(done);
            return;
        }

        indexingService.stopAsync().awaitTerminated();

        super.closeBatchCommitter();

        LOG.info(done);
    }
    
    @Override
    protected void saveBatchCommitterToXML(XML xml) {
        xml.addElement(CONFIG_KEY_CONFIG_FILE, getConfigFilePath());
    }
    
    @Override
    protected void loadBatchCommitterFromXML(XML xml) {
        setConfigFilePath(xml.getString(CONFIG_KEY_CONFIG_FILE, null));
    }
    
    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return new ReflectionToStringBuilder(this,
                ToStringStyle.SHORT_PREFIX_STYLE).toString();
    }
}
