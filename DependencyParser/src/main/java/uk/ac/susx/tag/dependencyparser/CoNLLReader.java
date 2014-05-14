package uk.ac.susx.tag.dependencyparser;

import uk.ac.susx.tag.dependencyparser.datastructures.Token;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

/**
 * Class for reading in vaguely CoNLL-esque data. It's pretty flexible on the matter, but here are the rules:
 *
 *  - Token per line
 *  - Sentences separated by blank lines
 *  - Each token line consists of a list of attributes for that token
 *  - The attributes should be in the same order for each token, and always present. If you really must have null values
 *    then don't use a single underscore as a null value. (this is where we depart from CoNLL, which uses underscores as null values)
 *  - Each attribute should be separated by any whitespace (no whitespace allowed inside attributes)
 *
 *  The format string that you pass to the reader is a comma separated list of the attributes in the order that should be
 *  expected for each token.
 *
 *  Say my data looks like this :
 *
 *   1    dogs    N
 *   2    hate    V
 *   3    cats    N
 *
 *  Then my format string would look like:   id, form, pos
 *    (with or without spaces)
 *
 *  Even if (like often is the case in CoNLL datasets) there were more attributes but you're ignoring them with
 *  underscores, the same format would still be appropriate:
 *
 *   1    dogs   _    N
 *   2    hate   _    V
 *   3    cats   _    N
 *
 *  There's an attribute we're ignoring using a single underscore. The format string "id, form, pos" is still appropriate.
 *  Any single underscores separated by whitespace will be ignored as if they were never seen.
 *
 *  "id" is a reserved word. And if your format doesn't specify IDs, then they'll be added to the tokens for you.
 *
 *  "head" and "deprel" are reserved words for listing the gold standard dependency relations for tokens. If your
 *  format includes these terms then those attributes will automatically be loaded into the Token object's "goldHead" and
 *  "goldDeprel" fields (so that the parser cannot cheat during prediction and/or the information is available during training).
 *
 * Created by Andrew D. Robertson on 16/04/2014.
 */
public class CoNLLReader implements AutoCloseable, Iterator<List<Token>>{

    private static final Pattern formatSplitter = Pattern.compile("\\s*,\\s*");
    private static final Pattern tokenLineSplitter = Pattern.compile("\\s+([_]\\s+)*");
    private static final Pattern trailingUnderscores = Pattern.compile("(\\s+[_])*\\s+[_]$");

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
    private void  readNextSentence() throws IOException {
        boolean inSentence = false;
        List<Token> sentence = new ArrayList<>();
        int id = 1;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().length() > 0) {
                Map<String, String> attributes = new HashMap<>();
                String[] items = tokenLineSplitter.split(trailingUnderscores.matcher(line.trim()).replaceAll(""));
                if (items.length < format.length) throw new RuntimeException("The CoNLL format specified suggests that there are more token attributes than there are");
                if (items.length > format.length) throw new RuntimeException("The CoNLL format specified suggests there there are fewer attributes than there are");
                for (int i = 0; i < format.length; i++) {
                    attributes.put(format[i], items[i]);
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
