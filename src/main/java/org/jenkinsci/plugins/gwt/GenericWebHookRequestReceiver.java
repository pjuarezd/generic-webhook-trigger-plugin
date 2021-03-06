package org.jenkinsci.plugins.gwt;

import static com.google.common.base.Charsets.UTF_8;
import static hudson.util.HttpResponses.okJSON;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;
import hudson.Extension;
import hudson.model.UnprotectedRootAction;
import hudson.security.csrf.CrumbExclusion;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;

import com.google.common.annotations.VisibleForTesting;

@Extension
public class GenericWebHookRequestReceiver extends CrumbExclusion implements UnprotectedRootAction {

  private static final String URL_NAME = "generic-webhook-trigger";
  private static final Logger LOGGER =
      Logger.getLogger(GenericWebHookRequestReceiver.class.getName());

  public HttpResponse doInvoke(StaplerRequest request) {
    String postContent = null;
    Map<String, String[]> parameterMap = null;
    Map<String, Enumeration<String>> headers = null;
    try {
      headers = getHeaders(request);
      parameterMap = request.getParameterMap();
      postContent = IOUtils.toString(request.getInputStream(), UTF_8.name());
    } catch (IOException e) {
      LOGGER.log(SEVERE, "", e);
    }

    return doInvoke(headers, parameterMap, postContent);
  }

  private Map<String, Enumeration<String>> getHeaders(StaplerRequest request) {
    Map<String, Enumeration<String>> headers = new HashMap<>();
    Enumeration<String> headersEnumeration = request.getHeaderNames();
    while (headersEnumeration.hasMoreElements()) {
      String headerName = headersEnumeration.nextElement();
      headers.put(headerName, request.getHeaders(headerName));
    }
    return headers;
  }

  @VisibleForTesting
  HttpResponse doInvoke(
      Map<String, Enumeration<String>> headers,
      Map<String, String[]> parameterMap,
      String postContent) {
    List<GenericTrigger> triggers = JobFinder.findAllJobsWithTrigger();
    if (triggers.isEmpty()) {
      LOGGER.log(
          INFO,
          "Did not find any jobs to trigger! The user invoking /generic-webhook-trigger/invoke must have read permission to any jobs that should be triggered.");
    }
    Map<String, String> triggerResults = new HashMap<>();
    for (GenericTrigger trigger : triggers) {
      try {
        LOGGER.log(INFO, "Triggering " + trigger.toString());
        LOGGER.log(FINE, " with:\n\n" + postContent + "\n\n");
        trigger.trigger(headers, parameterMap, postContent);
        triggerResults.put(trigger.toString(), "OK");
      } catch (Exception e) {
        LOGGER.log(SEVERE, trigger.toString(), e);
        triggerResults.put(trigger.toString(), ExceptionUtils.getStackTrace(e));
      }
    }
    Map<String, Object> response = new HashMap<>();
    response.put("triggerResults", triggerResults);
    return okJSON(response);
  }

  @Override
  public String getIconFileName() {
    return null;
  }

  @Override
  public String getDisplayName() {
    return null;
  }

  @Override
  public String getUrlName() {
    return URL_NAME;
  }

  @Override
  public boolean process(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    String pathInfo = request.getPathInfo();
    if (pathInfo != null && pathInfo.startsWith("/" + URL_NAME + "/")) {
      chain.doFilter(request, response);
      return true;
    }
    return false;
  }
}
