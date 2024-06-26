/* F95_ZK_4423_3Test.java

		Purpose:
		
		Description:
		
		History:
				Thu Nov 05 16:35:48 CST 2020, Created by leon

Copyright (C) 2020 Potix Corporation. All Rights Reserved.
*/
package org.zkoss.zktest.zats.test2;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.interactions.Actions;

import org.zkoss.test.webdriver.WebDriverTestCase;

public class F95_ZK_4423_3Test extends WebDriverTestCase {
	@Test
	public void test() {
		Actions act = new Actions(connect());

		act.dragAndDrop(toElement(jq(".z-panel-header-move").eq(2)), toElement(jq(".z-panel-header-move").eq(0))).perform();
		waitResponse();
		Assertions.assertEquals("onPortalDrop\n" + "cross 2 column!\n" + "onPortalMove", getZKLog());
		closeZKLog();
		Assertions.assertEquals(1, jq(".z-portalchildren").eq(0).toWidget().nChildren());
		Assertions.assertEquals(1, jq(".z-portalchildren").eq(1).toWidget().nChildren());
		Assertions.assertEquals(1, jq(".z-portalchildren").eq(2).toWidget().nChildren());

		act.dragAndDrop(toElement(jq(".z-panel-header-move").eq(0)), toElement(jq(".z-panel-header-move").eq(1))).perform();
		waitResponse();
		Assertions.assertEquals("onPortalDrop\n" + "moved!\n" + "onPortalMove", getZKLog());
		closeZKLog();
		Assertions.assertEquals(0, jq(".z-portalchildren").eq(0).toWidget().nChildren());
		Assertions.assertEquals(2, jq(".z-portalchildren").eq(1).toWidget().nChildren());
		Assertions.assertEquals(1, jq(".z-portalchildren").eq(2).toWidget().nChildren());


		act.dragAndDrop(toElement(jq(".z-panel-header-move").eq(3)), toElement(jq(".z-panel-header-move").eq(4))).perform();
		waitResponse();
		Assertions.assertEquals("onPortalDrop\n" + "onPortalMove", getZKLog());
		closeZKLog();
		Assertions.assertEquals(0, jq(".z-portalchildren").eq(3).toWidget().nChildren());
		Assertions.assertEquals(2, jq(".z-portalchildren").eq(4).toWidget().nChildren());

		act.dragAndDrop(toElement(jq(".z-panel-header-move").eq(5)), toElement(jq(".z-panel-header-move").eq(6))).perform();
		waitResponse();
		Assertions.assertEquals("onPortalDrop\n" + "onPortalMove", getZKLog());
		closeZKLog();
		Assertions.assertFalse(jq(".z-panel").eq(5).toWidget().is("visible"),
				"the dragged panel should be invisible");
	}
}
