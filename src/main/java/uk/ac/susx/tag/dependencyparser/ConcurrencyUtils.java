package uk.ac.susx.tag.dependencyparser;

/*
 * #%L
 * ConcurrencyUtils.java - dependencyparser - CASM Consulting - 2,014
 * %%
 * Copyright (C) 2014 CASM Consulting
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import uk.ac.susx.tag.dependencyparser.datastructures.Token;
import uk.ac.susx.tag.dependencyparser.textmanipulation.CoNLLWriter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Static concurrency utilities.
 *
 * User: Andrew D. Robertson
 * Date: 03/06/2014
 * Time: 15:13
 */
public class ConcurrencyUtils {

    /**
     * Utility object for holding a parsed sentence, and an ID assigned to that sentence.
     * For e.g. tracking the order in which sentences are parsed.
     *
     * Natural ordering, equals and hashing all uses the sentence ID.
     */
    public static class ParsedSentence implements Comparable<ParsedSentence>{

        public int id;
        public List<Token> data;

        public ParsedSentence(int id, List<Token> sentence) {
            this.id = id;
            this.data = sentence;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ParsedSentence that = (ParsedSentence)o;
            return id == that.id;
        }

        @Override
        public int hashCode() { return id; }

        @Override
        public int compareTo(ParsedSentence o) {
            if (o==null) throw new NullPointerException();
            return id - o.id;
        }
    }

    /**
     * Consumer-producer, to be simply run in a thread.
     *
     * Monitors a priority queue of ParsedSentences (inputQueue), which is ordered by the ID of the sentences.
     *
     * Fills a second blocking queue (outputQueue) with inputQueue sentences such that the IDs will only ever be in
     * order of execution (like inputQueue) but with the added constraint that no IDs are ever skipped.
     *
     * Example:
     *
     *  If there are 8 sentences to be parsed. And so far, sentences 5, 3, 6, 2 have been parsed and put into the
     *  inputQueue, then the priority nature of the input queue will order them 2, 3, 5, 6. But no sentences will be
     *  passed to the outputQueue by this consumer until sentence 1 is complete. Once sentences 1 is complete, then
     *  it will be taken and placed on the outputQueue, along with 2 and 3 and as many others without skipping any IDs
     *  as possible.
     *
     * TERMINATION: This consumer will terminate when it reads an empty sentence (an empty list of Tokens).
     */
    public static class SentenceConsumerProducerInOriginalOrder implements Runnable {

        private final BlockingQueue<ParsedSentence> outputQueue;
        private final PriorityBlockingQueue<ParsedSentence> inputQueue;
        private int currentID;

        public SentenceConsumerProducerInOriginalOrder(BlockingQueue<ParsedSentence> outputQueue, PriorityBlockingQueue<ParsedSentence> inputQueue) {
            this.outputQueue = outputQueue;
            this.inputQueue = inputQueue;
            currentID = 0;
        }

        public void run() {
            try {
                while(true) {
                    ParsedSentence sentence = inputQueue.take();

                    // If we see an empty sentence, then terminate, and propagate the termination signal to the output queue
                    if (sentence.data.isEmpty()) {
                        outputQueue.put(new ParsedSentence(sentence.id, new ArrayList<Token>())); break;
                    }
                    // If we see the next ID that we're looking for, then forward the sentence on to the output queue
                    else if (sentence.id == currentID) {
                        outputQueue.put(sentence);
                        currentID++;
                    }
                    // Otherwise, the next sentence we're interested in hasn't been parsed, put it back on the queue and wait for a few milliseconds
                    else {
                        inputQueue.put(sentence);
                        Thread.sleep(50);
                    }
                }
            } catch (InterruptedException e) {  e.printStackTrace(); }
        }
    }

    /**
     * Consumer, to be simply run in a thread.
     *
     * Monitors a queue for ParsedSentences. As soon as any are present, they are removed an written to a specified
     * file in a specified format.
     *
     * TERMINATION: This consumer will terminate when it reads an empty sentence (an empty list of Tokens).
     */
    public static class SentenceConsumerToFile implements Runnable {

        private final CoNLLWriter writer;
        private final BlockingQueue<ParsedSentence> queue;

        public SentenceConsumerToFile(BlockingQueue<ParsedSentence> q, File output, String dataFormat) throws IOException {
            queue = q;
            writer = new CoNLLWriter(output, dataFormat);
        }

        public void run() {
            try {
                while(true) {
                    // Block until the queue has a sentence in it
                    ParsedSentence sentence = queue.take();
                    // An empty sentences (empty list of Token objects) tells the thread to terminate
                    if (!sentence.data.isEmpty()) {
                        writer.write(sentence.data);
                    } else break;
                } writer.close();
            } catch (InterruptedException | IOException e) { e.printStackTrace();}
        }
    }
}
