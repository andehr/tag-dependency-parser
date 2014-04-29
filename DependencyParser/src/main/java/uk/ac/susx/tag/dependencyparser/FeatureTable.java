package uk.ac.susx.tag.dependencyparser;

import com.google.common.base.Joiner;
import uk.ac.susx.tag.dependencyparser.datastructures.Token;
import uk.ac.susx.tag.dependencyparser.parserstates.ParserState;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * This class represents the definition of a parser feature model. The specification is laid out in plain text by the
 * user, and then it can be loaded in in order to specify what features the parser extracts in order to make its
 * decisions.
 *
 * Format specification:
 *
 *   - Each line in the file that is blank or starts with a # is ignored.
 *   - Otherwise each line specifies one or more features to be extracted from one or more tokens.
 *   - Which features or tokens appear on a line is largely down to the user's logic (i.e. grouping features which all
 *     relate to the same token).
 *   - Each line is of the following format:
 *     - Each line is divided into columns by a colon (:)
 *     - The first column is for a whitespace separated list of token addresses (1 or more)
 *     - Each subsequent column (1 or more) is a feature to be extracted from the tokens at those addresses
 *     - A token address consists firstly of direct address to a part of the parser state (see ParserState class), e.g.
 *         stk[0]
 *         The above means "top item on the stack". Ensure that the parser style you select provides this address.
 *     - Secondly, any amount of uses of any of the functions below:
 *         - ldep = leftmost dependant (according to parsing decisions made so far)
 *         - rdep = rightmost dependant (according to parsing decisions made so far)
 *         - head = the head of the token (according to the parsing decisions made so far)
 *       e.g. head(ldep(rdep(buf[0])))
 *            the above means "the head of the leftmost dependant of the rightmost dependant of the token next on the buffer"
 *     - The features can be any of those attributes of tokens that you specify when creating Token objects (see token class)
 *       So if you've given your tokens the attributes "form" and "pos", then a feature line might look like:
 *         stk[0] buf[0] : pos : form
 *         The above means "extract 4 features:
 *                            1. pos tag of the item on top of stack
 *                            2. form of the item on top of stack
 *                            3. pos tag of next item on buffer
 *                            4. form of item on top of stack
 *     - There are 2 reserved feature types that you must not try to use in your feature table for other purposes:
 *
 *       1. "deprel", e.g.
 *          stk[0] : deprel
 *          Above means "get the dependency relation that's been assigned to the top item on the stack (mid-parse)
 *          NOTE: it's fine for your tokens to define a deprel attribute when you create them (as they would already
 *                have one assigned if they are training data), because the token will know to set aside that data
 *                as gold standard and not allow the parser to see it, even if you use "deprel" in the feature table.
 *
 *       2. "join" e.g.
 *          stk[0] buf[0] : join(pos, form)
 *          Above means "create a single feature made of the pos of top of stack plus the form of next buffer token
 *
 * Created by Andrew D. Robertson on 14/04/2014.
 */
public class FeatureTable {

    // Value used when a feature can't be extracted from the current parser state.
    private static final String absentFeature = "--absentFeature--";

    // Used during reading of the feature table
    private static final Pattern columnSplit = Pattern.compile("\\s*:\\s*");
    private static final Pattern termSplit = Pattern.compile("\\s+");
    private static final Pattern argSplit = Pattern.compile(",");
    private static final Pattern addressSplit = Pattern.compile("\\(");
    private static final Pattern indexSplit = Pattern.compile("\\[");
    private static final Pattern indexSplit2 = Pattern.compile("\\]");

    // List of lines that define which features to extract
    private List<FeatureDefinitionLine> featureDefinitionLines;

    public FeatureTable(File featureTable) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(featureTable), "UTF-8"))){
            readFeatureTable(br);
        }
    }

    public FeatureTable(InputStream featureTable) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(featureTable, "UTF-8"))){
            readFeatureTable(br);
        }
    }

    /**
     * Used by the parser to get a list of features for the current parser state. Generally the features are then indexed
     * and placed inside a feature vector.
     */
    public List<String> extractFeatures(ParserState state){
        List<String> features = new ArrayList<>();
        for (FeatureDefinitionLine lineDef : featureDefinitionLines) {
            features.addAll(lineDef.getFeatures(state));
        } return features;
    }

    private void readFeatureTable(BufferedReader br) throws IOException {
        featureDefinitionLines = new ArrayList<>();
        String line;
        while ((line=br.readLine()) != null) {
            if (!line.startsWith("#") && !line.trim().equals(""))
                featureDefinitionLines.add(readLine(line.toLowerCase()));
        }
    }

    private FeatureDefinitionLine readLine(String line) {
        // First column is whitespace separated address, subsequent columns are features.
        String[] columns = columnSplit.split(line);
        // Must define at least one token address and feature separated by a colon.
        if (columns.length < 2) throw new RuntimeException("A line must specify at least 1 address and feature, colon separated: " + line);

        // Fill with addresses from the first column
        String[] addressDefs = termSplit.split(columns[0]);
        List<TokenAddress> addresses = new ArrayList<>();

        // Extract token addresses for this line
        for (String addressDef : addressDefs){
            List<String> addressingFunctions = new ArrayList<>();
            String[] addressComponents = addressSplit.split(addressDef);
            // All but the last component are addressing functions
            addressingFunctions.addAll(Arrays.asList(addressComponents).subList(0, addressComponents.length - 1));
            String[] tokenAddress = indexSplit.split(addressComponents[addressComponents.length-1]);
            addresses.add(new TokenAddress(tokenAddress[0],
                                           Integer.parseInt(indexSplit2.split(tokenAddress[1])[0]),
                                           addressingFunctions));
        }
        // Get feature definitions, one per remaining column
        List<FeatureDefinition> featureDefs = new ArrayList<>();
        for (int i=1; i < columns.length; i++) {
            featureDefs.add(new FeatureDefinition(columns[i]));
        }  return new FeatureDefinitionLine(addresses, featureDefs);
    }

    /**
     * Represents a definition line within the specification file. Consists of 1 or more address with 1 or more
     * feature definitions.
     */
    private static class FeatureDefinitionLine {

        public List<TokenAddress> addresses;
        public List<FeatureDefinition> featureDefs;

        public FeatureDefinitionLine(List<TokenAddress> addresses, List<FeatureDefinition> featureDefs){
            this.addresses = addresses;
            this.featureDefs = featureDefs;
        }

        public List<String> getFeatures(ParserState state) {
            List<String> features = new ArrayList<>();
            for (FeatureDefinition def : featureDefs) {
                features.addAll(def.getFeatures(addresses, state));
            } return features;
        }

    }

    /**
     * Represents the definition of a single feature. I.e. what attribute of the token are we interested in? Do we
     * want to concatenate the features together?
     */
    private static class FeatureDefinition {

        private String featureType;
        private List<String> args;

        public FeatureDefinition(String featureDefinition) {
            args = new ArrayList<>();
            if (featureDefinition.startsWith("join")){
                featureType = "join";
                String[] featureArgs = argSplit.split(featureDefinition.substring(5, featureDefinition.length()-1));
                args.addAll(Arrays.asList(featureArgs));
            } else {
                featureType = featureDefinition;
            }
        }

        public List<String> getFeatures(List<TokenAddress> addresses, ParserState state) {
            List<String> features = new ArrayList<>();
            if (featureType.equals("join")) { // We need to concatenate attribute features across all addresses
                if (args.size() != addresses.size()) throw new RuntimeException("Number of addresses doesn't match number of features specified in Feature Table.");

                List<String> featureValues = new ArrayList<>(); // Feature for each arg+address

                // Resolve each address, and get the attribute feature of the resolved token
                for (int i=0; i<args.size(); i++) {
                    featureValues.add(getAttributeFeature(addresses.get(i).getToken(state), args.get(i)));
                }
                // Produce a feature that encodes the addresses, the join type, and the feature values
                Joiner joiner = Joiner.on(",");
                features.add(joiner.join(addresses)+"|"+this.toString()+"|"+joiner.join(featureValues));

            } else { // We're just dealing with an attribute feature per address
                for (TokenAddress address: addresses){
                    String featureValue = getAttributeFeature(address.getToken(state), featureType);
                    features.add(address.toString()+"|"+this.toString()+"|"+featureValue);
                }
            } return features;
        }

        private String getAttributeFeature(Token token, String attribute){
            if (token == null) return absentFeature;
            String feature = attribute.equals("deprel")? token.getDeprel() : token.getAtt(attribute);
            return feature==null? absentFeature : feature;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(featureType);
            if (args.size() > 0) {
                Joiner joiner = Joiner.on(",");
                sb.append("(");
                sb.append(joiner.join(args));
                sb.append(")");
            } return sb.toString();
        }
    }

    /**
     * Represents the location of a token in the parser state. ParserStates themselves determine what the user can
     * access through feature extraction.
     */
    private static class TokenAddress {

        private String structureType;
        private int address;
        private List<String> addressingFunctions;

        public TokenAddress(String structureType, int address, List<String> addressingFunctions) {
            this.structureType = structureType;
            this.address = address;
            this.addressingFunctions = addressingFunctions;
        }

        public Token getToken(ParserState state) {
            Token token = state.getToken(structureType, address);
            if (token==null) return null;
            try {
                for (String addressingFunction : addressingFunctions) {
                    switch(addressingFunction) {
                        case "head": token = token.getHead(); break;
                        case "ldep": token = token.getLeftmostChild(); break;
                        case "rdep": token = token.getRightmostChild(); break;
                        default: throw new RuntimeException("Unrecognised addressing function.");
                    }
                }
            } catch (NullPointerException e) { return null; }
            return token;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(structureType); sb.append("[");
            sb.append(address); sb.append("]");
            for (String f : addressingFunctions) {
                sb.append(">"); sb.append(f);
            } return sb.toString();
        }
    }
}
