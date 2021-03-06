/**
 * Copyright (C) 2009 Scalable Solutions.
 */

package se.scalablesolutions.akka.api;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

import junit.framework.TestCase;

import se.scalablesolutions.akka.kernel.reactor.EventBasedThreadPoolDispatcher;
import static se.scalablesolutions.akka.kernel.config.JavaConfig.*;

import java.util.concurrent.ThreadPoolExecutor;

public class ActiveObjectGuiceConfiguratorTest extends TestCase {
  static String messageLog = "";

    final private se.scalablesolutions.akka.kernel.config.ActiveObjectManager conf = new se.scalablesolutions.akka.kernel.config.ActiveObjectManager();

    protected void setUp() {
      se.scalablesolutions.akka.kernel.Kernel$.MODULE$.config();
      EventBasedThreadPoolDispatcher dispatcher = new EventBasedThreadPoolDispatcher();
        dispatcher
       .withNewThreadPoolWithBoundedBlockingQueue(100)
       .setCorePoolSize(16)
       .setMaxPoolSize(128)
       .setKeepAliveTimeInMillis(60000)
       .setRejectionPolicy(new ThreadPoolExecutor.CallerRunsPolicy())
       .buildThreadPool();

    conf.addExternalGuiceModule(new AbstractModule() {
      protected void configure() {
        bind(Ext.class).to(ExtImpl.class).in(Scopes.SINGLETON);
      }
    }).configure(
        new RestartStrategy(new AllForOne(), 3, 5000), new Component[]{
             new Component(
                Foo.class,
                new LifeCycle(new Permanent(), 1000),
                1000,
                dispatcher),
                //new RemoteAddress("localhost", 9999)),
            new Component(
                Bar.class,
                BarImpl.class,
                new LifeCycle(new Permanent(), 1000),
                1000,
                dispatcher)
        }).inject().supervise();

  }

  public void testGuiceActiveObjectInjection() {
    messageLog = "";
    Foo foo = conf.getInstance(Foo.class);
    Bar bar = conf.getInstance(Bar.class);
    assertEquals(foo.getBar(), bar);
  }

  public void testGuiceExternalDependencyInjection() {
    messageLog = "";
    Bar bar = conf.getInstance(Bar.class);
    Ext ext = conf.getExternalDependency(Ext.class);
    assertTrue(bar.getExt().toString().equals(ext.toString()));
  }

  public void testLookupNonSupervisedInstance() {
    try {
      String str = conf.getInstance(String.class);
      fail("exception should have been thrown");
    } catch (Exception e) {
      assertEquals("Class [java.lang.String] has not been put under supervision (by passing in the config to the 'configure' and then invoking 'supervise') method", e.getMessage());
    }
  }

  public void testActiveObjectInvocation() throws InterruptedException {
    messageLog = "";
    Foo foo = conf.getInstance(Foo.class);
    messageLog += foo.foo("foo ");
    foo.bar("bar ");
    messageLog += "before_bar ";
    Thread.sleep(500);
    assertEquals("foo return_foo before_bar ", messageLog);
  }

  public void testActiveObjectInvocationsInvocation() throws InterruptedException {
    messageLog = "";
    Foo foo = conf.getInstance(Foo.class);
    Bar bar = conf.getInstance(Bar.class);
    messageLog += foo.foo("foo ");
    foo.bar("bar ");
    messageLog += "before_bar ";
    Thread.sleep(500);
    assertEquals("foo return_foo before_bar ", messageLog);
  }


  public void testForcedTimeout() {
    messageLog = "";
    Foo foo = conf.getInstance(Foo.class);
    try {
      foo.longRunning();
      fail("exception should have been thrown");
    } catch (se.scalablesolutions.akka.kernel.reactor.FutureTimeoutException e) {
    }
  }

  public void testForcedException() {
    messageLog = "";
    Foo foo = conf.getInstance(Foo.class);
    try {
      foo.throwsException();
      fail("exception should have been thrown");
    } catch (RuntimeException e) {
    }
  }
}


