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
package com.norconex.crawler.core.store;

import java.util.Optional;
import java.util.Set;

import com.norconex.crawler.core.crawler.Crawler;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode
@ToString
public class MockDataStoreEngine implements DataStoreEngine {

    private DataStore<?> dataStore = new MockDataStore<>();
    public MockDataStoreEngine() {}
    public MockDataStoreEngine(DataStore<?> dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public void init(Crawler crawler) {
    }
    @Override
    public boolean clean() {
        return false;
    }
    @Override
    public void close() {
    }
    @SuppressWarnings("unchecked")
    @Override
    public <T> DataStore<T> openStore(String name, Class<? extends T> type) {
        return (DataStore<T>) dataStore;
    }
    @Override
    public boolean dropStore(String name) {
        return false;
    }
    @Override
    public boolean renameStore(DataStore<?> dataStore, String newName) {
        return false;
    }
    @Override
    public Set<String> getStoreNames() {
        return null;
    }
    @Override
    public Optional<Class<?>> getStoreType(String name) {
        return Optional.empty();
    }
}
