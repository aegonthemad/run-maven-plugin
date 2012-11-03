package org.atm.mvn.run;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Iterator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

public class ClassLocator {
    
    private ClassLoader classLoader;

    public void find() {
        
    }
    
    public List<URL> getMatchingClassPathResourceUrls(String className) 
            throws IOException {
        List<URL> rootClassPathResources = getRootClassPathResourceUrls();
        
        List<URL> matchingClassPathResourceUrls = Lists.newArrayList();
        
        for (URL rootClassPathResource : rootClassPathResources) {
            if (isArchive(rootClassPathResource)) {
                // search the archive files
                matchingClassPathResourceUrls.addAll( 
                    getMatchingClassPathResourceUrlsFromArchive(
                            rootClassPathResource, 
                            className));
            } else {
                // search the file system
                matchingClassPathResourceUrls.addAll(
                    getMatchingClassPathResourceUrlsFromFileSystem(
                            rootClassPathResource,
                            className));
            }
        }
        
        return matchingClassPathResourceUrls;
    }
    
    private List<URL> getMatchingClassPathResourceUrlsFromArchive(
            URL rootClassPathResource, 
            String className) 
            throws IOException {
        final String classFileName = className + ".class";
        
        List<URL> matchingClassPathResourceUrls = Lists.newArrayList();
        
        URLConnection archiveConnection = rootClassPathResource.openConnection();
        
        JarFile jarFile;
        String rootEntryPath;
        
        if (archiveConnection instanceof JarURLConnection) {
            JarURLConnection jarConnection = 
                (JarURLConnection) archiveConnection;
            
            // disable caching
            jarConnection.setUseCaches(false);
            
            jarFile = jarConnection.getJarFile();
            JarEntry jarEntry = jarConnection.getJarEntry();
            rootEntryPath = (jarEntry != null ? jarEntry.getName() : "");
        } else {
            // Unsupported archive type
            return ImmutableList.of();
        }
        
        try {
            List<JarEntry> jarEntries = 
                Lists.newArrayList(Iterators.forEnumeration(jarFile.entries()));
            
            for (JarEntry jarEntry : jarEntries) {
                String entryPath = jarEntry.getName();
                if (entryPath.startsWith(rootEntryPath)) {
                    String relativePath = entryPath.substring(rootEntryPath.length());
                    if (relativePath.endsWith(classFileName)) {
                        URL matchingClassPathResourceUrl =
                            new URL(rootClassPathResource.toExternalForm() + "!" + relativePath);
                        
                        matchingClassPathResourceUrls.add(matchingClassPathResourceUrl);
                    }
                }
            }
            return matchingClassPathResourceUrls;
        } finally {
            jarFile.close();
        }
    }

    private List<URL> getMatchingClassPathResourceUrlsFromFileSystem(
            URL rootClassPathResource, String className) {
        List<URL> matchingClassPathResourceUrls = Lists.newArrayList();
        
        
        return matchingClassPathResourceUrls;
    }

    public static boolean isArchive(URL rootClassPathResource) {
        String protocol = rootClassPathResource.getProtocol();
        return "jar".equals(protocol);
    }

    public List<URL> getRootClassPathResourceUrls() throws IOException {
        Iterator<URL> urlIterator = 
            Iterators.forEnumeration(classLoader.getResources("."));
        
        return Lists.newArrayList(urlIterator);
    }
}
