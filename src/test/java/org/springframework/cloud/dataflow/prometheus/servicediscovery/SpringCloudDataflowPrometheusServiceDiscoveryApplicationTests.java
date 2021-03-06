/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.dataflow.prometheus.servicediscovery;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;

import org.awaitility.Duration;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = {
		"metrics.prometheus.target.discoveryUrl=http://somehost:9393/runtime/apps",
		"metrics.prometheus.target.cron=* * * * * *",
		"metrics.prometheus.target.filePath=target/my-targets.json",
})
public class SpringCloudDataflowPrometheusServiceDiscoveryApplicationTests {


	private MockRestServiceServer mockServer;

	@Autowired
	private RestTemplate restTemplate;

	@SpyBean
	private DataflowPrometheusServiceDiscoveryApplication serviceDiscoveryApplication;

	@Before
	public void init() {
		mockServer = MockRestServiceServer.createServer(this.restTemplate);
	}

	@Test
	public void testScheduler() throws URISyntaxException, IOException {
		Assert.assertNotNull(serviceDiscoveryApplication);
		mockServer.expect(ExpectedCount.manyTimes(),
				requestTo(new URI("http://somehost:9393/runtime/apps")))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withStatus(HttpStatus.OK)
						.contentType(MediaType.APPLICATION_JSON)
						.body(asString("classpath:/runtime_apps_ticktock.json"))
				);

		await().atMost(Duration.TEN_SECONDS)
				.untilAsserted(() -> verify(serviceDiscoveryApplication,
						atLeast(10)).updateTargets());

		mockServer.verify();

		String targetsFileContent = asString("file:target/my-targets.json");
		assertThat(targetsFileContent,
				is("[{\"targets\":[\"172.18.0.4:20080\",\"172.18.0.4:20032\"],\"labels\":{\"job\":\"scdf\"}}]"));
	}

	private String asString(String resourceUri) throws IOException {
		return StreamUtils.copyToString(
				new DefaultResourceLoader().getResource(resourceUri).getInputStream(), Charset.forName("UTF-8"));
	}

}
