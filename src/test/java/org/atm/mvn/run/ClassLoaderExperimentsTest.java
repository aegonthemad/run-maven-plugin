package org.atm.mvn.run;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

import org.junit.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

public class ClassLoaderExperimentsTest {

    @Test
    public void rootResources() throws IOException {
        Thread currentThread = Thread.currentThread();
        ClassLoader classLoader = currentThread.getContextClassLoader();
        
        Enumeration<URL> resources = classLoader.getResources(".");
        
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            
            System.out.println(url);
        }
    }
    
    @Test
    public void springResolver() throws IOException {
        PathMatchingResourcePatternResolver resolver =
            new PathMatchingResourcePatternResolver();
        
        Resource[] resources = resolver.getResources(ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + "/**/*.class");
        for (Resource resource : resources) {
            System.out.println(resource);
        }
    }
}
