package com.saas.profile.dto.response;

public record SuccessResponse(boolean success) {
    public static final SuccessResponse OK = new SuccessResponse(true);
}
