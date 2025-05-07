/* F102_ZK_5640Test.java

        Purpose:
                
        Description:
                
        History:
                Wed May 07 15:27:20 CST 2025, Created by jamson

Copyright (C) 2025 Potix Corporation. All Rights Reserved.
*/
package org.zkoss.zktest.zats.test2;

import java.time.zone.ZoneRulesProvider;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.LogRecord;

import org.zkoss.lang.Library;
import org.zkoss.test.webdriver.WebDriverTestCase;

public class F102_ZK_5640Test extends WebDriverTestCase {

    @Test
    public void testDifferentVersions() {
        Logger logger = Logger.getLogger("org.zkoss.zk.ui.http.Wpds");
        TestLogHandler handler = new TestLogHandler();
        logger.addHandler(handler);
        Library.setProperty("org.zkoss.zk.moment.timezone.data.version", getJavaTZDBVersion() + "_"); // simulate different versions

        connect();
        waitResponse();

        StringBuilder sb = new StringBuilder();
        for (String log : handler.getLogs())
            sb.append(log);

        // if 2 version are the different, should log warning
        Assertions.assertEquals(getDifferentVersionExpectResult(), sb.toString());
    }

    @Test
    public void testSameVersions() {
        Logger logger = Logger.getLogger("org.zkoss.zk.ui.http.Wpds");
        TestLogHandler handler = new TestLogHandler();
        logger.addHandler(handler);
        Library.setProperty("org.zkoss.zk.moment.timezone.data.version", getJavaTZDBVersion()); // simulate same versions

        connect();
        waitResponse();

        StringBuilder sb = new StringBuilder();
        for (String log : handler.getLogs())
            sb.append(log);

        // if 2 version are the same, shouldn't log warning
        Assertions.assertEquals("", sb.toString());
    }

    private String getDifferentVersionExpectResult() {
        return "Time zone data version mismatch detected:\n" +
                " - Client (moment.js) tzdb version: " + getJsTZDBVersion() + "\n" +
                " - Server (JDK) tzdb version: " + getJavaTZDBVersion() + "\n" +
                "Date and time values may be incorrect if time zone rules differ.\n" +
                "To resolve, update the moment-timezone data on the client and/or the JDK time zone data (TZUpdater or Java update) on the server so both use the same version.";
    }

    private String getJavaTZDBVersion() {
        return ZoneRulesProvider.getVersions("UTC").lastEntry().getKey();
    }

    private String getJsTZDBVersion() {
        return Library.getProperty("org.zkoss.zk.moment.timezone.data.version");
    }

    private static class TestLogHandler extends Handler {

        private final List<String> logs = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            logs.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
        }

        public List<String> getLogs() {
            return logs;
        }
    }
}
