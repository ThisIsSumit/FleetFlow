package com.fleetflow.rls;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
@Component
public class TenantDataSourcePostProcessor implements BeanPostProcessor, EnvironmentAware {
  private boolean enabled = true;
  @Override public void setEnvironment(Environment env) { enabled = env.getProperty("fleetflow.rls.enabled", Boolean.class, true); }
  @Override public Object postProcessAfterInitialization(Object bean, String name) {
    return enabled && bean instanceof DataSource ds && !(bean instanceof TenantAwareDataSource) ? new TenantAwareDataSource(ds) : bean;
  }
}
