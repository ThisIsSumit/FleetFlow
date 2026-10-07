package com.fleetflow.account;
import java.util.regex.*;
/** Turns our plain-text emails into a small branded HTML email (first URL becomes a button). All text is HTML-escaped first. */
public final class EmailHtml {
  private EmailHtml() {}
  static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
  public static String render(String subject, String text) {
    String body = esc(text); Matcher m = Pattern.compile("https?://[^\\s]+").matcher(body);
    String before = body, url = null;
    if (m.find()) { url = m.group(); before = body.substring(0, m.start()); }
    var sb = new StringBuilder("<div style=\"font-family:Inter,Arial,sans-serif;max-width:520px;margin:0 auto;padding:24px;color:#0f172a\">")
        .append("<div style=\"font-size:20px;font-weight:600;color:#2563eb\">FleetFlow</div>")
        .append("<h2 style=\"margin:16px 0 8px;font-size:18px\">").append(esc(subject)).append("</h2>")
        .append("<p style=\"line-height:1.5\">").append(before.trim().replace("\n", "<br>")).append("</p>");
    if (url != null) sb.append("<p><a href=\"").append(url).append("\" style=\"display:inline-block;background:#2563eb;color:#fff;text-decoration:none;padding:10px 18px;border-radius:8px\">Open link</a></p>")
        .append("<p style=\"color:#64748b;font-size:12px\">If the button does not work, copy this link into your browser:<br>").append(url).append("</p>");
    return sb.append("<hr style=\"border:none;border-top:1px solid #e2e8f0;margin:24px 0\"><p style=\"color:#64748b;font-size:12px\">FleetFlow — Intelligent dispatch infrastructure for modern delivery operations.</p></div>").toString();
  }
}
