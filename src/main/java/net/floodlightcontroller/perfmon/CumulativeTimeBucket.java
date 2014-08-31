/**
 *    Copyright 2013, Big Switch Networks, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.perfmon;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import net.floodlightcontroller.core.IOFMessageListener;

@JsonSerialize(using=CumulativeTimeBucketJSONSerializer.class)
public class CumulativeTimeBucket {
	private static int SATISFIED_PROCTIME_NS = 25000; // nano second
    private long startTime_ns; // First pkt time-stamp in this bucket
    private Map<Integer, OneComponentTime> compStats;
    private long totalPktCnt;
    private long totalProcTimeNs; // total processing time for one pkt in
    private long sumSquaredProcTimeNs2;
    private long maxTotalProcTimeNs;
    private long minTotalProcTimeNs;
    private long avgTotalProcTimeNs;
    private long sigmaTotalProcTimeNs; // std. deviation
    private static long satisfiedLatencyCnt;  //per second
    private static long toleratedLatencyCnt;  //per second
    private static long untoleratedLatencyCnt;//per second
    private static long allPktInCntPerSec;    //per second
    private static double LPIndex;            //per second
    //Latency Performance index: LPIndex=(satisfiedLatencyCnt + 0.5*toleratedLatencyCnt)/allPktInCntPerSec;

    public long getStartTimeNs() {
        return startTime_ns;
    }

    public long getTotalPktCnt() {
        return totalPktCnt;
    }
    
    public long getAverageProcTimeNs() {
        return avgTotalProcTimeNs;
    }

    public long getMinTotalProcTimeNs() {
        return minTotalProcTimeNs;
    }
    
    public long getMaxTotalProcTimeNs() {
        return maxTotalProcTimeNs;
    }
    
    public long getTotalSigmaProcTimeNs() {
        return sigmaTotalProcTimeNs;
    }
    
    public long getSatisfiedLatencyCnt() {
        return satisfiedLatencyCnt;
    }
    
    public long getToleratedLatencyCnt() {
        return toleratedLatencyCnt;
    }
    
    public long getUntoleratedLatencyCnt() {
        return untoleratedLatencyCnt;
    }
    
    public long getAllPktInCntPerSec() {
        return allPktInCntPerSec;
    }
    
    public double getLPIndex() {
        return LPIndex;
    }
    
    public int getNumComps() {
        return compStats.values().size();
    }
    
    public Collection<OneComponentTime> getModules() {
        return compStats.values();
    }

    public CumulativeTimeBucket(List<IOFMessageListener> listeners) {
        compStats = new ConcurrentHashMap<Integer, OneComponentTime>(listeners.size());
        for (IOFMessageListener l : listeners) {
            OneComponentTime oct = new OneComponentTime(l);
            compStats.put(oct.hashCode(), oct);
        }
        startTime_ns = System.nanoTime();
    }

    private void updateSquaredProcessingTime(long curTimeNs) {
        sumSquaredProcTimeNs2 += (Math.pow(curTimeNs, 2));
    }
    
    /**
     * Resets all counters and counters for each component time
     */
    public void reset() {
        startTime_ns = System.nanoTime();
        totalPktCnt = 0;
        totalProcTimeNs = 0;
        avgTotalProcTimeNs = 0;
        sumSquaredProcTimeNs2 = 0;
        maxTotalProcTimeNs = 0;
        minTotalProcTimeNs = 0;
        sigmaTotalProcTimeNs = 0;
        for (OneComponentTime oct : compStats.values()) {
            oct.resetAllCounters();
        }
    }
    public static void resetPerSecond() {
    	computeLPIndex();
    	satisfiedLatencyCnt = 0;
    	toleratedLatencyCnt = 0;
    	untoleratedLatencyCnt = 0;
    	allPktInCntPerSec = 0;
    	LPIndex = 0;
    }
    
    //Reset Counter for LPIndex(latency performance index)
    public static class ResetCounterTask extends TimerTask {
    	SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        @Override
        public void run() {
        	System.out.println(sdf.format(new Date()));
        	CumulativeTimeBucket.resetPerSecond();
        }
    }
    //ResetCounterTask resetTask = new ResetCounterTask();
    //Timer timer = new Timer();
    //timer.schedule(resetTask, 0, 1000); // do resetTask per second;
    
    private void computeSigma() {
        // Computes std. deviation from the sum of count numbers and from
        // the sum of the squares of count numbers
        double temp = totalProcTimeNs;
        temp = Math.pow(temp, 2) / totalPktCnt;
        temp = (sumSquaredProcTimeNs2 - temp) / totalPktCnt;
        sigmaTotalProcTimeNs = (long) Math.sqrt(temp);
    }
    
    public void computeAverages() {
        // Must be called last to, needs latest info
        computeSigma();
        
        for (OneComponentTime oct : compStats.values()) {
            oct.computeSigma();
        }
    }
    
    public static void computeLPIndex() {
    	LPIndex=(satisfiedLatencyCnt + 0.5*toleratedLatencyCnt)/allPktInCntPerSec;
    }
    
    public void updatePerPacketCounters(long procTimeNs) {
        totalPktCnt++;
        totalProcTimeNs += procTimeNs;
        avgTotalProcTimeNs = totalProcTimeNs / totalPktCnt;
        updateSquaredProcessingTime(procTimeNs);
        
        if (procTimeNs > maxTotalProcTimeNs) {
            maxTotalProcTimeNs = procTimeNs;
        }
        
        if (procTimeNs < minTotalProcTimeNs) {
            minTotalProcTimeNs = procTimeNs;
        }
    }
    
    public void updataPerPacketInCounters(long procTimeNs){
    	allPktInCntPerSec++;
    	computeLPIndex();
    	if(procTimeNs<=SATISFIED_PROCTIME_NS) {
    		satisfiedLatencyCnt++;
    	}
    	else if(procTimeNs<=4*SATISFIED_PROCTIME_NS) {
    		toleratedLatencyCnt++;
    	}else {
    		untoleratedLatencyCnt++;
    	}   	
    }
        
    public void updateOneComponent(IOFMessageListener l, long procTimeNs) {
        compStats.get(l.hashCode()).updatePerPacketCounters(procTimeNs);
    }
}