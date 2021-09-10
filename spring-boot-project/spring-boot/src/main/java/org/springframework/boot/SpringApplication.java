/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.CachedIntrospectionResults;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.web.reactive.context.AnnotationConfigReactiveWebServerApplicationContext;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;

/**
 * Class that can be used to bootstrap and launch a Spring application from a Java main
 * method. By default class will perform the following steps to bootstrap your
 * application:
 *
 * <ul>
 * <li>Create an appropriate {@link ApplicationContext} instance (depending on your
 * classpath)</li>
 * <li>Register a {@link CommandLinePropertySource} to expose command line arguments as
 * Spring properties</li>
 * <li>Refresh the application context, loading all singleton beans</li>
 * <li>Trigger any {@link CommandLineRunner} beans</li>
 * </ul>
 *
 * In most circumstances the static {@link #run(Class, String[])} method can be called
 * directly from your {@literal main} method to bootstrap your application:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableAutoConfiguration
 * public class MyApplication  {
 *
 *   // ... Bean definitions
 *
 *   public static void main(String[] args) {
 *     SpringApplication.run(MyApplication.class, args);
 *   }
 * }
 * </pre>
 *
 * <p>
 * For more advanced configuration a {@link SpringApplication} instance can be created and
 * customized before being run:
 *
 * <pre class="code">
 * public static void main(String[] args) {
 *   SpringApplication application = new SpringApplication(MyApplication.class);
 *   // ... customize application settings here
 *   application.run(args)
 * }
 * </pre>
 *
 * {@link SpringApplication}s can read beans from a variety of different sources. It is
 * generally recommended that a single {@code @Configuration} class is used to bootstrap
 * your application, however, you may also set {@link #getSources() sources} from:
 * <ul>
 * <li>The fully qualified class name to be loaded by
 * {@link AnnotatedBeanDefinitionReader}</li>
 * <li>The location of an XML resource to be loaded by {@link XmlBeanDefinitionReader}, or
 * a groovy script to be loaded by {@link GroovyBeanDefinitionReader}</li>
 * <li>The name of a package to be scanned by {@link ClassPathBeanDefinitionScanner}</li>
 * </ul>
 *
 * Configuration properties are also bound to the {@link SpringApplication}. This makes it
 * possible to set {@link SpringApplication} properties dynamically, like additional
 * sources ("spring.main.sources" - a CSV list) the flag to indicate a web environment
 * ("spring.main.web-application-type=none") or the flag to switch off the banner
 * ("spring.main.banner-mode=off").
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @author Andy Wilkinson
 * @author Christian Dupuis
 * @author Stephane Nicoll
 * @author Jeremy Rickard
 * @author Craig Burke
 * @author Michael Simons
 * @author Madhura Bhave
 * @author Brian Clozel
 * @author Ethan Rubinson
 * @since 1.0.0
 * @see #run(Class, String[])
 * @see #run(Class[], String[])
 * @see #SpringApplication(Class...)
 */
public class SpringApplication {

	/**
	 * The class name of application context that will be used by default for non-web
	 * environments.
	 * @deprecated since 2.4.0 for removal in 2.6.0 in favor of using a
	 * {@link ApplicationContextFactory}
	 */
	@Deprecated
	public static final String DEFAULT_CONTEXT_CLASS = "org.springframework.context."
			+ "annotation.AnnotationConfigApplicationContext";

	/**
	 * The class name of application context that will be used by default for web
	 * environments.
	 * @deprecated since 2.4.0 for removal in 2.6.0 in favor of using an
	 * {@link ApplicationContextFactory}
	 */
	@Deprecated
	public static final String DEFAULT_SERVLET_WEB_CONTEXT_CLASS = "org.springframework.boot."
			+ "web.servlet.context.AnnotationConfigServletWebServerApplicationContext";

	/**
	 * The class name of application context that will be used by default for reactive web
	 * environments.
	 * @deprecated since 2.4.0 for removal in 2.6.0 in favor of using an
	 * {@link ApplicationContextFactory}
	 */
	@Deprecated
	public static final String DEFAULT_REACTIVE_WEB_CONTEXT_CLASS = "org.springframework."
			+ "boot.web.reactive.context.AnnotationConfigReactiveWebServerApplicationContext";

	/**
	 * Default banner location.
	 */
	public static final String BANNER_LOCATION_PROPERTY_VALUE = SpringApplicationBannerPrinter.DEFAULT_BANNER_LOCATION;

	/**
	 * Banner location property key.
	 */
	public static final String BANNER_LOCATION_PROPERTY = SpringApplicationBannerPrinter.BANNER_LOCATION_PROPERTY;

	private static final String SYSTEM_PROPERTY_JAVA_AWT_HEADLESS = "java.awt.headless";

	private static final Log logger = LogFactory.getLog(SpringApplication.class);

	static final SpringApplicationShutdownHook shutdownHook = new SpringApplicationShutdownHook();

	private Set<Class<?>> primarySources;

	private Set<String> sources = new LinkedHashSet<>();

	private Class<?> mainApplicationClass;

	private Banner.Mode bannerMode = Banner.Mode.CONSOLE;

	private boolean logStartupInfo = true;

	private boolean addCommandLineProperties = true;

	private boolean addConversionService = true;

	private Banner banner;

	private ResourceLoader resourceLoader;

	private BeanNameGenerator beanNameGenerator;

	private ConfigurableEnvironment environment;

	private WebApplicationType webApplicationType;

	private boolean headless = true;

	private boolean registerShutdownHook = true;

	private List<ApplicationContextInitializer<?>> initializers;

	private List<ApplicationListener<?>> listeners;

	private Map<String, Object> defaultProperties;

	private List<BootstrapRegistryInitializer> bootstrapRegistryInitializers;

	private Set<String> additionalProfiles = Collections.emptySet();

	private boolean allowBeanDefinitionOverriding;

	private boolean isCustomEnvironment = false;

	private boolean lazyInitialization = false;

	private String environmentPrefix;

	private ApplicationContextFactory applicationContextFactory = ApplicationContextFactory.DEFAULT;

	private ApplicationStartup applicationStartup = ApplicationStartup.DEFAULT;

	/**
	 * 通过给定的类，加载相关的信息 :启动类、web应用类型、启动载入器、初始化器列表、监听器列
	 *
	 * Create a new {@link SpringApplication} instance. The application context will load
	 * beans from the specified primary sources (see {@link SpringApplication class-level}
	 * documentation for details. The instance can be customized before calling
	 * {@link #run(String...)}.
	 * @param primarySources the primary bean sources
	 * @see #run(Class, String[])
	 * @see #SpringApplication(ResourceLoader, Class...)
	 * @see #setSources(Set)
	 */
	public SpringApplication(Class<?>... primarySources) {
		this(null, primarySources);
	}

	/**
	 *	构造器构造出SpringApplication :启动类、web应用类型、启动载入器、初始化器列表、监听器列
	 *
	 * Create a new {@link SpringApplication} instance. The application context will load
	 * beans from the specified primary sources (see {@link SpringApplication class-level}
	 * documentation for details. The instance can be customized before calling
	 * {@link #run(String...)}.
	 * @param resourceLoader the resource loader to use 需要使用的资源加载器，如果为空的话，默认加载classpath下的
	 * @param primarySources the primary bean sources  必须给定一个类信息
	 * @see #run(Class, String[])
	 * @see #setSources(Set)
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public SpringApplication(ResourceLoader resourceLoader, Class<?>... primarySources) {
		// 初始化resourceLoader，此时resourceLoader为null
		this.resourceLoader = resourceLoader;
		Assert.notNull(primarySources, "PrimarySources must not be null");
		// 将主启动类Class装入到该primarySources中
		this.primarySources = new LinkedHashSet<>(Arrays.asList(primarySources));
		// 获取到此次应用类型为Servlet，通过classpath下包含的类进行推断出来的。
		this.webApplicationType = WebApplicationType.deduceFromClasspath();
		// 初始化启动载入器，通过getSpringFactoriesInstances获取所有的启动载入器实例，此处获取到的载入器为空列表
		this.bootstrapRegistryInitializers = getBootstrapRegistryInitializersFromSpringFactories();
		// 设置上下文初始化器列表，用于后续在准备应用上下文时，在生成上下文后对上下文进行一些初始化（一共获得7个初始化器）
		setInitializers((Collection) getSpringFactoriesInstances(ApplicationContextInitializer.class));
		// 设置监听器列表，用于在启动时发布各种事件通知（springboot启动运用了许多的监听者模式）（一共8个监听器）
		setListeners((Collection) getSpringFactoriesInstances(ApplicationListener.class));
		// 获取到主启动类实例
		this.mainApplicationClass = deduceMainApplicationClass();
	}

	/**
	 * 从 Spring 工厂获取 Bootstrap Registry Initializers
	 * @return
	 */
	@SuppressWarnings("deprecation")
	private List<BootstrapRegistryInitializer> getBootstrapRegistryInitializersFromSpringFactories() {
		ArrayList<BootstrapRegistryInitializer> initializers = new ArrayList<>();
		// 通过spring的spring.factories定义文件，获取相对应的类实例。
		getSpringFactoriesInstances(Bootstrapper.class).stream()
				// 获取的实例，执行对应的initialize方法，为BootstrapRegistryInitializer类中的initialize
				.map((bootstrapper) -> ((BootstrapRegistryInitializer) bootstrapper::initialize))
				// 加入到准备好的initializers的list集合中
				.forEach(initializers::add);
		//
		initializers.addAll(getSpringFactoriesInstances(BootstrapRegistryInitializer.class));
		return initializers;
	}

	/**
	 * 通过运行时的栈信息，推断出主启动类
	 * 哪个包含main方法，哪个就是主启动。
	 *
	 * @return
	 */
	private Class<?> deduceMainApplicationClass() {
		try {
			StackTraceElement[] stackTrace = new RuntimeException().getStackTrace();
			for (StackTraceElement stackTraceElement : stackTrace) {
				if ("main".equals(stackTraceElement.getMethodName())) {
					return Class.forName(stackTraceElement.getClassName());
				}
			}
		}
		catch (ClassNotFoundException ex) {
			// Swallow and continue
		}
		return null;
	}

	/**
	 * 运行Spring应用程序，创建并刷新新的ApplicationContext
	 *
	 * 做的事情分为以下步骤：
	 * 1、初始化StopWatch,调用其start方法开始计时.
	 * 2、初始化启动上下文
	 * 3、调用configureHeadlessProperty设置系统属性java.awt.headless，这里设置为true，表示运行在服务器端，在没有显示器和鼠标键盘的模式下工作，模拟输入输出设备功能
	 * 4、调用SpringApplicationRunListeners#starting
	 * 5、创建一个DefaultApplicationArguments对象,它持有着args参数，就是main函数传进来的参数.调用prepareEnvironment方法.
	 * 6、配置忽略的bean
	 * 5、打印banner
	 * 6、创建SpringBoot上下文
	 * 7、为application context设置 ApplicationStartup
	 * 8、调用prepareContext
	 * 9、调用AbstractApplicationContext#refresh方法,并注册钩子
	 * 10、在容器完成刷新后，依次调用注册的Runners
	 * 11、停止计时
	 * 12、初始化过程中出现异常时调用handleRunFailure进行处理,然后抛出IllegalStateException异常.
	 *
	 * Run the Spring application, creating and refreshing a new
	 * {@link ApplicationContext}.
	 * @param args the application arguments (usually passed from a Java main method)
	 * @return a running {@link ApplicationContext}
	 */
	public ConfigurableApplicationContext run(String... args) {
		// 首先初始化一个计时器，并开始计时
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		// 初始化启动上下文
		DefaultBootstrapContext bootstrapContext = createBootstrapContext();
		ConfigurableApplicationContext context = null;
		// 设置系统参数：java.awt.headless（在系统可能缺少显示设备、键盘或鼠标这些外设的情况下可以使用该模式，例如Linux服务器）
		configureHeadlessProperty();
		// 初始化监听器列表
		SpringApplicationRunListeners listeners = getRunListeners(args);
		// 发布springboot开始启动事件
		listeners.starting(bootstrapContext, this.mainApplicationClass);
		try {
			// 创建一个DefaultApplicationArguments对象，它持有着args参数，就是main函数传进来的参数
			ApplicationArguments applicationArguments = new DefaultApplicationArguments(args);
			// 取得可配置的环境变量，即准备环境
			ConfigurableEnvironment environment = prepareEnvironment(listeners, bootstrapContext, applicationArguments);
			// 配置忽略BeanInfo
			configureIgnoreBeanInfo(environment);
			// 打印Banner，启动时打印的logo
			Banner printedBanner = printBanner(environment);
			// 创建SpringBoot上下文，即创建spring容器
			// （根据环境不同分为：
			// 		AnnotationConfigServletWebServerApplicationContext、
			// 		AnnotationConfigReactiveWebServerApplicationContext、
			// 		AnnotationConfigApplicationContext	）
			context = createApplicationContext();
			// 为application context设置 ApplicationStartup
			context.setApplicationStartup(this.applicationStartup);
			// 调用prepareContext，即spring容器前置处理
			prepareContext(bootstrapContext, context, environment, listeners, applicationArguments, printedBanner);
			// 刷新容器
			refreshContext(context);
			// spring容器后置处理
			afterRefresh(context, applicationArguments);
			// 结束计时器并打印，这就是我们启动后console的显示的时间
			stopWatch.stop();
			if (this.logStartupInfo) {
				new StartupInfoLogger(this.mainApplicationClass).logStarted(getApplicationLog(), stopWatch);
			}
			// 发出启动结束事件
			listeners.started(context);
			// 执行runner的run方法
			callRunners(context, applicationArguments);
		}
		catch (Throwable ex) {
			// 异常处理，如果run过程发生异常进行相应的处理
			handleRunFailure(context, ex, listeners);
			throw new IllegalStateException(ex);
		}

		try {
			listeners.running(context);
		}
		catch (Throwable ex) {
			// 异常处理，如果run过程发生异常
			handleRunFailure(context, ex, null);
			throw new IllegalStateException(ex);
		}
		// 返回最终构建的容器对象
		return context;
	}

	/**
	 * 创建 BootstrapContext
	 * 初始化启动上下文
	 * @return
	 */
	private DefaultBootstrapContext createBootstrapContext() {
		// 创建默认的DefaultBootstrapContext实例，即ConfigurableBootstrapContext
		DefaultBootstrapContext bootstrapContext = new DefaultBootstrapContext();
		// 循环执行注册器的初始化操作
		this.bootstrapRegistryInitializers.forEach((initializer) -> initializer.initialize(bootstrapContext));
		return bootstrapContext;
	}

	/**
	 * 主要是为了查找并设置配置文件信息到 environment 中
	 * @param listeners
	 * @param bootstrapContext
	 * @param applicationArguments
	 * @return
	 */
	private ConfigurableEnvironment prepareEnvironment(SpringApplicationRunListeners listeners,
													   DefaultBootstrapContext bootstrapContext, ApplicationArguments applicationArguments) {
		// 根据应用类型，创建应用环境：如得到系统的参数、JVM及Servlet等参数，等。
		// Create and configure the environment
		ConfigurableEnvironment environment = getOrCreateEnvironment();
		// 将 defaultProperties、commandLine及active-prifiles 属性加载到环境中
		// commandLine 在 args 中配置
		// 其它参数可在如下4个路径中配置：servletConfigInitParams、servletContextInitParams、systemProperties、systemEnvironment
		configureEnvironment(environment, applicationArguments.getSourceArgs());
		// 加一个ConfigurationPropertySources
		ConfigurationPropertySources.attach(environment);
		// 将 spirng.config 配置文件加载到环境中 主要步骤，查找active的profile
		listeners.environmentPrepared(bootstrapContext, environment);
		// 移动defaultProperties属性，使其成为最后一个
		DefaultPropertiesPropertySource.moveToEnd(environment);
		Assert.state(!environment.containsProperty("spring.main.environment-prefix"),
				"Environment prefix cannot be set via properties.");
		// 将environment绑定到SpringApplication，即与启动类进行绑定
		bindToSpringApplication(environment);
		// 判断是否为自定义的环境，如果用户使用spring.main.web-application-type属性手动设置了webApplicationType
		if (!this.isCustomEnvironment) {
			// 将环境对象转换成用户设置的webApplicationType相关类型，他们是继承同一个父类，直接强转
			environment = new EnvironmentConverter(getClassLoader()).convertEnvironmentIfNecessary(environment,
					deduceEnvironmentClass());
		}
		//将环境依附到PropertySources
		ConfigurationPropertySources.attach(environment);
		return environment;
	}

	private Class<? extends StandardEnvironment> deduceEnvironmentClass() {
		switch (this.webApplicationType) {
			case SERVLET:
				return ApplicationServletEnvironment.class;
			case REACTIVE:
				return ApplicationReactiveWebEnvironment.class;
			default:
				return ApplicationEnvironment.class;
		}
	}

	/**
	 * 准备上下文环境
	 *
	 * @param bootstrapContext
	 * @param context
	 * @param environment
	 * @param listeners
	 * @param applicationArguments
	 * @param printedBanner
	 */
	private void prepareContext(DefaultBootstrapContext bootstrapContext, ConfigurableApplicationContext context,
								ConfigurableEnvironment environment, SpringApplicationRunListeners listeners,
								ApplicationArguments applicationArguments, Banner printedBanner) {
		// 设置上下文的环境属性
		// 将ConfigurableEnvironment对象添加到context中
		context.setEnvironment(environment);
		// 后置处理 添加一些属性 最重要的就是添加conversionService
		postProcessApplicationContext(context);
		// 对各种ApplicationContextInitializer进行初始化和扩展，
		// 即执行所有ApplicationContextInitializer对象的initialize方法（这些对象是通过读取spring.factories加载）
		applyInitializers(context);
		// listeners中添加就绪的上下文对象，发布ApplicationContextInitializedEvent事件。即发布上下文准备完成事件到所有监听器
		listeners.contextPrepared(context);

		bootstrapContext.close(context);
		if (this.logStartupInfo) {
			logStartupInfo(context.getParent() == null);
			logStartupProfileInfo(context);
		}
		// 添加启动所需要的特定的单例bean， -> DefaultListableBeanFactory 对象
		// Add boot specific singleton beans
		ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
		// 将applicationArguments注册成单例Bean
		beanFactory.registerSingleton("springApplicationArguments", applicationArguments);
		// 将Banner注册为单例Bean
		if (printedBanner != null) {
			beanFactory.registerSingleton("springBootBanner", printedBanner);
		}
		// 设置允许bean定义覆盖
		if (beanFactory instanceof DefaultListableBeanFactory) {
			((DefaultListableBeanFactory) beanFactory)
					.setAllowBeanDefinitionOverriding(this.allowBeanDefinitionOverriding);
		}
		// 是否添加LazyInitializationBeanFactoryPostProcessor进行懒加载处理
		if (this.lazyInitialization) {
			context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor());
		}
		// 加载所有资源,返回的set为不可修改的，当前只有主运行类
		// Load the sources
		Set<Object> sources = getAllSources();
		Assert.notEmpty(sources, "Sources must not be empty");
		// 根据资源进行类定义的注册 此处就包括主类的注册（第二个参数，将set转换为Object类型的数组）
		load(context, sources.toArray(new Object[0]));
		// 发布ApplicationPreparedEvent事件
		listeners.contextLoaded(context);
	}

	/**
	 * 刷新上下文
	 *
	 * @param context
	 */
	private void refreshContext(ConfigurableApplicationContext context) {
		// 注册一个关闭钩子，在jvm停止时会触发，然后退出时执行一定的退出逻辑
		if (this.registerShutdownHook) {
			// 添加：Runtime.getRuntime().addShutdownHook()
			// 移除：Runtime.getRuntime().removeShutdownHook(this.shutdownHook)
			shutdownHook.registerApplicationContext(context);
		}
		// ApplicationContext真正开始初始化容器和创建bean的阶段
		refresh(context);
	}

	/**
	 * 设置系统参数：java.awt.headless（在系统可能缺少显示设备、键盘或鼠标这些外设的情况下可以使用该模式，例如Linux服务器）
	 */
	private void configureHeadlessProperty() {
		System.setProperty(SYSTEM_PROPERTY_JAVA_AWT_HEADLESS,
				System.getProperty(SYSTEM_PROPERTY_JAVA_AWT_HEADLESS, Boolean.toString(this.headless)));
	}

	/**
	 * 初始化监听器列表
	 * @param args
	 * @return
	 */
	private SpringApplicationRunListeners getRunListeners(String[] args) {
		// 获取了一个class数组types
		// 其中的元素分别为：SpringApplication和String数组的class对象，types主要用于在后序反射初始化对象时，获取到目标的构造方法。
		Class<?>[] types = new Class<?>[] { SpringApplication.class, String[].class };
		// 获取SpringApplicationRunListener实例对象。
		// 获取并维护SpringApplicationRunListener接口的子类对象列表。在后续的启动过程中，交由listeners发布的事件，
		// 实际上是遍历其内部管理的SpringApplicationRunListener对象列表进行发布的。
		return new SpringApplicationRunListeners(logger,
				getSpringFactoriesInstances(SpringApplicationRunListener.class, types, this, args),
				this.applicationStartup);
	}

	/**
	 * 在初始化启动载入器、初始化器、监听器时，均是调用本方法
	 * 通过传入不同的Class类型获取到不同的实例
	 * @param type
	 * @param <T>
	 * @return
	 */
	private <T> Collection<T> getSpringFactoriesInstances(Class<T> type) {
		return getSpringFactoriesInstances(type, new Class<?>[] {});
	}

	/**
	 * 获得工厂实例
	 * 方法的目的为：获取并维护SpringApplicationRunListener接口的子类对象列表。
	 * 在后续的启动过程中，交由listeners发布的事件，实际上是遍历其内部管理的SpringApplicationRunListener对象列表进行发布的。
	 * @param type
	 * @param parameterTypes
	 * @param args
	 * @param <T>
	 * @return
	 */
	private <T> Collection<T> getSpringFactoriesInstances(Class<T> type, Class<?>[] parameterTypes, Object... args) {
		// 由于this.resourceLoader为null，故此处返回的ClassUtils.getDefaultClassLoader()
		ClassLoader classLoader = getClassLoader();
		// Use names and ensure unique to protect against duplicates
		// 从此处获取到满足类型的所有类名称列表.使用名称并确保唯一，以防止重复。(只是类的名称)
		Set<String> names = new LinkedHashSet<>(SpringFactoriesLoader.loadFactoryNames(type, classLoader));
		// 用类名称列表，通过反射机制对它们进行实例化
		List<T> instances = createSpringFactoriesInstances(type, parameterTypes, classLoader, args, names);
		// 对实例进行排序
		AnnotationAwareOrderComparator.sort(instances);
		return instances;
	}

	/**
	 * 通过反射，创建工厂实例
	 *
	 * @param type
	 * @param parameterTypes
	 * @param classLoader
	 * @param args
	 * @param names
	 * @param <T>
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private <T> List<T> createSpringFactoriesInstances(Class<T> type, Class<?>[] parameterTypes,
													   ClassLoader classLoader, Object[] args, Set<String> names) {
		// 接收实例的list集合
		List<T> instances = new ArrayList<>(names.size());
		for (String name : names) {
			try {
				// 根据名称加载类信息
				Class<?> instanceClass = ClassUtils.forName(name, classLoader);
				Assert.isAssignable(type, instanceClass);
				// 根据类信息获取构造函数
				Constructor<?> constructor = instanceClass.getDeclaredConstructor(parameterTypes);
				// 构造对象
				T instance = (T) BeanUtils.instantiateClass(constructor, args);
				// 讲对象加入到list集合中
				instances.add(instance);
			}
			catch (Throwable ex) {
				throw new IllegalArgumentException("Cannot instantiate " + type + " : " + name, ex);
			}
		}
		return instances;
	}

	private ConfigurableEnvironment getOrCreateEnvironment() {
		if (this.environment != null) {
			return this.environment;
		}
		switch (this.webApplicationType) {
			case SERVLET:
				return new ApplicationServletEnvironment();
			case REACTIVE:
				return new ApplicationReactiveWebEnvironment();
			default:
				return new ApplicationEnvironment();
		}
	}

	/**
	 * Template method delegating to
	 * {@link #configurePropertySources(ConfigurableEnvironment, String[])} and
	 * {@link #configureProfiles(ConfigurableEnvironment, String[])} in that order.
	 * Override this method for complete control over Environment customization, or one of
	 * the above for fine-grained control over property sources or profiles, respectively.
	 * @param environment this application's environment
	 * @param args arguments passed to the {@code run} method
	 * @see #configureProfiles(ConfigurableEnvironment, String[])
	 * @see #configurePropertySources(ConfigurableEnvironment, String[])
	 */
	protected void configureEnvironment(ConfigurableEnvironment environment, String[] args) {
		if (this.addConversionService) {
			environment.setConversionService(new ApplicationConversionService());
		}
		// 配置PropertySources
		configurePropertySources(environment, args);
		// 配置Profiles
		configureProfiles(environment, args);
	}

	/**
	 * Add, remove or re-order any {@link PropertySource}s in this application's
	 * environment.
	 * @param environment this application's environment
	 * @param args arguments passed to the {@code run} method
	 * @see #configureEnvironment(ConfigurableEnvironment, String[])
	 */
	protected void configurePropertySources(ConfigurableEnvironment environment, String[] args) {
		MutablePropertySources sources = environment.getPropertySources();
		// 1. 如果defaultProperties不为空，则继续添加defaultProperties
		if (!CollectionUtils.isEmpty(this.defaultProperties)) {
			DefaultPropertiesPropertySource.addOrMerge(this.defaultProperties, sources);
		}
		// 2. 如果addCommandLineProperties为true并且有命令参数，
		//    分两步骤走：第一步存在commandLineArgs则继续设置属性；
		//    第二步commandLineArgs不存在则在头部添加commandLineArgs
		if (this.addCommandLineProperties && args.length > 0) {
			String name = CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME;
			if (sources.contains(name)) {
				PropertySource<?> source = sources.get(name);
				CompositePropertySource composite = new CompositePropertySource(name);
				composite.addPropertySource(
						new SimpleCommandLinePropertySource("springApplicationCommandLineArgs", args));
				composite.addPropertySource(source);
				sources.replace(name, composite);
			}
			else {
				sources.addFirst(new SimpleCommandLinePropertySource(args));
			}
		}
	}

	/**
	 * Configure which profiles are active (or active by default) for this application
	 * environment. Additional profiles may be activated during configuration file
	 * processing via the {@code spring.profiles.active} property.
	 * @param environment this application's environment
	 * @param args arguments passed to the {@code run} method
	 * @see #configureEnvironment(ConfigurableEnvironment, String[])
	 * @see org.springframework.boot.context.config.ConfigFileApplicationListener
	 */
	protected void configureProfiles(ConfigurableEnvironment environment, String[] args) {
	}


	private void configureIgnoreBeanInfo(ConfigurableEnvironment environment) {
		if (System.getProperty(CachedIntrospectionResults.IGNORE_BEANINFO_PROPERTY_NAME) == null) {
			Boolean ignore = environment.getProperty("spring.beaninfo.ignore", Boolean.class, Boolean.TRUE);
			System.setProperty(CachedIntrospectionResults.IGNORE_BEANINFO_PROPERTY_NAME, ignore.toString());
		}
	}

	/**
	 * Bind the environment to the {@link SpringApplication}.
	 * @param environment the environment to bind
	 */
	protected void bindToSpringApplication(ConfigurableEnvironment environment) {
		try {
			Binder.get(environment).bind("spring.main", Bindable.ofInstance(this));
		}
		catch (Exception ex) {
			throw new IllegalStateException("Cannot bind to SpringApplication", ex);
		}
	}

	/**
	 * 打印Banner
	 * @param environment
	 * @return
	 */
	private Banner printBanner(ConfigurableEnvironment environment) {
		// 如果Banner关闭了，则不打印
		if (this.bannerMode == Banner.Mode.OFF) {
			return null;
		}
		// 获取资源加载器ResourceLoader
		ResourceLoader resourceLoader = (this.resourceLoader != null) ? this.resourceLoader
				: new DefaultResourceLoader(null);
		// 实例化SpringApplicationBannerPrinter类
		// SpringApplicationBannerPrinter的属性DEFAULT_BANNER就是SpringBootBanner类，
		// SpringBootBanner的BANNER属性(字符数组)就是熟悉的控制台的banner图
		SpringApplicationBannerPrinter bannerPrinter = new SpringApplicationBannerPrinter(resourceLoader, this.banner);
		// 输出模式为日志，则打印到日志，否则输出到System.out
		if (this.bannerMode == Mode.LOG) {
			return bannerPrinter.print(environment, this.mainApplicationClass, logger);
		}
		return bannerPrinter.print(environment, this.mainApplicationClass, System.out);
	}

	/**
	 * Strategy method used to create the {@link ApplicationContext}. By default this
	 * method will respect any explicitly set application context class or factory before
	 * falling back to a suitable default.
	 * @return the application context (not yet refreshed)
	 * @see #setApplicationContextClass(Class)
	 * @see #setApplicationContextFactory(ApplicationContextFactory)
	 */
	protected ConfigurableApplicationContext createApplicationContext() {
		return this.applicationContextFactory.create(this.webApplicationType);
	}

	/**
	 * 后置处理 添加一些属性 最重要的就是添加conversionService
	 *
	 * Apply any relevant post processing the {@link ApplicationContext}. Subclasses can
	 * apply additional processing as required.
	 * @param context the application context
	 */
	protected void postProcessApplicationContext(ConfigurableApplicationContext context) {
		if (this.beanNameGenerator != null) {
			context.getBeanFactory().registerSingleton(AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR,
					this.beanNameGenerator);
		}
		if (this.resourceLoader != null) {
			if (context instanceof GenericApplicationContext) {
				((GenericApplicationContext) context).setResourceLoader(this.resourceLoader);
			}
			if (context instanceof DefaultResourceLoader) {
				((DefaultResourceLoader) context).setClassLoader(this.resourceLoader.getClassLoader());
			}
		}
		if (this.addConversionService) {
			context.getBeanFactory().setConversionService(context.getEnvironment().getConversionService());
		}
	}

	/**
	 * Apply any {@link ApplicationContextInitializer}s to the context before it is
	 * refreshed.
	 * @param context the configured ApplicationContext (not refreshed yet)
	 * @see ConfigurableApplicationContext#refresh()
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected void applyInitializers(ConfigurableApplicationContext context) {
		// 在构造SpringApplication 通过SPI读取的initializers
		for (ApplicationContextInitializer initializer : getInitializers()) {
			Class<?> requiredType = GenericTypeResolver.resolveTypeArgument(initializer.getClass(),
					ApplicationContextInitializer.class);
			Assert.isInstanceOf(requiredType, context, "Unable to call initializer.");
			initializer.initialize(context);
		}
	}

	/**
	 * Called to log startup information, subclasses may override to add additional
	 * logging.
	 * @param isRoot true if this application is the root of a context hierarchy
	 */
	protected void logStartupInfo(boolean isRoot) {
		if (isRoot) {
			new StartupInfoLogger(this.mainApplicationClass).logStarting(getApplicationLog());
		}
	}

	/**
	 * 记录配置文件信息
	 *
	 * Called to log active profile information.
	 * @param context the application context
	 */
	protected void logStartupProfileInfo(ConfigurableApplicationContext context) {
		Log log = getApplicationLog();
		if (log.isInfoEnabled()) {
			String[] activeProfiles = context.getEnvironment().getActiveProfiles();
			if (ObjectUtils.isEmpty(activeProfiles)) {
				String[] defaultProfiles = context.getEnvironment().getDefaultProfiles();
				log.info("No active profile set, falling back to default profiles: "
						+ StringUtils.arrayToCommaDelimitedString(defaultProfiles));
			}
			else {
				log.info("The following profiles are active: "
						+ StringUtils.arrayToCommaDelimitedString(activeProfiles));
			}
		}
	}

	/**
	 * Returns the {@link Log} for the application. By default will be deduced.
	 * @return the application log
	 */
	protected Log getApplicationLog() {
		if (this.mainApplicationClass == null) {
			return logger;
		}
		return LogFactory.getLog(this.mainApplicationClass);
	}

	/**
	 * 将bean信息加载到上下文中
	 *
	 * Load beans into the application context.
	 * @param context the context to load beans into
	 * @param sources the sources to load
	 */
	protected void load(ApplicationContext context, Object[] sources) {
		if (logger.isDebugEnabled()) {
			logger.debug("Loading source " + StringUtils.arrayToCommaDelimitedString(sources));
		}
		// 通过创建BeanDefinitionLoader来加载bean
		BeanDefinitionLoader loader = createBeanDefinitionLoader(getBeanDefinitionRegistry(context), sources);
		if (this.beanNameGenerator != null) {
			loader.setBeanNameGenerator(this.beanNameGenerator);
		}
		if (this.resourceLoader != null) {
			loader.setResourceLoader(this.resourceLoader);
		}
		if (this.environment != null) {
			loader.setEnvironment(this.environment);
		}
		// 指定定义的加载
		loader.load();
	}

	/**
	 * The ResourceLoader that will be used in the ApplicationContext.
	 * @return the resourceLoader the resource loader that will be used in the
	 * ApplicationContext (or null if the default)
	 */
	public ResourceLoader getResourceLoader() {
		return this.resourceLoader;
	}

	/**
	 * 获得类加载器
	 *
	 * Either the ClassLoader that will be used in the ApplicationContext (if
	 * {@link #setResourceLoader(ResourceLoader) resourceLoader} is set, or the context
	 * class loader (if not null), or the loader of the Spring {@link ClassUtils} class.
	 * @return a ClassLoader (never null)
	 */
	public ClassLoader getClassLoader() {
		// 如果当前资源加载器不为空，则调用当前资源加载器的类加载器
		if (this.resourceLoader != null) {
			return this.resourceLoader.getClassLoader();
		}
		// 获得默认的类加载器
		return ClassUtils.getDefaultClassLoader();
	}

	/**
	 * Get the bean definition registry.
	 * @param context the application context
	 * @return the BeanDefinitionRegistry if it can be determined
	 */
	private BeanDefinitionRegistry getBeanDefinitionRegistry(ApplicationContext context) {
		if (context instanceof BeanDefinitionRegistry) {
			return (BeanDefinitionRegistry) context;
		}
		if (context instanceof AbstractApplicationContext) {
			return (BeanDefinitionRegistry) ((AbstractApplicationContext) context).getBeanFactory();
		}
		throw new IllegalStateException("Could not locate BeanDefinitionRegistry");
	}

	/**
	 *
	 * Factory method used to create the {@link BeanDefinitionLoader}.
	 * @param registry the bean definition registry
	 * @param sources the sources to load
	 * @return the {@link BeanDefinitionLoader} that will be used to load beans
	 */
	protected BeanDefinitionLoader createBeanDefinitionLoader(BeanDefinitionRegistry registry, Object[] sources) {
		return new BeanDefinitionLoader(registry, sources);
	}

	/**
	 * 调用ConfigurableApplicationContext的刷新方法，ConfigurableApplicationContext是从ApplicationContext集成来的
	 *	ConfigurableApplicationContext接口实现有三个类，
	 *		1、AbstractApplicationContext
	 *		2、ReactiveWebServerApplicationContext
	 *		3、ServletWebServerApplicationContext
	 * Refresh the underlying {@link ApplicationContext}.
	 * @param applicationContext the application context to refresh
	 */
	protected void refresh(ConfigurableApplicationContext applicationContext) {
		// 调用应用上下文对象的refresh()方法
		applicationContext.refresh();
	}

	/**
	 * 空的方法
	 *
	 * Called after the context has been refreshed.
	 * @param context the application context
	 * @param args the application arguments
	 */
	protected void afterRefresh(ConfigurableApplicationContext context, ApplicationArguments args) {
	}

	private void callRunners(ApplicationContext context, ApplicationArguments args) {
		List<Object> runners = new ArrayList<>();
		runners.addAll(context.getBeansOfType(ApplicationRunner.class).values());
		runners.addAll(context.getBeansOfType(CommandLineRunner.class).values());
		AnnotationAwareOrderComparator.sort(runners);
		for (Object runner : new LinkedHashSet<>(runners)) {
			if (runner instanceof ApplicationRunner) {
				callRunner((ApplicationRunner) runner, args);
			}
			if (runner instanceof CommandLineRunner) {
				callRunner((CommandLineRunner) runner, args);
			}
		}
	}

	private void callRunner(ApplicationRunner runner, ApplicationArguments args) {
		try {
			(runner).run(args);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to execute ApplicationRunner", ex);
		}
	}

	private void callRunner(CommandLineRunner runner, ApplicationArguments args) {
		try {
			(runner).run(args.getSourceArgs());
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to execute CommandLineRunner", ex);
		}
	}

	/**
	 * 执行run方法过程中，出现异常进行相关的处理
	 *
	 * @param context
	 * @param exception
	 * @param listeners
	 */
	private void handleRunFailure(ConfigurableApplicationContext context, Throwable exception,
								  SpringApplicationRunListeners listeners) {
		try {
			try {
				// 获得退出码
				handleExitCode(context, exception);
				// listeners不为空，发送failed广播
				if (listeners != null) {
					listeners.failed(context, exception);
				}
			}
			finally {
				reportFailure(getExceptionReporters(context), exception);
				if (context != null) {
					context.close();
				}
			}
		}
		catch (Exception ex) {
			logger.warn("Unable to close ApplicationContext", ex);
		}
		ReflectionUtils.rethrowRuntimeException(exception);
	}

	private Collection<SpringBootExceptionReporter> getExceptionReporters(ConfigurableApplicationContext context) {
		try {
			return getSpringFactoriesInstances(SpringBootExceptionReporter.class,
					new Class<?>[] { ConfigurableApplicationContext.class }, context);
		}
		catch (Throwable ex) {
			return Collections.emptyList();
		}
	}

	/**
	 * 报告失败信息
	 *
	 * @param exceptionReporters
	 * @param failure
	 */
	private void reportFailure(Collection<SpringBootExceptionReporter> exceptionReporters, Throwable failure) {
		try {
			for (SpringBootExceptionReporter reporter : exceptionReporters) {
				if (reporter.reportException(failure)) {
					registerLoggedException(failure);
					return;
				}
			}
		}
		catch (Throwable ex) {
			// Continue with normal handling of the original failure
		}
		if (logger.isErrorEnabled()) {
			logger.error("Application run failed", failure);
			registerLoggedException(failure);
		}
	}

	/**
	 * 注册异常信息
	 *
	 * Register that the given exception has been logged. By default, if the running in
	 * the main thread, this method will suppress additional printing of the stacktrace.
	 * @param exception the exception that was logged
	 */
	protected void registerLoggedException(Throwable exception) {
		SpringBootExceptionHandler handler = getSpringBootExceptionHandler();
		if (handler != null) {
			// 注册异常信息
			handler.registerLoggedException(exception);
		}
	}

	/**
	 * 获得退出码
	 * @param context
	 * @param exception
	 */
	private void handleExitCode(ConfigurableApplicationContext context, Throwable exception) {
		// 根据异常获得退出码
		int exitCode = getExitCodeFromException(context, exception);
		// 如果退出码不等于0
		if (exitCode != 0) {
			// 上下文不为空
			if (context != null) {
				// 将退出的时间，推送给上下文
				context.publishEvent(new ExitCodeEvent(context, exitCode));
			}
			SpringBootExceptionHandler handler = getSpringBootExceptionHandler();
			if (handler != null) {
				handler.registerExitCode(exitCode);
			}
		}
	}

	/**
	 * 根据异常获得退出码
	 *
	 * @param context
	 * @param exception
	 * @return
	 */
	private int getExitCodeFromException(ConfigurableApplicationContext context, Throwable exception) {
		int exitCode = getExitCodeFromMappedException(context, exception);
		if (exitCode == 0) {
			exitCode = getExitCodeFromExitCodeGeneratorException(exception);
		}
		return exitCode;
	}

	/**
	 * 根据异常映射获取退出码
	 * @param context
	 * @param exception
	 * @return
	 */
	private int getExitCodeFromMappedException(ConfigurableApplicationContext context, Throwable exception) {
		// 如果context为空，或者context没有启动成功，直接返回0
		if (context == null || !context.isActive()) {
			return 0;
		}
		ExitCodeGenerators generators = new ExitCodeGenerators();
		Collection<ExitCodeExceptionMapper> beans = context.getBeansOfType(ExitCodeExceptionMapper.class).values();
		generators.addAll(exception, beans);
		return generators.getExitCode();
	}

	/**
	 * 递归调用，
	 * 获取到退出码
	 * @param exception
	 * @return
	 */
	private int getExitCodeFromExitCodeGeneratorException(Throwable exception) {
		if (exception == null) {
			return 0;
		}
		if (exception instanceof ExitCodeGenerator) {
			return ((ExitCodeGenerator) exception).getExitCode();
		}
		return getExitCodeFromExitCodeGeneratorException(exception.getCause());
	}

	SpringBootExceptionHandler getSpringBootExceptionHandler() {
		if (isMainThread(Thread.currentThread())) {
			return SpringBootExceptionHandler.forCurrentThread();
		}
		return null;
	}

	private boolean isMainThread(Thread currentThread) {
		return ("main".equals(currentThread.getName()) || "restartedMain".equals(currentThread.getName()))
				&& "main".equals(currentThread.getThreadGroup().getName());
	}

	/**
	 * Returns the main application class that has been deduced or explicitly configured.
	 * @return the main application class or {@code null}
	 */
	public Class<?> getMainApplicationClass() {
		return this.mainApplicationClass;
	}

	/**
	 * Set a specific main application class that will be used as a log source and to
	 * obtain version information. By default the main application class will be deduced.
	 * Can be set to {@code null} if there is no explicit application class.
	 * @param mainApplicationClass the mainApplicationClass to set or {@code null}
	 */
	public void setMainApplicationClass(Class<?> mainApplicationClass) {
		this.mainApplicationClass = mainApplicationClass;
	}

	/**
	 * Returns the type of web application that is being run.
	 * @return the type of web application
	 * @since 2.0.0
	 */
	public WebApplicationType getWebApplicationType() {
		return this.webApplicationType;
	}

	/**
	 * Sets the type of web application to be run. If not explicitly set the type of web
	 * application will be deduced based on the classpath.
	 * @param webApplicationType the web application type
	 * @since 2.0.0
	 */
	public void setWebApplicationType(WebApplicationType webApplicationType) {
		Assert.notNull(webApplicationType, "WebApplicationType must not be null");
		this.webApplicationType = webApplicationType;
	}

	/**
	 * Sets if bean definition overriding, by registering a definition with the same name
	 * as an existing definition, should be allowed. Defaults to {@code false}.
	 * @param allowBeanDefinitionOverriding if overriding is allowed
	 * @since 2.1.0
	 * @see DefaultListableBeanFactory#setAllowBeanDefinitionOverriding(boolean)
	 */
	public void setAllowBeanDefinitionOverriding(boolean allowBeanDefinitionOverriding) {
		this.allowBeanDefinitionOverriding = allowBeanDefinitionOverriding;
	}

	/**
	 * Sets if beans should be initialized lazily. Defaults to {@code false}.
	 * @param lazyInitialization if initialization should be lazy
	 * @since 2.2
	 * @see BeanDefinition#setLazyInit(boolean)
	 */
	public void setLazyInitialization(boolean lazyInitialization) {
		this.lazyInitialization = lazyInitialization;
	}

	/**
	 * Sets if the application is headless and should not instantiate AWT. Defaults to
	 * {@code true} to prevent java icons appearing.
	 * @param headless if the application is headless
	 */
	public void setHeadless(boolean headless) {
		this.headless = headless;
	}

	/**
	 * Sets if the created {@link ApplicationContext} should have a shutdown hook
	 * registered. Defaults to {@code true} to ensure that JVM shutdowns are handled
	 * gracefully.
	 * @param registerShutdownHook if the shutdown hook should be registered
	 * @see #getShutdownHandlers()
	 */
	public void setRegisterShutdownHook(boolean registerShutdownHook) {
		this.registerShutdownHook = registerShutdownHook;
	}

	/**
	 * Sets the {@link Banner} instance which will be used to print the banner when no
	 * static banner file is provided.
	 * @param banner the Banner instance to use
	 */
	public void setBanner(Banner banner) {
		this.banner = banner;
	}

	/**
	 * Sets the mode used to display the banner when the application runs. Defaults to
	 * {@code Banner.Mode.CONSOLE}.
	 * @param bannerMode the mode used to display the banner
	 */
	public void setBannerMode(Banner.Mode bannerMode) {
		this.bannerMode = bannerMode;
	}

	/**
	 * Sets if the application information should be logged when the application starts.
	 * Defaults to {@code true}.
	 * @param logStartupInfo if startup info should be logged.
	 */
	public void setLogStartupInfo(boolean logStartupInfo) {
		this.logStartupInfo = logStartupInfo;
	}

	/**
	 * Sets if a {@link CommandLinePropertySource} should be added to the application
	 * context in order to expose arguments. Defaults to {@code true}.
	 * @param addCommandLineProperties if command line arguments should be exposed
	 */
	public void setAddCommandLineProperties(boolean addCommandLineProperties) {
		this.addCommandLineProperties = addCommandLineProperties;
	}

	/**
	 * Sets if the {@link ApplicationConversionService} should be added to the application
	 * context's {@link Environment}.
	 * @param addConversionService if the application conversion service should be added
	 * @since 2.1.0
	 */
	public void setAddConversionService(boolean addConversionService) {
		this.addConversionService = addConversionService;
	}

	/**
	 * Adds a {@link Bootstrapper} that can be used to initialize the
	 * {@link BootstrapRegistry}.
	 * @param bootstrapper the bootstraper
	 * @since 2.4.0
	 * @deprecated since 2.4.5 for removal in 2.6 in favor of
	 * {@link #addBootstrapRegistryInitializer(BootstrapRegistryInitializer)}
	 */
	@Deprecated
	public void addBootstrapper(Bootstrapper bootstrapper) {
		Assert.notNull(bootstrapper, "Bootstrapper must not be null");
		this.bootstrapRegistryInitializers.add(bootstrapper::initialize);
	}

	/**
	 * Adds {@link BootstrapRegistryInitializer} instances that can be used to initialize
	 * the {@link BootstrapRegistry}.
	 * @param bootstrapRegistryInitializer the bootstrap registry initializer to add
	 * @since 2.4.5
	 */
	public void addBootstrapRegistryInitializer(BootstrapRegistryInitializer bootstrapRegistryInitializer) {
		Assert.notNull(bootstrapRegistryInitializer, "BootstrapRegistryInitializer must not be null");
		this.bootstrapRegistryInitializers.addAll(Arrays.asList(bootstrapRegistryInitializer));
	}

	/**
	 * Set default environment properties which will be used in addition to those in the
	 * existing {@link Environment}.
	 * @param defaultProperties the additional properties to set
	 */
	public void setDefaultProperties(Map<String, Object> defaultProperties) {
		this.defaultProperties = defaultProperties;
	}

	/**
	 * Convenient alternative to {@link #setDefaultProperties(Map)}.
	 * @param defaultProperties some {@link Properties}
	 */
	public void setDefaultProperties(Properties defaultProperties) {
		this.defaultProperties = new HashMap<>();
		for (Object key : Collections.list(defaultProperties.propertyNames())) {
			this.defaultProperties.put((String) key, defaultProperties.get(key));
		}
	}

	/**
	 * Set additional profile values to use (on top of those set in system or command line
	 * properties).
	 * @param profiles the additional profiles to set
	 */
	public void setAdditionalProfiles(String... profiles) {
		this.additionalProfiles = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(profiles)));
	}

	/**
	 * Return an immutable set of any additional profiles in use.
	 * @return the additional profiles
	 */
	public Set<String> getAdditionalProfiles() {
		return this.additionalProfiles;
	}

	/**
	 * Sets the bean name generator that should be used when generating bean names.
	 * @param beanNameGenerator the bean name generator
	 */
	public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
		this.beanNameGenerator = beanNameGenerator;
	}

	/**
	 * Sets the underlying environment that should be used with the created application
	 * context.
	 * @param environment the environment
	 */
	public void setEnvironment(ConfigurableEnvironment environment) {
		this.isCustomEnvironment = true;
		this.environment = environment;
	}

	/**
	 * Add additional items to the primary sources that will be added to an
	 * ApplicationContext when {@link #run(String...)} is called.
	 * <p>
	 * The sources here are added to those that were set in the constructor. Most users
	 * should consider using {@link #getSources()}/{@link #setSources(Set)} rather than
	 * calling this method.
	 * @param additionalPrimarySources the additional primary sources to add
	 * @see #SpringApplication(Class...)
	 * @see #getSources()
	 * @see #setSources(Set)
	 * @see #getAllSources()
	 */
	public void addPrimarySources(Collection<Class<?>> additionalPrimarySources) {
		this.primarySources.addAll(additionalPrimarySources);
	}

	/**
	 * Returns a mutable set of the sources that will be added to an ApplicationContext
	 * when {@link #run(String...)} is called.
	 * <p>
	 * Sources set here will be used in addition to any primary sources set in the
	 * constructor.
	 * @return the application sources.
	 * @see #SpringApplication(Class...)
	 * @see #getAllSources()
	 */
	public Set<String> getSources() {
		return this.sources;
	}

	/**
	 * Set additional sources that will be used to create an ApplicationContext. A source
	 * can be: a class name, package name, or an XML resource location.
	 * <p>
	 * Sources set here will be used in addition to any primary sources set in the
	 * constructor.
	 * @param sources the application sources to set
	 * @see #SpringApplication(Class...)
	 * @see #getAllSources()
	 */
	public void setSources(Set<String> sources) {
		Assert.notNull(sources, "Sources must not be null");
		this.sources = new LinkedHashSet<>(sources);
	}

	/**
	 *
	 * Return an immutable set of all the sources that will be added to an
	 * ApplicationContext when {@link #run(String...)} is called. This method combines any
	 * primary sources specified in the constructor with any additional ones that have
	 * been {@link #setSources(Set) explicitly set}.
	 * @return an immutable set of all sources
	 */
	public Set<Object> getAllSources() {
		Set<Object> allSources = new LinkedHashSet<>();
		if (!CollectionUtils.isEmpty(this.primarySources)) {
			allSources.addAll(this.primarySources);
		}
		if (!CollectionUtils.isEmpty(this.sources)) {
			allSources.addAll(this.sources);
		}
		return Collections.unmodifiableSet(allSources);
	}

	/**
	 * Sets the {@link ResourceLoader} that should be used when loading resources.
	 * @param resourceLoader the resource loader
	 */
	public void setResourceLoader(ResourceLoader resourceLoader) {
		Assert.notNull(resourceLoader, "ResourceLoader must not be null");
		this.resourceLoader = resourceLoader;
	}

	/**
	 * Return a prefix that should be applied when obtaining configuration properties from
	 * the system environment.
	 * @return the environment property prefix
	 * @since 2.5.0
	 */
	public String getEnvironmentPrefix() {
		return this.environmentPrefix;
	}

	/**
	 * Set the prefix that should be applied when obtaining configuration properties from
	 * the system environment.
	 * @param environmentPrefix the environment property prefix to set
	 * @since 2.5.0
	 */
	public void setEnvironmentPrefix(String environmentPrefix) {
		this.environmentPrefix = environmentPrefix;
	}

	/**
	 * Sets the type of Spring {@link ApplicationContext} that will be created. If not
	 * specified defaults to {@link #DEFAULT_SERVLET_WEB_CONTEXT_CLASS} for web based
	 * applications or {@link AnnotationConfigApplicationContext} for non web based
	 * applications.
	 * @param applicationContextClass the context class to set
	 * @deprecated since 2.4.0 for removal in 2.6.0 in favor of
	 * {@link #setApplicationContextFactory(ApplicationContextFactory)}
	 */
	@Deprecated
	public void setApplicationContextClass(Class<? extends ConfigurableApplicationContext> applicationContextClass) {
		this.webApplicationType = WebApplicationType.deduceFromApplicationContext(applicationContextClass);
		this.applicationContextFactory = ApplicationContextFactory.ofContextClass(applicationContextClass);
	}

	/**
	 * Sets the factory that will be called to create the application context. If not set,
	 * defaults to a factory that will create
	 * {@link AnnotationConfigServletWebServerApplicationContext} for servlet web
	 * applications, {@link AnnotationConfigReactiveWebServerApplicationContext} for
	 * reactive web applications, and {@link AnnotationConfigApplicationContext} for
	 * non-web applications.
	 * @param applicationContextFactory the factory for the context
	 * @since 2.4.0
	 */
	public void setApplicationContextFactory(ApplicationContextFactory applicationContextFactory) {
		this.applicationContextFactory = (applicationContextFactory != null) ? applicationContextFactory
				: ApplicationContextFactory.DEFAULT;
	}

	/**
	 * Sets the {@link ApplicationContextInitializer} that will be applied to the Spring
	 * {@link ApplicationContext}.
	 * @param initializers the initializers to set
	 */
	public void setInitializers(Collection<? extends ApplicationContextInitializer<?>> initializers) {
		this.initializers = new ArrayList<>(initializers);
	}

	/**
	 * Add {@link ApplicationContextInitializer}s to be applied to the Spring
	 * {@link ApplicationContext}.
	 * @param initializers the initializers to add
	 */
	public void addInitializers(ApplicationContextInitializer<?>... initializers) {
		this.initializers.addAll(Arrays.asList(initializers));
	}

	/**
	 * Returns read-only ordered Set of the {@link ApplicationContextInitializer}s that
	 * will be applied to the Spring {@link ApplicationContext}.
	 * @return the initializers
	 */
	public Set<ApplicationContextInitializer<?>> getInitializers() {
		return asUnmodifiableOrderedSet(this.initializers);
	}

	/**
	 * Sets the {@link ApplicationListener}s that will be applied to the SpringApplication
	 * and registered with the {@link ApplicationContext}.
	 * @param listeners the listeners to set
	 */
	public void setListeners(Collection<? extends ApplicationListener<?>> listeners) {
		this.listeners = new ArrayList<>(listeners);
	}

	/**
	 * Add {@link ApplicationListener}s to be applied to the SpringApplication and
	 * registered with the {@link ApplicationContext}.
	 * @param listeners the listeners to add
	 */
	public void addListeners(ApplicationListener<?>... listeners) {
		this.listeners.addAll(Arrays.asList(listeners));
	}

	/**
	 * Returns read-only ordered Set of the {@link ApplicationListener}s that will be
	 * applied to the SpringApplication and registered with the {@link ApplicationContext}
	 * .
	 * @return the listeners
	 */
	public Set<ApplicationListener<?>> getListeners() {
		return asUnmodifiableOrderedSet(this.listeners);
	}

	/**
	 * Set the {@link ApplicationStartup} to use for collecting startup metrics.
	 * @param applicationStartup the application startup to use
	 * @since 2.4.0
	 */
	public void setApplicationStartup(ApplicationStartup applicationStartup) {
		this.applicationStartup = (applicationStartup != null) ? applicationStartup : ApplicationStartup.DEFAULT;
	}

	/**
	 * Returns the {@link ApplicationStartup} used for collecting startup metrics.
	 * @return the application startup
	 * @since 2.4.0
	 */
	public ApplicationStartup getApplicationStartup() {
		return this.applicationStartup;
	}

	/**
	 * Return a {@link SpringApplicationShutdownHandlers} instance that can be used to add
	 * or remove handlers that perform actions before the JVM is shutdown.
	 * @return a {@link SpringApplicationShutdownHandlers} instance
	 * @since 2.5.1
	 */
	public static SpringApplicationShutdownHandlers getShutdownHandlers() {
		return shutdownHook.getHandlers();
	}

	/**
	 * 通过静态方法，创建配置类ConfigurableApplicationContext也就是ApplicationContext
	 *
	 * Static helper that can be used to run a {@link SpringApplication} from the
	 * specified source using default settings.
	 * @param primarySource the primary source to load
	 * @param args the application arguments (usually passed from a Java main method)
	 * @return the running {@link ApplicationContext}
	 */
	public static ConfigurableApplicationContext run(Class<?> primarySource, String... args) {
		return run(new Class<?>[] { primarySource }, args);
	}

	/**
	 * 重载方法
	 * 通过静态方法，创建配置类ConfigurableApplicationContext也就是ApplicationContext
	 *
	 * Static helper that can be used to run a {@link SpringApplication} from the
	 * specified sources using default settings and user supplied arguments.
	 * @param primarySources the primary sources to load
	 * @param args the application arguments (usually passed from a Java main method)
	 * @return the running {@link ApplicationContext}
	 */
	public static ConfigurableApplicationContext run(Class<?>[] primarySources, String[] args) {
		//先创建SpringApplication对象，再调用run方法
		return new SpringApplication(primarySources).run(args);
	}

	/**
	 * A basic main that can be used to launch an application. This method is useful when
	 * application sources are defined via a {@literal --spring.main.sources} command line
	 * argument.
	 * <p>
	 * Most developers will want to define their own main method and call the
	 * {@link #run(Class, String...) run} method instead.
	 * @param args command line arguments
	 * @throws Exception if the application cannot be started
	 * @see SpringApplication#run(Class[], String[])
	 * @see SpringApplication#run(Class, String...)
	 */
	public static void main(String[] args) throws Exception {
		SpringApplication.run(new Class<?>[0], args);
	}

	/**
	 * Static helper that can be used to exit a {@link SpringApplication} and obtain a
	 * code indicating success (0) or otherwise. Does not throw exceptions but should
	 * print stack traces of any encountered. Applies the specified
	 * {@link ExitCodeGenerator} in addition to any Spring beans that implement
	 * {@link ExitCodeGenerator}. In the case of multiple exit codes the highest value
	 * will be used (or if all values are negative, the lowest value will be used)
	 * @param context the context to close if possible
	 * @param exitCodeGenerators exist code generators
	 * @return the outcome (0 if successful)
	 */
	public static int exit(ApplicationContext context, ExitCodeGenerator... exitCodeGenerators) {
		Assert.notNull(context, "Context must not be null");
		int exitCode = 0;
		try {
			try {
				ExitCodeGenerators generators = new ExitCodeGenerators();
				Collection<ExitCodeGenerator> beans = context.getBeansOfType(ExitCodeGenerator.class).values();
				generators.addAll(exitCodeGenerators);
				generators.addAll(beans);
				exitCode = generators.getExitCode();
				if (exitCode != 0) {
					context.publishEvent(new ExitCodeEvent(context, exitCode));
				}
			}
			finally {
				close(context);
			}
		}
		catch (Exception ex) {
			ex.printStackTrace();
			exitCode = (exitCode != 0) ? exitCode : 1;
		}
		return exitCode;
	}

	private static void close(ApplicationContext context) {
		if (context instanceof ConfigurableApplicationContext) {
			ConfigurableApplicationContext closable = (ConfigurableApplicationContext) context;
			closable.close();
		}
	}

	private static <E> Set<E> asUnmodifiableOrderedSet(Collection<E> elements) {
		List<E> list = new ArrayList<>(elements);
		list.sort(AnnotationAwareOrderComparator.INSTANCE);
		return new LinkedHashSet<>(list);
	}

}
