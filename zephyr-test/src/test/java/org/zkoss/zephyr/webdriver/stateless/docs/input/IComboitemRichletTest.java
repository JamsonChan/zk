/* IComboitemRichletTest.java

	Purpose:

	Description:

	History:
		Tue Mar 01 18:15:04 CST 2022, Created by katherine

Copyright (C) 2022 Potix Corporation. All Rights Reserved.
*/
package org.zkoss.zephyr.webdriver.stateless.docs.input;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import org.zkoss.stateless.sul.IComboitem;
import org.zkoss.test.webdriver.WebDriverTestCase;

/**
 * A set of unit test for {@link IComboitem} Java Docs.
 * And also refers to something else on <a href="https://www.zkoss.org/wiki/ZK_Component_Reference/Input/Comboitem">Comboitem</a>,
 * if any.
 *
 * @author katherine
 * @see IComboitem
 */
public class IComboitemRichletTest extends WebDriverTestCase {
	@Test
	public void content() {
		connect("/input/iComboitem/content");
		assertEquals("content 1", jq(".z-comboitem-content").text());
		click(jq("@button"));
		waitResponse();
		assertEquals("content 2", jq(".z-comboitem-content").text());
	}

	@Test
	public void description() {
		connect("/input/iComboitem/description");
		assertEquals("description 1", jq(".z-comboitem-inner").text());
		click(jq("@button"));
		waitResponse();
		assertEquals("description 2", jq(".z-comboitem-inner").text());
	}
}