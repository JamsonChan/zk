/* South.ts

	Purpose:

	Description:

	History:
		Wed Jan  7 12:14:58     2009, Created by jumperchen

Copyright (C) 2008 Potix Corporation. All Rights Reserved.

This program is distributed under LGPL Version 2.1 in the hope that
it will be useful, but WITHOUT ANY WARRANTY.
*/
/**
 * A south region of a border layout.
 * @defaultValue {@link getZclass}: z-south.
 */
@zk.WrapClass('zul.layout.South')
export class South extends zul.layout.LayoutRegion {
	/** @internal */
	_sumFlexHeight = true; //indicate shall add this flex height for borderlayout. @See _fixMinFlex in widget.js
	/** @internal */
	override _cmargins = [0, 0, 0, 0];
	override sanchor = 'b';

	/**
	 * The width can't be specified in this component because its width is
	 * determined by other region components ({@link West} or {@link East}).
	 */
	override setWidth(width: string): this { // readonly
		return this;
	}

	/**
	 * @returns The value {@link zul.layout.Borderlayout.SOUTH}.
	 */
	override getPosition(): string {
		return zul.layout.Borderlayout.SOUTH;
	}

	/**
	 * @returns the size of this region. This method is shortcut for {@link getHeight}.
	 */
	getSize(): string | undefined {
		return this.getHeight();
	}

	/**
	 * Sets the size of this region. This method is shortcut for {@link setHeight}.
	 */
	setSize(size: string): this {
		return this.setHeight(size);
	}

	/** @internal */
	override _ambit2(ambit: zul.layout.LayoutRegionAmbit, mars: zk.Dimension, split: { offsetWidth; offsetHeight }): void {
		ambit.w = mars.width;
		ambit.h += split.offsetHeight;
		ambit.ts = ambit.y + ambit.h + (mars.height - mars.top); // total size;
		ambit.y = ambit.h + (mars.height - mars.top);
	}

	/** @internal */
	override _reszSp2(ambit: zul.layout.LayoutRegionAmbit, split: { w; h }): Partial<{ left; top; width; height }> {
		ambit.h -= split.h;
		ambit.y += split.h;
		return {
			left: jq.px0(ambit.x),
			top: jq.px0(ambit.y - split.h),
			width: jq.px0(ambit.w)
		};
	}
}