/**
 * Copyright Alex Objelean
 */
package ro.isdc.wro.maven.plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.classworlds.ClassRealm;
import org.codehaus.classworlds.ClassWorld;
import org.mockito.Mockito;

import ro.isdc.wro.http.DelegatingServletOutputStream;
import ro.isdc.wro.manager.WroManagerFactory;
import ro.isdc.wro.manager.factory.standalone.DefaultStandaloneContextAwareManagerFactory;
import ro.isdc.wro.manager.factory.standalone.StandaloneContext;
import ro.isdc.wro.manager.factory.standalone.StandaloneContextAwareManagerFactory;
import ro.isdc.wro.model.WroModel;
import ro.isdc.wro.model.group.processor.GroupsProcessor;
import ro.isdc.wro.model.resource.ResourceType;


/**
 * @goal run
 * @phase process-resources
 * @requiresDependencyResolution runtime
 *
 * @author Alex Objelean
 */
public class Wro4jMojo extends AbstractMojo {
  /**
   * File containing the groups definitions.
   *
   * @parameter default-value="${basedir}/src/main/webapp/WEB-INF/wro.xml"
   */
  private File wroFile;
  /**
   * The folder where web application context resides useful for locating resources relative to servletContext .
   *
   * @parameter default-value="${basedir}/src/main/webapp/"
   */
  private File contextFolder;
  /**
   * The path to the destination directory where the files are stored at the end of the process.
   *
   * @parameter default-value="${project.build.directory}/wro/" expression="${destinationFolder}"
   * @optional
   */
  private File destinationFolder;
  /**
   * @parameter expression="${cssDestinationFolder}"
   * @optional
   */
  private File cssDestinationFolder;
  /**
   * @parameter expression="${jsDestinationFolder}"
   * @optional
   */
  private File jsDestinationFolder;
  /**
   * Comma separated group names. This field is optional. If no value is provided, a file for each group will be
   * created.
   *
   * @parameter expression="${targetGroups}"
   * @optional
   */
  private String targetGroups;
  /**
   * @parameter default-value="true" expression="${minimize}"
   * @optional
   */
  private boolean minimize;
  /**
   * @parameter default-value="true" expression="${ignoreMissingResources}"
   * @optional
   */
  private boolean ignoreMissingResources;

  /**
   * @parameter expression="${wroManagerFactory}"
   * @optional
   */
  private String wroManagerFactory;

  /**
   * @parameter default-value="${project}"
   */
  private MavenProject mavenProject;


  /**
   * @param request {@link HttpServletRequest} to process
   * @return {@link WroManagerFactory} implementation.
   */
  private StandaloneContextAwareManagerFactory getManagerFactory(final HttpServletRequest request)
    throws MojoExecutionException {
    final StandaloneContextAwareManagerFactory managerFactory;
    if (wroManagerFactory != null) {
      managerFactory = createCustomManagerFactory();
    } else {
      managerFactory = createDefaultManagerFactory();
    }
    // initialize before return.
    managerFactory.initialize(createRunContext(), request);
    return managerFactory;
  }


  /**
   * Creates an instance of Manager factory based on the value of the wroManagerFactory plugin parameter value.
   */
  private StandaloneContextAwareManagerFactory createCustomManagerFactory()
    throws MojoExecutionException {
    StandaloneContextAwareManagerFactory managerFactory;
    try {
      final Class<?> wroManagerFactoryClass = Thread.currentThread().getContextClassLoader().loadClass(
        wroManagerFactory.trim());
      managerFactory = (StandaloneContextAwareManagerFactory)wroManagerFactoryClass.newInstance();
    } catch (final Exception e) {
      throw new MojoExecutionException("Invalid wroManagerFactory class named: " + wroManagerFactory);
    }
    return managerFactory;
  }


  /**
   * Creates default instance of {@link StandaloneContextAwareManagerFactory}.
   */
  private StandaloneContextAwareManagerFactory createDefaultManagerFactory() {
    return new DefaultStandaloneContextAwareManagerFactory() {
      @Override
      protected GroupsProcessor newGroupsProcessor() {
        final GroupsProcessor groupsProcessor = super.newGroupsProcessor();
        groupsProcessor.setIgnoreMissingResources(ignoreMissingResources);
        return groupsProcessor;
      }
    };
  }

  /**
   * @return {@link WroModel} object.
   */
  private WroModel getModel() throws MojoExecutionException {
    return getManagerFactory(Mockito.mock(HttpServletRequest.class)).getInstance().getModelFactory().getInstance();
  }

  /**
   * Creates a {@link StandaloneContext} by setting properties passed after mojo is initialized.
   */
  private StandaloneContext createRunContext() {
    final StandaloneContext runContext = new StandaloneContext();
    runContext.setContextFolder(contextFolder);
    runContext.setMinimize(minimize);
    runContext.setWroFile(wroFile);
    runContext.setIgnoreMissingResources(ignoreMissingResources);
    return runContext;
  }


  /**
   * {@inheritDoc}
   */
  public void execute()
    throws MojoExecutionException {
    validate();
    extendPluginClasspath();
    getLog().info("Executing the mojo: ");
    getLog().info("Wro4j Model path: " + wroFile.getPath());
    getLog().info("targetGroups: " + targetGroups);
    getLog().info("minimize: " + minimize);
    getLog().info("destinationFolder: " + destinationFolder);
    getLog().info("jsDestinationFolder: " + jsDestinationFolder);
    getLog().info("cssDestinationFolder: " + cssDestinationFolder);
    getLog().info("ignoreMissingResources: " + ignoreMissingResources);

    try {
      for (final String group : getTargetGroupsAsList()) {
        for (final ResourceType resourceType : ResourceType.values()) {
          final File destinationFolder = computeDestinationFolder(resourceType);
          final String groupWithExtension = group + "." + resourceType.name().toLowerCase();
          processGroup(groupWithExtension, destinationFolder);
        }
      }
    } catch (final Exception e) {
      throw new MojoExecutionException("Exception occured while processing: " + e.getMessage(), e);
    }
  }


  /**
   * Encodes a version using some logic.
   *
   * @param group the name of the resource to encode.
   * @return the name of the resource with the version encoded.
   */
  private String encodeVersion(final String group) {
    return group;
  }


  /**
   * Computes the destination folder based on resource type.
   *
   * @param resourceType {@link ResourceType} to process.
   * @return destinationFoder where the result of resourceType will be copied.
   * @throws MojoExecutionException if computed folder is null.
   */
  private File computeDestinationFolder(final ResourceType resourceType)
    throws MojoExecutionException {
    File folder = destinationFolder;
    if (resourceType == ResourceType.JS) {
      if (jsDestinationFolder != null) {
        folder = jsDestinationFolder;
      }
    }
    if (resourceType == ResourceType.CSS) {
      if (cssDestinationFolder != null) {
        folder = cssDestinationFolder;
      }
    }
    getLog().info("folder: " + folder);
    if (folder == null) {
      throw new MojoExecutionException(
        "Couldn't compute destination folder for resourceType: "
          + resourceType
          + ". That means that you didn't define one of the following parameters: destinationFolder, cssDestinationFolder, jsDestinationFolder");
    }
    if (!folder.exists()) {
      folder.mkdirs();
    }
    return folder;
  }


  /**
   * Checks if all required fields are configured.
   */
  private void validate()
    throws MojoExecutionException {
    if (wroFile == null) {
      throw new MojoExecutionException("wroFile was not set!");
    }
    if (destinationFolder == null) {
      throw new MojoExecutionException("destinationFolder was not set!");
    }
    if (contextFolder == null) {
      throw new MojoExecutionException("contextFolder was not set!");
    }
  }


  /**
   * Update the classpath.
   */
  @SuppressWarnings("unchecked")
  private void extendPluginClasspath() throws MojoExecutionException {
    //this code is inspired from http://teleal.org/weblog/Extending%20the%20Maven%20plugin%20classpath.html
    List<String> classpathElements;
    try {
      classpathElements = mavenProject.getRuntimeClasspathElements();
    } catch (final DependencyResolutionRequiredException e) {
      throw new MojoExecutionException("Could not get compile classpath elements", e);
    }
    final ClassRealm realm = createRealm(classpathElements);
    Thread.currentThread().setContextClassLoader(realm.getClassLoader());
  }

  /**
   * @return {@link ClassRealm} based on project dependencies.
   */
  private ClassRealm createRealm(final List<String> classpathElements) {
    final ClassWorld world = new ClassWorld();
    getLog().debug("Classpath elements:");
    ClassRealm realm;
    try {
      realm = world.newRealm("maven.plugin." + getClass().getSimpleName(),
          Thread.currentThread().getContextClassLoader());
      for (final String element : classpathElements) {
        final File elementFile = new File(element);
        getLog().debug("Adding element to plugin classpath: " + elementFile.getPath());
        final URL url = new URL("file:///" + elementFile.getPath() + (elementFile.isDirectory() ? "/" : ""));
        realm.addConstituent(url);
      }
    } catch (final Exception e) {
      getLog().error("Error retreiving URL for artifact", e);
      throw new RuntimeException(e);
    }
    return realm;
  }

  /**
   * @return a list containing all groups needs to be processed.
   */
  private List<String> getTargetGroupsAsList() throws MojoExecutionException {
    if (targetGroups == null) {
      final WroModel model = getModel();
      return model.getGroupNames();
    }
    return Arrays.asList(targetGroups.split(","));
  }

  /**
   * Process a single group.
   *
   * @throws IOException
   *           if any IO related exception occurs.
   */
  private void processGroup(final String group, final File parentFoder)
      throws IOException, MojoExecutionException {
    FileOutputStream fos = null;
    try {
      getLog().info("processing group: " + group);

      final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
      final HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
      Mockito.when(request.getRequestURI()).thenReturn(group);

      final File destinationFile = new File(parentFoder, encodeVersion(group));
      destinationFile.createNewFile();
      fos = new FileOutputStream(destinationFile);
      Mockito.when(response.getOutputStream()).thenReturn(new DelegatingServletOutputStream(fos));

      getManagerFactory(request).getInstance().process(request, response);

      // delete empty files
      if (destinationFile.length() == 0) {
        getLog().info("No content found for group: " + group);
        destinationFile.delete();
      } else {
        getLog().info(
            destinationFile.getAbsolutePath() + " (" + destinationFile.length() + "bytes" + ") has been created!");
      }
    } finally {
      fos.close();
    }
  }



  /**
   * @param wroFile the wroFile to set
   */
  public void setWroFile(final File wroFile) {
    this.wroFile = wroFile;
  }


  /**
   * @param destinationFolder the destinationFolder to set
   */
  public void setDestinationFolder(final File destinationFolder) {
    this.destinationFolder = destinationFolder;
  }


  /**
   * @param cssDestinationFolder the cssDestinationFolder to set
   */
  public void setCssDestinationFolder(final File cssDestinationFolder) {
    this.cssDestinationFolder = cssDestinationFolder;
  }


  /**
   * @param jsDestinationFolder the jsDestinationFolder to set
   */
  public void setJsDestinationFolder(final File jsDestinationFolder) {
    this.jsDestinationFolder = jsDestinationFolder;
  }


  /**
   * @param contextFolder the servletContextFolder to set
   */
  public void setContextFolder(final File contextFolder) {
    this.contextFolder = contextFolder;
  }


  /**
   * @param versionEncoder(targetGroups) comma separated group names.
   */
  public void setTargetGroups(final String targetGroups) {
    this.targetGroups = encodeVersion(targetGroups);
  }


  /**
   * @param minimize flag for minimization.
   */
  public void setMinimize(final boolean minimize) {
    this.minimize = minimize;
  }


  /**
   * @param ignoreMissingResources the ignoreMissingResources to set
   */
  public void setIgnoreMissingResources(final boolean ignoreMissingResources) {
    this.ignoreMissingResources = ignoreMissingResources;
  }


  /**
   * @param versionEncoder(wroManagerFactory) the wroManagerFactory to set
   */
  public void setWroManagerFactory(final String wroManagerFactory) {
    this.wroManagerFactory = encodeVersion(wroManagerFactory);
  }


  /**
   * Used for testing.
   * @param mavenProject the mavenProject to set
   */
  void setMavenProject(final MavenProject mavenProject) {
    this.mavenProject = mavenProject;
  }
}
