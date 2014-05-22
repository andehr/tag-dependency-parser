package uk.ac.susx.tag.dependencyparser.transitionselectionmethods;

import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import uk.ac.susx.tag.dependencyparser.Index;
import uk.ac.susx.tag.dependencyparser.Options;
import uk.ac.susx.tag.dependencyparser.parserstates.ParserState;
import uk.ac.susx.tag.dependencyparser.parsestyles.ParseStyle;

/**
 * For each decision the parser will need to make, the classifier it's using will suggest the best action, and give
 * decision scores for all possible actions. It's down to a selection method to decide how the parser proceeds with
 * this information.
 *
 * See individual method comments.
 *
 * NOTE: Subclasses should probably be stateless (or immutable state) for safety. And they need to be instantiated
 *       with no args.
 *
 * Created by Andrew D. Robertson on 29/04/2014.
 */
public interface SelectionMethod extends Options.Option {

    /**
     * Given the parser's current state, and the recommendations of the classifier for the next transition,
     * apply the best possible transition.
     *
     *
     * IMPORTANT NOTE: The Index object retrieved from the parser using parser.getIndex() will be READ-ONLY, so do
     *                 not attempt to call index.getTransitionID() or index.getFeatureID() with the parameter
     *                 "addIfNotPresent" set to true. This ensures that if the parser.parseSentence() function
     *                 is called concurrently, that threads are not trying to modify things naughtily.
     *
     * @param classifierRecommends The transition that the classifier recommends (which may or may not be feasible)
     * @param decisionScores A mapping from each transition ID to its score according to the classifier
     * @param state The parser's current state
     * @param parseStyle The style of parsing being used. Use this is make transitions on the parser state
     * @param index The index that knows the mapping between features and their IDs and transitions and their IDs
     */
    public void applyBestTransition(ParseStyle.Transition classifierRecommends,
                                    Int2DoubleMap decisionScores,
                                    ParserState state,
                                    ParseStyle parseStyle,
                                    Index index);

}
