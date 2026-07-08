package com.praxis.prism.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Thresholds that turn raw metrics into findings. Tunable via praxis.prism.*. */
@ConfigurationProperties(prefix = "praxis.prism")
public class PrismProperties {

    /** A method at/above this cyclomatic complexity is flagged HIGH_COMPLEXITY. */
    private int highComplexity = 10;
    /** A method longer than this many lines is flagged LONG_METHOD. */
    private int longMethodLoc = 60;
    /** A class with more methods than this is a GOD_OBJECT candidate. */
    private int godObjectMethods = 20;
    /** A class longer than this many lines is a GOD_OBJECT candidate. */
    private int godObjectLoc = 400;

    public int getHighComplexity() { return highComplexity; }
    public void setHighComplexity(int v) { this.highComplexity = v; }
    public int getLongMethodLoc() { return longMethodLoc; }
    public void setLongMethodLoc(int v) { this.longMethodLoc = v; }
    public int getGodObjectMethods() { return godObjectMethods; }
    public void setGodObjectMethods(int v) { this.godObjectMethods = v; }
    public int getGodObjectLoc() { return godObjectLoc; }
    public void setGodObjectLoc(int v) { this.godObjectLoc = v; }
}
