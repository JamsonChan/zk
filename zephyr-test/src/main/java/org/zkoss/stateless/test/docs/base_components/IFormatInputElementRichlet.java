/* IFormatInputElementRichlet.java

	Purpose:

	Description:

	History:
		Wed Feb 23 17:44:03 CST 2022, Created by katherine

Copyright (C) 2022 Potix Corporation. All Rights Reserved.
*/
package org.zkoss.stateless.test.docs.base_components;

import java.time.LocalDate;
import java.time.LocalTime;

import org.zkoss.stateless.annotation.RichletMapping;
import org.zkoss.stateless.sul.IFormatInputElement;
import org.zkoss.stateless.ui.StatelessRichlet;
import org.zkoss.stateless.sul.IComponent;
import org.zkoss.stateless.sul.IDatebox;
import org.zkoss.stateless.sul.IDecimalbox;
import org.zkoss.stateless.sul.IDoublebox;
import org.zkoss.stateless.sul.IDoublespinner;
import org.zkoss.stateless.sul.IIntbox;
import org.zkoss.stateless.sul.ILongbox;
import org.zkoss.stateless.sul.ISpinner;
import org.zkoss.stateless.sul.ITimebox;
import org.zkoss.stateless.sul.IVlayout;

/**
 * A set of example for {@link IFormatInputElement} Java Docs.
 * And also refers to something else on <a href="https://www.zkoss.org/wiki/ZK_Component_Reference/Base_Components/FormatInputElement">IFormatInputElement</a>,
 * if any.
 *
 * @author katherine
 * @see IFormatInputElement
 */
@RichletMapping("/base_components/iFormatInputElement")
public class IFormatInputElementRichlet implements StatelessRichlet {

	@RichletMapping("/format")
	public IComponent format() {
		return IVlayout.of(
				IDatebox.of(LocalDate.of(2000, 1, 1)).withFormat("dd/MMM yyyy").withLocale("en_US"),
				IDecimalbox.of("12345").withFormat("@ #,##0.00"),
				IDoublebox.of(12.3).withFormat("#,##0.00"),
				IDoublespinner.of(1234.567).withFormat("00,000.00"),
				IIntbox.of(1234567).withFormat("#,##0 'XYZ'"),
				ILongbox.of(99999).withFormat("00,000.0"),
				ISpinner.of(1).withFormat("#00"),
				ITimebox.of(LocalTime.of(1, 1)).withFormat("a hh:mm:ss").withLocale("en_US")
		);
	}
}