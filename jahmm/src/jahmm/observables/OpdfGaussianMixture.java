/*
 * Copyright (c) 2004-2009, Jean-Marc François. All Rights Reserved.
 * Licensed under the New BSD license.  See the LICENSE file.
 */
package jahmm.observables;

import jahmm.distributions.GaussianDistribution;
import jahmm.distributions.GaussianMixtureDistribution;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Collection;

/**
 * This class implements a mixture of mono variate Gaussian distributions.
 *
 * @author Benjamin Chung (Creation)
 * @author Jean-Marc Francois (Adaptations / small fix)
 */
public final class OpdfGaussianMixture extends OpdfBase<ObservationReal> implements Opdf<ObservationReal> {

    private static final long serialVersionUID = 1L;

    private GaussianMixtureDistribution distribution;

    /**
     * Creates a Gaussian mixture distribution. The mean values of the
     * distributions are evenly distributed between 0 and 1 and each variance is
     * equal to 1.
     *
     * @param nbGaussians The number of Gaussian distributions that compose this
     * mixture.
     */
    public OpdfGaussianMixture(int nbGaussians) {
        distribution = new GaussianMixtureDistribution(nbGaussians);
    }

    /**
     * Creates a Gaussian mixture distribution. The mean and variance of each
     * distribution composing the mixture are given as arguments.
     *
     * @param means The mean values of the Gaussian distributions.
     * @param variances The variances of the Gaussian distributions.
     * @param proportions The mixing proportions. This array does not have to be
     * normalized, but each element must be positive and the sum of its elements
     * must be strictly positive.
     */
    public OpdfGaussianMixture(double[] means, double[] variances, double... proportions) {
        distribution = new GaussianMixtureDistribution(means, variances, proportions);
    }

    private OpdfGaussianMixture(GaussianMixtureDistribution distribution) {
        this.distribution = distribution;
    }

    @Override
    public double probability(ObservationReal o) {
        return distribution.probability(o.value);
    }

    @Override
    public ObservationReal generate() {
        return new ObservationReal(distribution.generate());
    }

    /**
     * Returns the number of distributions composing this mixture.
     *
     * @return The number of distributions composing this mixture.
     */
    public int nbGaussians() {
        return distribution.nbGaussians();
    }

    /**
     * Returns the mixing proportions of each Gaussian distribution.
     *
     * @return A (copy of) array giving the distributions' proportion.
     */
    public double[] proportions() {
        return distribution.proportions();
    }

    /**
     * Returns the mean value of each distribution composing this mixture.
     *
     * @return A copy of the means array.
     */
    public double[] means() {
        double[] means = new double[nbGaussians()];
        GaussianDistribution[] distributions = distribution.distributions();

        for (int i = 0; i < distributions.length; i++) {
            means[i] = distributions[i].mean();
        }

        return means;
    }

    /**
     * Returns the mean value of each distribution composing this mixture.
     *
     * @return A copy of the means array.
     */
    public double[] variances() {
        double[] variances = new double[nbGaussians()];
        GaussianDistribution[] distributions = distribution.distributions();

        for (int i = 0; i < distributions.length; i++) {
            variances[i] = distributions[i].variance();
        }

        return variances;
    }

    /**
     * Fits this observation distribution function to a (non empty) set of
     * observations. This method performs one iteration of an
     * expectation-maximisation algorithm.
     *
     * @param oa A set of observations compatible with this function.
     */
    @Override
    public void fit(ObservationReal... oa) {
        fit(Arrays.asList(oa));
    }

    /**
     * Fits this observation distribution function to a (non empty) set of
     * observations. This method performs one iteration of an
     * expectation-maximisation algorithm.
     *
     * @param co A set of observations compatible with this function.
     */
    @Override
    public void fit(Collection<? extends ObservationReal> co) {
        double[] weights = new double[co.size()];
        Arrays.fill(weights, 1. / co.size());

        fit(co, weights);
    }

    /**
     * Fits this observation distribution function to a (non empty) weighted set
     * of observations. This method performs one iteration of an
     * expectation-maximisation algorithm. Equations (53) and (54) of Rabiner's
     * <i>A Tutorial on Hidden Markov Models and Selected Applications in Speech
     * Recognition</i> explain how the weights can be used.
     *
     * @param o A set of observations compatible with this function.
     * @param weights The weights associated to the observations.
     */
    @Override
    public void fit(ObservationReal[] o, double... weights) {
        fit(Arrays.asList(o), weights);
    }

    /**
     * Fits this observation distribution function to a (non empty) weighted set
     * of observations. This method performs one iteration of an
     * expectation-maximisation algorithm. Equations (53) and (54) of Rabiner's
     * <i>A Tutorial on Hidden Markov Models and Selected Applications in Speech
     * Recognition</i> explain how the weights can be used.
     *
     * @param co A set of observations compatible with this function.
     * @param weights The weights associated to the observations.
     */
    @Override
    public void fit(Collection<? extends ObservationReal> co, double... weights) {
        if (co.isEmpty() || co.size() != weights.length) {
            throw new IllegalArgumentException();
        }

        ObservationReal[] o = co.toArray(new ObservationReal[co.size()]);

        double[][] delta = getDelta(o);
        double[] newMixingProportions
                = computeNewMixingProportions(delta, o, weights);
        double[] newMeans = computeNewMeans(delta, o, weights);
        double[] newVariances = computeNewVariances(delta, o, weights);

        distribution = new GaussianMixtureDistribution(newMeans, newVariances, newMixingProportions);
    }

    /* 
     * Computes the relative weight of each observation for each distribution.
     */
    private double[][] getDelta(ObservationReal... o) {
        double[][] delta = new double[distribution.nbGaussians()][o.length];

        for (int i = 0; i < distribution.nbGaussians(); i++) {
            double[] proportions = distribution.proportions();
            GaussianDistribution[] distributions
                    = distribution.distributions();

            for (int t = 0; t < o.length; t++) {
                delta[i][t] = proportions[i]
                        * distributions[i].probability(o[t].value) / probability(o[t]);
            }
        }

        return delta;
    }

    /*
     * Estimates new mixing proportions given delta.
     */
    private double[] computeNewMixingProportions(double[][] delta,
            ObservationReal[] o, double[] weights) {
        double[] num = new double[distribution.nbGaussians()];
        double sum = 0.0;

        for (int i = 0; i < distribution.nbGaussians(); i++) {
            for (int t = 0; t < weights.length; t++) {
                num[i] += weights[t] * delta[i][t];
                sum += weights[t] * delta[i][t];
            }
        }

        double[] newMixingProportions = new double[distribution.nbGaussians()];
        for (int i = 0; i < distribution.nbGaussians(); i++) {
            newMixingProportions[i] = num[i] / sum;
        }

        return newMixingProportions;
    }

    /*
     * Estimates new mean values of each Gaussian given delta.
     */
    private double[] computeNewMeans(double[][] delta, ObservationReal[] o,
            double[] weights) {
        double[] num = new double[distribution.nbGaussians()];
        double[] sum = new double[distribution.nbGaussians()];

        for (int i = 0; i < distribution.nbGaussians(); i++) {
            for (int t = 0; t < o.length; t++) {
                num[i] += weights[t] * delta[i][t] * o[t].value;
                sum[i] += weights[t] * delta[i][t];
            }
        }

        double[] newMeans = new double[distribution.nbGaussians()];
        for (int i = 0; i < distribution.nbGaussians(); i++) {
            newMeans[i] = num[i] / sum[i];
        }

        return newMeans;
    }

    /*
     * Estimates new variance values of each Gaussian given delta.
     */
    private double[] computeNewVariances(double[][] delta, ObservationReal[] o,
            double[] weights) {
        double[] num = new double[distribution.nbGaussians()];
        double[] sum = new double[distribution.nbGaussians()];

        for (int i = 0; i < distribution.nbGaussians(); i++) {
            GaussianDistribution[] distributions = distribution.distributions();

            for (int t = 0; t < o.length; t++) {
                num[i] += weights[t] * delta[i][t]
                        * (o[t].value - distributions[i].mean())
                        * (o[t].value - distributions[i].mean());
                sum[i] += weights[t] * delta[i][t];
            }
        }

        double[] newVariances = new double[distribution.nbGaussians()];
        for (int i = 0; i < distribution.nbGaussians(); i++) {
            newVariances[i] = num[i] / sum[i];
        }

        return newVariances;
    }

    @Override
    public OpdfGaussianMixture clone() throws CloneNotSupportedException {
        return new OpdfGaussianMixture(this.distribution.clone());
    }

    /**
     *
     * @return
     */
    @Override
    public String toString() {
        return toString(NumberFormat.getInstance());
    }

    @Override
    public String toString(NumberFormat numberFormat) {
        StringBuilder sb = new StringBuilder("Gaussian mixture distribution --- ");

        double[] proportions = proportions();
        double[] means = means();
        double[] variances = variances();

        for (int i = 0; i < distribution.nbGaussians(); i++) {
            sb.append(String.format("Gaussian %s:\n\tMixing Prop = %s\n\tMean = %s\n\tVariance = %s\n", (i + 1), numberFormat.format(proportions[i]), numberFormat.format(means[i]), numberFormat.format(variances[i])));
        }

        return sb.toString();
    }
}
