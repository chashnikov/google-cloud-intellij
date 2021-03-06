/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.intellij.appengine.sdk;

import com.google.cloud.tools.appengine.api.AppEngineException;
import com.google.cloud.tools.appengine.api.whitelist.AppEngineJreWhitelist;
import com.google.cloud.tools.appengine.cloudsdk.CloudSdk;

import com.intellij.execution.configurations.ParametersList;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;

import gnu.trove.THashMap;
import gnu.trove.THashSet;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default implementation of {@link CloudSdkService} backed by {@link PropertiesComponent} for
 * serialization.
 *
 */
// TODO (eshaul) Offload path logic for retrieving AE libs to the common library once implemented
public class DefaultCloudSdkService extends CloudSdkService {

  private static final Logger logger = Logger.getInstance(DefaultCloudSdkService.class);

  private PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
  private static final String CLOUD_SDK_PROPERTY_KEY = "GCT_CLOUD_SDK_HOME_PATH";
  private static final Path JAVA_TOOLS_RELATIVE_PATH
      = Paths.get("platform", "google_appengine", "google", "appengine", "tools", "java");

  // Kept around for AppEngineGwtServer
  public static final Path LIB_APPENGINE_TOOLS_API_JAR
      = Paths.get("lib", "appengine-tools-api.jar");

  private Map<String, Set<String>> myMethodsBlackList;

  @Nullable
  @Override
  public Path getSdkHomePath() {
    if (propertiesComponent.getValue(CLOUD_SDK_PROPERTY_KEY) != null) {
      return Paths.get(propertiesComponent.getValue(CLOUD_SDK_PROPERTY_KEY));
    }

    // Let common library auto-discover Cloud SDK's location.
    try {
      return new CloudSdk.Builder().build().getSdkPath();
    } catch (AppEngineException aee) {
      return null;
    }
  }

  @Override
  public void setSdkHomePath(String cloudSdkHomePath) {
    propertiesComponent.setValue(CLOUD_SDK_PROPERTY_KEY, cloudSdkHomePath);
  }

  @Nullable
  private Path getJavaToolsBasePath() {
    return getSdkHomePath() != null
        ? getSdkHomePath().resolve(JAVA_TOOLS_RELATIVE_PATH.toString())
        : null;
  }

  @Nullable
  @Override
  // TODO(eshaul) used only by AppEngineGwtServer - can be removed if GWT support is removed
  public File getToolsApiJarFile() {
    return getJavaToolsBasePath() != null
        ? getJavaToolsBasePath().resolve(LIB_APPENGINE_TOOLS_API_JAR).toFile()
        : null;
  }

  @NotNull
  @Override
  public File[] getLibraries() {
    return getJavaToolsBasePath() != null
        ? getJarsFromDirectory(getJavaToolsBasePath().resolve(Paths.get("lib", "shared")).toFile())
        : new File[0];
  }

  @NotNull
  @Override
  public File[] getJspLibraries() {
    return getJavaToolsBasePath() != null
        ? getJarsFromDirectory(
            getJavaToolsBasePath().resolve(Paths.get("lib", "shared", "jsp")).toFile())
        : new File[0];
  }

  @Override
  public boolean isMethodInBlacklist(@NotNull String className, @NotNull String methodName) {
    if (myMethodsBlackList == null) {
      try {
        myMethodsBlackList = loadBlackList();
      } catch (IOException ioe) {
        logger.error(ioe);
        myMethodsBlackList = new THashMap<>();
      }
    }
    final Set<String> methods = myMethodsBlackList.get(className);
    return methods != null && methods.contains(methodName);
  }

  @Override
  public boolean isClassInWhiteList(@NotNull String className) {
    return AppEngineJreWhitelist.contains(className);
  }

  @NotNull
  @Override
  public List<String> getUserLibraryPaths() {
    List<String> result = new ArrayList<>();
    Path javaToolsBasePath = getJavaToolsBasePath();

    if (javaToolsBasePath == null) {
      return result;
    }

    String libUserDirectory = getLibUserDirectoryPath();
    if (libUserDirectory != null && new File(libUserDirectory).exists()) {
      result.add(getLibUserDirectoryPath());
    }

    Path optPath = javaToolsBasePath.resolve(Paths.get("lib", "opt", "user"));
    File optFile = optPath.toFile();

    ContainerUtil.addIfNotNull(
        result, findLatestVersion(new File(optFile, "appengine-endpoints")));
    ContainerUtil.addIfNotNull(result, findLatestVersion(new File(optFile, "jsr107")));
    return result;
  }

  @Nullable
  @Override
  public File getWebSchemeFile() {
    return getJavaToolsBasePath() != null
        ? getJavaToolsBasePath().resolve(Paths.get("docs", "appengine-web.xsd")).toFile()
        : null;
  }

  @Override
  public void patchJavaParametersForDevServer(@NotNull ParametersList vmParameters) {
    if (getJavaToolsBasePath() != null) {
      File agentPath = getJavaToolsBasePath().resolve(
          Paths.get("lib", "agent", "appengine-agent.jar")).toFile();
      if (agentPath.exists()) {
        vmParameters.add("-javaagent:" + agentPath.getAbsolutePath());
      }
      File patchPath = getJavaToolsBasePath().resolve(
          Paths.get("lib", "override", "appengine-dev-jdk-overrides.jar")).toFile();
      if (patchPath.exists()) {
        vmParameters.add("-Xbootclasspath/p:" + patchPath.getAbsolutePath());
      }
    }
  }

  private static String findLatestVersion(File dir) {
    String[] names = dir.list();
    if (names != null && names.length > 0) {
      String max = Collections.max(Arrays.asList(names));
      return FileUtil.toSystemIndependentName(new File(dir, max).getAbsolutePath());
    }
    return null;
  }

  private Map<String, Set<String>> loadBlackList() throws IOException {
    final InputStream stream = getClass().getResourceAsStream("/data/methodsBlacklist.txt");
    logger.assertTrue(stream != null, "/data/methodsBlacklist.txt not found");
    final THashMap<String, Set<String>> map = new THashMap<>();
    BufferedReader reader
        = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    try {
      String line;
      while ((line = reader.readLine()) != null) {
        final int i = line.indexOf(':');
        String className = line.substring(0, i);
        String methods = line.substring(i + 1);
        map.put(className, new THashSet<>(StringUtil.split(methods, ",")));
      }
    } finally {
      reader.close();
    }
    return map;
  }

  @Nullable
  private String getLibUserDirectoryPath() {
    return getJavaToolsBasePath() != null
        ? getJavaToolsBasePath().resolve(Paths.get("lib", "user")).toString()
        : null;
  }

  private File[] getJarsFromDirectory(File libFolder) {
    List<File> jars = new ArrayList<>();
    final File[] files = libFolder.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isFile() && file.getName().endsWith(".jar")) {
          jars.add(file);
        }
      }
    }
    return jars.toArray(new File[jars.size()]);
  }
}
