package uk.ac.susx.tag.dependencyparser;

import uk.ac.susx.tag.dependencyparser.datastructures.Token;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;

/**
 * Mostly used for evaluation purposes.
 *
 * Will output in the format expect by CoNLL shared task (2009 I think?).
 * Mostly used for comparing performance against the WSJ.
 *
 * TODO: allowing formatting of output, or implement some other sensible writer.
 *
 * User: Andrew D. Robertson
 * Date: 24/04/2014
 * Time: 11:28
 */
public class CoNLLWriter implements AutoCloseable {

    private BufferedWriter writer;

    public CoNLLWriter (File output) throws IOException {
        if (!output.exists())
            output.createNewFile();
        writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), "UTF-8"));
    }

    public void write(List<Token> sentence) throws IOException {
        for (Token token : sentence) {
            writer.write(formatToken(token));
        } writer.write("\n");
    }

    public String formatToken(Token token) {
        StringBuilder sb = new StringBuilder();
        sb.append(token.getID()); sb.append("\t");
        String form = token.getAtt("form");
        String pos = token.getAtt("pos");
        String deprel = token.getDeprel();
        Token head = token.getHead();

        sb.append(form==null? "_" : form); sb.append("\t_\t_\t");
        sb.append(pos==null? "_" : pos); sb.append("\t_\t_\t_\t");
        sb.append(head==null? "_" : head.getID()); sb.append("\t_\t");
        sb.append(deprel==null? "_" : deprel); sb.append("\t_\t_\t_\n");
        return sb.toString();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
