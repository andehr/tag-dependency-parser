package uk.ac.susx.tag.dependencyparser;

import org.reflections.Reflections;
import uk.ac.susx.tag.dependencyparser.classifiers.Classifier;
import uk.ac.susx.tag.dependencyparser.parsestyles.ParseStyle;

import java.util.Set;

/**
 * This is where any nasty reflection stuff happens for the purposes of configuration.
 *
 * Generally, the parser is designed to be extensible easily in 3 places.
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
 *  3. (parser-states) You should be able to define your own parser states and the structures it
 *     depends on and exposes to the feature extraction process. By adding another ParserState
 *     class in the parserstates package, and having a parse style reference it.
 *
 * Created by Andrew D. Robertson on 15/04/2014.
 */
public class Options {

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
}
