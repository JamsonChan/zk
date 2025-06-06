/* Include.java

	Purpose:
		
	Description:
		
	History:
		Wed Sep 28 18:01:03     2005, Created by tomyeh

Copyright (C) 2005 Potix Corporation. All Rights Reserved.

	This program is distributed under LGPL Version 2.1 in the hope that
	it will be useful, but WITHOUT ANY WARRANTY.
*/
package org.zkoss.zul;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.zkoss.io.Files;
import org.zkoss.json.JavaScriptValue;
import org.zkoss.lang.Exceptions;
import org.zkoss.lang.Library;
import org.zkoss.lang.Objects;
import org.zkoss.mesg.Messages;
import org.zkoss.web.Attributes;
import org.zkoss.web.servlet.Servlets;
import org.zkoss.zk.mesg.MZk;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.Execution;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.IdSpace;
import org.zkoss.zk.ui.Page;
import org.zkoss.zk.ui.UiException;
import org.zkoss.zk.ui.WrongValueException;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.ext.AfterCompose;
import org.zkoss.zk.ui.ext.DynamicPropertied;
import org.zkoss.zk.ui.ext.Includer;
import org.zkoss.zk.ui.metainfo.ComponentInfo;
import org.zkoss.zk.ui.metainfo.DefinitionNotFoundException;
import org.zkoss.zk.ui.metainfo.LanguageDefinition;
import org.zkoss.zk.ui.metainfo.NodeInfo;
import org.zkoss.zk.ui.metainfo.PageDefinition;
import org.zkoss.zk.ui.sys.ComponentRedraws;
import org.zkoss.zk.ui.sys.DesktopCtrl;
import org.zkoss.zk.ui.sys.HtmlPageRenders;
import org.zkoss.zk.ui.sys.UiEngine;
import org.zkoss.zk.ui.sys.WebAppCtrl;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.impl.Utils;
import org.zkoss.zul.impl.XulElement;
import org.zkoss.zul.mesg.MZul;

/**
 * Includes the result generated by any servlet, not limited to a ZUML page.
 *
 * <p>Non-XUL extension.
 *
 * <p>If this component is the only child of its parent, the default width
 * and height will become 100%.
 *
 * <h3>The instant mode</h3>
 *
 * <p>In the instant mode, the page to be included are loaded 'instantly'
 * with {@link Execution#createComponents} when {@link #afterCompose}
 * is called. Furthermore, the components are created as the child components
 * of this include component (like a macro component).
 *
 * <p>Notices:
 * <ul>
 * <li>The instant mode is supported automatically if the include component
 * is created by a ZUML page.
 * If you want to create it programmatically, you have to invoke {@link #afterCompose}
 * after assigning the source ({@link #setSrc}).</li>
 * <li>The instant mode doesn't support {@link #setProgressing} nor
 * {@link #setLocalized}</li>
 * <li>The directives of the included page won't be included.
 * It means &lt;?style?&gt; won't be evaluated.
 * Thus, if you want to embed JavaScript files or codes in a page
 * that might be included, it is better to use the script component
 * ({@link Script}).</li>
 * </ul>
 *
 * <h3>The defer mode</h3>
 *
 * <p>In the defer mode (the only mode supported by ZK prior to 3.6.2),
 * the page is included by servlet container (the <code>include</code> method
 * of <code>javax.servlet.RequestDispatcher</code>) in the render phase
 * (i.e., after all components are created). The page can be any
 * servlet; not limited to a ZUML page.
 *
 * <p>If it is eventually another ZUML page, a ZK page ({@link org.zkoss.zk.ui.Page})
 * is created and added to the current desktop.
 * You can access them only via inter-page API (see{@link org.zkoss.zk.ui.Path}).
 *
 * <p>Notice that if a non-ZUML page, such as HTML fragment, is included,
 * the content might be evaluated before ZK widgets are instantiated and
 * rendered (so-called mounted). Thus, the embedded JavaScript code might be
 * evaluated early. If you prefer to run them later, you could either use
 * <code>zk.afterMount(function(){...})</code> to defer the execute, or
 * specify the custom attribute called <code>org.zkoss.zul.include.html.defer</code>
 * to true.
 *
 * <h3>The auto mode (default)</h3>
 *
 * <p>In the auto mode, the include component decides the mode based on
 * the name specified in the src property ({@link #setSrc}).
 * If <code>src</code> is ended with the extension named <code>.zul</code>
 * or <code>.zhtml</code>, the <code>instant</code> mode is assumed.
 * Otherwise, the <code>defer</code> mode is assumed.
 * Notice that if a query string is specified, the <code>defer</code> mode
 * is assumed, too.
 *
 * <p>Notice that invoking {@link #setProgressing} or {@link #setLocalized}
 * with true will imply the <code>defer</code> mode (if the mode is <code>auto</code>).
 *
 * <p><b>Backward Compatibility:</b>
 * Since 3.6.2, there are three modes: auto (default), instant and defer.
 * The behavior prior to 3.6.2 is the same as the defer mode.
 * The default mode is <code>auto</code> since 5.0.
 * However, you can change it to <code>defer</code> by specifying a library
 * property named <code>org.zkoss.zul.include.mode</code> (for backward
 * compatibility).
 *
 * <h3>Passing Parameters</h3>
 *
 * <p>There are two ways to pass parameters to the included page:
 * <p>First, since ZK 3.0.4,
 * you can use {@link #setDynamicProperty}, or, in ZUL,
 * <pre><code>&lt;include src="/WEB-INF/mypage" arg="something"/&gt;</code></pre>
 *
 * <p>Second, you can use the query string:
 * <pre><code>&lt;include src="/WEB-INF/mypage?arg=something"/&gt;</code></pre>
 *
 * <p>With the query string, you can pass only the String values.
 * and the parameter can be accessed by {@link Execution#getParameter}
 * or javax.servlet.ServletRequest's getParameter.
 * Or, you can access it with the param variable in EL expressions.
 *
 * <p>On the other hand, the dynamic properties ({@link #setDynamicProperty})
 * are passed to the included page thru the request's attributes
 * You can pass any type of objects you want.
 * In the included page, you can access them by use of
 * {@link Execution#getAttribute} or javax.servlet.ServletRequest's
 * getAttribute. Or, you can access with the requestScope variable
 * in EL expressions.
 *
 * <h3>Macro Component versus {@link Include}</h3>
 *
 * If the include component is in the instant mode, it is almost the same as
 * a macro component. On the other hand, if in the defer mode, they are different:
 * <ol>
 * <li>{@link Include} (in defer mode) could include anything include ZUML,
 * JSP or any other
 * servlet, while a macro component could embed only a ZUML page.</li>
 * <li>If {@link Include} (in defer mode) includes a ZUML page, a
 * {@link org.zkoss.zk.ui.Page} instance is created which is owned
 * by {@link Include}. On the other hand, a macro component makes
 * the created components as the direct children -- i.e.,
 * you can browse them with {@link org.zkoss.zk.ui.Component#getChildren}.</li>
 * <li>{@link Include} (in defer mode) creates components in the Rendering phase,
 * while a macro component creates components in {@link org.zkoss.zk.ui.HtmlMacroComponent#afterCompose}.</li>
 * <li>{@link Include#invalidate} (in defer mode) will cause it to re-include
 * the page (and then recreate the page if it includes a ZUML page).
 * However, {@link org.zkoss.zk.ui.HtmlMacroComponent#invalidate} just causes it to redraw
 * and update the content at the client -- like any other component does.
 To re-create, you have to invoke {@link org.zkoss.zk.ui.HtmlMacroComponent#recreate}.</li>
 * </ol>
 *
 * <p>In additions to macro and {@link Include}, you can use the fulfill
 * attribute as follows:
 * <code>&lt;div fulfill="=/my/foo.zul"&gt;...&lt;/div&gt;</code>
 *
 * <h3>Custom Attribute</h3>
 * <dl>
 * <dt>org.zkoss.zul.include.html.defer</dt>
 * <dd>[default: false] Whether to defer the rendering of non-ZUML page until all widgets are
 * instantiated and rendered at client (so-called mounted).</dd>
 * </dl>
 * @author tomyeh
 * @see Iframe
 */
public class Include extends XulElement implements Includer, DynamicPropertied, AfterCompose, IdSpace {
	private static final Logger log = LoggerFactory.getLogger(Include.class);
	private static final String ATTR_RENDERED = "org.zkoss.zul.Include.rendered";
	private String _src;
	private Map<String, Object> _dynams;
	/** The child page. Note: it is recovered by PageImpl. */
	private transient Page _childpg;
	private String _mode = getDefaultMode();
	private String _renderResult;
	private boolean _localized;
	private boolean _progressing;
	private boolean _afterComposed;
	private boolean _instantMode;
	private boolean _comment;
	/** 0: not yet handled, 1: wait for echoEvent, 2: done. */
	private byte _progressStatus;
	//F70-ZK-2455 change enclosing tag
	private String _tag = "div";

	public Include() {
		setAttribute("z$is", Boolean.TRUE); //optional but optimized to mean no need to generate z$is since client handles it
	}

	public Include(String src) {
		this();
		setSrc(src);
	}

	/**
	 * Sets whether to show the {@link MZul#PLEASE_WAIT} message before a long operation.
	 * This implementation will automatically use an echo event like {@link Events#echoEvent(String, org.zkoss.zk.ui.Component, String)} 
	 * to suspend the including progress before using the {@link Clients#showBusy(String)} 
	 * method to show the {@link MZul#PLEASE_WAIT} message at client side. 
	 *
	 * <p>If setProgressing(true) is called, the <code>defer</code> mode is enabled automatically
	 * if the current mode is <code>auto</code>.
	 * 
	 * <p>Default: false.
	 * @since 3.0.4
	 */
	public void setProgressing(boolean progressing) {
		if (_progressing != progressing) {
			if (progressing && "instant".equals(_mode))
				throw new UnsupportedOperationException("progressing not allowed in instant mode");

			_progressing = progressing;
			fixMode(); //becomes defer mode if auto
			checkProgressing();

			if (!_instantMode) {
				getChildren().clear();
				invalidate();
			} else
				super.invalidate();
		}
	}

	/**
	 * Returns whether to show the {@link MZul#PLEASE_WAIT} message before a long operation.
	 * <p>Default: false.
	 * @since 3.0.4
	 */
	public boolean getProgressing() {
		return _progressing;
	}

	/**
	 * Internal use only.
	 *@since 3.0.4
	 */
	public void onEchoInclude() {
		Clients.clearBusy();
		super.invalidate();
	}

	/** Returns the src.
	 * <p>Default: null.
	 */
	public String getSrc() {
		return _src;
	}

	/** Sets the src.
	 *
	 * <p>If src is changed, the whole component is invalidate.
	 * Thus, you want to smart-update, you have to override this method.
	 *
	 * @param src the source URI. If null or empty, nothing is included.
	 * You can specify the source URI with the query string and they
	 * will become a parameter that can be accessed by use
	 * of {@link Execution#getParameter} or javax.servlet.ServletRequest's getParameter.
	 * For example, if "/a.zul?b=c" is specified, you can access
	 * the parameter with ${param.b} in a.zul.
	 * @see #setDynamicProperty
	 */
	public void setSrc(String src) {
		if (src != null && src.length() == 0)
			src = null;

		if (!Objects.equals(_src, src)) {
			_src = src;
			fixMode();
			if (!_instantMode)
				invalidate();
			else
				super.invalidate();
			//invalidate is redundant in instant mode, but less memory leak in IE
		}
	}

	// B60-ZK-1160: Exception when closing tab with included content
	// Must clean up included content before detaching tab panel
	public void detach() {
		if (_childpg != null) {
			_childpg.removeComponents();
			_childpg = null;
		}
		super.detach();
	}

	/** Returns the inclusion mode.
	 * There are three modes: auto, instant and defer.
	 * The behavior prior to 3.6.2 is the same as the defer mode.
	 * <p>Default: auto (since 5.0.0).
	 * @since 3.6.2
	 */
	public String getMode() {
		return _mode;
	}

	/** Sets the inclusion mode.
	 * @param mode the inclusion mode: auto, instant or defer.
	 * @since 3.6.2
	 */
	public void setMode(String mode) throws WrongValueException {
		if (!_mode.equals(mode)) {
			if (!"auto".equals(mode) && !"instant".equals(mode) && !"defer".equals(mode))
				throw new WrongValueException("Unknown mode: " + mode);
			if ((_localized || _progressing) && "instant".equals(mode))
				throw new UnsupportedOperationException("localized/progressing not allowed in the instant mode");

			_mode = mode;
			fixMode();
			if (!_instantMode)
				invalidate();
			else
				super.invalidate();
		}
	}

	/** Returns the name of the enclosing tag.
	 * <p>Default: div 
	 * @since 7.0.4
	 */
	public String getEnclosingTag() {
		return _tag;
	}

	/**Sets the the name of the enclosing tag
	 * <p>Default: div
	 * @since 7.0.4
	 */
	public void setEnclosingTag(String tag) {
		if (tag == null || tag.length() == 0)
			throw new IllegalArgumentException();
		if (!_tag.equals(tag)) {
			_tag = tag;
			smartUpdate("enclosingTag", _tag);
		}
	}

	private void fixMode() {
		fixModeOnly();
		// see the comment inside applyChangesToContent();
		applyChangesToContent();
	}

	private void fixModeOnly() { //called by afterCompose
		if ("auto".equals(_mode)) {
			if (_src != null && !_progressing && !_localized) {
				// according to the spec if query string exists, it should be defer
				// mode automatically.
				if (_src.contains("?")) {
					_instantMode = false;
				} else {
					String ext = Servlets.getExtension(_src);

					// ZK-2567: use defer mode for unrecognized files
					try {
						LanguageDefinition lang = LanguageDefinition.getByExtension(ext);
						_instantMode = ("xhtml".equals(lang.getName()) || "xul/html".equals(lang.getName()));
					} catch (DefinitionNotFoundException e) {
						_instantMode = false;
					}
				}
			} else
				_instantMode = false;
		} else
			_instantMode = "instant".equals(_mode);
	}

	private void applyChangesToContent() {
		if (_src == null) {
			if (!getChildren().isEmpty())
				getChildren().clear();
			else if (!_instantMode && getChildPage() != null)
				getChildPage().removeComponents();
		} else {
			if (_instantMode && _afterComposed)
				afterCompose();
			else if (!_instantMode && "auto".equals(getMode()) && !getChildren().isEmpty())
				//Bug ZK-1437: auto mode has no chance to clear the content if src is changed (_instantMode become false)
				getChildren().clear();
		}
	}

	/** Returns whether the source depends on the current Locale.
	 * If true, it will search xxx_en_US.yyy, xxx_en.yyy and xxx.yyy
	 * for the proper content, where src is assumed to be xxx.yyy.
	 *
	 * <p>Default: false;
	 */
	public boolean isLocalized() {
		return _localized;
	}

	/** Sets whether the source depends on the current Locale.
	 */
	public void setLocalized(boolean localized) {
		if (_localized != localized) {
			if (localized && "instant".equals(_mode))
				throw new UnsupportedOperationException("localized not supported in instant mode yet");

			_localized = localized;
			if (_localized)
				fixMode(); //becomes defer mode if auto
			if (!_instantMode)
				invalidate();
			else
				super.invalidate();
			//invalidate is redundant in instant mode, but less memory leak in IE
		}
	}

	/** Returns whether to generate the included content inside
	 * the HTML comment.
	 * <p>Default: false.
	 *
	 * <p>It is useful if you want to include non-HTML content.
	 * For example, 
	 * <pre><code>&lt;include src="a.xml" comment="true"/&gt;</code></pre>
	 * Then, it will generate
	 * <pre><code>&lt;div id="uuid"&gt;
	 *&lt;!--
	 * //the content of a.xml
	 *--&gt;
	 *&lt;/div&gt;</code></pre>
	 *
	 * <p>Notice that it is ignored in the instance mode ({@link #getMode}).
	 * @since 5.0.0
	 */
	public boolean isComment() {
		return _comment;
	}

	/** Sets  whether to generate the included content inside
	 * the HTML comment.
	 * @see #isComment
	 * @since 5.0.0
	 */
	public void setComment(boolean comment) {
		_comment = comment;
	}

	//Includer//
	public Page getChildPage() {
		return _childpg;
	}

	public void setChildPage(Page page) {
		if (_childpg != null && page == null) {
			final Desktop desktop = getDesktop();
			if (desktop != null)
				((DesktopCtrl) desktop).removePage(_childpg);
		}
		_childpg = page;
	}

	public void setRenderingResult(String result) {
		_renderResult = result;
	}

	public void onPageAttached(Page newpage, Page oldpage) {
		if (newpage != null)
			Events.postEvent("onAfterCompose", this, null);
		super.onPageAttached(newpage, oldpage);
	}

	public void onAfterCompose() {
		if (!_afterComposed)
			afterCompose();
	}

	//AfterCompose//
	public void afterCompose() {
		_afterComposed = true;
		fixModeOnly();
		if (_instantMode) {
			final Execution exec = getExecution();
			final Map<String, Object> old = setupDynams(exec);
			final String attrRenderedKey = ATTR_RENDERED + '$' + getUuid();
			final String oldSrc = (String) exec.getAttribute(attrRenderedKey);
			if (!Objects.equals(oldSrc, _src)) {
				try {
					getChildren().clear();
					final int j = _src.indexOf('?');
					// ZK-2642: Check if included html file will produce duplicated html, head and body tag
					PageDefinition pdef = exec.getPageDefinition(j >= 0 ? _src.substring(0, j) : _src);
					List<NodeInfo> nodes = pdef.getChildren();
					if (nodes != null && nodes.size() > 0) {
						NodeInfo firstNode = nodes.get(0);
						if (firstNode instanceof ComponentInfo && "html".equals(((ComponentInfo) firstNode).getTag()))
							throw new UiException(
									"Root element <html> and DOCTYPE are not allowed in included file: [" + _src + "]");
					}
					exec.createComponents(pdef, this, _dynams);
					if (j >= 0)
						log.warn("Query string is not allowed in instant mode: [" + _src + "]");
					exec.setAttribute(attrRenderedKey, _src);
				} finally {
					restoreDynams(exec, old);
				}
			}
		} else {
			// just in case
			getChildren().clear();
		}
	}

	private Execution getExecution() {
		final Desktop desktop = getDesktop();
		return desktop != null ? desktop.getExecution() : Executions.getCurrent();
	}

	//DynamicPropertied//
	/** Returns whether a dynamic property is defined.
	 */
	public boolean hasDynamicProperty(String name) {
		return _dynams != null && _dynams.containsKey(name);
	}

	public Map<String, Object> getDynamicProperties() {
		return _dynams;
	}

	/** Returns the parameter associated with the specified name,
	 * or null if not found.
	 *
	 * @since 3.0.4
	 * @see #setDynamicProperty
	 */
	public Object getDynamicProperty(String name) {
		return _dynams != null ? _dynams.get(name) : null;
	}

	/** Adds a dynamic property that will be passed to the included page
	 * via the request's attribute.
	 *
	 * <p>For example, if setDynamicProperty("abc", new Integer(4)) is called,
	 * then the included page can retrieved the abc property
	 * by use of <code>${reqestScope.abc}</code>
	 *
	 * @since 3.0.4
	 */
	public void setDynamicProperty(String name, Object value) {
		if (_dynams == null)
			_dynams = new HashMap<String, Object>();
		_dynams.put(name, value);
	}

	/** Removes all dynamic properties.
	 * @since 5.0.1
	 */
	public void clearDynamicProperties() {
		_dynams = null;
	}

	//-- Component --//
	/** Invalidates this component.
	 * It works for both the instant and defer mode.
	 * Notice that all children will be detached (the instant mode) and
	 * the page will be reloaded (and new children will be created).
	 */
	public void invalidate() {
		super.invalidate();
		//invalidate is redundant in instant mode, but less memory leak in IE

		// see the comment inside applyChangesToContent();
		applyChangesToContent();

		if (_progressStatus >= 2)
			_progressStatus = 0;
		checkProgressing();
	}

	/** Checks if _progressingg is defined.
	 */
	private void checkProgressing() {
		if (_progressing && _progressStatus == 0) {
			_progressStatus = 1;
			Clients.showBusy(Messages.get(MZul.PLEASE_WAIT));
			Events.echoEvent("onEchoInclude", this, null);
		}
	}

	/** Default: not childable.
	 */
	protected boolean isChildable() {
		return _instantMode;
	}

	protected void renderProperties(org.zkoss.zk.ui.sys.ContentRenderer renderer) throws java.io.IOException {
		super.renderProperties(renderer);

		setChildPage(null);
		render(renderer, "comment", _comment);
		render(renderer, "enclosingTag", _tag);

		if (_instantMode && _afterComposed)
			return; //instant mode (done by redrawChildren())

		final UiEngine ueng = ((WebAppCtrl) getDesktop().getWebApp()).getUiEngine();
		Component old = ueng.setOwner(this);
		try {
			if (_progressStatus == 1) {
				_progressStatus = 2;
			} else if (_src != null && _src.length() > 0) {
				final StringBuffer incsb;
				{
					final StringWriter sw = new StringWriter();
					include(sw);
					incsb = sw.getBuffer();
					// ZK-2642: Check if included html file will produce duplicated html, head and body tag
					String str = incsb != null ? incsb.toString() : "";
					if (!str.isEmpty() && (str.contains("<html") || str.contains("<!DOCTYPE")))
						throw new UiException(
								"Root element <html> and DOCTYPE are not allowed in included file: [" + _src + "]");
				}

				//Don't output sw directly if getChildPage() is not null
				//Otherwise, script of the included zul page will be evaluated
				//first (since it is part of rc.temp)

				if (getChildPage() == null //only able to handle non-ZUL page
						&& !Utils.testAttribute(this, "org.zkoss.zul.include.html.defer", false, true)) {
					final HtmlPageRenders.RenderContext rc = HtmlPageRenders.getRenderContext(null);
					if (rc != null && !rc.included) { //Use zk().detachChildren() only if not included
						final Writer cwout = rc.temp;
						cwout.write("<div id=\"");
						cwout.write(getUuid());
						cwout.write("\" style=\"display:none\">");
						if (_comment)
							cwout.write("\n<!--\n");
						Files.write(cwout, incsb);
						if (_comment)
							cwout.write("\n-->\n");
						cwout.write("</div>");

						renderer.render("_xcnt", new JavaScriptValue("zk('" + getUuid() + "').detachChildren()"));
						return; //done
					}
				}

				renderer.render("_xcnt", incsb);
				if (_renderResult != null && _renderResult.length() > 0)
					renderer.renderDirectly("_childjs",
							"function(){"
									// B65-ZK-1836
									+ _renderResult.replaceAll("</(?i)(?=script>)", "<\\\\/") + '}');
			}
		} finally {
			_renderResult = null;
			ueng.setOwner(old);
		}
	}

	private void include(Writer out) throws IOException {
		final Desktop desktop = getDesktop();
		final Execution exec = getExecution();
		final String src = exec.toAbsoluteURI(_src, false);
		final Map<String, Object> old = setupDynams(exec);
		ComponentRedraws.beforeRedraw(true); //starting a new page
		try {
			exec.include(out, src, null, 0);
		} catch (Throwable err) {
			setChildPage(null);
			//though DHtmlLayoutServlet handles exception, we still have to
			//handle it because src might not be ZUML
			final String errpg = desktop.getWebApp().getConfiguration().getErrorPage(desktop.getDeviceType(), err);
			if (errpg != null) {
				try {
					exec.setAttribute("javax.servlet.error.message", Exceptions.getMessage(err));
					exec.setAttribute("javax.servlet.error.exception_list", List.of(err));
					exec.setAttribute("javax.servlet.error.exception", err);
					exec.setAttribute("javax.servlet.error.exception_type", err.getClass());
					exec.setAttribute("javax.servlet.error.status_code", new Integer(500));
					exec.include(out, errpg, null, 0);
					return; //done
				} catch (IOException ex) { //eat it (connection off)
				} catch (Throwable ex) {
					log.warn("Failed to load the error page: " + errpg, ex);
				}
			}

			final String msg = Messages.get(MZk.PAGE_FAILED,
					new Object[] { src, Exceptions.getMessage(err), Exceptions.formatStackTrace(null, err, null, 6) });
			final Map<String, String> attrs = new HashMap<String, String>();
			attrs.put(Attributes.ALERT_TYPE, "error");
			attrs.put(Attributes.ALERT, msg);
			exec.include(out, "~./html/alert.dsp", attrs, Execution.PASS_THRU_ATTR);
		} finally {
			ComponentRedraws.afterRedraw();
			restoreDynams(exec, old);
		}
	}

	private Map<String, Object> setupDynams(Execution exec) {
		if (_dynams == null || _dynams.isEmpty())
			return null;

		final Map<String, Object> old = new HashMap<String, Object>();
		for (Map.Entry<String, Object> me : _dynams.entrySet()) {
			final String nm = me.getKey();
			final Object val = me.getValue();

			old.put(nm, exec.getAttribute(nm));

			if (val != null)
				exec.setAttribute(nm, val);
			else
				exec.removeAttribute(nm);
		}
		return old;
	}

	private static void restoreDynams(Execution exec, Map<String, Object> old) {
		if (old != null)
			for (Map.Entry<String, Object> me : old.entrySet()) {
				final String nm = me.getKey();
				final Object val = me.getValue();

				if (val != null)
					exec.setAttribute(nm, val);
				else
					exec.removeAttribute(nm);
			}
	}

	private static String getDefaultMode() {
		if (_defMode == null)
			_defMode = Library.getProperty("org.zkoss.zul.include.mode", "auto");
		return _defMode;
	}

	private static String _defMode;
}
