/* Copyright 2020-2023 Norconex Inc.
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
package com.norconex.committer.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.map.ListOrderedMap;

import com.norconex.commons.lang.map.PropertyMatcher;
import com.norconex.commons.lang.map.PropertyMatchers;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * A base implementation taking care of basic plumbing, such as
 * firing main Committer events (including exceptions),
 * storing the Committer context (available via {@link #getCommitterContext()}),
 * and adding support for filtering unwanted requests.
 * </p>
 *
 * {@nx.block #restrictTo
 * <h3>Restricting committer to specific documents</h3>
 * <p>
 * Optionally apply a committer only to certain type of documents.
 * Documents are restricted based on their
 * metadata field names and values. This option can be used to
 * perform document routing when you have multiple committers defined.
 * </p>
 * }
 *
 * {@nx.block #fieldMappings
 * <h3>Field mappings</h3>
 * <p>
 * By default, this abstract class applies field mappings for metadata fields,
 * but leaves the document reference and content (input stream) for concrete
 * implementations to handle. In other words, they only apply to
 * a committer request metadata.
 * Field mappings are performed on committer requests before upserts and
 * deletes are actually performed.
 * </p>
 * }
 *
 * {@nx.xml.usage
 * <!-- multiple "restrictTo" tags allowed (only one needs to match) -->
 * <restrictTo>
 *   <fieldMatcher
 *     {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *       (field-matching expression)
 *   </fieldMatcher>
 *   <valueMatcher
 *     {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *       (value-matching expression)
 *   </valueMatcher>
 * </restrictTo>
 * <fieldMappings>
 *   <!-- Add as many field mappings as needed -->
 *   <mapping fromField="(source field name)" toField="(target field name)"/>
 * </fieldMappings>
 * }
 * <p>
 * Implementing classes inherit the above XML configuration.
 * </p>
 */
@SuppressWarnings("javadoc")
@Data
@Accessors(chain = true)
public class BaseCommitterConfig {

    private final PropertyMatchers restrictions = new PropertyMatchers();
    private final Map<String, String> fieldMappings = new ListOrderedMap<>();

    /**
     * Adds one restriction this committer should be restricted to.
     * @param restriction the restriction
     */
    public BaseCommitterConfig addRestriction(PropertyMatcher restriction) {
        restrictions.add(restriction);
        return this;
    }
    /**
     * Adds restrictions this committer should be restricted to.
     * @param restrictions the restrictions
     */
    public BaseCommitterConfig addRestrictions(
            List<PropertyMatcher> restrictions) {
        if (restrictions != null) {
            this.restrictions.addAll(restrictions);
        }
        return this;
    }
    /**
     * Removes all restrictions on a given field.
     * @param field the field to remove restrictions on
     * @return how many elements were removed
     */
    public int removeRestriction(String field) {
        return restrictions.remove(field);
    }
    /**
     * Removes a restriction.
     * @param restriction the restriction to remove
     * @return <code>true</code> if this committer contained the restriction
     */
    public boolean removeRestriction(PropertyMatcher restriction) {
        return restrictions.remove(restriction);
    }
    /**
     * Clears all restrictions.
     */
    public void clearRestrictions() {
        restrictions.clear();
    }
    /**
     * Gets all restrictions
     * @return the restrictions
     */
    public PropertyMatchers getRestrictions() {
        return restrictions;
    }

    /**
     * Gets an unmodifiable copy of the metadata mappings.
     * @return metadata mappings
     */
    public Map<String, String> getFieldMappings() {
        return Collections.unmodifiableMap(fieldMappings);
    }
    /**
     * Sets a metadata field mapping.
     * @param fromField source field
     * @param toField target field
     */
    public BaseCommitterConfig setFieldMapping(
            String fromField, String toField) {
        fieldMappings.put(fromField, toField);
        return this;
    }
    /**
     * Sets a metadata field mappings, where the key is the source field and
     * the value is the target field.
     * @param mappings metadata field mappings
     */
    public BaseCommitterConfig setFieldMappings(Map<String, String> mappings) {
        fieldMappings.putAll(mappings);
        return this;
    }
    public String removeFieldMapping(String fromField) {
        return fieldMappings.remove(fromField);
    }
    public void clearFieldMappings() {
        fieldMappings.clear();
    }
}