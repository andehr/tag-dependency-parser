package uk.ac.susx.tag.dependencyparser;

import uk.ac.susx.tag.dependencyparser.datastructures.Token;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Created with IntelliJ IDEA.
 * User: Andrew D. Robertson
 * Date: 03/06/2014
 * Time: 15:13
 */
public class ConcurrencyUtils {

    public static class ParsedSentence implements Comparable<ParsedSentence>{
        public int id;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ParsedSentence that = (ParsedSentence) o;

            return id == that.id;
        }

        @Override
        public int hashCode() {
            return id;
        }

        public List<Token> data;

        public ParsedSentence(int id, List<Token> sentence) {
            this.id = id;
            this.data = sentence;
        }

        @Override
        public int compareTo(ParsedSentence o) {
            if (o==null) throw new NullPointerException();
            return id - o.id;
        }
    }

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

                    if (sentence.data.isEmpty()) {
                        outputQueue.put(new ParsedSentence(sentence.id, new ArrayList<Token>())); break;
                    }

                    else if (sentence.id == currentID) {
                        outputQueue.put(sentence);
                        currentID++;
                    }

                    else {
                        inputQueue.put(sentence);
                        Thread.sleep(50);
                    }
                }
            } catch (InterruptedException e) {  e.printStackTrace(); }
        }
    }

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
                    ParsedSentence sentence = queue.take();
                    if (!sentence.data.isEmpty()) {
                        writer.write(sentence.data);
                    } else break;
                } writer.close();
            } catch (InterruptedException | IOException e) { e.printStackTrace();}
        }
    }
}
