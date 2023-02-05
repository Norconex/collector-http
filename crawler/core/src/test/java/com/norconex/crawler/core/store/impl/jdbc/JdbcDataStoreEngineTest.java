/* Copyright 2021-2023 Norconex Inc.
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
package com.norconex.crawler.core.store.impl.jdbc;

import java.net.MalformedURLException;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.core.store.AbstractDataStoreEngineTest;
import com.norconex.crawler.core.store.DataStoreEngine;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class JdbcDataStoreEngineTest extends AbstractDataStoreEngineTest {


    @Override
    protected DataStoreEngine createEngine() {
        try {
            var connStr = "jdbc:h2:file:" + StringUtils.removeStart(
                    getTempDir().toUri().toURL() + "test", "file:/");
            LOG.info("Creating new JDBC data store engine using: {}", connStr);
            var engine = new JdbcDataStoreEngine();
            var cfg = new Properties();
            cfg.add("jdbcUrl", connStr);
            engine.setConfigProperties(cfg);
            return engine;
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}
