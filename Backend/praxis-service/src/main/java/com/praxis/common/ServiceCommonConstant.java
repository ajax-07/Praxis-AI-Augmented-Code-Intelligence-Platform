package com.praxis.common;

public class ServiceCommonConstant {
    private ServiceCommonConstant() {
        // this is private constructor to secure the public constant
    }

    public static final String PROMPT_VERSION = "v1";
    public static final String PROGRESS = "progress";
    public static final String  CURRENT_STATUS = "Current status: ";
    public static final String  ANALYSIS_ID = "analysisId";
    public static final String CONSUMER_GROUP = "praxis-workers";
    public static final String PARSING_MSG = "Parsing and measuring code…";
    public static final String ANALYZING_MSG = "Selecting high-risk code for review…";
    public static final String SUMMARIZING_MSG = "Generating explanations and refactors…";
    public static final String SCORING_MSG = "Computing repository health score…";
}
