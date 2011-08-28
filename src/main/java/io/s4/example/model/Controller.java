/*
 * Copyright (c) 2011 The S4 Project, http://s4.io.
 * All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *          http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the
 * License. See accompanying LICENSE file. 
 */
package io.s4.example.model;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import org.slf4j.Logger;

import com.google.inject.Inject;
import com.google.inject.name.Named;

/*
 * Train a classifier, run a test, compute the accuracy of the classifier.
 */
public class Controller {

    private static final Logger logger = LoggerFactory
            .getLogger(Controller.class);

    final private String trainFilename;
    final private String testFilename;
    final private long numTrainVectors;
    final private long numTestVectors;
    private int vectorSize;
    private int numClasses;

    @Inject
    public Controller(@Named("model.train_data") String trainFilename,
            @Named("model.test_data") String testFilename,
            @Named("model.logger.level") String logLevel) {

        this.trainFilename = trainFilename;
        this.testFilename = testFilename;
        this.numTrainVectors = getNumLines(trainFilename);
        this.numTestVectors = getNumLines(testFilename);

        logger.info("Number of test vectors is " + numTestVectors);
        logger.info("Number of train vectors is " + numTrainVectors);
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory
                .getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.toLevel(logLevel));
    }

    public void start() {

        logger.info("Processing file: " + trainFilename);
        try {

            /* Get vector size and number of classes from data set. */
            getDataSetInfo(trainFilename);

            MyApp app = new MyApp(numClasses, vectorSize, numTrainVectors);

            logger.info("Init app.");
            app.init();

            /* Initialize modelPEs by injecting events. */
            for (int i = 0; i < numClasses; i++) {
                ObsEvent obsEvent = new ObsEvent(-1, new float[vectorSize],
                        -1.0f, i, -1, true);
                app.injectByKey(obsEvent);
            }

            // TODO this is temporary until we have a direct REST API to PEs.
            logger.info("WAITING 10 seconds");
            Thread.sleep(10000);
            logger.info("Created model PEs.");

            /* For now we only need one iteration. */
            for (int i = 0; i < 1; i++) {
                logger.info("Starting iteration {}.", i);
                injectData(app, true, trainFilename);

                /*
                 * Make sure all the data has been processed. ModelPE will reset
                 * the total count after all the data is processed so we wait
                 * until the count is equal to zero. TODO
                 */
                logger.info("WAITING 10 seconds");
                Thread.sleep(10000);
                logger.info("End of iteration {}.", i);
            }

            // while count not zero wait
            /* Start testing. */
            logger.info("Start testing.");
            injectData(app, false, testFilename);

            logger.info("WAITING 10 seconds");
            Thread.sleep(10000);
            
            /* Done. */
            app.remove();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            logger.error(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            logger.error(e.getMessage());
        } catch (InterruptedException e) {
            e.printStackTrace();
            logger.error(e.getMessage());
        }
    }

    private void injectData(MyApp app, boolean isTraining, String filename)
            throws FileNotFoundException, IOException {

        DataFile data = new DataFile(filename);
        int count = 0;
        for (String line : data) {

            String[] result = line.split("\\s");

            /* Class ID range starts in 1, shift to start in zero. */
            int classID = Integer.parseInt(result[0]) - 1;

            float[] vector = new float[vectorSize];
            for (int j = 0; j < vectorSize; j++) {

                vector[j] = Float.parseFloat(result[j + 1]);
            }
            ObsEvent obsEvent = new ObsEvent(count++, vector, -1.0f, classID,
                    -1, isTraining);
            app.injectToAll(obsEvent);
        }
        data.close();
    }

    private void getDataSetInfo(String filename) throws FileNotFoundException,
            IOException {

        Map<Integer, Long> countsPerClass = new HashMap<Integer, Long>();

        DataFile data = new DataFile(filename);

        for (String line : data) {

            String[] result = line.split("\\s");

            /* Format is: label val1 val2 ... valN */
            if (vectorSize == 0) {
                vectorSize = result.length - 1;
            }

            /* Class ID range starts in 1, shift to start in zero. */
            int classID = Integer.parseInt(result[0]) - 1;

            /* Count num vectors per class. */
            if (!countsPerClass.containsKey(classID)) {
                countsPerClass.put(classID, 1L);
            } else {
                long count = countsPerClass.get(classID) + 1;
                countsPerClass.put(classID, count);
            }
        }
        data.close();

        /* Summary. */
        numClasses = countsPerClass.size();
        logger.info("Number of classes is " + numClasses);
        logger.info("Vector size is " + vectorSize);

        for (Map.Entry<Integer, Long> entry : countsPerClass.entrySet()) {

            int key = entry.getKey();
            long val = entry.getValue();

            logger.info("Num vectors for class ID: " + key + " is " + val);
        }
    }

    /*
     * @return Returns the number of lines in a text file.
     */
    private long getNumLines(String filename) {

        long count = 0;
        try {
            DataFile data = new DataFile(filename);

            for (@SuppressWarnings("unused")
            String line : data) {
                count++;
            }
            data.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return count;
    }
}
