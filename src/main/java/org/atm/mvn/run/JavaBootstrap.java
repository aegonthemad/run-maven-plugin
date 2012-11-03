/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.atm.mvn.run;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.text.MessageFormat;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.logging.Log;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import com.google.common.collect.Lists;

/**
 * Runs the main method of a given class in a custom class path.
 */
public class JavaBootstrap {
    
    private List<URL> classPathUrls = Lists.newArrayList();
    
    private String className;
    
    private String[] args;

    private Log log;
    
    public JavaBootstrap(
            List<URL> classPathUrls, 
            String className,
            String[] args) {
        super();
        this.classPathUrls = classPathUrls;
        this.className = className;
        this.args = args;
    }

    public void setLogger(Log log) {
        this.log = log;
    }

    /**
     * Run the main method in the class using the specified class path.
     * 
     * @throws Exception
     *             Any exceptions that might occur.
     */
    public void run() throws Exception {
        Thread currentThread = Thread.currentThread();
        
        ClassLoader existingClassLoader = 
                currentThread.getContextClassLoader();
        try {
            /*
             * Replace the current class loader with one with all of the needed
             * dependencies.
             */
            ClassLoader bootstrapClassLoader = 
                createClassLoader(
                        ClassLoader.getSystemClassLoader(),
                        false);
            
            currentThread.setContextClassLoader(bootstrapClassLoader);
            
            // find the java class
            Class<?> mainClass = 
                resolveClass(bootstrapClassLoader);
            
            // find the main method
            Method mainMethod = resolveMainMethod(mainClass);
            
            // invoke the main method
            invokeMain(mainMethod);
        } finally {
            currentThread.setContextClassLoader(existingClassLoader);
        }
    }

    /**
     * Create a class loader with the provided class path URLs and the given
     * parent class loader.
     * 
     * @param parentClassLoader
     *            The parent class loader.
     * @param childDelegation
     *            true if ? TODO
     * @return The {@link ClassLoader} instance.
     */
    protected ClassLoader createClassLoader(
            ClassLoader parentClassLoader, 
            boolean childDelegation) {
        IsolatedClassLoader classLoader = 
            new IsolatedClassLoader(
                    parentClassLoader, childDelegation);
        
        log.debug("Building Java Classpath:");
        for (URL classPathUrl : classPathUrls) {
            log.debug("  " + classPathUrl);
            classLoader.addURL(classPathUrl);
        }
        
        return classLoader;
    }

    /**
     * Attempt to resolve the class from the class name specified.
     * 
     * @param bootstrapClassLoader
     *            The class loader to use to load the class.
     * @return The {@link Class} instance
     * @throws RuntimeException
     *             if the class cannot be resolved.
     */
    protected Class<?> resolveClass(ClassLoader bootstrapClassLoader) {
        // try resolving just the fully qualified class name
        try {
            Class<?> loadedClass = bootstrapClassLoader.loadClass(className);
            
            log.debug("Resolved fully qualified class name: " + className);
            
            return loadedClass;
        } catch (ClassNotFoundException e) {
        }
        
        PathMatchingResourcePatternResolver resourceResolver = 
            new PathMatchingResourcePatternResolver(bootstrapClassLoader);
        
        // look for class files for the simple class name
        String locationPattern =
            ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
                "**/" + className + ".class";
        
        Resource[] resources;
        try {
            // run the search with spring
            // TODO might be possible to do this without a spring dependency
            resources = resourceResolver.getResources(locationPattern);
            
            log.debug("Found resources matching class name pattern: " + locationPattern);
            for (Resource resource : resources) {
                log.debug("  " + resource);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        
        if (resources.length > 0) {
            // assume that the first resource found is the one we want.
            Resource resource = resources[0];
            
            try {
                String url = resource.getURL().toString();
                
                log.debug("Attempting to resolve class name from URL: " + url);
                
                int jarMarkerIndex = url.indexOf('!');
                if (jarMarkerIndex != -1) {
                    /* 
                     * This is not a jar based class file, so the proper fully
                     * qualified class name is not clear since the URL may 
                     * contain path segments that are not part of the package 
                     */
                    String classUrl = url.toString();
                    int pathStartIndex = classUrl.indexOf(":/") + 2;
                    String classFilePath = classUrl.substring(pathStartIndex);
                    
                    /* 
                     * begin with the path minus .class and converted to fully 
                     * qualified class name format.
                     */
                    String currentClassName = 
                        classFilePath
                            // remove the file extension
                            .substring(
                                0, 
                                classFilePath.indexOf(".class"))
                            // convert the path separators to package separators
                            .replace('/', '.');
                    
                    // search for the class
                    while (StringUtils.isNotEmpty(currentClassName)) {
                        // try loading the current class name
                        try {
                            Class<?> loadedClass = 
                                bootstrapClassLoader.loadClass(currentClassName);
                            
                            log.debug(MessageFormat.format(
                                    "Resolved class {0} " +
                                    "from URL {1} " +
                                    "for specified class name {1}.",
                                    currentClassName,
                                    url,
                                    className));
                            
                            return loadedClass;
                        } catch (ClassNotFoundException e) {
                            // class not found, so substring the path if possible
                            int nextPackageSeparatorIndex =
                                    currentClassName.indexOf('.');
                            if (nextPackageSeparatorIndex == -1) {
                                // nothing more to try
                                throw new RuntimeException(
                                        "Unable to load class from class file " + 
                                        classUrl,
                                        e);
                            } else {
                                // the next child path
                                currentClassName = 
                                    currentClassName.substring(
                                            nextPackageSeparatorIndex + 1);
                            }
                        }
                    }
                } else {
                    /* 
                     * this is a resource contained in a JAR file, the pattern is:
                     * jar://path/to/jarFile.jar!/fully/qualified/ClassName.class
                     */
                    String className = 
                        url.substring(
                                jarMarkerIndex + 2, 
                                url.indexOf(".class"))
                            .replace('/', '.');
                    
                    try {
                        Class<?> loadedClass = 
                            bootstrapClassLoader.loadClass(className);

                        log.debug(MessageFormat.format(
                                "Resolved class {0} " +
                                "from URL {1} " +
                                "for specified class name {1}.",
                                className,
                                url,
                                this.className));
                        
                        return loadedClass;
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(
                                "Unable to load class from resource: " + url, 
                                e);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(
                        "Unable to convert resource URL to String: " + resource, 
                        e);
            }
        }
        
        throw new RuntimeException(
                "Unable to resolve class for class name specified: " + className);
    }

    /**
     * Try to resolve the main method in the class.
     * 
     * @param mainClass
     *            The class with the main method.
     * @return The {@link Method} instance
     * @throws RuntimeException
     *             if the method cannot be resolved.
     */
    protected Method resolveMainMethod(Class<?> mainClass) {
        try {
            return mainClass.getMethod("main", new Class[] { String[].class });
        } catch (SecurityException e) {
            throw new RuntimeException(
                    "Unable to resolve main method of " + mainClass, 
                    e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(
                    "Unable to resolve main method of " + mainClass, 
                    e);
        }
    }

    /**
     * Invoke the main method.
     * 
     * @param mainMethod
     *            The main method
     * @throws Exception
     *             Exceptions due to invocation.
     */
    protected void invokeMain(Method mainMethod) throws Exception {
        try {
            mainMethod.invoke(null, new Object[] { args });
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            } else if (e.getCause() instanceof Error) {
                throw (Error) e.getCause();
            } else {
                throw e;
            }
        }
    }
}
