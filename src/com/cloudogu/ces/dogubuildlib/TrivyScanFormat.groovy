package com.cloudogu.ces.dogubuildlib

/**
 * Defines the output format for the trivy report.
 * Should have been an enum, but unfortunately jenkins does not allow enums.
 */
class TrivyScanFormat {
    /**
     * Output as HTML file.
     */
    static String HTML = "html"

    /**
     * Output as JSON file.
     */
    static String JSON = "json"

    /**
     * Output as plain text file.
     */
    static String PLAIN = "plain"
}