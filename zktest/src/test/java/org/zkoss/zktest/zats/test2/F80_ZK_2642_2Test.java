/* F80_ZK_2642_2Test.java

	Purpose:
		
	Description:
		
	History:
		Tue Dec 29 10:53:28 CST 2015, Created by Jameschu

Copyright (C) 2015 Potix Corporation. All Rights Reserved.
*/
package org.zkoss.zktest.zats.test2;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

import org.zkoss.zktest.zats.ZATSTestCase;

/**
 * @author jameschu
 */
public class F80_ZK_2642_2Test extends ZATSTestCase {
	@Test
	public void test() {
		try {
			connect();
		} catch (Exception e) {
			assertTrue(e.getMessage().toString().contains("Root element <html> and DOCTYPE are not allowed in included file"));
			return;
		}
		fail();
	}
}
