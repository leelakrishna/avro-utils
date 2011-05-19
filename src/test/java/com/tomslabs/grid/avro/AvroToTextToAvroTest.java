/*
 * Copyright 2011, Bestofmedia, Inc.
 * 
 * Bestofmedia licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.tomslabs.grid.avro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.avro.file.DataFileReader;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.CounterGroup;
import org.apache.hadoop.mapreduce.Job;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AvroToTextToAvroTest extends HadoopTestBase {

    private File tempDir;
    private File inputDir;
    private File outputDir;

    @Before
    public void setup() {
        tempDir = FileUtils.generateTempBaseDir();
        inputDir = FileUtils.generateTempDir(tempDir);
        outputDir = FileUtils.generateTempDir(tempDir);
    }

    @After
    public void tearDown() throws IOException {
        FileUtils.deleteRecursively(tempDir);
    }

    @Test
    public void testMapReduce() throws Throwable {
        FileSystem fs = FileSystem.get(localConf);
        String inputFile = AvroIOUtils.createAvroInputFile(inputDir, "foo", "foo", "bar", "baz", "foo", "baz");
        AvroIOUtils.dumpAvroFiles(inputDir);
        Path input = localFileToPath(inputFile).getParent();
        Path countOutput = new Path(outputDir.getAbsolutePath());
        fs.delete(countOutput, true);

        Job countJob = AvroToTextToAvro.createSubmitableJob(localConf, input, countOutput);
        assertTrue("count job failed", countJob.waitForCompletion(true));

        CounterGroup group = countJob.getCounters().getGroup("org.apache.hadoop.mapred.Task$Counter");
        assertEquals("Wrong number of mapper input records", 6, group.findCounter("MAP_INPUT_RECORDS").getValue());
        assertEquals("Wrong number of mapper output records", 6, group.findCounter("MAP_OUTPUT_RECORDS").getValue());
        assertEquals("Wrong number of reduce output records", 3, group.findCounter("REDUCE_OUTPUT_RECORDS").getValue());

        AvroIOUtils.dumpAvroFiles(outputDir);

        List<String> res = readOutput(outputDir);
        assertEquals(3, res.size());

        assertTrue(res.contains("foo"));
        assertTrue(res.contains("bar"));
        assertTrue(res.contains("baz"));

    }

    private static List<String> readOutput(File dir) throws Throwable {
        List<String> results = new ArrayList<String>();
        
        if (dir.isDirectory()) {
            for (File f : dir.listFiles()) {
                if (f.getName().endsWith(".avro")) {
                    fill(f, results);
                }
            }
        }

        return results;
    }

    private static void fill(File file, List<String> results) throws Throwable {
        DataFileReader<GenericRecord> reader = new DataFileReader<GenericRecord>(file, new GenericDatumReader<GenericRecord>());
        while (reader.hasNext()) {
            GenericRecord record = reader.next();
            String word = record.get("word").toString();
            results.add(word);
        }
    }
}
