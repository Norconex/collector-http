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
package com.norconex.importer.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.function.FailableBiFunction;

import com.norconex.commons.lang.io.IOUtil;
import com.norconex.commons.lang.io.TextReader;

import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

// Move to nx commons lang?

@RequiredArgsConstructor
public class ReadAdapter {

    private final Supplier<InputStream> inputSupplier;
    private final Charset defaultCharset;

    public ReadAdapter(Supplier<InputStream> inputSupplier) {
        this.inputSupplier = inputSupplier;
        defaultCharset = null;
    }
    public ReadAdapter(InputStream inputStream) {
        inputSupplier = () -> inputStream;
        defaultCharset = null;
    }

    public InputStream asInputStream() {
        return inputSupplier.get();
    }

    public Reader asReader() {
        return asReader(null);
    }
    public Reader asReader(Charset charset) {
        return new InputStreamReader(IOUtil.toNonNullInputStream(
                inputSupplier.get()),
                ObjectUtils.firstNonNull(
                        charset, defaultCharset, StandardCharsets.UTF_8));
    }

    /**
     * Consumes the source as chunks of text.
     * @param textConsumer text chunk consumer
     * @return <code>true</code> if the text was all read, <code>false</code>
     *     if it was aborted by the consumer, without consideration whether
     *     it was all read or not.
     * @throws IOException
     */
    public boolean asChunkedText(
            @NonNull
            FailableBiFunction<Integer, String, Boolean, IOException>
                    textConsumer) throws IOException {
        return asChunkedText(textConsumer, null);
    }
    public boolean asChunkedText(
            @NonNull
            FailableBiFunction<Integer, String, Boolean, IOException>
                    textConsumer,
            ChunkedReadOptions chunkedReadOptions)
                    throws IOException {
        var options = chunkedReadOptions != null
                ? chunkedReadOptions : new ChunkedReadOptions();
        var chunkIndex = 0;
        String text = null;
        var keepReading = false;
        try (var reader = new TextReader(
                asReader(options.charset), options.maxChunkSize)) {
            while ((text = reader.readText()) != null) {
                keepReading = textConsumer.apply(chunkIndex, text);
                chunkIndex++;
                if (!keepReading) {
                    break;
                }
            }
            // should have been incremented at least once if there was content
            if (chunkIndex == 0 && !options.skipEmpty()) {
                textConsumer.apply(chunkIndex, "");
            }
            return keepReading;
        } catch (IOException e) {
            throw new IOException("Cannot read chunked text.", e);
        }
    }

    @Data
    @Accessors(fluent = true)
    public static class ChunkedReadOptions {
        private Charset charset;
        private int maxChunkSize = TextReader.DEFAULT_MAX_READ_SIZE;
        private boolean skipEmpty;
    }
}
