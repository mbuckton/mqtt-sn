/*
 * Copyright (c) 2021 Simon Johnson <simon622 AT gmail DOT com>
 *
 * Find me on GitHub:
 * https://github.com/simon622
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.slj.mqtt.sn.load.runner;

import org.slj.mqtt.sn.load.ExecutionProfile;

import java.util.concurrent.*;
import java.util.logging.Level;

public class ThreadPoolLoadTestRunner extends AbstractLoadTestRunner {

    static final int THREAD_COUNT = 250;
    static final int BOUNDED_QUEUE_SIZE = 1000000;
    private ThreadPoolExecutor executorService = null;

    public ThreadPoolLoadTestRunner(Class<? extends ExecutionProfile> profile, int numInstances, int rampSeconds) {
        super(profile, numInstances, rampSeconds);
    }

    protected void init() {
        if(executorService == null) {
            final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(BOUNDED_QUEUE_SIZE);
            executorService = new ThreadPoolExecutor(THREAD_COUNT, THREAD_COUNT, 0, TimeUnit.MILLISECONDS, queue, this.factory, new RejectedExecutionHandler() {
                public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                    try {
                        logger.log(Level.WARNING, "could not submit job to pooled service - queue and pool exhausted");
                    } catch(Exception e) {
                        logger.log(Level.SEVERE, "error tidying up no-run simulator", e);
                    }
                }
            });
        }
    }

    void run(Runnable runnable) {
        executorService.submit(runnable);
    }
}
