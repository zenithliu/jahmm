/*
 * Copyright (c) 2004-2009, Jean-Marc François. All Rights Reserved.
 * Licensed under the New BSD license.  See the LICENSE file.
 */
package jahmm.calculators;

import jahmm.RegularHmmBase;
import jahmm.observables.Observation;
import java.util.Iterator;
import java.util.List;

/**
 * This class can be used to compute the most probable state sequence matching a
 * given observation sequence (given an HMM).
 */
public final class RegularViterbiCalculatorBase {
    /*
     * The psy and delta values, as described in Rabiner and Juand classical
     * papers.
     */

    private final double[][] delta;
    private final int[][] psy;
    private final int[] stateSequence;
    private double lnProbability;

    /**
     * Computes the most likely state sequence matching an observation sequence
     * given an HMM.
     *
     * @param <O>
     * @param hmm A Hidden Markov Model;
     * @param oseq An observations sequence.
     */
    public <O extends Observation> RegularViterbiCalculatorBase(List<? extends O> oseq, RegularHmmBase<O> hmm) {
        if (oseq.isEmpty()) {
            throw new IllegalArgumentException("Invalid empty sequence");
        }
        delta = new double[oseq.size()][hmm.nbStates()];
        psy = new int[oseq.size()][hmm.nbStates()];
        stateSequence = new int[oseq.size()];
        for (int i = 0; i < hmm.nbStates(); i++) {
            delta[0][i] = -Math.log(hmm.getPi(i)) - Math.log(hmm.getOpdf(i).probability(oseq.get(0)));
            psy[0][i] = 0;
        }
        Iterator<? extends O> oseqIterator = oseq.iterator();
        if (oseqIterator.hasNext()) {
            oseqIterator.next();
        }
        int t = 1;
        while (oseqIterator.hasNext()) {
            O observation = oseqIterator.next();
            for (int i = 0; i < hmm.nbStates(); i++) {
                computeStep(hmm, observation, t, i);
            }
            t++;
        }
        lnProbability = Double.MAX_VALUE;
        for (int i = 0; i < hmm.nbStates(); i++) {
            double thisProbability = delta[oseq.size() - 1][i];

            if (lnProbability > thisProbability) {
                lnProbability = thisProbability;
                stateSequence[oseq.size() - 1] = i;
            }
        }
        lnProbability = -lnProbability;

        for (int t2 = oseq.size() - 2; t2 >= 0; t2--) {
            stateSequence[t2] = psy[t2 + 1][stateSequence[t2 + 1]];
        }
    }

    /*
     * Computes delta and psy[t][j] (t > 0) 
     */
    private <O extends Observation> void
            computeStep(RegularHmmBase<O> hmm, O o, int t, int j) {
        double minDelta = Double.MAX_VALUE;
        int min_psy = 0;

        for (int i = 0; i < hmm.nbStates(); i++) {
            double thisDelta = delta[t - 1][i] - Math.log(hmm.getAij(i, j));

            if (minDelta > thisDelta) {
                minDelta = thisDelta;
                min_psy = i;
            }
        }

        delta[t][j] = minDelta - Math.log(hmm.getOpdf(j).probability(o));
        psy[t][j] = min_psy;
    }

    /**
     * Returns the natural logarithm of the probability of the given observation
     * sequence on the most likely state sequence of the given HMM.
     *
     * @return <code>ln(P[O,S|H])</code> where <code>O</code> is the given
     * observation sequence, <code>H</code> the given HMM and <code>S</code> the
     * most likely state sequence of this observation sequence given this HMM.
     */
    public double lnProbability() {
        return lnProbability;
    }

    /**
     * Returns a (clone of) the array containing the computed most likely state
     * sequence.
     *
     * @return The state sequence; the i-th value of the array is the index of
     * the i-th state of the state sequence.
     */
    public int[] stateSequence() {
        return stateSequence.clone();
    }
}
