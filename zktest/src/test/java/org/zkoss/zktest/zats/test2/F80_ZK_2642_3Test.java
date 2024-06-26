/* F80_ZK_2642_3Test.java

	Purpose:
		
	Description:
		
	History:
		Tue Dec 29 10:53:28 CST 2015, Created by Jameschu

Copyright (C) 2015 Potix Corporation. All Rights Reserved.
*/
package org.zkoss.zktest.zats.test2;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.zkoss.test.webdriver.WebDriverTestCase;
import org.zkoss.test.webdriver.ztl.JQuery;

/**
 * @author jameschu
 */
public class F80_ZK_2642_3Test extends WebDriverTestCase {
	@Test
	public void test() {
		connect();
		click(jq("@button").get(0));
		waitResponse();
		JQuery jq = jq(".z-messagebox span");
		assertTrue(jq.exists());
		assertTrue(jq.html().contains("Root element &lt;html&gt; and DOCTYPE are not allowed in included file"));
	}
}
