/*
 * Copyright 2013 Alex Holmes
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

package com.alexholmes.hadooputils.combine.avro.mapred;

import org.apache.avro.Schema;
import org.apache.avro.file.DataFileStream;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.mapred.AvroJob;
import org.apache.avro.mapred.Pair;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.util.Utf8;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.mapred.JobConf;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

/**
 * Created with IntelliJ IDEA.
 * User: aholmes
 * Date: 7/19/13
 * Time: 2:53 AM
 * To change this template use File | Settings | File Templates.
 */
public class WordCountUtil {

    private static final File DIR
            = new File(System.getProperty("test.dir", ".") + "/mapred");
    public static final File LINES_FILE
            = new File(new File(DIR, "in"), "lines.avro");
    private static final File LINES_TEXT_FILE
            = new File(new File(DIR, "in"), "lines.txt");
    public static final File COUNTS_FILE
            = new File(new File(DIR, "out"), "part-00000.avro");
    private static final File SORTED_FILE
            = new File(new File(DIR, "out"), "part-00000.avro");

    public static final String[] LINES = new String[] {
            "the quick brown fox jumps over the lazy dog",
            "the cow jumps over the moon",
            "the rain in spain falls mainly on the plains"
    };

    public static final Map<String,Long> COUNTS =
            new TreeMap<String,Long>();
    static {
        for (String line : LINES) {
            StringTokenizer tokens = new StringTokenizer(line);
            while (tokens.hasMoreTokens()) {
                String word = tokens.nextToken();
                long count = COUNTS.containsKey(word) ? COUNTS.get(word) : 0L;
                count++;
                COUNTS.put(word, count);
            }
        }
    }

    public static void writeLinesFile() throws IOException {
        FileUtil.fullyDelete(DIR);
        DatumWriter<Utf8> writer = new GenericDatumWriter<Utf8>();
        DataFileWriter<Utf8> out = new DataFileWriter<Utf8>(writer);
        LINES_FILE.getParentFile().mkdirs();
        out.create(Schema.create(Schema.Type.STRING), LINES_FILE);
        for (String line : LINES)
            out.append(new Utf8(line));
        out.close();
    }

    public static void writeLinesBytesFile() throws IOException {
        FileUtil.fullyDelete(DIR);
        DatumWriter<ByteBuffer> writer = new GenericDatumWriter<ByteBuffer>();
        DataFileWriter<ByteBuffer> out = new DataFileWriter<ByteBuffer>(writer);
        LINES_FILE.getParentFile().mkdirs();
        out.create(Schema.create(Schema.Type.BYTES), LINES_FILE);
        for (String line : LINES)
            out.append(ByteBuffer.wrap(line.getBytes("UTF-8")));
        out.close();
    }

    public static void writeLinesTextFile() throws IOException {
        FileUtil.fullyDelete(DIR);
        LINES_FILE.getParentFile().mkdirs();
        PrintStream out = new PrintStream(LINES_TEXT_FILE);
        for (String line : LINES)
            out.println(line);
        out.close();
    }

    public static void validateCountsFile() throws Exception {
        DatumReader<Pair<Utf8,Long>> reader
                = new SpecificDatumReader<Pair<Utf8,Long>>();
        InputStream in = new BufferedInputStream(new FileInputStream(COUNTS_FILE));
        DataFileStream<Pair<Utf8,Long>> counts
                = new DataFileStream<Pair<Utf8,Long>>(in,reader);
        int numWords = 0;
        for (Pair<Utf8,Long> wc : counts) {
            assertEquals(wc.key().toString(),
                    COUNTS.get(wc.key().toString()), wc.value());
            numWords++;
        }
        checkMeta(counts);
        in.close();
        assertEquals(COUNTS.size(), numWords);
    }

    public static void validateSortedFile() throws Exception {
        DatumReader<ByteBuffer> reader = new GenericDatumReader<ByteBuffer>();
        InputStream in = new BufferedInputStream(
                new FileInputStream(SORTED_FILE));
        DataFileStream<ByteBuffer> lines =
                new DataFileStream<ByteBuffer>(in,reader);
        List<String> sortedLines = new ArrayList<String>();
        for (String line : LINES) {
            sortedLines.add(line);
        }
        Collections.sort(sortedLines);
        for (String expectedLine : sortedLines) {
            ByteBuffer buf = lines.next();
            byte[] b = new byte[buf.remaining()];
            buf.get(b);
            assertEquals(expectedLine, new String(b, "UTF-8").trim());
        }
        assertFalse(lines.hasNext());
    }

    // metadata tests
    private static final String STRING_KEY = "string-key";
    private static final String LONG_KEY = "long-key";
    private static final String BYTES_KEY = "bytes-key";

    private static final String STRING_META_VALUE = "value";
    private static final long LONG_META_VALUE = 666;
    private static final byte[] BYTES_META_VALUE
            = new byte[] {(byte)0x00, (byte)0x80, (byte)0xff};

    public static void setMeta(JobConf job) {
        AvroJob.setOutputMeta(job, STRING_KEY, STRING_META_VALUE);
        AvroJob.setOutputMeta(job, LONG_KEY, LONG_META_VALUE);
        AvroJob.setOutputMeta(job, BYTES_KEY, BYTES_META_VALUE);
    }

    public static void checkMeta(DataFileStream<?> in) throws Exception {
        assertEquals(STRING_META_VALUE, in.getMetaString(STRING_KEY));
        assertEquals(LONG_META_VALUE, in.getMetaLong(LONG_KEY));
        assertTrue(Arrays.equals(BYTES_META_VALUE, in.getMeta(BYTES_KEY)));
    }

}

