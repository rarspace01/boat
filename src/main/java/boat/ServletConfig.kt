package boat
import org.springframework.boot.web.servlet.ServletRegistrationBean
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.DispatcherServlet

@Configuration
class ServletConfig {

    @Bean
    fun dispatcherServlet(): DispatcherServlet {
        val dispatcherServlet = DispatcherServlet()
        dispatcherServlet.setDispatchOptionsRequest(true)
        return dispatcherServlet
    }

    @Bean
    fun dispatcherServletRegistration(dispatcherServlet: DispatcherServlet): ServletRegistrationBean<DispatcherServlet> {
        val registration = ServletRegistrationBean(dispatcherServlet, "/*")
        registration.setName(DispatcherServletAutoConfiguration.DEFAULT_DISPATCHER_SERVLET_BEAN_NAME)
        return registration
    }
}