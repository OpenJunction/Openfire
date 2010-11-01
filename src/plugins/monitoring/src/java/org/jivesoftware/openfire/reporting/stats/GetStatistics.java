/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */
package org.jivesoftware.openfire.reporting.stats;

import org.jivesoftware.openfire.stats.Statistic;
import org.jivesoftware.openfire.stats.StatisticsManager;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.cache.ClusterTask;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.Map;

/**
 * Command that will be executed in each cluster node (except the invoker) to
 * collect samples of statistics that keep track information that is local to
 * the cluster node. Statistics that are able to gather the sample from all the
 * cluster nodes are ignored by this command.
 *
 * @author Gaston Dombiak
 */
public class GetStatistics implements ClusterTask {
    private Map<String, Double> samples;

    public Object getResult() {
        return samples;
    }

    public void run() {
        samples = new HashMap<String, Double>();
        for (Map.Entry<String, Statistic> statisticEntry : StatisticsManager.getInstance().getAllStatistics()) {
            String key = statisticEntry.getKey();
            Statistic statistic = statisticEntry.getValue();
            // Only sample statistics that keep info of the cluster node and not the entire cluster
            if (statistic.isPartialSample()) {
                double statSample = sampleStat(key, statistic);
                // Store sample result
                samples.put(key, statSample);
            }
        }
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        // Ignore
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        // Ignore
    }

    /**
     * Profiles the sampling to make sure that it does not take longer than half a second to
     * complete, if it does, a warning is logged.
     *
     * @param statKey the key related to the statistic.
     * @param statistic the statistic to be sampled.
     * @return the sample.
     */
    private double sampleStat(String statKey, Statistic statistic) {
        long start = System.currentTimeMillis();
        double sample = statistic.sample();
        if (System.currentTimeMillis() - start >= 500) {
            Log.warn("Stat " + statKey + " took longer than a second to sample.");
        }
        return sample;
    }
}
