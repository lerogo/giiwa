package org.giiwa.core.base;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.giiwa.core.bean.X;
import org.giiwa.core.json.JSON;

public class Url {

	private static Log log = LogFactory.getLog(Url.class);

	String url;
	String protocol;
	String uri;
	String ip;
	int port;

	Map<String, String> query;

	public JSON toJson() {
		return JSON.create().append("url", url);
	}

	public static Url fromJson(JSON jo) {
		String url = jo.getString("url");
		return Url.create(url);
	}

	public String getUrl() {
		return url;
	}

	public String getProtocol() {
		return protocol;
	}

	public String getIp() {
		return ip;
	}

	public int getPort(int defaultPort) {
		return port > 0 ? port : defaultPort;
	}

	public Map<String, String> getQuery() {
		return query;
	}

	/**
	 * 
	 * @param url,
	 *            protocol://ip:port?user=&passwd=
	 * @return
	 */
	public static Url create(String url) {
		Url u = new Url();
		u.url = url;
		try {
			if (u.parse()) {
				return u;
			}
		} catch (Exception e) {
			e.printStackTrace();
			log.error(e.getMessage(), e);
		}
		return null;
	}

	private boolean parse() throws Exception {
		String s = url;
		int i = s.indexOf("://");
		if (i > 0) {
			protocol = s.substring(0, i);
			s = s.substring(i + 3);
		}

		i = s.indexOf("?");
		if (i >= 0) {
			String query = (i >= (s.length() - 1)) ? null : s.substring(i + 1);
			s = i > 0 ? s.substring(0, i) : null;

			// parse query
			if (!X.isEmpty(query)) {
				this.query = new TreeMap<String, String>();
				String[] ss = query.split("&");
				for (String s1 : ss) {
					i = s1.indexOf("=");
					if (i > -1) {
						String n = s1.substring(0, i);
						String v = s1.substring(i + 1);
						this.query.put(n, URLDecoder.decode(v, "UTF8"));
					}
				}
			}
		}

		if (s != null) {
			i = s.indexOf("/");
			if (i >= 0) {
				uri = s.substring(i);
				s = i > 0 ? s.substring(0, i) : null;
			}
		}

		if (s != null) {
			i = s.indexOf(":");
			if (i >= 0) {
				port = (i >= (s.length() - 1)) ? 0 : X.toInt(s.substring(i + 1));
				ip = i > 0 ? s.substring(0, i) : null;
			} else {
				ip = s;
			}
		}

		return true;
	}

	@Override
	public String toString() {
		return "Url [url=" + url + ", protocol=" + protocol + ", ip=" + ip + ", port=" + port + ", uri=" + uri
				+ ", query=" + query + "]";
	}

	public String get(String name) {
		return query == null ? X.EMPTY : query.get(name);
	}

	public Url append(String name, String value) {
		if (query == null) {
			query = new TreeMap<String, String>();
		}
		query.put(name, value);
		return this;
	}

	public String encodedUrl() {
		StringBuilder sb = new StringBuilder();
		if (!X.isEmpty(protocol)) {
			sb.append(protocol).append("://");
		}
		if (!X.isEmpty(ip)) {
			sb.append(ip);
		}
		if (port > 0) {
			sb.append(":").append(port);
		}
		if (!X.isEmpty(uri)) {
			sb.append(uri);
		}
		if (query != null && !query.isEmpty()) {
			try {
				StringBuilder sb1 = new StringBuilder();
				for (String name : query.keySet()) {
					if (sb1.length() > 0) {
						sb1.append("&");
					}
					sb1.append(name).append("=");
					sb1.append(URLEncoder.encode(query.get(name), "UTF8"));
				}
				sb.append("?").append(sb1.toString());
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
		return sb.toString();
	}

	public boolean isProtocol(String protocol) {
		return X.isEmpty(this.protocol) || X.isSame("*", this.protocol) || X.isSame(this.protocol, protocol);
	}

	public static String encode(String url) {
		try {
			return URLEncoder.encode(url, "UTF8");
		} catch (UnsupportedEncodingException e) {
			log.error(e.getMessage(), e);
		}
		return null;
	}

	public static String decode(String url) {
		try {
			return URLDecoder.decode(url, "UTF8");
		} catch (UnsupportedEncodingException e) {
			log.error(e.getMessage(), e);
		}
		return null;
	}

	public static void main(String[] args) {
		String s = "http://11/aaa?a=1";
		Url u = Url.create(s);
		System.out.println(u);
		System.out.println(u.encodedUrl());

	}
}