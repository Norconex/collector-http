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
package com.norconex.crawler.web.fetch.impl.webdriver;

import java.awt.Dimension;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.openqa.selenium.By;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.crawler.core.fetch.BaseFetcherConfig;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Configuration for {@link WebDriverHttpFetcher}.
 * </p>
 * @see WebDriverHttpFetcher
 * @since 3.0.0
 */
@Data
@Accessors(chain = true)
public class WebDriverHttpFetcherConfig extends BaseFetcherConfig {

    public enum WaitElementType {
        TAGNAME(By::tagName),
        CLASSNAME(By::className),
        CSSSELECTOR(By::cssSelector),
        ID(By::id),
        LINKTEXT(By::linkText),
        NAME(By::name),
        PARTIALLINKTEXT(By::partialLinkText),
        XPATH(By::xpath);
        private final Function<String, By> byFunction;
        WaitElementType(Function<String, By> byFunction) {
            this.byFunction = byFunction;
        }
        By getBy(String selector) {
            return byFunction.apply(selector);
        }
    }

    private Browser browser = Browser.FIREFOX;
    // Default will try to detect driver installation on OS
    private Path driverPath;
    // Default will try to detect browser installation on OS
    private Path browserPath;
    private URL remoteURL;

    private HttpSniffer httpSniffer;
    private ScreenshotHandler screenshotHandler;

    private final Map<String, String> capabilities = new HashMap<>();

    private Dimension windowSize;

    private String earlyPageScript;
    private String latePageScript;

    private long pageLoadTimeout;
    private long implicitlyWait;
    private long scriptTimeout;
    private long threadWait;

    private WaitElementType waitForElementType;
    private String waitForElementSelector;
    private long waitForElementTimeout;


    public Map<String, String> getCapabilities(
            Map<String, String> capabilities) {
        return Collections.unmodifiableMap(capabilities);
    }
    public WebDriverHttpFetcherConfig setCapabilities(
            Map<String, String> capabilities) {
        CollectionUtil.setAll(this.capabilities, capabilities);
        return this;
    }
}