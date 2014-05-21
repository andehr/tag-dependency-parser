package uk.ac.susx.tag.dependencyparser.transitionselectionmethods;

import com.google.common.collect.Ordering;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import uk.ac.susx.tag.dependencyparser.Index;
import uk.ac.susx.tag.dependencyparser.parserstates.ParserState;
import uk.ac.susx.tag.dependencyparser.parsestyles.ParseStyle;

import java.util.HashMap;
import java.util.Map;

/**
 * Procedure:
 *   1. Try the transition that the classifier recommends
 *   2. If that is impossible, find transition of each type that got the largest positive number as its score.
 *   3. Try the best of each type from best to worst. (A good parse style would have a transition that is always
 *      possible in a non-terminal state, so one of these should work. If it don't, then throw an exception).
 *
 *   This method is sensible for dealing with the output of the Liblinear SVM package, as far as I know. Each score
 *   represents how far the decision was from the margin, so a bigger score is better because the decision was
 *   easier.
 *
 *   Each score represents the outcome of a 2 way classification: the transition of interest versus all other transitions.
 *   A positive number is a classification in favour of the transition of interest (which is why we can just pick the
 *   highest value, even though there will be positive and negative distances from the margin).
 *
 * Created by Andrew D. Robertson on 29/04/2014.
 */
public class SelectionMethodConfidence implements SelectionMethod {

    @Override
    public void applyBestTransition(ParseStyle.Transition classifierRecommends, Int2DoubleMap decisionScores, ParserState state, ParseStyle parseStyle, Index index) {

        // Try the transition that the classifier thinks is best.
        if (parseStyle.transition(state, classifierRecommends, true)) return;

        // If the transition is not possible, then continue with the aim of selecting the next best based on the decision scores returned from the classifier

        Map<String, Integer> bestIDPerBaseTrans = new HashMap<>();   // Transition ID --> Transition name (i.e. the base transition type without any label, e.g. "leftArc" instead of "leftArc|amod").
        Map<String, Double> bestScorePerBaseTrans = new HashMap<>(); // Transition name --> best score for this transition type

        // First we find the best transition (the one the classifier was most sure about) for each DISTINCT type of transition, e.g. the best right arc, the best left arc,...
        for(Int2DoubleMap.Entry entry : decisionScores.int2DoubleEntrySet()) {

            // Resolve the ID to the actual transition.
            ParseStyle.Transition t = index.getTransition(entry.getIntKey());

            // If this beats the current best score for this type of transition, then record new best
            if (!bestScorePerBaseTrans.containsKey(t.transitionName) || entry.getDoubleValue() > bestScorePerBaseTrans.get(t.transitionName)) {
                bestScorePerBaseTrans.put(t.transitionName, entry.getDoubleValue());
                bestIDPerBaseTrans.put(t.transitionName, entry.getIntKey());
            }
        }
        // Sort the set of distinct arcs by their score, and try them best to worst to see which one is possible.
        // Currently the best overall transition (the one that the classifier suggested and was impossible) will be tried again here. But that's no biggy.
        for (Map.Entry<String, Double> entry : new ScoredTransitionTypeOrdering().reverse().immutableSortedCopy(bestScorePerBaseTrans.entrySet())){
            if(parseStyle.transition(state, index.getTransition(bestIDPerBaseTrans.get(entry.getKey())), true))
                return;
        }
        // If we've reached this point, then all types of transition have been tried and none were possible
        throw new RuntimeException("No transition was possible. This should be impossible. Perhaps the parse style was implemented incorrectly.");
    }

    @Override
    public String key() {
        return "confidence";
    }

    /**
     * Ordering for sorting a list of entries by their values.
     */
    private static class ScoredTransitionTypeOrdering extends Ordering<Map.Entry<String,Double>> {
        @Override
        public int compare(Map.Entry<String, Double> entry1, Map.Entry<String, Double> entry2) {
            return entry1.getValue().compareTo(entry2.getValue());
        }
    }
}
