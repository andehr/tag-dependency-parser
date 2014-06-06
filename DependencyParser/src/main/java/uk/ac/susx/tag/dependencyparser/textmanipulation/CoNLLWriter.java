package uk.ac.susx.tag.dependencyparser.textmanipulation;

import com.google.common.base.Joiner;
import uk.ac.susx.tag.dependencyparser.datastructures.Token;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Mostly used for evaluation purposes.
 *
 * Will output in the format expected by CoNLL shared task (by default 2009).
 * Mostly used for comparing performance against the WSJ.
 *
 * Though you can specify your own output format.
 *
 * The specification is a comma separated list of attributes that should appear for each token.
 * Token per line, blank lines separate sentences. Attributes will be separated by tabs.
 *
 * There are some keywords that grab things other than from the attribute map of a token:
 *
 *   id : the ID of the token
 *   deprel : the dependency relation assigned to this token by the parser (or _ if no such relation)
 *   head   : the ID of the head token assigned to this token by the parser (or _ if no such head)
 *   ignore  : a filler, simply prints an underscore (effectively providing an ignored attribute, perhaps
 *             for subsequent processors to fill for example)
 *
 * CONVERSION: The Parser class has a method for using a CoNLL Reader and Writer to convert between formats.
 *
 * USAGE NOTE: Best usage is probably with Java's try-with-resources, see example usage in Parser.parseFile()
 *             Otherwise, you'll need to manually close the writer.
 *
 * User: Andrew D. Robertson
 * Date: 24/04/2014
 * Time: 11:28
 */
public class CoNLLWriter implements AutoCloseable {

    private static final Pattern formatSplitter = Pattern.compile("\\s*,\\s*");
    private BufferedWriter writer;
    private String[] format;

    public CoNLLWriter (File output) throws IOException {
        this(output, "id, form, ignore, ignore, pos, ignore, ignore, ignore, head, ignore, deprel, ignore, ignore, ignore");
    }

    public CoNLLWriter (File output, String outputFormat) throws IOException {
        if (!output.exists())
            output.createNewFile();
        writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), "UTF-8"));
        this.format = formatSplitter.split(outputFormat.toLowerCase());
    }

    /**
     * Write a sentence to the opened file using the format specified.
     */
    public void write(List<Token> sentence) throws IOException {
        for (Token token : sentence) {
            writer.write(formatToken(token, format));
            writer.write("\n");
        } writer.write("\n");
    }

    /**
     * Given a token, and a format specification, produce a string representing that token.
     *
     * Usage doesn't typically involve this method; its called internally. However, I anticipate that this functionality
     * may be useful elsewhere.
     */
    public static String formatToken(Token token, String[] outputFormat) {
        List<String> attributes = new ArrayList<>();
        for (String attributeType : outputFormat){
            if (attributeType.equals("ignore")){
                attributes.add("_");
            } else if (attributeType.equals("id")) {
                attributes.add(Integer.toString(token.getID()));
            } else if (attributeType.equals("deprel")) {
                String deprel = token.getDeprel();
                attributes.add(deprel==null? "_" : deprel);
            } else if (attributeType.equals("head")) {
                String head = token.getHead()==null? "_" : Integer.toString(token.getHeadID());
                attributes.add(head);
            } else if (token.hasAtt(attributeType)) {
                attributes.add(token.getAtt(attributeType));
            } else throw new RuntimeException("Output format specifies an attribute that the token does not possess. Token: "+token+", attribute: "+attributeType);
        }
        return Joiner.on("\t").join(attributes);
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
