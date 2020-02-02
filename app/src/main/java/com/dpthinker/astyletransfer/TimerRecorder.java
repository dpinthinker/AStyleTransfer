/**
 * Copyright 2019-2020 dpthinker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dpthinker.astyletransfer;

public class TimerRecorder {
    public final static String CPU = "CPU";
    public final static String GPU = "GPU";
    public final static String NNAPI = "NNAPI";

    private static String predictType = CPU;
    private static String predictTime;
    private static String transformTime;

    private static TimerRecorder recorder = null;

    public static TimerRecorder getInstance() {
        if (recorder == null) {
            recorder = new TimerRecorder();
        }

        return recorder;
    }

    public void clean() {
        resetPredictType();
        predictTime = null;
        transformTime = null;
    }

    private void resetPredictType() {
        predictType = CPU;
    }

    public void setPredictTime(String predictTime) {
        this.predictTime = predictTime;
    }

    public void setPredictType(String predictType) {
        this.predictType = predictType;
    }

    public void setTransformTime(String transformTime) {
        this.transformTime = transformTime;
    }

    public String getCollectedTime() {
        StringBuffer result = new StringBuffer();
        result.append("Predict: ").append(predictType).append(", ")
                .append(predictTime).append("ms");
        result.append("\n");
        result.append("Transform: ").append("CPU, ").append(transformTime).append("ms");
        return result.toString();
    }
}
