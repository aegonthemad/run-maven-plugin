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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.collection.CollectRequest;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.DependencyFilter;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.resolution.DependencyRequest;
import org.sonatype.aether.resolution.DependencyResolutionException;
import org.sonatype.aether.resolution.DependencyResult;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.util.filter.ScopeDependencyFilter;

import com.google.common.collect.Lists;

/**
 * 
 */
@Mojo(
        name = "java",
        requiresProject = false)
public class JavaMojo extends AbstractMojo {
    
    /**
     * The entry point to Aether, i.e. the component doing all the work.
     */
    @Component
    private RepositorySystem repositorySystem;

    /**
     * The current repository/network configuration of Maven.
     */
    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repositorySystemSession;

//    /**
//     * The project's remote repositories to use for the resolution of plugins and their dependencies.
//     */
//    @Parameter(defaultValue = "${project.remotePluginRepositories}", readonly = true)
//    private List<RemoteRepository> remoteRepositories;
    
    @Parameter(defaultValue = "${project}", readonly = true, required = false)
    private MavenProject project;
    
    @Parameter(defaultValue = "${spec}", readonly = true, required = false)
    private String artifactSpec;
    
    @Parameter(defaultValue = "${groupId}", readonly = true, required = false)
    private String artifactGroupId;
    
    @Parameter(defaultValue = "${artifactId}", readonly = true, required = false)
    private String artifactArtifactId;
    
    @Parameter(defaultValue = "${packaging}", readonly = true, required = false)
    private String artifactPackaging;
    
    @Parameter(defaultValue = "${version}", readonly = true, required = false)
    private String artifactVersion;
    
    @Parameter(defaultValue = "${classifier}", readonly = true, required = false)
    private String artifactClassifier;
    
    @Parameter(defaultValue = "${transitive}", readonly = true, required = false)
    private boolean resolveTransitiveDependencies = true;

    @Parameter(defaultValue = "${name}", readonly = true, required = false)
    private String className;

    private String[] args;
    
    @Override
    public void execute() 
            throws MojoExecutionException, MojoFailureException {
        List<URL> classPath = resolveClassPath();
        
        JavaBootstrap bootstrap = new JavaBootstrap(classPath, className, args);
        bootstrap.setLogger(getLog());
        
        try {
            bootstrap.run();
        } catch (Exception e) {
            throw new MojoExecutionException(
                    "Failed to run " + className, 
                    e);
        }
    }
    
    private List<URL> resolveClassPath() throws MojoExecutionException {
        if (project == null) {
            // no project, just resolve the specified object
            return resolveClassPathWithoutProject();
        } else if (StringUtils.isNotBlank(artifactSpec)
                || StringUtils.isNotBlank(artifactArtifactId)) {
            // specific request for artifact, overriding project presence
            return resolveClassPathWithoutProject();
        } else {
            // has a project
            return resolveClassPathWithProject();
        }
    }

    private List<URL> resolveClassPathWithProject() {
        throw new UnsupportedOperationException("Not implemented");
    }

    private List<URL> resolveClassPathWithoutProject() 
            throws MojoExecutionException {
        Artifact specifiedArtifact;
        
        if (StringUtils.isNotBlank(artifactSpec)) {
            specifiedArtifact = new DefaultArtifact(artifactSpec);
        } else if (StringUtils.isNotBlank(artifactArtifactId)
                && StringUtils.isNotBlank(artifactGroupId)) {
            specifiedArtifact = 
                new DefaultArtifact(
                        artifactGroupId, 
                        artifactArtifactId, 
                        artifactClassifier, 
                        artifactPackaging, 
                        artifactVersion); 
        } else {
            throw new MojoExecutionException(
                    "Unable to determine where to find the main class.");
        }
        
        return resolveArtifact(specifiedArtifact, resolveTransitiveDependencies);
    }

    private List<URL> resolveArtifact(Artifact artifact, boolean transitive) {
        // we need to resolve the artifacts needed for finding and running the class
        DependencyRequest dependencyRequest = new DependencyRequest();
        
        // create a collect request for resolving the dependencies of the artifact
        CollectRequest collectRequest = new CollectRequest();
        dependencyRequest.setCollectRequest(collectRequest);
        
        // here's the artifact desired
        Dependency root = new Dependency(artifact, null);
        collectRequest.setRoot(root);
        
//        collectRequest.setRepositories(remoteRepositories);
        
        // specify a scope on the dependency resolution
        DependencyFilter dependencyFilter = new ScopeDependencyFilter("runtime");
        dependencyRequest.setFilter(dependencyFilter);
        
        try {
            DependencyResult dependencyResult = 
                repositorySystem.resolveDependencies(
                        repositorySystemSession, 
                        dependencyRequest);
            
            List<ArtifactResult> artifactResults = dependencyResult.getArtifactResults();
            
            List<URL> artifactUrls = Lists.newArrayList();
            for (ArtifactResult artifactResult : artifactResults) {
                Artifact localArtifact = artifactResult.getArtifact();
                File localArtifactFile = localArtifact.getFile();
                
                try {
                    artifactUrls.add(localArtifactFile.toURI().toURL());
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }
            
            return artifactUrls;
        } catch (DependencyResolutionException e) {
            throw new RuntimeException(e);
        }
    }
}
