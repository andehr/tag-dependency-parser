package uk.ac.susx.tag.dependencyparser;

import org.reflections.Reflections;
import uk.ac.susx.tag.dependencyparser.classifiers.Classifier;
import uk.ac.susx.tag.dependencyparser.parsestyles.ParseStyle;
import uk.ac.susx.tag.dependencyparser.transitionselectionmethods.SelectionMethod;

import java.util.Set;

/**
 * This is where any nasty reflection stuff happens for the purposes of configuration.
 *
 * Generally, the parser is designed to be extensible easily in 4 places.
 *
 *  1. (classifiers) You should be able to define your own classifier, place it in the classifiers
 *     package, and then when you use Options.getClassifier() it will be available (via reflection).
 *
 *  2. (parse-styles) You should be able to define your own parse style (i.e. the set of transitions
 *     that the parser has available to it, and how it learns which is the right transition during
 *     training), by creating a ParseStyle in the parsestyles package. It will then be available
 *     in the Options.getParserStyle() method. The style also specifies what data-structures the parser is
 *     expected to be using, by returning the appropriate instance in the getNewParserState() method (see 4)
 *
 *  3. (transition selection methods) You should be able to define your own method of selecting the
 *     best transition at parse-time. Look at the SelectionMethods in the transitionselectionmethods
 *     package. A SelectionMethod sees what the classifier recommends, and decision scores, and then
 *     has the final say in which transition is selected. By the same process of reflection as above,
 *     each SelectionMethod specifies a key string, which the user can request. Then the
 *     Options.getSelectionMethod() will be able to grab your implementation for the user using this
 *     key.
 *
 *  4. (parser-states) You should be able to define your own parser states and the structures it
 *     depends on and exposes to the feature extraction process, by adding another ParserState
 *     class in the parserstates package, and having a parse style reference it.
 *
 *
 * The general procedure of methods in this class is as follows:
 *
 *   1. There will be an interface or abstract class which is found in a package representing extensible functionality,
 *      where the user may implement/extend said interface/class in order to provide their own new functionality.
 *
 *   2. The method first grabs the set of all classes in the relevant package implementing the relevant interface.
 *
 *   3. The method then attempts to match the user specified key to the ones returned by the individual implementations.
 *
 *   4. If a match is found, then an instance is created of the requested class and returned. Otherwise an exception is
 *      thrown.
 *
 * Created by Andrew D. Robertson on 15/04/2014.
 */
public class Options {

    /**
     * The first step to becoming a new option in any of the extensible parts of this project, is to extend a class
     * that implements this interface. The new option must provide a key by which the user selects the option.
     *
     * For example, when coding the "arc eager" style of parsing, I extended the "ParseStyle" class (which in turn
     * implements the Option interface). Then for the key() method, returned the string "arc-eager". Then it becomes
     * a feasible parse style option.
     */
    public static interface Option {
        public String key();
    }

    public static Classifier getClassifier(String classifierKey){
        return getOption("uk.ac.susx.tag.dependencyparser.classifiers", Classifier.class, classifierKey);
    }

    public static ParseStyle getParserStyle(String parseStyleKey){
        return getOption("uk.ac.susx.tag.dependencyparser.parsestyles", ParseStyle.class, parseStyleKey);
    }

    public static SelectionMethod getSelectionMethod(String selectionMethodKey){
        return getOption("uk.ac.susx.tag.dependencyparser.transitionselectionmethods", SelectionMethod.class, selectionMethodKey);
    }

    /**
     * Use this for testing what options are available at a glance and/or checking whether your custom options
     * are being loaded correctly.
     */
    public static void printAvailableOptionsSummary() {
        System.out.println("--- Available Options ---");

        System.out.println("\nParse styles:");
        printAvailableOptions("uk.ac.susx.tag.dependencyparser.parsestyles", ParseStyle.class);

        System.out.println("\nClassifier types:");
        printAvailableOptions("uk.ac.susx.tag.dependencyparser.classifiers", Classifier.class);

        System.out.println("\nTransition selection methods:");
        printAvailableOptions("uk.ac.susx.tag.dependencyparser.transitionselectionmethods", SelectionMethod.class);
    }

    private static <O extends Option> O getOption(String packagePath, Class<O> type, String key){
        Reflections reflections = new Reflections(packagePath);
        Set<Class<? extends O>> foundOptions = reflections.getSubTypesOf(type);
        for(Class<? extends O> klass : foundOptions) {
            try {
                O option = klass.newInstance();
                if(key.equals(option.key())) return option;
            } catch (InstantiationException | IllegalAccessException e) {  throw new RuntimeException(e); }
        } throw new RuntimeException("No option found matching the specified key");
    }

    private static <O extends Option> void printAvailableOptions(String packagePath, Class<O> type) {
        Reflections reflections = new Reflections(packagePath);
        Set<Class<? extends O>> options = reflections.getSubTypesOf(type);
        for (Class<? extends O> klass : options) {
            try {
                System.out.println(" " + klass.newInstance().key());
            } catch (InstantiationException | IllegalAccessException e) { throw new RuntimeException(e);}
        }
    }

    /**
     * Print out the available options.
     */
    public static void main(String[] args){ printAvailableOptionsSummary(); }

}
