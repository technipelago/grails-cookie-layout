/*
 * Copyright 2011 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grails.plugin.cookielayout;

import grails.util.Environment;
import groovy.lang.GroovyObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.groovy.grails.commons.GrailsClassUtils;
import org.codehaus.groovy.grails.commons.GrailsStringUtils;
import org.codehaus.groovy.grails.io.support.GrailsResourceUtils;
import org.codehaus.groovy.grails.web.metaclass.ControllerDynamicMethods;
import org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes;
import org.codehaus.groovy.grails.web.servlet.view.AbstractGrailsView;
import org.codehaus.groovy.grails.web.servlet.view.GrailsViewResolver;
import org.codehaus.groovy.grails.web.servlet.view.LayoutViewResolver;
import org.codehaus.groovy.grails.web.sitemesh.GSPSitemeshPage;
import org.codehaus.groovy.grails.web.sitemesh.GroovyPageLayoutFinder;
import org.codehaus.groovy.grails.web.sitemesh.SpringMVCViewDecorator;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;

import com.opensymphony.module.sitemesh.Decorator;
import com.opensymphony.module.sitemesh.Page;
import com.opensymphony.sitemesh.Content;

/**
 * This is a modified version of standard GroovyPageLayoutFinder
 * that checks a cookie to see if an alternate layout is specified.
 *
 * @author Graeme Rocher
 * @author Goran Ehrsson
 */
public class CookiePageLayoutFinder extends GroovyPageLayoutFinder {
    public static final String LAYOUT_ATTRIBUTE = "org.grails.layout.name";
    public static final String NONE_LAYOUT = "_none_";
    public static final String RENDERING_VIEW_ATTRIBUTE = "org.grails.rendering.view";
    private static final Log LOG = LogFactory.getLog(CookiePageLayoutFinder.class);
    private static final long LAYOUT_CACHE_EXPIRATION_MILLIS = Long.getLong("grails.gsp.reload.interval", 5000);
    private static final String LAYOUTS_PATH = "/layouts";

    private Map<String, DecoratorCacheValue> decoratorCache = new ConcurrentHashMap<String, DecoratorCacheValue>();
    private Map<LayoutCacheKey, DecoratorCacheValue> layoutDecoratorCache = new ConcurrentHashMap<LayoutCacheKey, DecoratorCacheValue>();

    private String defaultDecoratorName;
    private boolean gspReloadEnabled;
    private boolean cacheEnabled = (Environment.getCurrent() != Environment.DEVELOPMENT);
    private ViewResolver viewResolver;
    private boolean enableNonGspViews = false;
    private String cookieLayoutName;
    private String cookieLayoutAppend = null;
    private boolean checkRequest = false;

    public void setDefaultDecoratorName(String defaultDecoratorName) {
        this.defaultDecoratorName = defaultDecoratorName;
    }

    public void setEnableNonGspViews(boolean enableNonGspViews) {
        this.enableNonGspViews = enableNonGspViews;
    }

    public void setGspReloadEnabled(boolean gspReloadEnabled) {
        this.gspReloadEnabled = gspReloadEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public void setViewResolver(ViewResolver viewResolver) {
        if(viewResolver instanceof LayoutViewResolver) {
            this.viewResolver = ((LayoutViewResolver)viewResolver).getInnerViewResolver();
        } else {
            this.viewResolver = viewResolver;
        }
    }
    public void setCookieLayoutName(String cookieName) {
        this.cookieLayoutName = cookieName;
    }

    public void setCookieLayoutAppend(String append) {
        this.cookieLayoutAppend = append;
    }

    public void setCheckRequest(boolean arg) { this.checkRequest = arg; }

    public Decorator findLayout(HttpServletRequest request, Content page) {
        return findLayout(request, GSPSitemeshPage.content2htmlPage(page));
    }

    public Decorator findLayout(HttpServletRequest request, Page page) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Evaluating layout for request: " + request.getRequestURI());
        }
        final Object layoutAttribute = request.getAttribute(LAYOUT_ATTRIBUTE);
        if (request.getAttribute(RENDERING_VIEW_ATTRIBUTE) != null || layoutAttribute != null) {
            String layoutName = layoutAttribute == null ? null : layoutAttribute.toString();

            if (layoutName == null) {
                layoutName = page.getProperty("meta.layout");
            }

            /*
             * The following block is the main change applied to original GroovyPageLayoutFinder
             */
            String cookieLayout = getLayoutFromCookie(request, cookieLayoutName);
            if (!GrailsStringUtils.isBlank(cookieLayout)) {
                if (cookieLayoutAppend != null) {
                    layoutName = layoutName + cookieLayoutAppend + cookieLayout;
                } else {
                    layoutName = cookieLayout;
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Found theme cookie: " + cookieLayout);
                }
            }

            Decorator d = null;

            if (GrailsStringUtils.isBlank(layoutName)) {
                GroovyObject controller = (GroovyObject)request.getAttribute(GrailsApplicationAttributes.CONTROLLER);
                if (controller != null) {
                    String controllerName = (String)controller.getProperty(ControllerDynamicMethods.CONTROLLER_NAME_PROPERTY);
                    String actionUri = (String)controller.getProperty(ControllerDynamicMethods.ACTION_URI_PROPERTY);

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Found controller in request, location layout for controller [" + controllerName
                                + "] and action [" + actionUri + "]");
                    }

                    LayoutCacheKey cacheKey = null;
                    boolean cachedIsNull = false;

                    if (cacheEnabled) {
                        cacheKey = new LayoutCacheKey(controllerName, actionUri, cookieLayout);
                        DecoratorCacheValue cacheValue = layoutDecoratorCache.get(cacheKey);
                        if (cacheValue != null && (!gspReloadEnabled || !cacheValue.isExpired())) {
                            d = cacheValue.getDecorator();
                            if (d == null) {
                                cachedIsNull = true;
                            }
                        }
                    }

                    if (d == null && !cachedIsNull) {
                        d = resolveDecorator(request, controller, controllerName, actionUri);
                        if (cacheEnabled) {
                            layoutDecoratorCache.put(cacheKey, new DecoratorCacheValue(d));
                        }
                    }
                }
                else {
                    d = getApplicationDefaultDecorator(request);
                }
            }
            else {
                d = getNamedDecorator(request, layoutName);
            }

            if (d != null) {
                return d;
            }
        }
        return null;
    }

    protected Decorator getApplicationDefaultDecorator(HttpServletRequest request) {
        return getNamedDecorator(request, defaultDecoratorName == null ? "application" : defaultDecoratorName,
                !enableNonGspViews || defaultDecoratorName == null);
    }

    public Decorator getNamedDecorator(HttpServletRequest request, String name) {
        return getNamedDecorator(request, name, !enableNonGspViews);
    }

    public Decorator getNamedDecorator(HttpServletRequest request, String name, boolean viewMustExist) {
        if (GrailsStringUtils.isBlank(name) || NONE_LAYOUT.equals(name)) {
            return null;
        }

        if (cacheEnabled) {
            DecoratorCacheValue cacheValue = decoratorCache.get(name);
            if (cacheValue != null && (!gspReloadEnabled || !cacheValue.isExpired())) {
                return cacheValue.getDecorator();
            }
        }

        View view;
        try {
            view = viewResolver.resolveViewName(GrailsResourceUtils.appendPiecesForUri(LAYOUTS_PATH, name),
                    request.getLocale());
            // it's only possible to check that GroovyPageView exists
            if (viewMustExist && !(view instanceof AbstractGrailsView)) {
                view = null;
            }
        }
        catch (Exception e) {
            throw new RuntimeException("Unable to resolve view", e);
        }

        Decorator d = null;
        if (view != null) {
            d = createDecorator(name, view);
        }

        if (cacheEnabled) {
            decoratorCache.put(name, new DecoratorCacheValue(d));
        }
        return d;
    }

    private Decorator resolveDecorator(HttpServletRequest request, GroovyObject controller, String controllerName,
            String actionUri) {
        Decorator d = null;

        Object layoutProperty = GrailsClassUtils.getStaticPropertyValue(controller.getClass(), "layout");
        if (layoutProperty instanceof CharSequence) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("layout property found in controller, looking for template named " + layoutProperty);
            }
            d = getNamedDecorator(request, layoutProperty.toString());
        }
        else {
            if (d == null && !GrailsStringUtils.isBlank(actionUri)) {
                d = getNamedDecorator(request, actionUri.substring(1), true);
            }

            if (d == null && !GrailsStringUtils.isBlank(controllerName)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Action layout not found, trying controller");
                }
                d = getNamedDecorator(request, controllerName, true);
            }

            if (d == null) {
                d = getApplicationDefaultDecorator(request);
            }
        }

        return d;
    }

    private Decorator createDecorator(String decoratorName, View view) {
        return new SpringMVCViewDecorator(decoratorName, view);
    }

    private String getLayoutFromCookie(HttpServletRequest request, String cookieName) {
        if(checkRequest) {
            final Object requestLayout = request.getAttribute(cookieName);
            if(requestLayout != null) {
                return requestLayout.toString();
            }
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (c.getName().equals(cookieName)) {
                    return c.getValue();
                }
            }
        }
        return "";
    }

    private static class LayoutCacheKey {
        private String controllerName;
        private String actionUri;
        private String themeName;

        public LayoutCacheKey(String controllerName, String actionUri, String themeName) {
            this.controllerName = controllerName;
            this.actionUri = actionUri;
            this.themeName = themeName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            LayoutCacheKey that = (LayoutCacheKey) o;

            if (!actionUri.equals(that.actionUri)) return false;
            if (!controllerName.equals(that.controllerName)) return false;
            if (!themeName.equals(that.themeName)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = controllerName.hashCode();
            result = 31 * result + actionUri.hashCode();
            result = 31 * result + themeName.hashCode();
            return result;
        }
    }

    private static class DecoratorCacheValue {
        Decorator decorator;
        long createTimestamp = System.currentTimeMillis();

        public DecoratorCacheValue(Decorator decorator) {
            this.decorator = decorator;
        }

        public Decorator getDecorator() {
            return decorator;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - createTimestamp > LAYOUT_CACHE_EXPIRATION_MILLIS;
        }
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (!(viewResolver instanceof GrailsViewResolver)) {
            setViewResolver(event.getApplicationContext().getBean(GrailsViewResolver.class));
        }

    }
}