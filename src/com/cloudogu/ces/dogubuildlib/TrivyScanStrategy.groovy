package com.cloudogu.ces.dogubuildlib

/**
 * Definitions of scan strategies for trivy scanner.
 * Should have been an enum, but unfortunately jenkins does not allow enums.
 */
class TrivyScanStrategy {
    /**
     * Strategy: Fail when any vulnerability was found.
     */
    static String FAIL = "fail"

    /**
     * Strategy: Make build unstable when any vulnerability was found.
     */
    static String UNSTABLE = "unstable"

    /**
     * Strategy: Ignore any found vulnerability.
     */
    static String IGNORE = "ignore"
}