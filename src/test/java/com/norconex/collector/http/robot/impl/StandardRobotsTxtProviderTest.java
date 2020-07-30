/* Copyright 2010-2019 Norconex Inc.
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
package com.norconex.collector.http.robot.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.collector.core.filter.IReferenceFilter;
import com.norconex.collector.core.filter.impl.RegexReferenceFilter;
import com.norconex.collector.http.robot.IRobotsTxtFilter;

/**
 * @author Pascal Essiembre
 */
public class StandardRobotsTxtProviderTest {

    @Test
    public void testGetRobotsTxt() throws IOException {
        String robotTxt1 =
                "User-agent: *\n"
              + "Disallow: /dontgo/there/\n"
              + "User-agent: mister-crawler\n"
              + "Disallow: /bathroom/\n"
              + "User-agent: miss-crawler\n"
              + "Disallow: /tvremote/\n";
        String robotTxt2 =
                " User-agent : mister-crawler \n"
              + "  Disallow : /bathroom/ \n"
              + "   User-agent : * \n"
              + "    Disallow : /dontgo/there/ \n";
        String robotTxt3 =
                "User-agent: miss-crawler\n"
              + "Disallow: /tvremote/\n"
              + "User-agent: *\n"
              + "Disallow: /dontgo/there/\n";
        String robotTxt4 =
                "User-agent: miss-crawler\n"
              + "Disallow: /tvremote/\n"
              + "User-agent: *\n"
              + "Disallow: /dontgo/there/\n"
              + "User-agent: mister-crawler\n"
              + "Disallow: /bathroom/\n";
        String robotTxt5 =
                "# robots.txt\n"
              + "User-agent: *\n"
              + "Disallow: /some/fake/ # Spiders, keep out! \n"
              + "Disallow: /spidertrap/\n"
              + "Allow: /open/\n"
              + " Allow : / \n";
        // An empty Disallow means allow all.
        // Test made for https://github.com/Norconex/collector-http/issues/129
        // Standard: https://en.wikipedia.org/wiki/Robots_exclusion_standard
        String robotTxt6 =
                "User-agent: *\n\n"
              + "Disallow: \n\n";

        // Make sure trailing comments do not throw it off.
        String robotTxt7 =
                "User-agent: *\n\n"
              + "Disallow: # allow all\n\n";


        assertStartsWith("Robots.txt -> Disallow: /bathroom/",
                parseRobotRule("mister-crawler", robotTxt1).get(1));
        assertStartsWith("Robots.txt -> Disallow: /bathroom/",
                parseRobotRule("mister-crawler", robotTxt2).get(0));
        assertStartsWith("Robots.txt -> Disallow: /dontgo/there/",
                parseRobotRule("mister-crawler", robotTxt3).get(0));
        assertStartsWith("Robots.txt -> Disallow: /bathroom/",
                parseRobotRule("mister-crawler", robotTxt4).get(1));

        assertStartsWith("Robots.txt -> Disallow: /some/fake/",
                parseRobotRule("mister-crawler", robotTxt5).get(0));
        assertStartsWith("Robots.txt -> Disallow: /spidertrap/",
                parseRobotRule("mister-crawler", robotTxt5).get(1));
        assertStartsWith("Robots.txt -> Allow: /open/",
                parseRobotRule("mister-crawler", robotTxt5).get(2));
        Assertions.assertEquals(3,
                parseRobotRule("mister-crawler", robotTxt5).size());

        Assertions.assertTrue(
                parseRobotRule("mister-crawler", robotTxt6).isEmpty());
        Assertions.assertTrue(
                parseRobotRule("mister-crawler", robotTxt7).isEmpty());
    }

    @Test
    public void testWildcardPattern() throws IOException {
        String robotTxt =
                "User-agent: *\n\n"
              + "Disallow: /testing/*/wildcards\n";
        IReferenceFilter rule =
                parseRobotRule("mister-crawler", robotTxt).get(0);

        assertMatch(
                "http://www.test.com/testing/some/random/path/wildcards", rule);
        assertMatch(
                "http://www.test.com/testing/some/random/path/wildcards/test",
                rule);

        assertNoMatch("http://www.test.com/testing/wildcards", rule);
        assertNoMatch("http://www.test.com/wildcards", rule);
    }

    @Test
    public void testStringEndPattern() throws IOException {
        String robotTxt =
                "User-agent: *\n\n"
              + "Disallow: /testing/anchors$\n";
        IReferenceFilter rule =
                parseRobotRule("mister-crawler", robotTxt).get(0);

        assertMatch("http://www.test.com/testing/anchors", rule);
        assertMatch("http://www.test.com/testing/anchors/", rule);

        assertNoMatch("http://www.test.com/testing/anchors/test", rule);
        assertNoMatch("http://www.test.com/randomly/testing/anchors", rule);
    }

    @Test
    public void testRegexEscape() throws IOException {
        String robotTxt =
                "User-agent: *\n\n"
              + "Disallow: /testing/reg.ex/escape?\n";
        IReferenceFilter rule =
                parseRobotRule("mister-crawler", robotTxt).get(0);

        assertMatch("http://www.test.com/testing/reg.ex/escape?", rule);
        assertMatch("http://www.test.com/testing/reg.ex/escape?test", rule);

        assertNoMatch("http://www.test.com/testing/reggex/escape?", rule);
        assertNoMatch("http://www.test.com/testing/reggex/escape?test", rule);
        assertNoMatch("http://www.test.com/testing/reg*ex/escape?", rule);
        assertNoMatch("http://www.test.com/testing/reg*ex/escape?test", rule);
    }

    private void assertStartsWith(
            String startsWith, IReferenceFilter robotRule) {
        String rule = StringUtils.substring(
                robotRule.toString(), 0, startsWith.length());
        Assertions.assertEquals(startsWith, rule);
    }

    private void assertMatch(
            String url, IReferenceFilter robotRule, Boolean match) {
        RegexReferenceFilter regexFilter = (RegexReferenceFilter) robotRule;
        Assertions.assertEquals(
                match,
                url.matches(regexFilter.getRegex()));
    }

    private void assertMatch(
            String url, IReferenceFilter robotRule) {
        assertMatch(url, robotRule, true);
    }

    private void assertNoMatch(
            String url, IReferenceFilter robotRule) {
        assertMatch(url, robotRule, false);
    }

    private List<IRobotsTxtFilter> parseRobotRule(
            String agent, String content, String url) throws IOException {
        StandardRobotsTxtProvider robotProvider =
                new StandardRobotsTxtProvider();
        return robotProvider.parseRobotsTxt(
                IOUtils.toInputStream(content, StandardCharsets.UTF_8),
                url, "mister-crawler").getFilters();
    }

    private List<IRobotsTxtFilter> parseRobotRule(String agent, String content)
            throws IOException {
        return parseRobotRule(agent, content,
                "http://www.test.com/some/fake/url.html");
    }
}
