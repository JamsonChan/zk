/* Issue0079Test.java

	Purpose:
		
	Description:
		
	History:
		4:51 PM 2021/11/3, Created by jumperchen

Copyright (C) 2021 Potix Corporation. All Rights Reserved.
*/
package org.zkoss.zephyr.webdriver.stateless.issues;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import org.zkoss.test.webdriver.WebDriverTestCase;

/**
 * @author jumperchen
 */
public class Issue0079Test extends WebDriverTestCase {
	@Test
	public void testNoException() {
		connect("/stateless/issues/issue_0079.sul");

		click(jq("$btn"));
		waitResponse();
		assertEquals("OK", jq("$text").val());
	}
}