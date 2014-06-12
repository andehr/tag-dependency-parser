package uk.ac.susx.tag.dependencyparser;

/*
 * #%L
 * Options.java - dependencyparser - CASM Consulting - 2,014
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

import org.reflections.Reflections;
import uk.ac.susx.tag.dependencyparser.classifiers.Classifier;
import uk.ac.susx.tag.dependencyparser.parsestyles.ParseStyle;
import uk.ac.susx.tag.dependencyparser.transitionselectionmethods.SelectionMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * This is where any nasty reflection stuff happens for the purposes of configuration (and extensibility)
 *
 * Generally, the parser is designed to be extensible easily in 4 places without having to change source code
 * and re-compile.
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
 * IMPORTANT NOTE:
 *
 *   This wonderful extensibility is accomplished using Reflection. Reflection in java can be slow. So these
 *   functions are only ever invoked when a parser is created or at the beginning of training, since one off uses
 *   won't make any difference. So if you find yourself editing my code, DON'T find yourself calling these functions
 *   for every single sentence you parse or something crazy like that.
 *
 * Created by Andrew D. Robertson on 15/04/2014.
 */
public class Options {

    private static String classifiersPackage = "uk.ac.susx.tag.dependencyparser.classifiers";
    private static String parseStylesPackage = "uk.ac.susx.tag.dependencyparser.parsestyles";
    private static String transitionSelectionMethodsPackage = "uk.ac.susx.tag.dependencyparser.transitionselectionmethods";

    /**
     * TEST.
     * Print out the available options.
     */
    public static void main(String[] args){ printAvailableOptionsSummary(); }

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
        return getOption(classifiersPackage, Classifier.class, classifierKey);
    }

    public static ParseStyle getParserStyle(String parseStyleKey){
        return getOption(parseStylesPackage, ParseStyle.class, parseStyleKey);
    }

    public static SelectionMethod getSelectionMethod(String selectionMethodKey){
        return getOption(transitionSelectionMethodsPackage, SelectionMethod.class, selectionMethodKey);
    }

    /**
     * Use this for testing what options are available at a glance and/or checking whether your custom options
     * are being loaded correctly.
     */
    public static void printAvailableOptionsSummary() {
        System.out.println("--- Available Options ---");

        printOptions("\nParse styles:", getAvailableOptions(parseStylesPackage, ParseStyle.class));

        printOptions("\nClassifier types:", getAvailableOptions(classifiersPackage, Classifier.class));

        printOptions("\nTransition selection methods:", getAvailableOptions(transitionSelectionMethodsPackage, SelectionMethod.class));
    }
    private static void printOptions(String title, List<String> options) {
        System.out.println(title);
        for (String option : options)
            System.out.println(" " + option);
    }

    // Here comes the reflection and generics fun...

    /**
     * Search *packagePath* for a class which is a subtype of *type* (which must extend Option) and which
     * returns *key* from its key() method.
     */
    private static <O extends Option> O getOption(String packagePath, Class<O> type, String key){
        Reflections reflections = new Reflections(packagePath);

        // Find all those classes which are subtypes of *type* and therefore of Option (therefore each having the key() method)
        Set<Class<? extends O>> foundOptions = reflections.getSubTypesOf(type);

        // For each of the found classes, create a new instance of it, and if the result of its key() matches *key*, then return the instance.
        for(Class<? extends O> klass : foundOptions) {
            try {
                O option = klass.newInstance();
                if(key.equals(option.key())) return option;
            } catch (InstantiationException | IllegalAccessException e) { throw new RuntimeException(e); }
        } throw new RuntimeException("No option found matching the specified key");
    }

    /**
     * For each class in *packagePath* which is a subtype of *type* (and therefore implements Option), record
     * the value returned from its key() method, thereby presenting the list of available options in a package.
     */
    private static <O extends Option> List<String> getAvailableOptions(String packagePath, Class<O> type) {
        List<String> foundOptions = new ArrayList<>();
        Reflections reflections = new Reflections(packagePath);
        Set<Class<? extends O>> options = reflections.getSubTypesOf(type);
        for (Class<? extends O> klass : options) {
            try {
                foundOptions.add(klass.newInstance().key());
            } catch (InstantiationException | IllegalAccessException e) { throw new RuntimeException(e);}
        } return foundOptions;
    }
}
