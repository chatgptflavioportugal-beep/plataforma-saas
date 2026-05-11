package com.saas.exception;

public class PlanFeatureNotAvailableException extends RuntimeException {

    private final String feature;
    private final String currentPlan;
    private final String requiredPlan;

    public PlanFeatureNotAvailableException(String feature, String currentPlan, String requiredPlan) {
        super("Feature não disponível no plano atual: " + feature);
        this.feature = feature;
        this.currentPlan = currentPlan;
        this.requiredPlan = requiredPlan;
    }

    public String getFeature() { return feature; }
    public String getCurrentPlan() { return currentPlan; }
    public String getRequiredPlan() { return requiredPlan; }
}
