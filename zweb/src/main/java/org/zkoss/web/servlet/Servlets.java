/* Servlets.java

	Purpose:
	Description:
	History:
	90/12/10 22:24:28, Create, Tom M. Yeh.

Copyright (C) 2001 Potix Corporation. All Rights Reserved.

{{IS_RIGHT
	This program is distributed under LGPL Version 2.1 in the hope that
	it will be useful, but WITHOUT ANY WARRANTY.
}}IS_RIGHT
*/
package org.zkoss.web.servlet;

import static org.zkoss.lang.Generics.cast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.zkoss.idom.Element;
import org.zkoss.idom.input.SAXBuilder;
import org.zkoss.lang.SystemException;
import org.zkoss.util.CacheMap;
import org.zkoss.util.Checksums;
import org.zkoss.util.Locales;
import org.zkoss.util.URLs;
import org.zkoss.util.resource.Locator;
import org.zkoss.util.resource.Locators;
import org.zkoss.web.Attributes;
import org.zkoss.web.servlet.http.Encodes;
import org.zkoss.web.servlet.http.Https;
import org.zkoss.web.util.resource.ExtendletContext;
import org.zkoss.web.util.resource.ServletContextLocator;

/**
 * The servlet relevant utilities.
 *
 * @author tomyeh
 * @see org.zkoss.web.servlet.http.Https
 * @see org.zkoss.web.servlet.Charsets
 */
public class Servlets {
	private static final Logger log = LoggerFactory.getLogger(Servlets.class);

	private static ClientIdentifier _clientId;
	private static final Pattern _rwebkit = Pattern.compile(".*(webkit)[ /]([\\w.]+).*"),
			_ropera = Pattern.compile(".*+(opera)(?:.*version)?[ /]([\\w.]+).*"),
			_rmozilla = Pattern.compile(".*(mozilla)(?:.*? rv:([\\w.]+))?.*"),
			_rchrome = Pattern.compile(".*(chrome)[ /]([\\w.]+).*"),
			_randroid = Pattern.compile(".*(android)[ /]([\\w.]+).*"),
			_redge = Pattern.compile(".*(edg)[ /]([\\w.]+).*"),
			_redgeLegacy = Pattern.compile(".*(edge)[ /]([\\w.]+).*"),
			_rsafari = Pattern.compile(".*(safari)[ /]([\\w.]+).*"), _rtrident = Pattern.compile("trident/([0-9\\.]+)");

	private static final boolean _svl24, _svl23, _svl3;

	static {
		boolean b = false;
		try {
			HttpSession.class.forName("javax.servlet.annotation.WebServlet");
			b = true;
		} catch (Throwable ex) {
			// expected
		}
		_svl3 = b;

		if (!b) {
			try {
				ServletResponse.class.getMethod("getContentType", new Class[0]);
				b = true;
			} catch (Throwable ex) {
				// expected
			}
		}
		_svl24 = b;

		if (!b) {
			try {
				HttpSession.class.getMethod("getServletContext", new Class[0]);
				b = true;
			} catch (Throwable ex) {
				// expected
			}
		}
		_svl23 = b;
	}

	/** Utilities; no instantiation required. */
	protected Servlets() {
	}

	/** Returns whether a URL starts with xxx://, mailto:, about:,
	 * javascript:, data:, //
	 */
	public static final boolean isUniversalURL(String uri) {
		if (uri == null || uri.length() == 0)
			return false;

		final char cc = uri.charAt(0);
		return cc >= 'a' && cc <= 'z' && (uri.indexOf("://") > 0 || uri.startsWith("mailto:")
				|| uri.startsWith("javascript:") || uri.startsWith("about:") || uri.startsWith("data:")) || uri.startsWith("//");
	}

	/** Returns whether the current Web server supports Servlet 3.0 or above.
	 *
	 * @since 6.0.0
	 */
	public static final boolean isServlet3() {
		return _svl3;
	}

	/** Returns whether the current Web server supports Servlet 2.4 or above.
	 *
	 * @since 3.0.0
	 */
	public static final boolean isServlet24() {
		return _svl24;
	}

	/** Returns whether the current Web server supports Servlet 2.3 or above.
	 * Thus, if {@link #isServlet24} returns true, {@link #isServlet23}
	 * must return true, too.
	 *
	 * @since 3.0.0
	 */
	public static final boolean isServlet23() {
		return _svl23;
	}

	//-- resource locator --//
	/** Locates a page based on the current Locale. It never returns null.
	 *
	 * <p>Notice that it cannot resolve a path starting with '~', and containing
	 * '*', because it cannot access the content of the other servlet context.
	 *
	 * <p>If an URI contains "*", it will be replaced with a proper Locale.
	 * For example, if the current Locale is zh_TW and the resource is
	 * named "ab*.cd", then it searches "ab_zh_TW.cd", "ab_zh.cd" and
	 * then "ab.cd", until any of them is found.
	 *
	 * <blockquote>Note: "*" must be right before ".", or the last character.
	 * For example, "ab*.cd" and "ab*" are both correct, while
	 * "ab*cd" and "ab*\/cd" are ignored.</blockquote>
	 *
	 * <p>If an URI contains two "*", the first "*" will be replaced with
	 * a browser code and the second with a proper locale.
	 * The browser code depends on what browser
	 * the user are used to visit the web site.
	 * Currently, the code for Internet Explorer is "ie", Safari is "saf",
	 * Opera is "opr" and all others are "moz".
	 * Thus, in the above example, if the resource is named "ab**.cd"
	 * and Firefox is used, then it searches "abmoz_zh_TW.cd", "abmoz_zh.cd"
	 * and then "abmoz.cd", until any of them is found.
	 *
	 * <p>Note: it assumes the path as name_lang_cn_var.ext where
	 * ".ext" is optional. Example, my_zh_tw.html.jsp.
	 *
	 * @param ctx the servlet context to locate pages
	 * @param pgpath the page path excluding servlet name. It is OK to have
	 * the query string. It might contain "*" for current browser code and Locale.
	 * @param locator the locator used to locate resource. If null, ctx
	 * is assumed.
	 * @return the path that matches the wildcard; <code>pgpath</code>, otherwise
	 * never null
	 * @see Locales#getCurrent
	 */
	@SuppressWarnings("unchecked")
	public static final String locate(ServletContext ctx, ServletRequest request, String pgpath, Locator locator)
			throws ServletException {
		if (pgpath == null)
			return pgpath;
		final int f = pgpath.indexOf('*');
		if (f < 0 || isUniversalURL(pgpath))
			return pgpath;
		final int jquest = pgpath.indexOf('?');
		if (jquest >= 0 && f > jquest)
			return pgpath;
		//optimize the case that no "*" at all

		final String qstr;
		if (jquest >= 0) {
			qstr = pgpath.substring(jquest);
			pgpath = pgpath.substring(0, jquest);
		} else {
			qstr = null;
		}

		//by browser?
		int l = pgpath.lastIndexOf('*');
		if (l > f) { //two '*'
			final String bc = Servlets.isBrowser(request, "safari") ? "saf" : Servlets.isBrowser(request, "opera") ? "opr" : "moz";
			l += bc.length() - 1;
			pgpath = pgpath.substring(0, f) + bc + pgpath.substring(f + 1);
		}

		//remove "*"
		pgpath = pgpath.substring(0, l) + pgpath.substring(l + 1); //remove

		//by locale? 1) before the first dot, 2) the last char if no dot
		boolean byLocale = l == pgpath.length() || (pgpath.charAt(l) == '.' && pgpath.indexOf('/', l + 1) < 0);
		if (byLocale) {
			//make sure no dot before it
			for (int j = l; --j >= 0;) {
				final char cc = pgpath.charAt(j);
				if (cc == '.') {
					byLocale = false;
					break;
				} else if (cc == '/') {
					break;
				}
			}
		}
		if (!byLocale)
			return qstr != null ? pgpath + qstr : pgpath; //not by locale

		final String PGPATH_CACHE = "org.zkoss.web.pgpath.cache";
		Map<URIIndex, String> map = (Map<URIIndex, String>) ctx.getAttribute(PGPATH_CACHE);
		if (map == null) {
			map = Collections.synchronizedMap( //10 min
					new CacheMap<URIIndex, String>(500, 10 * 60 * 1000));
			ctx.setAttribute(PGPATH_CACHE, map);
		}

		final Locale locale = Locales.getCurrent();
		final URIIndex index = new URIIndex(pgpath, locale);

		String uri = map.get(index);
		if (uri == null) {
			HttpServletRequest httpServletRequest = (HttpServletRequest) request;
			Path path = Path.of(httpServletRequest.getServletPath());
			if (!path.endsWith("/")) {
				path = path.getParent();
			}
			final Locators.URLLocation loc = Locators.locate(pgpath, locale,
					locator != null ? locator : new ServletContextLocator(ctx, path.toString().replace('\\', '/')));
			uri = loc != null ? loc.file : pgpath;
			map.put(index, uri);
		}

		return qstr != null ? uri + qstr : uri;
	}

	private static class URIIndex {
		private final String _uri;
		private final Locale _locale;

		private URIIndex(String uri, Locale locale) {
			if (uri == null || locale == null)
				throw new IllegalArgumentException("null");
			_uri = uri;
			_locale = locale;
		}

		public int hashCode() {
			return _uri.hashCode();
		}

		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof URIIndex))
				return false;
			//To speed up, don't check whether o is the right class
			final URIIndex idx = (URIIndex) o;
			return _uri.equals(idx._uri) && _locale.equals(idx._locale);
		}
	}

	/** Sets the client identifier that is used to assist {@link #isBrowser}
	 * to identify a client.
	 *
	 * <p>Notice that the client identifier must be thread-safe.
	 *
	 * @param clientId the client identifier. If null, only the default types
	 * are recognized.
	 * @see ClientIdentifier
	 * @since 6.0.0
	 */
	public static void setClientIdentifier(ClientIdentifier clientId) {
		_clientId = clientId;
	}

	/** Returns the client identifier, or null if no such plugin.
	 * @see ClientIdentifier
	 * @since 6.0.0
	 */
	public static ClientIdentifier getClientIdentifier() {
		return _clientId;
	}

	/** Returns the version of the given browser name, or null if the client
	 * is not the given browsers.
	 *
	 * <p>Notice that, after this method is called, an attribute named zk
	 * will be stored to the request, such that you can retrieve
	 * the browser information by use of EL, such as
	 * <code>${zk.ff &gt; 5}</code>.
	 *
	 * @param request the request.
	 * @param name the browser's name. It includes "ff", "gecko",
	 * "webkit", "safari", "opera", "android", "mobile", "ios", "iphone", "ipad"
	 * and "ipod".
	 * And, "ff" is the same as "gecko", and "webkit" is the same as "safari".
	 * @since 6.0.0
	 */
	public static Double getBrowser(ServletRequest request, String name) {
		return (Double) browserInfo(request).get(name);
	}

	/** Returns the name of the browser, or null if not identifiable.
	 * @since 6.0.0
	 */
	public static String getBrowser(ServletRequest request) {
		return (String) ((Map) browserInfo(request).get("browser")).get("name");
	}

	/** Returns the version of the given browser name, or null if the client
	 * is not the given browsers.
	 *
	 * <p>Notice that, after this method is called, an attribute named zk
	 * will be stored to the request, such that you can retrieve
	 * the browser information by use of EL, such as
	 * <code>${zk.ff &gt; 5}</code>.
	 *
	 * @param userAgent the user agent (i.e., the user-agent header in HTTP).
	 * @param name the browser's name. It includes "ff", "gecko",
	 * "webkit", "safari", "opera", "android", "mobile", "ios", "iphone", "ipad"
	 * and "ipod".
	 * And, "ff" is the same as "gecko", and "webit" is the same as "safari".
	 * @since 6.0.0
	 */
	public static Double getBrowser(String userAgent, String name) {
		final Map<String, Object> zk = new HashMap<String, Object>();
		browserInfo(zk, userAgent);
		return (Double) zk.get(name);
	}

	/** Returns the name of the browser, or null if not identifiable.
	 * @param userAgent the user agent (i.e., the user-agent header in HTTP).
	 * @since 6.0.0
	 */
	public static String getBrowser(String userAgent) {
		final Map<String, Object> zk = new HashMap<String, Object>();
		browserInfo(zk, userAgent);
		return (String) ((Map) zk.get("browser")).get("name");
	}

	private static Map browserInfo(ServletRequest request) {
		//No need synchronized since the servlet request is executed synchronously
		Map<String, Object> zk = cast((Map) request.getAttribute("zk"));
		if (zk == null)
			request.setAttribute("zk", zk = new HashMap<String, Object>(4));

		if (!zk.containsKey("browser"))
			browserInfo(zk, getUserAgent(request));
		return zk;
	}

	private static void browserInfo(Map<String, Object> zk, String ua) {
		if (ua != null) {
			// ZK-1822: In locale Turkish, it can prevent 'I'.toLowerCase becomes 'i' without dot.
			ua = ua.toLowerCase(Locale.ENGLISH);
			if (_clientId != null) {
				final ClientIdentifier ci = _clientId.matches(ua);
				if (ci != null) {
					browserInfo(zk, ci.getName(), ci.getVersion());
					return;
				}
			}
			Matcher m = _randroid.matcher(ua);
			if (m.matches()) {
				double v = getVersion(m);
				zk.put("android", v);
				zk.put("mobile", v);
			}
			m = _rwebkit.matcher(ua);
			if (m.matches()) {
				double version;
				browserInfo(zk, "webkit", version = getVersion(m));

				//B70-ZK-2088: make sure Browser is not chrome
				boolean matchChrome = false;
				m = _rchrome.matcher(ua);
				if (m.matches()) {
					zk.put("chrome", getVersion(m));
					matchChrome = true;
				}

				m = _rsafari.matcher(ua);
				if (!matchChrome && m.matches())
					zk.put("safari", getVersion(m));

				m = _redge.matcher(ua);
				if (m.matches()) {
					zk.put("edge", getVersion(m));
				}

				m = _redgeLegacy.matcher(ua);
				if (m.matches()) {
					zk.put("edge_legacy", getVersion(m));
				}

				for (int j = _ios.length; --j >= 0;)
					if (ua.indexOf(_ios[j]) >= 0) {
						zk.put(_ios[j], version);
						zk.put("ios", version);
						zk.put("mobile", version);
						return;
					}
				return;
			}
			m = _ropera.matcher(ua);
			if (m.matches()) {
				browserInfo(zk, "opera", getVersion(m));
				return;
			}

			if (ua.indexOf("compatible") < 0) {
				m = _rmozilla.matcher(ua);
				if (m.matches()) {
					double version = getVersion(m);
					if (version < 5) { //http://www.useragentstring.com/_uas_Firefox_version_5.0.php
						int j = ua.indexOf("firefox/");
						if (j >= 0) {
							int k = ua.indexOf('.', j += 8);
							if (k >= 0) {
								for (int len = ua.length(); ++k < len;) {
									final char cc = ua.charAt(k);
									if (cc < '0' || cc > '9')
										break;
								}
								try {
									version = Double.parseDouble(ua.substring(j, k));
								} catch (Throwable ex) {
									// expected
								}
							}
						}
					}

					//the version after gecko/* is confusing, so we
					//use firefox's version instead
					browserInfo(zk, "gecko", version);
					zk.put("ff", version);
					return;
				}
			}
		}
		zk.put("browser", Collections.emptyMap()); //none matched
	}

	private static void browserInfo(Map<String, Object> zk, String name, double version) {
		final Map<String, Object> bi = new HashMap<String, Object>(4);
		bi.put("name", name);
		bi.put("version", version);
		zk.put("browser", bi);
		zk.put(name, version);
	}

	private static final String[] _ios = { "ipod", "iphone", "ipad" };

	private static double getVersion(Matcher m) {
		return m.groupCount() < 2 ? 1/*ignore it*/ : getVersion(m.group(2));
	}

	private static double getVersion(String version) {
		try {
			int j = version.indexOf('.');
			if (j >= 0) {
				j = version.indexOf('.', j + 1);
				if (j >= 0)
					version = version.substring(0, j);
			}
			return Double.parseDouble(version);
		} catch (Throwable t) {
			return 1; //ignore it
		}
	}

	/**
	 * Returns whether the client is a browser of the specified type.
	 * To more accurately specify the browser, please use {@link  #getUserAgent(ServletRequest)} instead.
	 * @param type the type of the browser.
	 * The syntax: {@code <browser-name>[<version-number>][-]}.<br/>
	 * For example, ie9, ios and ie6-.
	 * And, <code>ie9</code> means Internet Explorer 9 and later, while
	 * <code>ie6-</code> means Internet Explorer 6 (not prior, nor later).
	 * @since 3.5.1
	 */
	public static boolean isBrowser(ServletRequest req, String type) {
		return (req instanceof HttpServletRequest) && isBrowser(getUserAgent(req), type);
	}

	/** Returns whether the user agent is a browser of the specified type.
	 * To more accurately specify the browser, please use {@link  #getUserAgent(ServletRequest)} instead.
	 * @param userAgent represents a client.
	 * For HTTP clients, It is the user-agent header.
	 * @param type the type of the browser, or a list of types separated by comma.<br/>
	 * The syntax: {@code <browser-name>[<version-number>][-]}.<br/>
	 * For example, ie9, ios and ie6-.
	 * And, <code>ie9</code> means Internet Explorer 9 and later, while
	 * <code>ie6-</code> means Internet Explorer 6 (not prior, nor later).<br/>
	 * If a list of types are specified (such as <code>ie6-,ie7-</code>),
	 * this method returns true if any of them matches (i.e., OR condition).
	 * @since 3.5.1
	 */
	public static boolean isBrowser(String userAgent, String type) {
		String[] types = type.split(",");
		for (int j = 0; j < types.length; ++j)
			if (browser(userAgent, types[j]))
				return true; //OR
		return false;
	}

	private static boolean browser(String userAgent, String type) {
		if (userAgent == null) //Bug ZK-1582: userAgent could be null if it is robot.
			return false;

		int last = (type = type.trim()).length();
		if (last == 0)
			return false;

		char cc = type.charAt(last - 1);
		final boolean equals = cc == '-' || cc == '_';
		if (equals || cc == '+')
			last--;

		int j;
		for (j = last; j > 0; --j)
			if ((cc = type.charAt(j - 1)) != '.' && (cc < '0' || cc > '9'))
				break;

		Double vtype = null;
		if (j < last) {
			try {
				vtype = Double.parseDouble(type.substring(j, last));
			} catch (Throwable t) {
				// expected
			}
			last = j;
		}

		String btype = type.substring(0, last);
		Double vclient = getBrowser(userAgent, btype);
		if (vclient == null && userAgent.indexOf(btype) < 0)
			return false; //not matched
		if (vtype == null)
			return true; //not care about version

		if (vclient == null)
			return false; //not matched for Bug ZK-1930

		double v1 = vclient.doubleValue(), v2 = vtype.doubleValue();
		return equals ? v1 == v2 : v1 >= v2;
	}

	/** Returns the user-agent header, which indicates what the client is,
	 * or an empty string if not available.
	 *
	 * <p>Note: it doesn't return null.
	 *
	 * @since 3.0.2
	 */
	public static final String getUserAgent(ServletRequest req) {
		if (req instanceof HttpServletRequest) {
			String s = ((HttpServletRequest) req).getHeader("user-agent");
			if (s != null) {
				String cache = (String) req.getAttribute("$$zkagent$$");
				if (cache != null)
					return cache;
				req.setAttribute("$$zkagent$$", s);
				return s;
			}
		}
		return "";
	}

	/**
	 * Tests whether this page is included by another page.
	 */
	public static final boolean isIncluded(ServletRequest request) {
		return request.getAttribute(Attributes.INCLUDE_CONTEXT_PATH) != null
				|| request.getAttribute("org.zkoss.web.servlet.include") != null;
		//org.zkoss.web.servlet.include is used by ZK (or others)
		//to 'simulate' inclusion
	}

	/**
	 * Tests whether this page is forwarded by another page.
	 */
	public static final boolean isForwarded(ServletRequest request) {
		return request.getAttribute(Attributes.FORWARD_CONTEXT_PATH) != null
				|| request.getAttribute("org.zkoss.web.servlet.forward") != null;
	}

	/**
	 * Forward to the specified URI.
	 * It enhances RequestDispatcher in the following ways.
	 *
	 * <ul>
	 *  <li>It handles "~ctx"" where ctx is the context path of the
	 *  foreign context. It is called foreign URI.</li>
	 *  <li>It detects whether the page calling this method
	 * is included by another servlet/page. If so, it uses
	 * RequestDispatcher.include() instead of RequestDispatcher.forward().</li>
	 *  <li>The forwarded page could accept additional parameters --
	 * actually converting parameters to a query string
	 * and appending it to uri.</li>
	 * <li>In additions, it does HTTP encoding, i.e., converts '+' and other
	 * characters to comply HTTP.</li>
	 * </ul>
	 *
	 * <p>NOTE: don't include query parameters in uri.
	 *
	 * @param ctx the servlet context. If null, uri cannot be foreign URI.
	 * It is ignored if URI is relevant (neither starts with '/' nor '~').
	 * @param uri the URI to include. It is OK to relevant (without leading
	 * '/'). If starts with "/", the context path of request is assumed.
	 * To reference to foreign context, use "~ctx" where ctx is the
	 * context path of the foreign context (without leading '/').
	 * If it could be any context path recognized by the Web container or
	 * any name registered with {@link #addExtendletContext}.
	 * <br/>Notice that, since 3.6.3, <code>uri</code> could contain
	 * '*' (to denote locale and browser). Refer to {@link #locate}.
	 * @param params the parameter map; null to ignore
	 * @param mode one of {@link #OVERWRITE_URI}, {@link #IGNORE_PARAM},
	 * and {@link #APPEND_PARAM}. It defines how to handle if both uri
	 * and params contains the same parameter.
	 */
	public static final void forward(ServletContext ctx, ServletRequest request, ServletResponse response, String uri,
			Map params, int mode) throws IOException, ServletException {
		//		if (log.isDebugEnabled()) log.debug("Forwarding "+uri);

		//include or foward depending whether this page is included or not
		if (isIncluded(request)) {
			include(ctx, request, response, uri, params, mode);
			return;
		}

		uri = locate(ctx, request, uri, null);

		final RequestDispatcher disp = getRequestDispatcher(ctx, request, uri, params, mode);
		if (disp == null)
			throw new ServletException("No dispatcher available to forward to " + uri);

		if (mode == PASS_THRU_ATTR && params != null && !params.isEmpty()) {
			final Map old = setPassThruAttr(request, params);
			try {
				disp.forward(request, response);
			} catch (ClassCastException ex) {
				//Tom M. Yeh, 2006/09/21: Bug 1548478
				//Cause: http://issues.apache.org/bugzilla/show_bug.cgi?id=39417
				//
				//Bug or limitation of Catalina: not accepting HttpServletRequest
				//othere than the original one or wrapper of original one
				//
				//Real Cause: org.apache.catalina.core.ApplicationDispatcher
				//call unwrapRequest() twice, and then causes ClassCastException
				//
				//Resolution: since it is the almost last statement, it is safe
				//to ignore this exception
				if (!(request instanceof org.zkoss.web.portlet.RenderHttpServletRequest))
					throw ex; //not the case described above
			} finally {
				restorePassThruAttr(request, old);
			}
		} else {
			disp.forward(request, response);
		}
	}

	/** A shortcut of forward(request, response, uri, null, 0).
	 */
	public static final void forward(ServletContext ctx, ServletRequest request, ServletResponse response, String uri)
			throws IOException, ServletException {
		forward(ctx, request, response, uri, null, 0);
	}

	/**
	 * Includes the resource at the specified URI.
	 * It enhances RequestDispatcher to allow the inclusion with
	 * a parameter map -- acutually converting parameters to a query string
	 * and appending it to uri.
	 *
	 * <p>NOTE: don't include query parameters in uri.
	 *
	 * @param ctx the servlet context. If null, uri cannot be foreign URI.
	 * It is ignored if URI is relevant (neither starts with '/' nor '~').
	 * @param uri the URI to include. It is OK to relevant (without leading
	 * '/'). If starts with "/", the context path of request is assumed.
	 * To reference to foreign context, use "~ctx/" where ctx is the
	 * context path of the foreign context (without leading '/').
	 * <br/>Notice that, since 3.6.3, <code>uri</code> could contain
	 * '*' (to denote locale and browser). Refer to {@link #locate}.
	 * @param params the parameter map; null to ignore
	 * @param mode one of {@link #OVERWRITE_URI}, {@link #IGNORE_PARAM},
	 * and {@link #APPEND_PARAM}. It defines how to handle if both uri
	 * and params contains the same parameter.
	 */
	public static final void include(ServletContext ctx, ServletRequest request, ServletResponse response, String uri,
			Map params, int mode) throws IOException, ServletException {
		//		if (log.isDebugEnabled()) log.debug("Including "+uri+" at "+ctx);

		//Note: we don't optimize the include to call ClassWebResource here
		//since 1) it is too low level (might have some risk)
		//2) no clean way to access ClassWebResouce here

		//20050606: Tom Yeh
		//We have to set this special attribute for jetty
		//Otherwise, if including a page crossing context might not return
		//the same session
		request.setAttribute("org.mortbay.jetty.servlet.Dispatcher.shared_session", Boolean.TRUE);

		uri = locate(ctx, request, uri, null);

		final RequestDispatcher disp = getRequestDispatcher(ctx, request, uri, params, mode);
		if (disp == null)
			throw new ServletException("No dispatcher available to include " + uri);

		if (mode == PASS_THRU_ATTR && params != null && !params.isEmpty()) {
			final Map old = setPassThruAttr(request, params);
			try {
				disp.include(request, response);
			} finally {
				restorePassThruAttr(request, old);
			}
		} else {
			disp.include(request, response);
		}
	}

	/** A shortcut of include(request, response, uri, null, 0).
	 */
	public static final void include(ServletContext ctx, ServletRequest request, ServletResponse response, String uri)
			throws IOException, ServletException {
		include(ctx, request, response, uri, null, 0);
	}

	/** Sets the arg attribute to pass parameters thru request's attribute.
	 */
	private static final Map setPassThruAttr(ServletRequest request, Map params) {
		final Map old = (Map) request.getAttribute(Attributes.ARG);
		request.setAttribute(Attributes.ARG, params);
		return old;
	}

	/** Restores what has been done by {@link #setPassThruAttr}.
	 */
	private static final void restorePassThruAttr(ServletRequest request, Map old) {
		if (old != null)
			request.setAttribute(Attributes.ARG, old);
		else
			request.removeAttribute(Attributes.ARG);
	}

	/** Returns the request dispatch of the specified URI.
	 *
	 * @param ctx the servlet context. If null, uri cannot be foreign URI.
	 * It is ignored if uri is relevant (neither starts with '/' nor '~').
	 * @param request the request. If null, uri cannot be relevant.
	 * It is used only if uri is relevant.
	 * @param uri the URI to include. It is OK to relevant (without leading
	 * '/'). If starts with "/", the context path of request is assumed.
	 * To reference to foreign context, use "~ctx/" where ctx is the
	 * context path of the foreign context (without leading '/').
	 * @param params the parameter map; null to ignore
	 * @param mode one of {@link #OVERWRITE_URI}, {@link #IGNORE_PARAM},
	 * and {@link #APPEND_PARAM}. It defines how to handle if both uri
	 * and params contains the same parameter.
	 */
	public static final RequestDispatcher getRequestDispatcher(ServletContext ctx, ServletRequest request, String uri,
			Map params, int mode) throws ServletException {
		final char cc = uri.length() > 0 ? uri.charAt(0) : (char) 0;
		if (ctx == null || (cc != '/' && cc != '~')) { //... or relevant
			if (request == null)
				throw new IllegalArgumentException(ctx == null ? "Servlet context and request cannot be both null"
						: "Request is required to use revalant URI: " + uri);
			if (cc == '~')
				throw new IllegalArgumentException("Servlet context is required to use foreign URI: " + uri);
			uri = generateURI(uri, params, mode);
			return request.getRequestDispatcher(uri);
		}

		//NO NEED to encodeURL since it is forward/include
		return new ParsedURI(ctx, uri).getRequestDispatcher(params, mode);
	}

	/** Returns the resource of the specified uri.
	 * Unlike ServletContext.getResource, it handles "~" like
	 * {@link #getRequestDispatcher} did.
	 * <p>Since 5.0.7, file://, http://, https:// and ftp:// are supported.
	 */
	public static final URL getResource(ServletContext ctx, String uri) throws UnsupportedEncodingException {
		try {
			if (uri != null && uri.toLowerCase(java.util.Locale.ENGLISH).startsWith("file://")) {
				final File file = new File(new URI(Https.sanitizePath(uri)));
				return file.exists() ? file.toURI().toURL() : null;
				//spec: return null if not found
			}

			URL url = toURL(uri);
			if (url != null)
				return url; //unfortunately, we cannot detect if it exists
			return new ParsedURI(ctx, uri).getResource();
		} catch (Throwable ex) {
			log.warn("Ignored: failed to load " + Encodes.encodeURI(uri), ex);
			return null; //spec: return null if not found
		}
	}

	/** Returns the resource stream of the specified uri.
	 * Unlike ServletContext.getResource, it handles "~" like
	 * {@link #getRequestDispatcher} did.
	 * <p>Since 5.0.7, file://, http://, https:// and ftp:// are supported.
	 */
	public static final InputStream getResourceAsStream(ServletContext ctx, String uri) throws IOException {
		try {
			if (uri != null && uri.toLowerCase(java.util.Locale.ENGLISH).startsWith("file://")) {
				final File file = new File(new URI(uri));
				return file.exists() ? new BufferedInputStream(new FileInputStream(file)) : null;
				//spec: return null if not found
			}

			URL url = toURL(uri);
			if (url != null) {
				// prevent SSRF warning
				url = URLs.sanitizeURL(url);
				return url.openStream();
			}
			return new ParsedURI(ctx, uri).getResourceAsStream();
		} catch (Throwable ex) {
			log.warn("Ignored: failed to load " + Encodes.encodeURI(uri), ex);
			return null; //spec: return null if not found
		}
	}

	/** Converts URI to URL if starts with http:/, https:/ or ftp:/ */
	private static URL toURL(String uri) throws MalformedURLException {
		String s;
		if (uri != null && ((s = uri.toLowerCase(java.util.Locale.ENGLISH)).startsWith("http://")
				|| s.startsWith("https://") || s.startsWith("ftp://")))
			return new URL(uri);
		return null;
	}

	/** Used to resolve "~" in URI. */
	private static class ParsedURI {
		private ServletContext _svlctx;
		private ExtendletContext _extctx;
		private String _uri;

		private ParsedURI(final ServletContext ctx, final String uri) {
			if (uri != null && uri.length() > 0 && uri.charAt(0) == '~') { //refer to foreign context
				final int j = uri.indexOf('/', 1);
				final String ctxroot;
				if (j >= 0) {
					ctxroot = "/" + uri.substring(1, j);
					_uri = Https.sanitizePath(uri.substring(j));
				} else {
					ctxroot = "/" + uri.substring(1);
					_uri = "/";
				}

				_extctx = getExtendletContext(ctx, ctxroot.substring(1));
				if (_extctx == null) {
					_svlctx = ctx.getContext(ctxroot);
					if (_svlctx == null)
						throw new SystemException("Context not found or not visible to " + ctx + ": " + ctxroot);
				}
			} else {
				_svlctx = ctx;
				_uri = Https.sanitizePath(uri);
			}
		}

		private RequestDispatcher getRequestDispatcher(Map params, int mode) {
			if (_extctx == null && _svlctx == null) //not found
				return null;

			final String uri = generateURI(_uri, params, mode);
			return _svlctx != null ? _svlctx.getRequestDispatcher(uri) : _extctx.getRequestDispatcher(uri);
		}

		private URL getResource() throws MalformedURLException {
			return _svlctx != null ? _svlctx.getResource(_uri) : _extctx != null ? _extctx.getResource(_uri) : null;
		}

		private InputStream getResourceAsStream() {
			return _svlctx != null ? _svlctx.getResourceAsStream(_uri)
					: _extctx != null ? _extctx.getResourceAsStream(_uri) : null;
		}
	}

	/** Whether to overwrite uri if both uri and params contain the same
	 * parameter.
	 * Used by {@link #generateURI}
	 */
	public static final int OVERWRITE_URI = 0;
	/** Whether to ignore params if both uri and params contain the same
	 * parameter.
	 * Used by {@link #generateURI}
	 */
	public static final int IGNORE_PARAM = 1;
	/** Whether to append params if both uri and params contain the same
	 * parameter. In other words, they both appear as the final query string.
	 * Used by {@link #generateURI}
	 */
	public static final int APPEND_PARAM = 2;
	/** Whether the specified parameters shall be passed thru the request
	 * attribute called arg.
	 */
	public static final int PASS_THRU_ATTR = 3;

	/** Generates URI by appending the parameters.
	 * Note: it doesn't support the ~xxx/ format.
	 *
	 * @param params the parameters to apend to the query string
	 * @param mode one of {@link #OVERWRITE_URI}, {@link #IGNORE_PARAM},
	 * {@link #APPEND_PARAM} and {@link #PASS_THRU_ATTR}.
	 * It defines how to handle if both uri and params contains the same
	 * parameter.
	 * mode is used only if both uri contains query string and params is
	 * not empty.
	 * @see Encodes#encodeURL(ServletContext, ServletRequest, ServletResponse, String)
	 */
	public static final String generateURI(String uri, Map params, int mode) {
		if (uri.startsWith("~"))
			throw new IllegalArgumentException("~ctx not supported here: " + uri);

		final int j = uri.indexOf('?');
		String qstr = null;
		if (j >= 0) {
			qstr = uri.substring(j);
			uri = uri.substring(0, j);
		}

		try {
			uri = Encodes.encodeURI(uri);
			final boolean noQstr = qstr == null;
			final boolean noParams = mode == PASS_THRU_ATTR || params == null || params.isEmpty();
			if (noQstr && noParams)
				return uri;

			if (noQstr != noParams)
				mode = APPEND_PARAM;

			final StringBuffer sb = new StringBuffer(80).append(uri);
			if (qstr != null)
				sb.append(qstr);

			switch (mode) {
			case IGNORE_PARAM:
				//removing params that is conflict
				for (final Iterator it = params.entrySet().iterator(); it.hasNext();) {
					final Map.Entry me = (Map.Entry) it.next();
					final String nm = (String) me.getKey();
					if (Encodes.containsQuery(qstr, nm))
						it.remove();
				}
				//flow thru
			case OVERWRITE_URI:
				return Encodes.setToQueryString(sb, params).toString();
			case APPEND_PARAM:
				return Encodes.addToQueryString(sb, params).toString();
			default:
				throw new IllegalArgumentException("Unknown mode: " + mode);
			}
		} catch (UnsupportedEncodingException ex) {
			throw new SystemException(ex);
		}
	}

	/** A list of context root paths (e.g., "/abc"). */
	private static class ContextRootsHolder {
		private static final List<String> INSTANCE = Collections.unmodifiableList(
				myGetContextPaths());

		private static final List<String> myGetContextPaths() {
			final String APP_XML = "/META-INF/application.xml";
			final List<String> ctxroots = new LinkedList<String>();
			final URL xmlURL = Locators.getDefault().getResource(APP_XML);
			if (xmlURL == null)
				throw new SystemException("File not found: " + APP_XML);
			Element root = null;
			try {
				//		if (log.isDebugEnabled()) log.debug("Parsing "+APP_XML);
				root = new SAXBuilder(false, false, true).build(xmlURL).getRootElement();

			} catch (Exception ex) {
				throw SystemException.Aide.wrap(ex);
			}
			for (Element e : root.getElements("module")) {
				final String ctxroot = (String) e.getContent("web/context-root");
				if (ctxroot == null) {
					//				if (log.finerable()) log.finer("Skip non-web: "+e.getContent("java"));
					continue;
				}

				ctxroots.add(ctxroot.startsWith("/") ? ctxroot : "/" + ctxroot);
			}

			//		log.info("Context found: "+ctxroots);
			return new ArrayList<String>(ctxroots);
		}
	}

	/** Returns a list of context paths (e.g., "/abc") that this application
	 * has. This implementation parse application.xml. For war that doesn't
	 * contain application.xml might have to override this method and
	 * parse another file to know what context being loaded.
	 */
	public static final List<String> getContextPaths() {
		return ContextRootsHolder.INSTANCE;
	}


	/** Returns a token to represent a limit-time offer.
	 * It is mainly used as an parameter value (mostlycalled zk_lto), and then
	 * you could verify whether it is expired by {@link #isOfferExpired}.
	 */
	public static final String getLimitTimeOffer() {
		final String lto = Long.toHexString(System.currentTimeMillis());
		return lto + Checksums.getChecksum(lto, "");
	}

	/** Returns whether a token returned by getLimitTimeOffer expired.
	 * @param timeout how long the office shall expire, unit: seconds.
	 */
	public static final boolean isOfferExpired(String lto, int timeout) {
		int len;
		if (lto == null || (len = lto.length()) <= 1)
			return true;

		final char cksm = lto.charAt(len - 1);
		lto = lto.substring(0, len - 1);
		if (cksm != Checksums.getChecksum(lto, ""))
			return true;

		try {
			return Long.parseLong(lto, 16) + timeout * 1000L < System.currentTimeMillis();
		} catch (NumberFormatException ex) {
			return true;
		}
	}

	/** Adds an extended context.
	 * @return the previous extended context, if any, associated with
	 * the specified name.
	 */
	public static final ExtendletContext addExtendletContext(ServletContext ctx, String name, ExtendletContext extctx) {
		if (name == null || extctx == null)
			throw new IllegalArgumentException("null");
		return getExtWebCtxs(ctx).put(name, extctx);
	}

	/** Removes an extended context of the specified name.
	 */
	public static final ExtendletContext removeExtendletContext(ServletContext ctx, String name) {
		return getExtWebCtxs(ctx).remove(name);
	}

	/** Returns the extended context of the specified name.
	 */
	public static final ExtendletContext getExtendletContext(ServletContext ctx, String name) {
		return getExtWebCtxs(ctx).get(name);
	}

	@SuppressWarnings("unchecked")
	private static final Map<String, ExtendletContext> getExtWebCtxs(ServletContext ctx) {
		final String attr = "javax.zkoss.web.servlets.ExtendletContexts";
		//such that it could be shared among portlets
		synchronized (Servlets.class) { //don't use ctx because it might be a proxy (in portlet)
			Map<String, ExtendletContext> ctxs = (Map<String, ExtendletContext>) ctx.getAttribute(attr);
			if (ctxs == null)
				ctx.setAttribute(attr, ctxs = Collections.synchronizedMap(new HashMap<String, ExtendletContext>(4)));
			return ctxs;
		}
	}

	/** Returns the file/path extension of the specified path (excluding dot),
	 * or null if no extension at all.
	 *
	 * <p>Note: the extension is converted to the lower case.
	 *
	 * @param path the path. If path is null, null is returned.
	 * @since 2.4.1
	 * @see #getExtension(String, boolean)
	 */
	public static final String getExtension(String path) {
		if (path != null) {
			int j = path.lastIndexOf('.');
			if (j >= 0 && path.indexOf('/', j + 1) < 0)
				return path.substring(j + 1).toLowerCase(java.util.Locale.ENGLISH);
			//don't worry jsessionid since it is handled by container
		}
		return null;
	}

	/** Returns the file/path extension of the specified path (excluding dot),
	 * or null if no extension at all.
	 *
	 * <p>Note: the extension is converted to the lower case.
	 *
	 * <p>The result is the same for both {@link #getExtension(String)}
	 * and {@link #getExtension(String, boolean)}, if the path
	 * has only one dot. However, if there are more than one dot, e.g.,
	 * /a/b.c.d, then {@link #getExtension(String)} retrieves the last
	 * extension, that is, d in this example.
	 * On the other hand, if you invoke getExtension(path, false),
	 * it returns the complete extension, that is, c.d in this example.
	 *
	 * @param path the path. If path is null, null is returned.
	 * @param lastOnly whether to return the last portion of extensioin
	 * if there are two or more dots.
	 * In other wors, getExtension(path) is the same as
	 * getExtension(path, true).
	 * @since 3.5.1
	 */
	public static final String getExtension(String path, boolean lastOnly) {
		if (lastOnly)
			return getExtension(path);
		if (path == null)
			return null;

		int dot = -1;
		for (int j = path.length(); --j >= 0;) {
			final char cc = path.charAt(j);
			if (cc == '.')
				dot = j;
			else if (cc == '/')
				break;
		}
		return dot >= 0 ? path.substring(dot + 1).toLowerCase(java.util.Locale.ENGLISH) : "";
	}

	/** Returns the request detail information.
	 * It is used to log the debug info.
	 * @since 3.0.5
	 */
	public static String getDetail(ServletRequest request) {
		final HttpServletRequest hreq = request instanceof HttpServletRequest ? (HttpServletRequest) request : null;
		final StringBuffer sb = new StringBuffer(128);
		if (hreq != null) {
			sb.append(" sid: ").append(hreq.getHeader("ZK-SID")).append('\n');
			addHeaderInfo(sb, hreq, "user-agent");
			addHeaderInfo(sb, hreq, "content-length");
			addHeaderInfo(sb, hreq, "content-type");
			//			sb.append(" method: ").append(hreq.getMethod());
		}
		sb.append(" ip: ").append(request.getRemoteAddr());
		return sb.toString();
	}

	private static void addHeaderInfo(StringBuffer sb, HttpServletRequest request, String header) {
		sb.append(' ').append(header).append(": ").append(request.getHeader(header)).append('\n');
	}

	/** A plugin used to provide additional browser information for
	 * {@link #getBrowser}.
	 * @since 6.0.0
	 * @see #setClientIdentifier
	 */
	public static interface ClientIdentifier {
		/** Returns the information of the client if userAgent matches
		 * this client, or null if not matched.
		 * @param userAgent represents a client.
		 */
		public ClientIdentifier matches(String userAgent);

		/** Returns the name of the browser.
		 * It is called only against the instance returned by {@link #matches}.
		 */
		public String getName();

		/** Returns the version of the browser.
		 * It is called only against the instance returned by {@link #matches}.
		 */
		public double getVersion();
	}

	/** Returns the normal path; that is, will elminate the double dots
	 * ".."(parent) and single dot "."(current) in the path as possible. e.g.
	 * /abc/../def would be normalized to /def; /abc/./def would be
	 * normalized to /abc/def; /abc//def would be normalized to /abc/def.
	 * <p>Note that if found no way to navigate the path, it is deemed as an illegal path. e.g.
	 * /../abc or /../../abc is deemed as illegal path since we don't
	 * know how to continue doing the normalize.
	 * @since 3.6.2
	 */
	public static String getNormalPath(String path) {
		final StringBuffer sb = new StringBuffer(path);
		final IntStack slashes = new IntStack(32); //most 32 slash in a path
		slashes.push(-1);
		int j = 0, colon = -100, dot1 = -100, dot2 = -100;
		for (; j < sb.length(); ++j) {
			final char c = sb.charAt(j);
			switch (c) {
			case '/':
				if (dot1 >= 0) { //single dot or double dots
					if (dot2 >= 0) { //double dots
						int preslash = slashes.pop();
						if (preslash == 0) { //special case "/../"
							throw new IllegalArgumentException("Illegal path: " + path);
						}
						if (slashes.isEmpty()) {
							slashes.push(-1);
						}
						dot2 = -100;
					}
					int b = slashes.peek();
					sb.delete(b + 1, j + 1);
					j = b;
					dot1 = -100;
				} else { //no dot
					int s = slashes.peek();
					if (s >= 0) {
						if (j == (s + 1)) { //consequtive slashs
							if (colon == (s - 1)) { //e.g. "http://abc"
								slashes.clear();
								slashes.push(-1);
								slashes.push(j);
							} else {
								--j;
								sb.delete(j, j + 1);
							}
							continue;
						}
					}
					slashes.push(j);
				}
				break;
			case '.':
				if (dot1 < 0) {
					if (slashes.peek() == (j - 1))
						dot1 = j;
				} else if (dot2 < 0) {
					dot2 = j;
				} else { //more than 2 consecutive dots
					throw new IllegalArgumentException("Illegal path: " + path);
				}
				break;
			case ':':
				if (colon >= 0) {
					throw new IllegalArgumentException("Illegal path: " + path);
				}
				colon = j;
			default:
				dot1 = dot2 = -100;
			}
		}
		return sb.toString();
	}

	private static class IntStack {
		private int _top = -1;
		private int[] _value;

		public IntStack(int sz) {
			_value = new int[sz];
		}

		public boolean isEmpty() {
			return _top < 0;
		}

		public int peek() {
			return _top >= 0 && _top < _value.length ? _value[_top] : -100;
		}

		public int pop() {
			return _value[_top--];
		}

		public void push(int val) {
			_value[++_top] = val;
		}

		public void clear() {
			_top = -1;
		}
	}
}
