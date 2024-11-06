/* B80_ZK_3076Test.java

	Purpose:

	Description:

	History:
		Thu, Jun 16, 2016 11:44:16 AM, Created by Sefi

Copyright (C)  Potix Corporation. All Rights Reserved.

This program is distributed under LGPL Version 2.1 in the hope that
it will be useful, but WITHOUT ANY WARRANTY.
*/
package org.zkoss.zktest.zats.test2;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.zkoss.test.webdriver.WebDriverTestCase;

/**
 *
 * @author Sefi
 */
public class B80_ZK_3076Test extends WebDriverTestCase {
    @Test
    public void test(){
        disableConnectionWait();
        connect();
        Assertions.assertTrue(getEval("document.body.innerHTML").contains("Class not found: Date"));
    }
}
