package com.wisely.ch9_4;

import static java.lang.System.getProperty;

import java.io.File;
import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.channel.MessageChannels;
import org.springframework.integration.dsl.core.Pollers;
import org.springframework.integration.dsl.file.Files;
import org.springframework.integration.dsl.mail.Mail;
import org.springframework.integration.feed.inbound.FeedEntryMessageSource;
import org.springframework.integration.file.support.FileExistsMode;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.messaging.MessageChannel;

import com.rometools.rome.feed.synd.SyndEntry;

@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	/**
	 * 读取流程
	 */
	@Value("https://spring.io/blog.atom") // 1、通过@value注解自动获得https://spring.io/blog.atom的资源
	Resource resource;

	@Bean(name = PollerMetadata.DEFAULT_POLLER)
	public PollerMetadata poller() { // 2、使用FluentAPI和Pollers配置默认的轮询方式
		return Pollers.fixedRate(500).get();
	}

	@Bean
	public FeedEntryMessageSource feedMessageSource() throws IOException { // 3、FeedEntryMessageSource实际为feed：inbound-channel-adapter，此处即构造feed的入站通道适配器作为数据输入。
		FeedEntryMessageSource messageSource = new FeedEntryMessageSource(resource.getURL(), "news");
		return messageSource;
	}

	@Bean
	IntegrationFlow myFlow() throws IOException {
		return IntegrationFlows.from(feedMessageSource()) // 4、流程从from方法开始
				.<SyndEntry, String>route(payload -> payload.getCategories().get(0).getName(), // 5、通过路由方法route来选择路由，消息体（payload）的类型为SyndEntry，作为判断条件的类型为String，判断的值是通过payload获得的分类（Category）；
						mapping -> mapping.channelMapping("releases", "releasesChannel") // 6、通过不同分类的值转向不同的消息通道，若分类为releases，则转向releasesChannel；若分类为engineering，则转向engineeringChannel；若分类为news，则转向newsChannel。
								.channelMapping("engineering", "engineeringChannel")
								.channelMapping("news", "newsChannel"))
				.get(); // 7、通过get方法获得IntegrationFlow实体，配置为Spring的Bean
	}
	
	/**
	 * releases流程
	 * @return
	 */
	@Bean
	public IntegrationFlow releasesFlow() {
		return IntegrationFlows.from(MessageChannels.queue("releasesChannel", 10))	//1、从消息通道releasesChannel开始获取数据
				.<SyndEntry, String> transform(
						payload->"《" + payload.getTitle() + "》" + 
				payload.getLink() + getProperty("line.separator"))	//2、使用transform方法进行数据转换。payload类型为SyndEntry，将其转换为字符串类型，并自定义数据的格式。
				.handle(Files.outboundAdapter(new File("e:/springblog"))	//3、用handle方法处理file的出站适配器。Files类是有SpringIntegrationJavaDSL提供的FluentAPI用来构造文件输出的适配器。
						.fileExistsMode(FileExistsMode.APPEND)
						.charset("UTF-8")
						.fileNameGenerator(message->"releases.txt")
						.get())
				.get();
	}

	/**
	 * engineering流程，与releases流程相同
	 */
	@Bean
	public IntegrationFlow engineeringFlow() {
		return IntegrationFlows.from(MessageChannels.queue("engineeringChannel", 10))
				.<SyndEntry, String> transform(
						payload -> "《" + payload.getTitle() + "》 " + payload.getLink() + getProperty("line.separator"))
				.handle(Files.outboundAdapter(new File("e:/springblog"))
						.fileExistsMode(FileExistsMode.APPEND)
						.charset("UTF-8")
						.fileNameGenerator(message -> "engineering.txt")
						.get())
				.get();
	}
	
	/**
	 * news流程
	 */
	@Bean
	public IntegrationFlow newsFlow() {
		return IntegrationFlows.from(MessageChannels.queue("newsChannel", 10))
				.<SyndEntry, String> transform(
						payload->"《" + payload.getTitle() + "》" + payload.getLink() + getProperty("line.separator"))
				.enrichHeaders(	//1、通过enricherHeader来增加消息头的信息
						Mail.headers()
						.subject("来自Spring的新闻")
						.to("2421697126@qq.com")
						.from("2421697126@qq.com"))
				.handle(Mail.outboundAdapter("smtp.qq.com")	//2、邮件发送的相关信息通过SpringIntegration Java DSL提供的Mail的headers方法来构造。使用handle方法来定义邮件发送的出站适配器，使用SpringIntegration JavaDSL提供的Mail.outboundAdapter来构造，这里使用13545275466@163.com邮箱向自己发送邮件。
						.port(25)
						.protocol("smtp")
						.credentials("2421697126@qq.com", "password")
						.javaMailProperties(p->p.put("mail.debug", "false")),
						e->e.id("smtpOut"))
				.get();
	}
}
