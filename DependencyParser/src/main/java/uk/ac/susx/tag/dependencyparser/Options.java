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
 *     in the Options.getParserStyle() method. It also specifies what data-structures the parser is
 *     expected to be using, by returning the appropriate instance in the getNewParserState() method.
 *     Which brings us to...
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
 *     depends on and exposes to the feature extraction process. By adding another ParserState
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


    public static Classifier getClassifier(String classifierKey) {
        Reflections reflections = new Reflections("uk.ac.susx.tag.dependencyparser.classifiers");
        Set<Class<? extends Classifier>> foundClassifiers = reflections.getSubTypesOf(Classifier.class);

        for(Class<? extends Classifier> klass : foundClassifiers) {
            try {
                // Create the instance
                Classifier classifier = klass.newInstance();
                if (classifierKey.equals(classifier.key())) return classifier;

            } catch (IllegalAccessException | InstantiationException e) { throw new RuntimeException(e); }
        } throw new RuntimeException("No classifier found matching the specified key.");
    }

    public static ParseStyle getParserStyle(String parseStyleKey) {
        Reflections reflections = new Reflections("uk.ac.susx.tag.dependencyparser.parsestyles");
        Set<Class<? extends ParseStyle>> foundStyles = reflections.getSubTypesOf(ParseStyle.class);

        for(Class<? extends ParseStyle> klass : foundStyles) {
            try {
                // Create the instance
                ParseStyle style = klass.newInstance();
                if (parseStyleKey.equals(style.key())) return style;

            } catch (IllegalAccessException | InstantiationException e) { throw new RuntimeException(e); }
        } throw new RuntimeException("No style found matching the specified key.");
    }

    public static SelectionMethod getSelectionMethod(String selectionMethodKey){
        Reflections reflections = new Reflections("uk.ac.susx.tag.dependencyparser.transitionselectionmethods");
        Set<Class<? extends SelectionMethod>> foundMethods = reflections.getSubTypesOf(SelectionMethod.class);

        for(Class<? extends SelectionMethod> klass : foundMethods) {
            try {
                // Create the instance
                SelectionMethod method = klass.newInstance();
                if(selectionMethodKey.equals(method.key())) return method;

            } catch (InstantiationException | IllegalAccessException e) {  throw new RuntimeException(e); }
        } throw new RuntimeException("No selection method found matching the specified key");
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

    private static void printAvailableOptions(String reflectionTarget, Class<? extends Option> type) {
        Reflections reflections = new Reflections(reflectionTarget);
        Set options = reflections.getSubTypesOf(type); // I tried it the generics way; it got ugly. The compiler was throwing errors that IntelliJ couldn't predict.
        for (Object klass : options) {
            try {
                Class<? extends Option> klassProper = (Class<? extends Option>)klass; // UTTER FILTH
                System.out.println(" " + klassProper.newInstance().key());
            } catch (InstantiationException | IllegalAccessException e) { throw new RuntimeException(e);}
        }
    }

    public static void main(String[] args){
        printAvailableOptionsSummary();
    }


}
