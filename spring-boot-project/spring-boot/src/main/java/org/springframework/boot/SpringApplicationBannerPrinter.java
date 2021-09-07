/*
 * Copyright 2012-2020 the original author or authors.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;

import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

/**
 * Class used by {@link SpringApplication} to print the application banner.
 *
 * @author Phillip Webb
 */
class SpringApplicationBannerPrinter {

	static final String BANNER_LOCATION_PROPERTY = "spring.banner.location";

	static final String BANNER_IMAGE_LOCATION_PROPERTY = "spring.banner.image.location";

	static final String DEFAULT_BANNER_LOCATION = "banner.txt";

	static final String[] IMAGE_EXTENSION = { "gif", "jpg", "png" };

	private static final Banner DEFAULT_BANNER = new SpringBootBanner();

	private final ResourceLoader resourceLoader;

	private final Banner fallbackBanner;

	SpringApplicationBannerPrinter(ResourceLoader resourceLoader, Banner fallbackBanner) {
		this.resourceLoader = resourceLoader;
		this.fallbackBanner = fallbackBanner;
	}

	Banner print(Environment environment, Class<?> sourceClass, Log logger) {
		Banner banner = getBanner(environment);
		try {
			logger.info(createStringFromBanner(banner, environment, sourceClass));
		}
		catch (UnsupportedEncodingException ex) {
			logger.warn("Failed to create String for banner", ex);
		}
		return new PrintedBanner(banner, sourceClass);
	}

	Banner print(Environment environment, Class<?> sourceClass, PrintStream out) {
		// 1. 获取Banner
		Banner banner = getBanner(environment);
		// 2. 调用Banner中的printBanner方法
		banner.printBanner(environment, sourceClass, out);
		// 3. 实例化PrintedBanner类
		return new PrintedBanner(banner, sourceClass);
	}

	private Banner getBanner(Environment environment) {
		// 实例化Banners
		Banners banners = new Banners();
		// 设置ImageBanner
		banners.addIfNotNull(getImageBanner(environment));
		// 设置TextBanner
		banners.addIfNotNull(getTextBanner(environment));
		// 如果Banners对象的banners不为空，也就是至少找到了banner.gif，banner.jpg，banner.png，banner.txt其中的一个，
		// 那么返回该Banners对象，否则返回默认的SpringBootBanner对象
		if (banners.hasAtLeastOneBanner()) {
			return banners;
		}
		// fallbackBanner不为null的话,返回fallbackBanner.
		if (this.fallbackBanner != null) {
			return this.fallbackBanner;
		}
		// 返回默认的banner.默认的bannenr为 SpringBootBanner.
		return DEFAULT_BANNER;
	}

	private Banner getTextBanner(Environment environment) {
		String location = environment.getProperty(BANNER_LOCATION_PROPERTY, DEFAULT_BANNER_LOCATION);
		Resource resource = this.resourceLoader.getResource(location);
		try {
			if (resource.exists() && !resource.getURL().toExternalForm().contains("liquibase-core")) {
				return new ResourceBanner(resource);
			}
		}
		catch (IOException ex) {
			// Ignore
		}
		return null;
	}

	private Banner getImageBanner(Environment environment) {
		// 首先判断是否配置了系统属性banner.image.location
		String location = environment.getProperty(BANNER_IMAGE_LOCATION_PROPERTY);
		// 如果有相关属性，直接返回ImageBanner.
		if (StringUtils.hasLength(location)) {
			Resource resource = this.resourceLoader.getResource(location);
			return resource.exists() ? new ImageBanner(resource) : null;
		}
		// IMAGE_EXTENSION 这个常量值：{ "gif", "jpg", "png" }，如果有相关图片的话，返回
		for (String ext : IMAGE_EXTENSION) {
			Resource resource = this.resourceLoader.getResource("banner." + ext);
			if (resource.exists()) {
				return new ImageBanner(resource);
			}
		}
		return null;
	}

	private String createStringFromBanner(Banner banner, Environment environment, Class<?> mainApplicationClass)
			throws UnsupportedEncodingException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		banner.printBanner(environment, mainApplicationClass, new PrintStream(baos));
		String charset = environment.getProperty("spring.banner.charset", "UTF-8");
		return baos.toString(charset);
	}

	/**
	 * {@link Banner} comprised of other {@link Banner Banners}.
	 */
	private static class Banners implements Banner {

		private final List<Banner> banners = new ArrayList<>();

		/**
		 * 先判断是否为空，不为空才进行设定
		 * @param banner
		 */
		void addIfNotNull(Banner banner) {
			if (banner != null) {
				this.banners.add(banner);
			}
		}

		boolean hasAtLeastOneBanner() {
			return !this.banners.isEmpty();
		}

		@Override
		public void printBanner(Environment environment, Class<?> sourceClass, PrintStream out) {
			for (Banner banner : this.banners) {
				banner.printBanner(environment, sourceClass, out);
			}
		}

	}

	/**
	 * Decorator that allows a {@link Banner} to be printed again without needing to
	 * specify the source class.
	 */
	private static class PrintedBanner implements Banner {

		private final Banner banner;

		private final Class<?> sourceClass;

		PrintedBanner(Banner banner, Class<?> sourceClass) {
			this.banner = banner;
			this.sourceClass = sourceClass;
		}

		@Override
		public void printBanner(Environment environment, Class<?> sourceClass, PrintStream out) {
			sourceClass = (sourceClass != null) ? sourceClass : this.sourceClass;
			this.banner.printBanner(environment, sourceClass, out);
		}

	}

}
