/*
 * Copyright 2016 Red Hat, Inc.
 * <p>
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 */
package com.redhat.training.gpte.springboot;

import org.apache.activemq.jms.pool.PooledConnectionFactory;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.amqp.AMQPComponent;
import org.apache.camel.component.servlet.CamelHttpTransportServlet;
import org.apache.camel.dataformat.bindy.csv.BindyCsvDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestOperationResponseMsgDefinition;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportResource;

@SpringBootApplication
// load regular Spring XML file from the classpath that contains the Camel XML DSL
@ImportResource({"classpath:spring/camel-context.xml"})
public class Application extends RouteBuilder {

    /**
     * A main method to start this application.
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
    
    @Override
    public void configure () throws Exception {
    	
	  onException(IllegalArgumentException.class)
	      .to("log:fail")
	      .to("amqp:queue:errorQueue")
	      .handled(true)
	      .stop();
	
	  BindyCsvDataFormat format = new BindyCsvDataFormat(org.acme.Customer.class);
	  format.setLocale("default");
	  
	  restConfiguration().component("servlet").port("8080")
	  	.host("localhost");

	  rest("/service")
	  	.post("/customers")
		  	.to("direct:split")
	  	;
	  
	  from("direct:split")
	  		.setExchangePattern(ExchangePattern.InOnly)
	      .split()
	      .tokenize(";")
	      .to("log:tokenized")
	      .unmarshal(format)
	      .to("log:unmarshalled")
	      .to("dozer:customerToAccount?mappingFile=transformation.xml&sourceModel=org.acme.Customer&targetModel=org.globex.Account")
	      .to("log:transformed")
	      .marshal().json(JsonLibrary.Jackson)
	      .to("amqp:queue:accountQueue")
	      .end()
	      .transform(simple("Customer data processed"))
	      ;
	  
    }

    @Bean
    ServletRegistrationBean servletRegistrationBean() {
        ServletRegistrationBean servlet = new ServletRegistrationBean(
            new CamelHttpTransportServlet(), "/rest/*");
        servlet.setName("CamelServlet");
        return servlet;
    }

    @Bean(name = "amqp-component")
    AMQPComponent amqpComponent(AMQPConfiguration config) {
        JmsConnectionFactory qpid = new JmsConnectionFactory(config.getUsername(), config.getPassword(), "amqp://"+ config.getHost() + ":" + config.getPort());
        qpid.setTopicPrefix("topic://");

        PooledConnectionFactory factory = new PooledConnectionFactory();
        factory.setConnectionFactory(qpid);

        return new AMQPComponent(factory);
    }
    

}