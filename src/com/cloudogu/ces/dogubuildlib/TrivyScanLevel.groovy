package com.cloudogu.ces.dogubuildlib

/**
 * Defines the vulnerability Levels which should be mentioned.
 * Should have been an enum, but unfortunately jenkins does not allow enums.
 */
class TrivyScanLevel {
    /**
     * Only critical vulnerabilities.
     */
    static String CRITICAL = "CRITICAL"

    /**
     * High or critical vulnerabilities.
     */
    static String HIGH = "CRITICAL,HIGH"

    /**
     * Medium or higher vulnerabilities.
     */
    static String MEDIUM = "CRITICAL,HIGH,MEDIUM"

    /**
     * All vunlerabilities.
     */
    static String ALL = "UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL"
}