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
