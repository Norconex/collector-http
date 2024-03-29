/* Copyright 2015-2023 Norconex Inc.
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
package com.norconex.crawler.web.fetch.util;

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;

class GenericRedirectUrlProviderTest {

    @Test
    void testWriteRead() {
        var p = new GenericRedirectUrlProvider();
        p.setFallbackCharset(StandardCharsets.UTF_8.toString());
        assertThatNoException().isThrownBy(() ->
                BeanMapper.DEFAULT.assertWriteRead(p));
    }
}
