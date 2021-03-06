/*
 * Copyright (c) 2010-2015 Evolveum
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

package com.evolveum.midpoint.web.security;

import com.evolveum.midpoint.common.configuration.api.MidpointConfiguration;
import com.evolveum.midpoint.model.api.ModelInteractionService;
import com.evolveum.midpoint.model.api.ModelService;
import com.evolveum.midpoint.model.api.TaskService;
import com.evolveum.midpoint.model.api.WorkflowService;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.crypto.Protector;
import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.task.api.TaskManager;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.application.DescriptorLoader;
import com.evolveum.midpoint.web.component.GuiComponents;
import com.evolveum.midpoint.web.page.PageBase;
import com.evolveum.midpoint.web.page.admin.home.PageDashboard;
import com.evolveum.midpoint.web.page.error.PageError;
import com.evolveum.midpoint.web.page.error.PageError401;
import com.evolveum.midpoint.web.page.error.PageError403;
import com.evolveum.midpoint.web.page.error.PageError404;
import com.evolveum.midpoint.web.page.login.PageLogin;
import com.evolveum.midpoint.web.page.self.PageSelf;
import com.evolveum.midpoint.web.page.self.PageSelfDashboard;
import com.evolveum.midpoint.web.resource.img.ImgResources;
import com.evolveum.midpoint.web.util.MidPointPageParametersEncoder;
import com.evolveum.midpoint.web.util.Utf8BundleStringResourceLoader;
import com.evolveum.midpoint.web.util.WebMiscUtil;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.io.IOUtils;
import org.apache.wicket.Page;
import org.apache.wicket.RuntimeConfigurationType;
import org.apache.wicket.authroles.authentication.AbstractAuthenticatedWebSession;
import org.apache.wicket.authroles.authentication.AuthenticatedWebApplication;
import org.apache.wicket.core.request.handler.PageProvider;
import org.apache.wicket.core.request.handler.RenderPageRequestHandler;
import org.apache.wicket.core.request.mapper.MountedMapper;
import org.apache.wicket.markup.head.PriorityFirstComparator;
import org.apache.wicket.markup.html.SecurePackageResourceGuard;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.request.IRequestHandler;
import org.apache.wicket.request.Url;
import org.apache.wicket.request.component.IRequestablePage;
import org.apache.wicket.request.cycle.AbstractRequestCycleListener;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.resource.PackageResourceReference;
import org.apache.wicket.request.resource.SharedResourceReference;
import org.apache.wicket.resource.loader.IStringResourceLoader;
import org.apache.wicket.settings.IApplicationSettings;
import org.apache.wicket.settings.IRequestLoggerSettings;
import org.apache.wicket.settings.IResourceSettings;
import org.apache.wicket.spring.injection.annot.SpringComponentInjector;
import org.apache.wicket.util.lang.Bytes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FilenameFilter;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.util.*;

/**
 * @author lazyman
 */
@Component("midpointApplication")
public class MidPointApplication extends AuthenticatedWebApplication {

    /**
     * Max. photo size for user/jpegPhoto
     */
    public static final Bytes FOCUS_PHOTO_MAX_FILE_SIZE = Bytes.kilobytes(192);

    public static final String WEB_APP_CONFIGURATION = "midpoint.webApplication";

    public static final List<LocaleDescriptor> AVAILABLE_LOCALES;

    private static final String LOCALIZATION_DESCRIPTOR = "/localization/locale.properties";

    private static final String PROP_NAME = ".name";
    private static final String PROP_FLAG = ".flag";
    private static final String PROP_DEFAULT = ".default";

    private static final Trace LOGGER = TraceManager.getTrace(MidPointApplication.class);

	private static final String REQUEST_LOGGER_NAME = "com.evolveum.midpoint.request.web";
    private static final Trace REQUEST_LOGGER = TraceManager.getTrace(REQUEST_LOGGER_NAME);

    static {
        List<LocaleDescriptor> locales = new ArrayList<>();
        try {
            ClassLoader classLoader = MidPointApplication.class.getClassLoader();
            Enumeration<URL> urls = classLoader.getResources(LOCALIZATION_DESCRIPTOR);
            while (urls.hasMoreElements()) {
                final URL url = urls.nextElement();
                LOGGER.debug("Found localization descriptor {}.", new Object[]{url.toString()});

                Properties properties = new Properties();
                Reader reader = null;
                try {
                    reader = new InputStreamReader(url.openStream(), "utf-8");
                    properties.load(reader);

                    Map<String, Map<String, String>> localeMap = new HashMap<>();
                    Set<String> keys = (Set) properties.keySet();
                    for (String key : keys) {
                        String[] array = key.split("\\.");
                        if (array.length != 2) {
                            continue;
                        }

                        String locale = array[0];
                        Map<String, String> map = localeMap.get(locale);
                        if (map == null) {
                            map = new HashMap<>();
                            localeMap.put(locale, map);
                        }

                        map.put(key, properties.getProperty(key));
                    }

                    for (String key : localeMap.keySet()) {
                        Map<String, String> localeDefinition = localeMap.get(key);
                        if (!localeDefinition.containsKey(key + PROP_NAME)
                                || !localeDefinition.containsKey(key + PROP_FLAG)) {
                            continue;
                        }

                        LocaleDescriptor descriptor = new LocaleDescriptor(
                                localeDefinition.get(key + PROP_NAME),
                                localeDefinition.get(key + PROP_FLAG),
                                localeDefinition.get(key + PROP_DEFAULT),
                                WebMiscUtil.getLocaleFromString(key)
                        );
                        locales.add(descriptor);
                    }
                } catch (Exception ex) {
                    LoggingUtils.logException(LOGGER, "Couldn't load localization", ex);
                } finally {
                    IOUtils.closeQuietly(reader);
                }
            }

            Collections.sort(locales);
        } catch (Exception ex) {
            LoggingUtils.logException(LOGGER, "Couldn't load locales", ex);
        }

        AVAILABLE_LOCALES = Collections.unmodifiableList(locales);
    }

    @Autowired
    transient ModelService model;
    @Autowired
    transient ModelInteractionService modelInteractionService;
    @Autowired
    transient TaskService taskService;
    @Autowired
    transient PrismContext prismContext;
    @Autowired
    transient TaskManager taskManager;
    @Autowired
    transient private WorkflowService workflowService;
    @Autowired
    transient MidpointConfiguration configuration;
    @Autowired(required = true)
    transient Protector protector;
    private WebApplicationConfiguration webApplicationConfiguration;

    @Override
    protected void onDestroy() {
        GuiComponents.destroy();

        super.onDestroy();
    }

    @Override
    public Class<? extends PageBase> getHomePage() {
        if (WebMiscUtil.isAuthorized(AuthorizationConstants.AUTZ_UI_DASHBOARD_URL,
                AuthorizationConstants.AUTZ_UI_HOME_ALL_URL)) {
            return PageDashboard.class;
        } else {
            return PageSelfDashboard.class;
        }
    }

    @Override
    public void init() {
        super.init();

        getJavaScriptLibrarySettings().setJQueryReference(
                new PackageResourceReference(MidPointApplication.class,
                        "../../../../../webjars/adminlte/2.3.0/plugins/jQuery/jQuery-2.1.4.min.js"));

        GuiComponents.init();

        getComponentInstantiationListeners().add(new SpringComponentInjector(this));

        IResourceSettings resourceSettings = getResourceSettings();
        resourceSettings.setParentFolderPlaceholder("$-$");
        resourceSettings.setHeaderItemComparator(new PriorityFirstComparator(true));
        SecurePackageResourceGuard guard = (SecurePackageResourceGuard) resourceSettings.getPackageResourceGuard();
        guard.addPattern("+*.woff2");

        List<IStringResourceLoader> resourceLoaders = resourceSettings.getStringResourceLoaders();
        resourceLoaders.add(0, new Utf8BundleStringResourceLoader("localization/Midpoint"));

        resourceSettings.setThrowExceptionOnMissingResource(false);
        getMarkupSettings().setStripWicketTags(true);
        getMarkupSettings().setDefaultBeforeDisabledLink("");
        getMarkupSettings().setDefaultAfterDisabledLink("");

        if (RuntimeConfigurationType.DEVELOPMENT.equals(getConfigurationType())) {
            getDebugSettings().setAjaxDebugModeEnabled(true);
            getDebugSettings().setDevelopmentUtilitiesEnabled(true);
        }

        //pretty url for resources (e.g. images)
        mountFiles(ImgResources.BASE_PATH, ImgResources.class);

        //exception handling an error pages
        IApplicationSettings appSettings = getApplicationSettings();
        appSettings.setAccessDeniedPage(PageError401.class);
        appSettings.setInternalErrorPage(PageError.class);
        appSettings.setPageExpiredErrorPage(PageError.class);

        mount(new MountedMapper("/error", PageError.class, MidPointPageParametersEncoder.ENCODER));
        mount(new MountedMapper("/error/401", PageError401.class, MidPointPageParametersEncoder.ENCODER));
        mount(new MountedMapper("/error/403", PageError403.class, MidPointPageParametersEncoder.ENCODER));
        mount(new MountedMapper("/error/404", PageError404.class, MidPointPageParametersEncoder.ENCODER));

        getRequestCycleListeners().add(new AbstractRequestCycleListener() {

            @Override
            public IRequestHandler onException(RequestCycle cycle, Exception ex) {
            	if (REQUEST_LOGGER.isTraceEnabled()) {
					REQUEST_LOGGER.trace("REQUEST CYCLE: Exception: {}, handler {}", ex,
						WebMiscUtil.debugHandler(cycle.getActiveRequestHandler()), ex);
				}
                LoggingUtils.logUnexpectedException(LOGGER, "Error occurred during page rendering", ex);
                return new RenderPageRequestHandler(new PageProvider(new PageError(ex)));
            }

			@Override
			public void onRequestHandlerScheduled(RequestCycle cycle, IRequestHandler handler) {
				if (handler instanceof RenderPageRequestHandler) {
					Class<? extends IRequestablePage> pageClass = ((RenderPageRequestHandler)handler).getPageClass();
					if (REQUEST_LOGGER.isTraceEnabled()) {
						REQUEST_LOGGER.trace("REQUEST CYCLE: Scheduled redirect to page {}", pageClass);
					}
					if (PageError.class.isAssignableFrom(pageClass)) {
						REQUEST_LOGGER.info("REQUEST CYCLE: Scheduled redirect to error page {}", pageClass);
					}
				} else {
					if (REQUEST_LOGGER.isTraceEnabled()) {
						REQUEST_LOGGER.trace("REQUEST CYCLE: Scheduled request handler {}", 
							WebMiscUtil.debugHandler(handler));
					}
				}
				super.onRequestHandlerScheduled(cycle, handler);
			}
			
			@Override
			public void onRequestHandlerResolved(RequestCycle cycle, IRequestHandler handler)
			{
				if (REQUEST_LOGGER.isTraceEnabled()) {
					REQUEST_LOGGER.trace("REQUEST CYCLE: Resolved request handler {}", 
						WebMiscUtil.debugHandler(handler));
				}
			}
            
			@Override
			public void onBeginRequest(RequestCycle cycle)
			{
				if (REQUEST_LOGGER.isTraceEnabled()) {
					REQUEST_LOGGER.trace("REQUEST CYCLE: Begin request: '{}', handler {}", cycle.getRequest().getOriginalUrl(),
						WebMiscUtil.debugHandler(cycle.getActiveRequestHandler()));
				}
				super.onBeginRequest(cycle);
			}
			
			@Override
			public void onEndRequest(RequestCycle cycle)
			{
				if (REQUEST_LOGGER.isTraceEnabled()) {
					REQUEST_LOGGER.trace("REQUEST CYCLE: End request: '{}', next handler: {}", cycle.getRequest().getOriginalUrl(),
							WebMiscUtil.debugHandler(cycle.getRequestHandlerScheduledAfterCurrent()));
				}
				super.onBeginRequest(cycle);
			}
			
			@Override
			public void onDetach(RequestCycle cycle)
			{
				if (REQUEST_LOGGER.isTraceEnabled()) {
					REQUEST_LOGGER.trace("REQUEST CYCLE: Detach, request: '{}', next handler: {}", cycle.getRequest().getOriginalUrl(),
							WebMiscUtil.debugHandler(cycle.getRequestHandlerScheduledAfterCurrent()));
					
				}
				super.onBeginRequest(cycle);
			}
			
			@Override
			public void onExceptionRequestHandlerResolved(RequestCycle cycle, IRequestHandler handler,
				Exception exception)
			{
				if (REQUEST_LOGGER.isTraceEnabled()) {
					REQUEST_LOGGER.trace("REQUEST CYCLE: Exception - Resolved request handler {}", 
						WebMiscUtil.debugHandler(handler), exception);
				}
			}

			@Override
			public void onRequestHandlerExecuted(RequestCycle cycle, IRequestHandler handler)
			{
				if (REQUEST_LOGGER.isTraceEnabled()) {
					REQUEST_LOGGER.trace("REQUEST CYCLE: Request handler executed {}", 
						WebMiscUtil.debugHandler(handler));
				}
			}

			@Override
			public void onUrlMapped(RequestCycle cycle, IRequestHandler handler, Url url)
			{
				if (REQUEST_LOGGER.isTraceEnabled()) {
					REQUEST_LOGGER.trace("REQUEST CYCLE: Url '{}' mapped, handler {}", url, 
						WebMiscUtil.debugHandler(handler));
				}
			}
            
        });
        
        if (REQUEST_LOGGER.isDebugEnabled()) {
	        IRequestLoggerSettings requestLoggerSettings = getRequestLoggerSettings();
	        requestLoggerSettings.setRequestLoggerEnabled(true);
	        if (REQUEST_LOGGER.isTraceEnabled()) {
	        	requestLoggerSettings.setRecordSessionSize(true);
	        }
        }

        //descriptor loader, used for customization
        new DescriptorLoader().loadData(this);
    }

    private void mountFiles(String path, Class<?> clazz) {
        try {
            List<Resource> list = new ArrayList<>();
            String packagePath = clazz.getPackage().getName().replace('.', '/');

            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] res = resolver.getResources("classpath:" + packagePath + "/*.png");
            if (res != null) {
                list.addAll(Arrays.asList(res));
            }
            res = resolver.getResources("classpath:" + packagePath + "/*.gif");
            if (res != null) {
                list.addAll(Arrays.asList(res));
            }

            for (Resource resource : list) {
                URI uri = resource.getURI();
                File file = new File(uri.toString());
                mountResource(path + "/" + file.getName(), new SharedResourceReference(clazz, file.getName()));
            }
        } catch (Exception ex) {
            LoggingUtils.logException(LOGGER, "Couldn't mount files", ex);
        }
    }

    public WebApplicationConfiguration getWebApplicationConfiguration() {
        if (webApplicationConfiguration == null) {
            Configuration config = configuration.getConfiguration(WEB_APP_CONFIGURATION);
            webApplicationConfiguration = new WebApplicationConfiguration(config);
        }
        return webApplicationConfiguration;
    }

    public ModelService getModel() {
        return model;
    }

    public TaskManager getTaskManager() {
        return taskManager;
    }

    public TaskService getTaskService() {
        return taskService;
    }

    public PrismContext getPrismContext() {
        return prismContext;
    }

    public Protector getProtector() {
        return protector;
    }

    @Override
    protected Class<? extends WebPage> getSignInPageClass() {
        return PageLogin.class;
    }

    @Override
    protected Class<? extends AbstractAuthenticatedWebSession> getWebSessionClass() {
        return MidPointAuthWebSession.class;
    }

    public WorkflowService getWorkflowService() {
        return workflowService;
    }

    public ModelInteractionService getModelInteractionService() {
        return modelInteractionService;
    }

    public String getString(String key) {
        IResourceSettings resourceSettings = getResourceSettings();
        List<IStringResourceLoader> resourceLoaders = resourceSettings.getStringResourceLoaders();
        IStringResourceLoader loader = resourceLoaders.get(0);
        return loader.loadStringResource((Class) null, key, null, null, null);
    }

    public static boolean containsLocale(Locale locale) {
        if (locale == null) {
            return false;
        }

        for (LocaleDescriptor descriptor : AVAILABLE_LOCALES) {
            if (locale.equals(descriptor.getLocale())) {
                return true;
            }
        }

        return false;
    }

    public static Locale getDefaultLocale() {
        for (LocaleDescriptor descriptor : AVAILABLE_LOCALES) {
            if (descriptor.isDefault()) {
                return descriptor.getLocale();
            }
        }

        return new Locale("en", "US");
    }

    private static class ResourceFileFilter implements FilenameFilter {

        @Override
        public boolean accept(File parent, String name) {
            if (name.endsWith("png") || name.endsWith("gif")) {
                return true;
            }

            return false;
        }
    }
}
