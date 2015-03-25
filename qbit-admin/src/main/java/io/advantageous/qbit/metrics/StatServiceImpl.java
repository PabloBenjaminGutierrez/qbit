/*
 * Copyright (c) 2015. Rick Hightower, Geoff Chandler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * QBit - The Microservice lib for Java : JSON, WebSocket, REST. Be The Web!
 */

package io.advantageous.qbit.metrics;

import io.advantageous.qbit.annotation.Service;
import io.advantageous.qbit.metrics.support.MinuteStat;
import io.advantageous.qbit.queue.QueueCallBackHandler;
import io.advantageous.qbit.service.ServiceFlushable;
import io.advantageous.qbit.service.ServiceProxyUtils;
import io.advantageous.qbit.util.Timer;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stat Service Impl
 */
@Service("statService")
public class StatServiceImpl implements QueueCallBackHandler {
    private final StatRecorder recorder;
    private final StatReplicator replica;
    private final Timer timer;
    private long now;
    private long startMinute;
    private Map<String, MinuteStat> currentMinuteOfStatsMap;
    private Map<String, MinuteStat> lastMinuteOfStatsMap;


    public StatServiceImpl(final StatRecorder recorder,
                           final StatReplicator replica,
                           final Timer timer) {
        this.recorder = recorder;
        this.currentMinuteOfStatsMap = new ConcurrentHashMap<>(100);
        this.lastMinuteOfStatsMap = new ConcurrentHashMap<>(100);
        this.timer = timer;
        now = timer.now();
        startMinute = now;
        this.replica = replica;
    }


    public void recordCount(String name, int count) {
        recordWithTime(name, count, now);
    }


    public void increment(String name) {
        recordWithTime(name, 1, now);
    }


    public void incrementAll(final String... names) {
        for (String name : names) {
            increment(name);
        }
    }

    public int currentMinuteCount(String name) {
        return oneMinuteOfStats(name).getTotalCount();
    }


    public int lastTenSecondCount(String name) {
        return oneMinuteOfStats(name).countLastTenSeconds(now);
    }

    public int lastFiveSecondCount(String name) {
        return oneMinuteOfStats(name).countLastFiveSeconds(now);
    }

    public int lastNSecondsCount(String name, int secondCount) {
        return oneMinuteOfStats(name).countLastSeconds(now, secondCount);
    }

    public int lastNSecondsCountExact(String name, int secondCount) {
        int count = oneMinuteOfStats(name).countLastSeconds(now, secondCount);
        int count2 = lastOneMinuteOfStats(name).countLastSeconds(now, secondCount);
        return count + count2;
    }


    public int lastTenSecondCountExact(String name) {

        return oneMinuteOfStats(name).countLastTenSeconds(now) +
                lastOneMinuteOfStats(name).countLastTenSeconds(now);
    }

    public int lastFiveSecondCountExact(String name) {
        return oneMinuteOfStats(name).countLastFiveSeconds(now) +
                lastOneMinuteOfStats(name).countLastTenSeconds(now);

    }

    public int currentSecondCount(String name) {
        return oneMinuteOfStats(name).countThisSecond(now);
    }

    public int lastSecondCount(String name) {
        return oneMinuteOfStats(name).countLastSecond(now);
    }

    public void recordWithTime(String name, int count, long now) {
        oneMinuteOfStats(name).changeBy(count, now);
        replica.recordCount(name, count, now);
    }

    public void recordAllCounts(final long timestamp,
                                final String[] names,
                                final int[] counts) {
        for (int index = 0; index < names.length; index++) {
            String name = names[index];
            int count = counts[index];
            recordWithTime(name, count, timestamp);
        }
    }

    public void recordAllCountsWithTimes(
            final String[] names,
            final int[] counts,
            final long[] times) {

        for (int index = 0; index < names.length; index++) {
            String name = names[index];
            int count = counts[index];
            long now = times[index];
            recordWithTime(name, count, now);
        }
    }


    private MinuteStat oneMinuteOfStats(String name) {
        MinuteStat oneMinuteOfStats = this.currentMinuteOfStatsMap.get(name);
        if (oneMinuteOfStats == null) {
            oneMinuteOfStats = new MinuteStat(now, name);
            this.currentMinuteOfStatsMap.put(name, oneMinuteOfStats);
        }
        return oneMinuteOfStats;
    }

    private MinuteStat lastOneMinuteOfStats(String name) {
        MinuteStat oneMinuteOfStats = this.lastMinuteOfStatsMap.get(name);
        if (oneMinuteOfStats == null) {
            oneMinuteOfStats = new MinuteStat(now, name);
            this.lastMinuteOfStatsMap.put(name, oneMinuteOfStats);
        }
        return oneMinuteOfStats;
    }


    public void queueLimit() {
        process();
    }

    public void queueEmpty() {
        process();
    }

    public void tick() {
        now = timer.now();
    }

    void process() {
        tick();
        long duration = (now - startMinute) / 1_000;
        if (duration > 60) {
            startMinute = now;

            final ArrayList<MinuteStat> stats = new ArrayList<>(this.currentMinuteOfStatsMap.values());
            this.recorder.record(stats);
            this.lastMinuteOfStatsMap = currentMinuteOfStatsMap;
            this.currentMinuteOfStatsMap = new ConcurrentHashMap<>(100);
        }
        ServiceProxyUtils.flushServiceProxy(replica);

        if (replica instanceof ServiceFlushable) {
            ((ServiceFlushable) replica).flush();
        }
    }

    public int lastMinuteCount(String name) {

        return lastOneMinuteOfStats(name).getTotalCount();
    }
}