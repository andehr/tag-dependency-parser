package uk.ac.susx.tag.dependencyparser.textmanipulation;

/*
 * #%L
 * CoNLLReader.java - dependencyparser - CASM Consulting - 2,014
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

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Class for reading in CoNLL-esque data. Here are the rules:
 *
 *  - Token per line
 *  - Sentences separated by blank lines
 *  - Each token line consists of a list of attributes for that token
 *  - Same number of attributes for each token (if you want to use a null value for features that don't have a particular
 *    attribute, probably best to copy the null value used by the feature extraction process when it can't find a
 *    particular feature. You can find this in the "absentFeature" field of the FeatureTable class.
 *  - The attributes should be in the same order for each token, and always present.
 *  - Each attribute should be separated by any whitespace (no whitespace allowed inside attributes)
 *
 *  The format string that you pass to the reader is a comma separated list of the attributes in the order that should be
 *  expected for each token.
 *
 *  Say my data looks like this:
 *
 *   1    dogs    N
 *   2    hate    V
 *   3    cats    N
 *
 *  Then my format string would look like:   id, form, pos
 *    (with or without spaces)
 *
 *  If there are attributes that you'd like the parser to ignore  (like the third column of the data below), then
 *  you can use the keyword "ignore":     id, form, ignore, pos
 *
 *   1    dogs   _    N
 *   2    hate   _    V
 *   3    cats   _    N
 *
 *  "id" is a reserved word. And if your format doesn't specify IDs, then they'll be added to the tokens for you
 *  (because they are required by the parsing process).
 *
 *  "head" and "deprel" are reserved words for listing the gold standard dependency relations for tokens. If your
 *  format includes these terms then those attributes will automatically be loaded into the Token object's "goldHead" and
 *  "goldDeprel" fields (so that the parser cannot cheat during prediction and/or the information is available during training).
 *
 * CONVERSION: The Parser class has a method for using a CoNLL Reader and Writer to convert between formats.
 *
 * USAGE NOTE: Best usage is probably with Java's try-with-resources, see example usage in Parser.parseFile()
 *             Otherwise, you'll need to manually close the reader.
 *
 * Created by Andrew D. Robertson on 16/04/2014.
 */
public class CoNLLReader implements AutoCloseable, Iterator<List<Token>>{

    private static final Pattern formatSplitter = Pattern.compile("\\s*,\\s*");
    private static final Pattern tokenLineSplitter = Pattern.compile("\\s+");

    private BufferedReader reader;
    private String[] format;
    private List<Token> nextSentence;
    private boolean idPresent;

    public CoNLLReader(File conllFile) throws IOException {
        this(conllFile, "id, form, pos, head, deprel");
    }

    public CoNLLReader(File conllFile, String format) throws IOException {
        reader = new BufferedReader(new InputStreamReader(new FileInputStream(conllFile), "UTF-8"));
        this.format = formatSplitter.split(format.toLowerCase());
        idPresent = false;
        for (String formatItem : this.format) {
            if (formatItem.equals("id")) {
                idPresent = true; break;
            }
        }
        readNextSentence();
    }


    @Override
    public boolean hasNext() {
        return nextSentence!=null && nextSentence.size() > 0;
    }

    @Override
    public List<Token> next() {
        if (hasNext()) {
            List<Token> toReturn = nextSentence;
            try {
                readNextSentence();
            } catch (IOException e) {
                nextSentence = null;
            }
            return toReturn;
        } else throw new NoSuchElementException();
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }


    @Override
    public void close() throws IOException {
        reader.close();
    }

    /**
     * Always one read ahead of the next() method so that the answer to hasNext() is always quick.
     */
    private void readNextSentence() throws IOException {
        boolean inSentence = false;
        List<Token> sentence = new ArrayList<>();
        int id = 1;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().length() > 0) {
                Map<String, String> attributes = new HashMap<>();
                String[] items = tokenLineSplitter.split(line.trim());
                if (items.length < format.length) throw new RuntimeException("The CoNLL format specified suggests that there are more token attributes than there are.\nLine: " + line);
                if (items.length > format.length) throw new RuntimeException("The CoNLL format specified suggests there there are fewer attributes than there are\nLine: " + line);
                for (int i = 0; i < format.length; i++) {
                    if (!format[i].equals("ignore")) {
                        attributes.put(format[i], items[i]);
                    }
                }
                if (idPresent) { // If the user's format has specified that IDs are already present, then use them
                    int givenID = Integer.parseInt(attributes.get("id"));
                    attributes.remove("id"); // ID is a separate field from a Token's attributes
                    sentence.add(new Token(givenID, attributes)); // Token automatically ensures that "deprel" and "head" attributes are separated out as gold standard annotations
                } else {  // Otherwise assign our own.
                    sentence.add(new Token(id, attributes)); // Token automatically ensures that "deprel" and "head" attributes are separated out as gold standard annotations
                }
                inSentence = true;
                id++;
            } else if (inSentence) break;
        }
        nextSentence = sentence;
        if(line == null) reader.close();
    }
}
