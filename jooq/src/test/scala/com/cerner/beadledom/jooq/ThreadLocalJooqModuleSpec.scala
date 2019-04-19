package com.cerner.beadledom.jooq

import com.google.inject.{AbstractModule, CreationException, Guice}
import javax.sql.DataSource
import org.jooq.SQLDialect
import org.jooq.tools.jdbc.MockConnection
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSpec, MustMatchers}

/**
 * Spec for ThreadLocalJooqModule.
 *
 * @author John Leacox
 */
class ThreadLocalJooqModuleSpec
    extends FunSpec with MockitoSugar with BeforeAndAfter with MustMatchers {
  describe("ThreadLocalJooqModule") {
    it("throws an IllegalStateException if a DataSource binding is missing") {
      val module = new AbstractModule {
        override def configure(): Unit = {
          bind(classOf[SQLDialect]).toInstance(SQLDialect.MYSQL)

          install(new ThreadLocalJooqModule)
        }
      }

      intercept[CreationException] {
        Guice.createInjector(module)
      }
    }

    it("throws an IllegalStateException if a SQLDialect binding is missing") {
      val dataSource = mock[DataSource]

      val module = new AbstractModule {
        override def configure(): Unit = {
          bind(classOf[DataSource]).toInstance(dataSource)

          install(new ThreadLocalJooqModule)
        }
      }

      intercept[CreationException] {
        Guice.createInjector(module)
      }
    }

    it("binds a DSLContextProvider") {
      val dataSource = mock[DataSource]

      val module = new AbstractModule {
        override def configure(): Unit = {
          bind(classOf[DataSource]).toInstance(dataSource)
          bind(classOf[SQLDialect]).toInstance(SQLDialect.MYSQL)
          bind(classOf[JooqTransactionalHooks]).to(classOf[ThreadLocalJooqTransactionalHooks])

          install(new ThreadLocalJooqModule)
        }
      }

      val injector = Guice.createInjector(module)
      injector.getInstance(classOf[DSLContextProvider])
    }

    it("binds an interceptor for JooqTransactional") {
      val connection = new MockConnection(new EmptyMockDataProvider)
      val dataSource = mock[DataSource]
      when(dataSource.getConnection).thenReturn(connection)

      val module = new AbstractModule {
        override def configure(): Unit = {
          bind(classOf[DataSource]).toInstance(dataSource)
          bind(classOf[SQLDialect]).toInstance(SQLDialect.MYSQL)
          bind(classOf[JooqTransactionalHooks]).to(classOf[ThreadLocalJooqTransactionalHooks])

          install(new ThreadLocalJooqModule)

          bind(classOf[Interceptable])
        }
      }

      val injector = Guice.createInjector(module)
      val interceptable = injector.getInstance(classOf[Interceptable])

      interceptable.intercept()

      interceptable.lastElements
          .map(c => c.toString)
          .find(s => s.contains("cglib")) mustBe defined
    }
  }
}

private class Interceptable {
  var lastElements: Array[StackTraceElement] = _

  @JooqTransactional def intercept(): Unit = {
    lastElements = Thread.currentThread().getStackTrace
  }
}
